# Erudito — Planejamento Técnico

## 1. Visão geral

Este documento define o planejamento técnico e a arquitetura do aplicativo **Erudito**, desenvolvido segundo a abordagem Spec-Driven Development (SDD) em cinco bundles progressivos.

## 2. Tecnologias e Ferramentas

| Tecnologia | Versão / Detalhe | Finalidade |
|---|---|---|
| Kotlin | `2.2.10` | Linguagem principal do aplicativo (JVM 11) |
| Jetpack Compose | BOM `2024.09.00` (Material 3) | Desenvolvimento das interfaces declarativas e acessibilidade |
| Navigation Compose | `2.8.9` | Navegação entre telas (`EruditoNavHost` e `Screen`) |
| Retrofit + Moshi + OkHttp | `2.12.0` / `1.15.2` / `4.10.0` | Consumo da MediaWiki Action API, Parsoid REST API e logging HTTP condicional (`BuildConfig.DEBUG`) |
| Coil Compose | `2.7.0` | Carregamento assíncrono de imagens HTTPS e cache em disco |
| Room | `2.7.0` (KSP `2.3.5`) | Persistência local SQLite v4 (`erudito_database.db`) e migrations `v1→v2→v3→v4` |
| Jsoup | `1.18.3` | Sanitização DOM (`Safelist`), extração de fórmulas LaTeX e extração textual de fallback |
| AndroidX WebKit + KaTeX local | `1.18.0` / KaTeX `0.19.1` | `WebViewAssetLoader` local e renderização matemática offline sem CDN externa |
| ConnectivityManager (`NetworkCallback`) | API 24+ / `NOT_SUSPENDED` API 28+ | Observação em tempo real de conectividade utilizável (`AndroidConnectivityObserver`) |
| Kotlin Coroutines + Flow | `1.10.2` | Execução assíncrona estruturada, `StateFlow`, processamento HTML em `Dispatchers.Default` e propagação de `CancellationException` |
| Dagger Hilt + Hilt Navigation Compose | `2.55` / `1.2.0` | Injeção de dependências (`EruditoHiltModule`, `@HiltAndroidApp`, `@AndroidEntryPoint`, `@HiltViewModel`) |
| JUnit 4 + Robolectric + Compose UI Test | `4.13.2` / `4.16.1` | Testes unitários, integração Room real, segurança WebView e UI Compose |

## 3. Arquitetura do Projeto

O aplicativo adota **Clean Architecture** combinada ao padrão **MVVM**, integrada via Hilt (`EruditoHiltModule`), com separação estrita de dependências entre camadas:

### 3.1. Presentation (`com.example.erudito.presentation`)
- **Telas Compose:** `HomeScreen`, `CollectionsScreen`, `CollectionDetailScreen`, `ArticleDetailScreen`, `SearchScreen`, `WikipediaArticleScreen`.
- **Componentes reutilizáveis:** `ArticleBodyContent`, `StructuredArticleWebView` (com `EruditoArticleWebView`, `LocalArticleDocumentHandler`, `StructuredArticleFallbackView`), `ArticleHtmlDocumentBuilder`, `StructuredArticleSecurityPolicy`, `CollectionPickerDialog`, `CollectionPickerCoordinator`, `ArticleCard`, `CollectionCard`, `SearchResultCard`, `CreateEditCollectionDialog`, `DeleteConfirmationDialog`, `EruditoTopAppBar`, `EmptyStateView`.
- **ViewModels:** utilizam `StateFlow` para estado imutável e `Channel(Channel.BUFFERED)` para eventos pontuais (`feedbackEvents`), com controle de concorrência por `Job`, proteção contra cliques simultâneos e tratamento explícito de `CancellationException`. A camada depende apenas de casos de uso, modelos e interfaces do **Domain** (`StructuredArticleHtmlProcessor`, `ConnectivityObserver`, `ArticleDomainMapper`).
- **Navegação:** `EruditoNavHost` e `Screen` (`home`, `collections`, `collection/{collectionId}`, `article/{articleId}`, `search`, `wikipedia_article/{pageId}`).

### 3.2. Domain (`com.example.erudito.domain`)
- **Modelos puros:** `Collection`, `Article` (inclui `lastSyncedAt`, `contentFormat`, `extractedFormulaCount`, `unextractableFormulaCount`), `ArticleContentFormat` (`PLAIN_TEXT`, `STRUCTURED_HTML`), `CollectionWithArticles`, `WikipediaSearchResult`, `WikipediaArticle` (`hasExtractedContent`), `WikipediaException`.
- **Contratos e abstrações:** `CollectionRepository`, `ArticleRepository`, `WikipediaRepository`, `ConnectivityObserver`, `StructuredArticleHtmlProcessor` (abstração para sanitização de exibição e extração textual sem acoplar Domain ou Presentation ao Jsoup/Data).
- **Mapeadores de domínio:** `ArticleDomainMapper` (`Article.toWikipediaArticle`).
- **Sincronização (`domain/sync/`):** `SyncPolicy`, `ArticleSyncMerger`, `MergeResult`, `SyncResult`, `RefreshArticleOutcome`, `SkipReason`, `SyncArticleFailure`.
- **Use Cases (`domain/usecase/`):** 17 casos de uso cobrindo RN01–RN11 (`CreateCollectionUseCase`, `RenameCollectionUseCase`, `DeleteCollectionUseCase`, `GetCollectionsUseCase`, `GetCollectionWithArticlesUseCase`, `GetSavedArticlesUseCase`, `GetArticleByIdUseCase`, `ObserveArticleByIdUseCase`, `GetSavedArticleByWikipediaPageIdUseCase`, `SearchWikipediaArticlesUseCase`, `GetWikipediaArticleUseCase`, `SaveWikipediaArticleToCollectionsUseCase`, `AddArticleToCollectionsUseCase`, `RemoveArticleFromCollectionUseCase`, `GetCollectionIdsForArticleUseCase`, `RefreshSavedArticleUseCase`, `SyncSavedArticlesUseCase`).

### 3.3. Data (`com.example.erudito.data`)
- **Repositórios:** `CollectionRepositoryImpl`, `ArticleRepositoryImpl`, `WikipediaRepositoryImpl`.
- **Conectividade:** `AndroidConnectivityObserver` (instância singleton compartilhada via Hilt, verificando `NET_CAPABILITY_INTERNET`, `NET_CAPABILITY_VALIDATED` e `NET_CAPABILITY_NOT_SUSPENDED` no Android P+).
- **Persistência Room v4 (`EruditoDatabase`):**
  - Entidades: `CollectionEntity`, `ArticleEntity`, `CollectionArticleCrossRef` e projeções `CollectionWithArticlesEntity`, `CollectionWithCount`.
  - DAOs: `CollectionDao`, `ArticleDao` (transações atômicas, prevenção de duplicidade e atualização via `@Update`).
  - Migrations: `MIGRATION_1_2` (`wikipediaPageId` + índice único), `MIGRATION_2_3` (`lastSyncedAt`), `MIGRATION_3_4` (`contentFormat`, `extractedFormulaCount`, `unextractableFormulaCount`).
- **Rede & Parsing:** `WikipediaApiService` (Action API + Parsoid REST), `WikipediaResponseDtos` (Moshi), `WikipediaHtmlSanitizer` (implementando `StructuredArticleHtmlProcessor`) e mappers (`CollectionMapper`, `ArticleMapper`, `WikipediaMapper`).

## 4. Decisões Técnicas Consolidadas

### 4.1. Build, Hilt e KSP
- `EruditoApplication` e `MainActivity` utilizam herança explícita `@HiltAndroidApp(Application::class)` e `@AndroidEntryPoint(ComponentActivity::class)` compatível com AGP 9.1.1.
- `gradle.properties` define `ksp.incremental=false` para garantir consistência em builds limpos e com `--rerun-tasks` usando Hilt `2.55` + KSP `2.3.5` com Configuration Cache ativo.
- Dependências não utilizadas pelo projeto (`accompanist-permissions`, `camera-*`, `datastore-preferences`, `play-services-location`) foram mantidas comentadas em `app/build.gradle.kts`.

### 4.2. Offline First, Política de Sincronização e Merge Defensivo
- **Leitura local prioritária:** coleções e artigos salvos são sempre lidos do Room via `Flow`. Leituras online sem salvamento não gravam dados no banco; se offline ou em falha de rede, `WikipediaArticleViewModel` recorre à cópia salva por `wikipediaPageId`.
- **Política de frescor (`SyncPolicy`):** intervalo mínimo padrão de **6 horas** (`6L * 60L * 60L * 1000L` ms). Auto-sync executado no máximo uma vez por ciclo de vida na `HomeViewModel` (ou uma vez após retorno de conectividade na mesma sessão), sem `WorkManager`. Atualização manual usa `forceRefresh = true`.
- **Merge defensivo (`ArticleSyncMerger`):** protege conteúdo `STRUCTURED_HTML` local íntegro (`unextractableFormulaCount == 0`) contra sobrescrita por `PLAIN_TEXT`, respostas vazias (`hasExtractedContent == false`) ou `STRUCTURED_HTML` com fórmulas indisponíveis. Atualiza `lastUpdated` somente quando há alteração real de conteúdo/metadados e preserva associações N:N usando `@Update`.

### 4.3. Conteúdo Estruturado, KaTeX Local e Segurança da WebView
- **Pipeline Parsoid (`WikipediaRepositoryImpl` + `WikipediaHtmlSanitizer`):** obtém HTML de `w/rest.php/v1/page/{title}/html`, remove scripts/frames/estilos/ruído editorial, extrai fórmulas (`data-mw`, MathML `<annotation>`, `alttext`), normaliza sintaxe TeX compatível com KaTeX, força `https://` em imagens e aplica `Safelist` estrita.
- **Processamento assíncrono:** `StructuredArticleWebView` e `StructuredArticleFallbackView` executam sanitização de exibição e extração de texto em `Dispatchers.Default` (`produceState` + `withContext`).
- **Isolamento e Segurança (`StructuredArticleSecurityPolicy` + `ArticleHtmlDocumentBuilder`):**
  - Documento HTML e assets estáticos (KaTeX `0.19.1`, `article.css`, `math_renderer.js`) são servidos localmente via `WebViewAssetLoader` na origem `https://appassets.androidplatform.net/`, com `Cache-Control: no-store` para o documento dinâmico.
  - Política de Conteúdo (CSP) restringe scripts, estilos e fontes à origem local e permite imagens apenas via `https:`.
  - `WebSettings` desabilita acesso a `file://`, `content://`, DOM Storage, database, geolocalização, janelas automáticas e mixed content. Links externos passam por validação `http`/`https` e são delegados via `LocalUriHandler`.
  - Erros no frame principal ou `onRenderProcessGone` realizam limpeza idempotente da `WebView` e ativam o fallback textual assíncrono.

### 4.4. Validação Final e Entrega (Bundle 5)
- **Etapa 1:** correção Hilt/KSP (`--rerun-tasks`) e limpeza de dependências Gradle.
- **Etapa 2:** correções de Clean Architecture (`StructuredArticleHtmlProcessor`, `ArticleDomainMapper`), compartilhamento de `AndroidConnectivityObserver`, processamento HTML em `Dispatchers.Default` e propagação consistente de `CancellationException`.
- **Etapa 3:** extração de `CollectionPickerCoordinator`, garantia HTTPS para imagens, melhorias de UI/acessibilidade e limpeza de código.
- **Etapa 4:** auditoria de cobertura, adição de 3 testes pontuais, validação dos 5 comandos Gradle (incluindo `gradle clean` e `--rerun-tasks`), aprovação de **44 suítes / 288 testes** e inspeção do APK `app-debug.apk`.
- **Etapa 5:** atualização integral da documentação (`README.md`, `SPECIFICATION.md`, `PLANNING.md`, `TASKS.md`, `TEST_RESULTS.md`) e empacotamento do entregável `Erudito-1.0-debug.zip` na raiz do projeto.
