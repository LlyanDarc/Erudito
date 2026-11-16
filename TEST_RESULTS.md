# Erudito — Registro de Resultados de Testes

**Data da Última Revisão:** 16 de Novembro de 2026  
**Ambiente:** Android SDK 36 (`compileSdk 36` / `targetSdk 36` / `minSdk 24`), Gradle 9.3.1, Kotlin 2.2.10, Robolectric 4.16.1, JUnit 4.13.2, Dagger Hilt 2.55 (KSP), Room 2.7.0  
**Comando Final Executado:** `gradle :app:compileDebugKotlin :app:testDebugUnitTest --rerun-tasks` (+ verificação final via `compile_applet`)  
**Escopo Validado:** Bundles 1, 2, 3 e **Bundle 4 — Offline First e Sincronização** (Etapas 1, 2, 3 e 4 concluídas; Bundle 5 pendente)

---

## 1. Histórico Consolidado por Marco e Etapa (Execuções Separadas)

> **Nota metodológica:** Cada marco abaixo representa uma execução independente da suíte completa no momento da respectiva validação. Os totais **não devem ser somados** entre si. Campos não preservados integralmente no histórico são marcados explicitamente.

| Marco / Etapa | Escopo Validado | Comando Executado | Suítes | Executados | Aprovados | Falhas / Erros | Ignorados | Compilação | Observações Relevantes |
|---|---|---|---|---|---|---|---|---|---|
| **Bundles 1–3** (15/11/2026) | Biblioteca offline, pesquisa/leitura Wikipédia e integração N:N (`v1 → v2`) | `gradle :app:compileDebugKotlin :app:testDebugUnitTest --rerun-tasks` | 30 | 156 | 156 | 0 | 0 | Sucesso | Marco de encerramento do Bundle 3 (`2m 2s`). |
| **Bundle 4 — Etapa 1** | Conectividade (`NET_CAPABILITY_VALIDATED`), leitura local prioritária, fallback offline e observação reativa | *Não disponível nos registros anteriores* | *Não disponível nos registros anteriores* | *Não disponível nos registros anteriores* | 175 | 0 | 0 | Sucesso | Incluiu validação posterior específica para exigência de `NET_CAPABILITY_VALIDATED` em `AndroidConnectivityObserverTest`. |
| **Bundle 4 — Etapa 2** | Room v3 (`MIGRATION_2_3`), `lastSyncedAt`, `SyncPolicy`, `ArticleSyncMerger` (`hasExtractedContent`), `@Update` e redirecionamento | *Não disponível nos registros anteriores* (via `:app:testDebugUnitTest` + `compile_applet`) | 35 | 202 | 202 | 0 | 0 | Sucesso | Contagem obtida após correção pontual do merge defensivo para eliminar heurística e usar `hasExtractedContent`. |
| **Bundle 4 — Etapa 3** | Auto-sync na `HomeScreen`, `HomeSyncStatusBanner`, atualização manual (`forceRefresh`) em `ArticleDetailScreen` e cópia salva na `WikipediaArticleScreen` | *Não disponível nos registros anteriores* (via `:app:testDebugUnitTest` + `compile_applet`) | 38 | 219 | 219 | 0 | 0 | Sucesso | Aprovado em auditoria técnica nas 5 áreas (Home, status, detalhes/Wikipédia, Room N:N e testes). |
| **Bundle 4 — Etapa 4 (Validação Final — 16/11/2026)** | Consolidação documental, auditoria de conformidade e reexecução integral sem cache (`--rerun-tasks`) | `gradle :app:compileDebugKotlin :app:testDebugUnitTest --rerun-tasks` (+ `compile_applet`) | **38** | **219** | **219** | **0** | **0** | **Sucesso** (`BUILD SUCCESSFUL`) | **33 tarefas executadas em 2m 32s** (`:app:kspDebugKotlin`, `:app:compileDebugKotlin`, `:app:processDebugResources`, `:app:testDebugUnitTest`). |

---

## 2. Detalhamento Completo da Execução Final (Bundle 4 — Etapa 4)

Execução realizada em **16 de Novembro de 2026** com `gradle :app:compileDebugKotlin :app:testDebugUnitTest --rerun-tasks`:

| Camada | Test Suite | Executados | Aprovados | Reprovados | Ignorados | Tempo |
|---|---|---|---|---|---|---|
| Domain (Sync) | `ArticleSyncMergerAndPolicyTest` | 11 | 11 | 0 | 0 | 0.00s |
| Domain | `SyncUseCasesTest` | 11 | 11 | 0 | 0 | 0.54s |
| Domain | `ObserveArticleByIdAndLocalLookupUseCasesTest` | 3 | 3 | 0 | 0 | 0.01s |
| Domain | `ArticleCollectionIntegrationUseCasesTest` | 10 | 10 | 0 | 0 | 0.05s |
| Domain | `CreateCollectionUseCaseTest` | 4 | 4 | 0 | 0 | 0.01s |
| Domain | `RenameCollectionUseCaseTest` | 4 | 4 | 0 | 0 | 0.01s |
| Domain | `DeleteCollectionUseCaseTest` | 1 | 1 | 0 | 0 | 0.00s |
| Domain | `GetCollectionsUseCaseTest` | 2 | 2 | 0 | 0 | 0.00s |
| Domain | `GetCollectionWithArticlesUseCaseTest` | 2 | 2 | 0 | 0 | 0.01s |
| Domain | `GetSavedArticlesUseCaseTest` | 2 | 2 | 0 | 0 | 0.00s |
| Domain | `GetArticleByIdUseCaseTest` | 2 | 2 | 0 | 0 | 0.00s |
| Domain | `WikipediaUseCasesTest` | 7 | 7 | 0 | 0 | 0.01s |
| Data (Connectivity) | `AndroidConnectivityObserverTest` | 5 | 5 | 0 | 0 | 4.22s |
| Data (Room Integration) | `RoomDatabaseIntegrationTest` | 18 | 18 | 0 | 0 | 4.15s |
| Data (Mappers) | `ArticleAndWikipediaMapperTest` | 5 | 5 | 0 | 0 | 0.01s |
| Data (Remote DTOs) | `WikipediaDtoDeserializationTest` | 7 | 7 | 0 | 0 | 0.23s |
| Data (Repositories) | `ArticleRepositoryImplTest` | 6 | 6 | 0 | 0 | 0.34s |
| Data (Repositories) | `WikipediaRepositoryImplTest` | 9 | 9 | 0 | 0 | 0.13s |
| Presentation (ViewModel) | `HomeSyncTest` | 7 | 7 | 0 | 0 | 0.53s |
| Presentation (ViewModel) | `ArticleDetailSyncTest` | 7 | 7 | 0 | 0 | 1.01s |
| Presentation (ViewModel) | `WikipediaArticleRefreshSavedCopyTest` | 3 | 3 | 0 | 0 | 0.23s |
| Presentation (ViewModel) | `SearchViewModelTest` | 10 | 10 | 0 | 0 | 0.02s |
| Presentation (ViewModel) | `WikipediaArticleViewModelTest` | 17 | 17 | 0 | 0 | 0.08s |
| Presentation (ViewModel) | `CollectionDetailViewModelTest` | 6 | 6 | 0 | 0 | 0.03s |
| Presentation (ViewModel) | `ArticleDetailViewModelTest` | 8 | 8 | 0 | 0 | 0.07s |
| Presentation (UI & Compose) | `HomeConnectivityTest` | 1 | 1 | 0 | 0 | 0.14s |
| Presentation (UI & Compose) | `HomeErrorHandlingTest` | 2 | 2 | 0 | 0 | 0.18s |
| Presentation (UI & Compose) | `HomeScreenDuplicateKeyTest` | 1 | 1 | 0 | 0 | 0.11s |
| Presentation (UI & Compose) | `SearchScreenTest` | 7 | 7 | 0 | 0 | 0.97s |
| Presentation (UI & Compose) | `WikipediaArticleScreenTest` | 11 | 11 | 0 | 0 | 1.42s |
| Presentation (UI & Compose) | `CollectionPickerDialogTest` | 5 | 5 | 0 | 0 | 1.03s |
| Presentation (UI & Compose) | `CollectionDetailScreenTest` | 3 | 3 | 0 | 0 | 0.83s |
| Presentation (UI & Compose) | `ArticleDetailScreenTest` | 7 | 7 | 0 | 0 | 5.59s |
| Presentation (UI & Compose) | `CollectionFeedbackMessagesTest` | 5 | 5 | 0 | 0 | 1.09s |
| Presentation (Navigation) | `EruditoNavigationTest` | 5 | 5 | 0 | 0 | 1.16s |
| Presentation (E2E Room + Nav) | `Bundle3EndToEndIntegrationTest` | 3 | 3 | 0 | 0 | 2.79s |
| Presentation / Resources | `ExampleRobolectricTest` | 1 | 1 | 0 | 0 | 8.47s |
| Unit | `ExampleUnitTest` | 1 | 1 | 0 | 0 | 0.00s |
| **Total (Execução Final)** | **38 Suítes** | **219** | **219** | **0** | **0** | **2m 32s (Gradle build + rerun-tasks)** |

**Resultado Geral:** **SUCESSO (38 suítes, 219 testes executados, 219 aprovados, 0 falhas, 0 erros, 0 ignorados)**

---

## 3. Cobertura por Bundle e Etapa

### 3.1. Bundle 1 — Biblioteca Pessoal Offline
- Regras de negócio RN01–RN07 na camada Domain (`CreateCollectionUseCaseTest`, `RenameCollectionUseCaseTest`, `DeleteCollectionUseCaseTest`, `GetCollectionsUseCaseTest`, `GetCollectionWithArticlesUseCaseTest`, `GetSavedArticlesUseCaseTest`, `GetArticleByIdUseCaseTest`).
- Persistência SQLite, integridade referencial, remoção em cascata (RN05) e pré-população atômica (`RoomDatabaseIntegrationTest`).
- Feedbacks visuais, tratamento de erros e chaves únicas em listas (`CollectionFeedbackMessagesTest`, `HomeErrorHandlingTest`, `HomeScreenDuplicateKeyTest`).

### 3.2. Bundle 2 — Pesquisa e Consulta à Wikipédia
- Desserialização de DTOs da API MediaWiki e mapeamento para modelos de domínio (`WikipediaDtoDeserializationTest`, `ArticleAndWikipediaMapperTest`, `WikipediaRepositoryImplTest`, `WikipediaUseCasesTest`).
- Fluxo de pesquisa e leitura online (`SearchViewModelTest`, `SearchScreenTest`, `WikipediaArticleViewModelTest`, `WikipediaArticleScreenTest`, `EruditoNavigationTest`).

### 3.3. Bundle 3 — Integração entre Artigos e Coleções (Relação N:N)
- **Banco Room & Migração v1 → v2 (`RoomDatabaseIntegrationTest`):** migração `MIGRATION_1_2` preservando dados legados, índice único para `wikipediaPageId` não nulo, reutilização de artigos por `wikipediaPageId` e por fallback de URL legado, rollback atômico e remoção individual de `CollectionArticleCrossRef`.
- **Repositório, Casos de Uso & Telas (`ArticleRepositoryImplTest`, `ArticleCollectionIntegrationUseCasesTest`, `CollectionPickerDialogTest`, `CollectionDetailViewModelTest`, `CollectionDetailScreenTest`, `ArticleDetailViewModelTest`, `ArticleDetailScreenTest`):** seleção múltipla, criação inline de coleções, desvinculação segura e abertura da URL persistida via `LocalUriHandler`.
- **Integração Ponta a Ponta (`Bundle3EndToEndIntegrationTest`, `EruditoNavigationTest`):** validação completa com `EruditoDatabase` real em memória, repositórios reais, UseCases reais, ViewModels reais e `EruditoNavHost`.

### 3.4. Bundle 4 — Offline First e Sincronização
- **Etapa 1 — Conectividade, Leitura Local Prioritária e Fallback Offline:**
  - `AndroidConnectivityObserverTest`: validação de `NET_CAPABILITY_INTERNET` + `NET_CAPABILITY_VALIDATED` + `NET_CAPABILITY_NOT_SUSPENDED`, rede sem `VALIDATED` tratada como offline, rede sem `INTERNET` como offline, perda/recuperação de rede e registro/cancelamento de `NetworkCallback`.
  - `ObserveArticleByIdAndLocalLookupUseCasesTest`, `ArticleRepositoryImplTest`, `ArticleAndWikipediaMapperTest`: observação contínua por `Flow` e conversão `Article.toWikipediaArticle()`.
  - `WikipediaArticleViewModelTest`, `WikipediaArticleScreenTest`, `SearchViewModelTest`, `SearchScreenTest`, `ArticleDetailViewModelTest`, `HomeConnectivityTest`, `EruditoNavigationTest`: leitura offline prioritária e fallback automático para cópia local salva sem auto-salvamento de artigos online.
- **Etapa 2 — Banco de Dados v3, Estratégia de Sincronização e Merge Defensivo:**
  - `RoomDatabaseIntegrationTest`: validação das migrações `v1 → v2 → v3` e `v2 → v3` (`MIGRATION_2_3`), verificação de `lastSyncedAt = NULL` nos registros pré-existentes, preservação integral de registros e associações `collection_article_cross_ref` após atualização via `@Update`, e checagem de colisão do índice único `wikipediaPageId`.
  - `ArticleSyncMergerAndPolicyTest` e `ArticleAndWikipediaMapperTest`: janela de frescor de 6 horas (`SyncPolicy`), proteção explícita por `hasExtractedContent` (impedindo sobrescrita quando `extract` vem vazio/nulo com fallback de `description`), preservação de descrição/imagem locais, `lastUpdated` inalterado sem mudança real de conteúdo e atualização segura de `lastSyncedAt`.
  - `SyncUseCasesTest`: `RefreshSavedArticleUseCase` e `SyncSavedArticlesUseCase` cobrindo elegibilidade, `forceRefresh`, artigos sem `wikipediaPageId`, colisões em redirecionamento, falhas parciais, falhas totais, modo offline e propagação de `CancellationException`.
- **Etapa 3 — Integração da Sincronização com a Interface:**
  - `HomeSyncTest`: auto-sync único por ciclo de vida da `HomeViewModel`, não redisparo por emissões do Room, recuperação única ao retornar online, diferenciação entre atualizações/sem alterações/falha parcial/falha total/offline, retry manual sem recriar ViewModel e guarda contra execução simultânea.
  - `ArticleDetailSyncTest` e `ArticleDetailScreenTest`: atualização manual (`forceRefresh = true`), exibição da data/hora de `lastSyncedAt`, estado para artigo sem vínculo remoto, isolamento de mensagens técnicas de exceção e prevenção de duplo disparo.
  - `WikipediaArticleRefreshSavedCopyTest` e `WikipediaArticleScreenTest`: atualização manual da cópia local salva (`"Atualizar cópia salva"`) e guarda contra chamadas concorrentes.

---

## 4. Pendências para Validação Manual em Ambiente Android Studio / Dispositivo Real

As seguintes verificações dependem de execução manual em dispositivo físico ou emulador interativo no Android Studio (fora do alcance dos testes JVM/Robolectric):

- **Integração real com a API MediaWiki (`pt.wikipedia.org`):** validar latência, respostas reais de pesquisa/extrato/redirecionamentos e aceitação do cabeçalho `User-Agent` configurado.
- **Conectividade real em hardware (`AndroidConnectivityObserver`):** validar transições reais entre Wi-Fi, dados móveis, modo avião e **portal cativo** (rede com `NET_CAPABILITY_INTERNET` sem `NET_CAPABILITY_VALIDATED`).
- **Cache em disco do Coil:** confirmar carregamento real de imagens online e exibição via cache em disco após ativar o modo avião.
- **Migração Room em disco e persistência entre processos (`erudito_database.db`):** testar atualização real de instalação prévia (`v1`/`v2 → v3`) preservando artigos salvos e coleções N:N, além de encerramento completo (*force stop*) e reabertura do processo Android.
- **Experiência visual e interação real:** conferência de contraste/densidade Material 3, banners de status na `HomeScreen`, snackbars e abertura de link externo no navegador (`LocalUriHandler`) em `WikipediaArticleScreen` e `ArticleDetailScreen`.
- **Geração e validação de artefato instalável:** geração de APK debug (`gradle assembleDebug`) para instalação manual no dispositivo de avaliação (prevista na etapa de entrega do Bundle 5).
