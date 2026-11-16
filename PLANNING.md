# Erudito — Planejamento Técnico

## 1. Visão geral

Este documento define o planejamento técnico e a estratégia de desenvolvimento do aplicativo Erudito, utilizando a abordagem Spec-Driven Development (SDD).

## 2. Tecnologias e Ferramentas

| Tecnologia | Finalidade |
|---|---|
| Kotlin | Linguagem principal do aplicativo (`2.2.10`) |
| Jetpack Compose | Desenvolvimento das interfaces (Material 3) |
| Navigation Compose | Navegação entre telas (`2.8.9`) |
| Retrofit + Moshi + OkHttp | Consumo e desserialização da API MediaWiki da Wikipédia (`2.12.0` / `1.15.2` / `4.10.0`) |
| Coil | Carregamento assíncrono de imagens com fallback offline (`2.7.0`) |
| Room | Persistência local e migrações de schema (`v1 → v2 → v3` com `MIGRATION_1_2` e `MIGRATION_2_3`) |
| ConnectivityManager (`NetworkCallback`) | Observação em tempo real de conectividade utilizável (`AndroidConnectivityObserver`) |
| Kotlin Coroutines | Execução assíncrona com tratamento explícito de `CancellationException` |
| Flow / StateFlow | Observação reativa do banco Room, conectividade e estados de UI |
| Hilt | Injeção de dependências (`EruditoHiltModule`, `@HiltAndroidApp`, `@HiltViewModel`) |
| JUnit 4 | Testes unitários de regras de domínio, merge, casos de uso e ViewModels |
| Robolectric | Testes locais de UI Compose, conectividade Android, navegação e integração Room no JVM (`4.16.1`) |

## 3. Arquitetura do Projeto

O aplicativo utiliza **Clean Architecture** combinada ao padrão **MVVM**, organizada em três camadas principais integradas via Hilt (`EruditoHiltModule`):

- **Presentation:**
  - Interfaces em Jetpack Compose (`HomeScreen`, `CollectionsScreen`, `CollectionDetailScreen`, `ArticleDetailScreen`, `SearchScreen`, `WikipediaArticleScreen`).
  - Componentes reutilizáveis (`CollectionPickerDialog`, `ArticleCard`, `CollectionCard`, `SearchResultCard`, diálogos de criação/renomeação/exclusão, `EruditoTopAppBar`, `EmptyStateView`).
  - ViewModels com `StateFlow` e `Channel` de eventos (`feedbackEvents`), incluindo `HomeSyncStatus` (`Idle`, `Syncing`, `CompletedWithUpdates`, `CompletedNoChanges`, `PartialFailure`, `Failed`, `Offline`), controle de concorrência por `Job` e estados de carregamento/fallback offline.
  - Navegação tipada via `EruditoNavHost` e `Screen`.
- **Domain:**
  - Modelos puros (`Collection`, `Article` com `lastSyncedAt`, `CollectionWithArticles`, `WikipediaSearchResult`, `WikipediaArticle` com `hasExtractedContent`, `WikipediaException`).
  - Contratos de repositório e conectividade (`CollectionRepository`, `ArticleRepository`, `WikipediaRepository`, `ConnectivityObserver`).
  - Componentes de sincronização em `domain/sync/` (`SyncPolicy`, `ArticleSyncMerger`, `MergeResult`, `SyncResult`, `RefreshArticleOutcome`, `SkipReason`, `SyncArticleFailure`).
  - Use Cases cobrindo RN01–RN10 (`CreateCollectionUseCase`, `RenameCollectionUseCase`, `DeleteCollectionUseCase`, `GetCollectionsUseCase`, `GetCollectionWithArticlesUseCase`, `GetSavedArticlesUseCase`, `GetArticleByIdUseCase`, `ObserveArticleByIdUseCase`, `GetSavedArticleByWikipediaPageIdUseCase`, `SearchWikipediaArticlesUseCase`, `GetWikipediaArticleUseCase`, `SaveWikipediaArticleToCollectionsUseCase`, `AddArticleToCollectionsUseCase`, `RemoveArticleFromCollectionUseCase`, `GetCollectionIdsForArticleUseCase`, `RefreshSavedArticleUseCase`, `SyncSavedArticlesUseCase`).
- **Data:**
  - Repositórios (`CollectionRepositoryImpl`, `ArticleRepositoryImpl`, `WikipediaRepositoryImpl`).
  - Conectividade (`AndroidConnectivityObserver` verificando `NET_CAPABILITY_INTERNET`, `NET_CAPABILITY_VALIDATED` e `NET_CAPABILITY_NOT_SUSPENDED`).
  - Room Database v3 (`EruditoDatabase`, `MIGRATION_1_2`, `MIGRATION_2_3`), DAOs (`CollectionDao`, `ArticleDao`), entidades (`CollectionEntity`, `ArticleEntity`, `CollectionArticleCrossRef`) e relações (`CollectionWithArticlesEntity`, `CollectionWithCount`).
  - API MediaWiki (`WikipediaApiService`, `WikipediaResponseDtos`) e mappers (`CollectionMapper`, `ArticleMapper`, `WikipediaMapper`).

## 4. Decisões Técnicas e Estratégia do Bundle 4 (Offline First e Sincronização)

### 4.1. Ordem de Implementação Executada
1. **Etapa 1 — Conectividade e Leitura Local Prioritária:** implementação de `ConnectivityObserver` / `AndroidConnectivityObserver`, indicadores reais de conectividade, `ObserveArticleByIdUseCase`, `GetSavedArticleByWikipediaPageIdUseCase`, mapeamento `Article.toWikipediaArticle()` e fallback automático offline/rede em `WikipediaArticleViewModel` sem auto-salvamento.
2. **Etapa 2 — Banco de Dados, Estratégia de Sincronização e Merge Defensivo:** adição de `lastSyncedAt` em `ArticleEntity`, atualização do `EruditoDatabase` para versão 3 (`MIGRATION_2_3` mantendo `lastSyncedAt = NULL` nos registros existentes), `SyncPolicy` (6h), `ArticleSyncMerger` com flag explícita `WikipediaArticle.hasExtractedContent`, `RefreshSavedArticleUseCase` e `SyncSavedArticlesUseCase` usando `ArticleDao.updateArticle` (`@Update`).
3. **Etapa 3 — Integração da Sincronização com a Interface:** auto-sync controlado na `HomeViewModel` + `HomeSyncStatusBanner`, atualização manual (`forceRefresh = true`) na `ArticleDetailScreen` e atualização manual da cópia salva na `WikipediaArticleScreen`, com guardas de concorrência e isolamento de erros.
4. **Etapa 4 — Documentação, Auditoria e Validação Final:** consolidação documental, auditoria de consistência e validação completa de compilação e testes automatizados.

### 4.2. Política de Sincronização e Concorrência
- **Intervalo de frescor (`SyncPolicy`):** padrão de **6 horas** (`6L * 60L * 60L * 1000L` ms). Artigos com `lastSyncedAt == null` ou fora da janela são elegíveis na sincronização automática; artigos sem `wikipediaPageId` válido (`null` ou `<= 0`) são ignorados sem chamadas remotas.
- **Auto-sync na Home:** disparado no máximo uma vez por ciclo de vida do `HomeViewModel` quando há artigos sincronizáveis e rede válida (com recuperação automática única caso a tela abra offline e a rede retorne). Recomposições e emissões reativas do Room não disparam sincronizações recorrentes.
- **Atualização manual (`forceRefresh = true`):** disponível na `ArticleDetailScreen` e para artigos já salvos na `WikipediaArticleScreen`, ignorando a janela de 6h sem duplicar chamadas concorrentes (`syncJob`, `refreshJob`, `refreshSavedJob`).
- **Escopo sem WorkManager:** conforme o planejamento do Bundle 4, não há agendamento periódico em segundo plano com `WorkManager`.

### 4.3. Merge Defensivo, Tratamento de Falhas e Preservação N:N
- **Merge defensivo (`ArticleSyncMerger`):** a propriedade explícita `hasExtractedContent` gerada pelo `WikipediaMapper` diferencia um `extract` real da Wikipédia de conteúdo preenchido apenas por fallback de `description`. Conteúdo remoto vazio ou `hasExtractedContent == false` nunca sobrescreve conteúdo local válido.
- **Controle temporal:** `lastSyncedAt` só é atualizado após resposta remota bem-sucedida; `lastUpdated` só é alterado quando título, URL, descrição, imagem ou conteúdo realmente mudam, preservando a ordenação dos artigos na biblioteca.
- **Preservação de vínculos N:N e redirecionamentos:** a persistência durante sincronização utiliza exclusivamente `@Update` (`ArticleDao.updateArticle`), evitando `OnConflictStrategy.REPLACE` e preservando todas as linhas de `collection_article_cross_ref`. Em eventual redirecionamento com mudança de `pageId`, `RefreshSavedArticleUseCase` verifica colisão no índice único de `wikipediaPageId` antes de atualizar.
- **Tratamento de falhas e cancelamento:** `SyncResult` e `RefreshArticleOutcome` distinguem atualizados, inalterados, ignorados, falha parcial, falha total e offline; `CancellationException` é sempre propagada após limpeza dos estados de carregamento; exceções técnicas internas não são expostas na interface.

### 4.4. Testes Realizados
- Testes unitários de domínio (`SyncUseCasesTest`, `ArticleSyncMergerAndPolicyTest`, `ObserveArticleByIdAndLocalLookupUseCasesTest`), testes de dados e migração Room `v1 → v2 → v3` (`RoomDatabaseIntegrationTest`, `AndroidConnectivityObserverTest`, `ArticleRepositoryImplTest`, `ArticleAndWikipediaMapperTest`) e testes de ViewModel/Compose/Robolectric (`HomeSyncTest`, `ArticleDetailSyncTest`, `WikipediaArticleRefreshSavedCopyTest`, `HomeConnectivityTest`, `EruditoNavigationTest`, `Bundle3EndToEndIntegrationTest`), detalhados em `TEST_RESULTS.md`.

