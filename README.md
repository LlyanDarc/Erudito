# Erudito — Biblioteca Pessoal de Conhecimento

O **Erudito** é um aplicativo Android nativo desenvolvido em **Kotlin** e **Jetpack Compose** que funciona como uma biblioteca pessoal de conhecimento integrada à **Wikipédia em Português** (`pt.wikipedia.org`). O aplicativo permite pesquisar artigos, ler conteúdos estruturados com renderização offline de fórmulas matemáticas em LaTeX, salvar artigos em múltiplas coleções personalizadas e consultar ou sincronizar a biblioteca seguindo a arquitetura **Offline First**.

---

## 1. Objetivo e Problema Resolvido

Durante pesquisas na web, conteúdos de estudo frequentemente ficam dispersos entre abas, favoritos e históricos de navegação, dificultando a organização por temas e o acesso sem internet.

O **Erudito** resolve esse problema oferecendo um repositório pessoal centralizado no dispositivo:
- Pesquisa direta na Wikipédia em Português com pré-visualização estruturada.
- Armazenamento local persistente com vínculo por `wikipediaPageId` e organização flexível em múltiplas coleções (relação N:N) sem duplicação do artigo.
- Leitura fiel de artigos estruturados (títulos, tabelas, listas, imagens HTTPS e fórmulas matemáticas) mesmo offline.
- Sincronização defensiva que preserva o conteúdo local íntegro diante de falhas de rede ou respostas remotas incompletas.

---

## 2. Status do Projeto

| Bundle | Descrição | Status |
|---|---|---|
| **Bundle 1** | Biblioteca pessoal offline e gerenciamento de coleções | **Concluído** |
| **Bundle 2** | Pesquisa e consulta à Wikipédia | **Concluído** |
| **Bundle 3** | Integração entre artigos e coleções (relação N:N) | **Concluído** |
| **Bundle 4** | Offline First, sincronização e conteúdo estruturado (Parsoid + KaTeX) | **Concluído** |
| **Bundle 5** | Auditoria técnica, Hilt/KSP, Clean Architecture, testes, APK e documentação | **Concluído (validação técnica e entrega)** |

---

## 3. Funcionalidades Implementadas

- **Painel Inicial da Biblioteca (`HomeScreen`):** resumo quantitativo, atalhos para coleções e pesquisa, lista reativa de artigos salvos, indicador de conectividade e banner discreto de sincronização automática (`HomeSyncStatusBanner`).
- **Gerenciamento de Coleções (`CollectionsScreen`, `CollectionDetailScreen`):** criação, listagem com contagem de artigos, renomeação (com validação de nome vazio/duplicado) e exclusão com confirmação e remoção em cascata apenas dos vínculos.
- **Pesquisa na Wikipédia (`SearchScreen`):** busca em `pt.wikipedia.org` via MediaWiki Action API, cards com título, descrição, miniatura (Coil) e tratamento específico de estados (`Initial`, `Loading`, `Success`, `Empty`, `Error`) e modo offline.
- **Leitura Online e Fallback Local (`WikipediaArticleScreen`):** busca de metadados + HTML estruturado da API REST Parsoid, exibição formatada via `StructuredArticleWebView` (ou `PLAIN_TEXT` para registros legados), fallback automático para cópia salva no Room quando offline ou em falha de rede, e botão **"Atualizar cópia salva"** quando o artigo já está na biblioteca.
- **Leitura Offline (`ArticleDetailScreen`):** leitura integral offline com exibição de última sincronização, gerenciamento das coleções associadas, atualização manual (`forceRefresh = true`) e abertura segura da URL original no navegador externo.
- **Organização Multi-Coleção (`CollectionPickerDialog` + `CollectionPickerCoordinator`):** associação de um artigo a uma ou várias coleções simultaneamente, indicação `"Já salvo"`, criação inline de novas coleções e remoção individual de vínculos sem apagar o artigo da biblioteca nem de outras coleções.
- **Renderização Estruturada e Matemática (Parsoid + KaTeX):** sanitização HTML segura com Jsoup, extração e normalização de fórmulas LaTeX (`data-mw`, MathML `<annotation>`, `alttext`), aviso informativo quando alguma expressão não pode ser extraída, e renderização offline com **KaTeX 0.19.1** empacotado nos assets do APK.

---

## 4. Tecnologias e Bibliotecas Utilizadas

| Categoria | Tecnologia / Biblioteca | Versão | Uso no Projeto |
|---|---|---|---|
| **Linguagem & Plataforma** | Kotlin / Android SDK | Kotlin `2.2.10` / `compileSdk 36`, `targetSdk 36`, `minSdk 24` | Código-fonte principal e alvo de compatibilidade |
| **Build & Processamento** | Android Gradle Plugin / Gradle / KSP | AGP `9.1.1` / Gradle `9.3.1` / KSP `2.3.5` | Compilação, geração de código Hilt, Room e Moshi |
| **Interface** | Jetpack Compose + Material 3 | Compose BOM `2024.09.00` | UI declarativa, temas claro/escuro e acessibilidade |
| **Navegação** | Navigation Compose | `2.8.9` | Roteamento entre telas no `EruditoNavHost` |
| **Injeção de Dependências** | Dagger Hilt + Hilt Navigation Compose | Hilt `2.55` / `1.2.0` | Grafo de dependências (`EruditoHiltModule`) |
| **Persistência Local** | Room (`room-runtime`, `room-ktx`, `room-compiler`) | `2.7.0` | Banco SQLite v4 (`erudito_database.db`), migrations e `Flow` |
| **Rede & Serialização** | Retrofit / OkHttp / Logging Interceptor / Moshi | `2.12.0` / `4.10.0` / `1.15.2` | MediaWiki Action API, Parsoid REST e DTOs (`moshi-kotlin-codegen`) |
| **Imagens** | Coil Compose | `2.7.0` | Carregamento assíncrono e cache de miniaturas/imagens |
| **HTML & WebView** | Jsoup / AndroidX WebKit / KaTeX local | Jsoup `1.18.3` / WebKit `1.18.0` / KaTeX `0.19.1` | Sanitização DOM, `WebViewAssetLoader` e fórmulas offline |
| **Assincronia** | Kotlinx Coroutines (`core`, `android`) | `1.10.2` | Execução assíncrona estruturada e `StateFlow` |
| **Testes** | JUnit 4 / Robolectric / Coroutines Test / Compose UI Test | `4.13.2` / `4.16.1` / `1.10.2` | Testes unitários, integração Room real e testes de UI Compose |

---

## 5. Arquitetura (Clean Architecture + MVVM)

O projeto segue **Clean Architecture** combinada ao padrão **MVVM**, com dependência apontando estritamente das camadas externas para o núcleo de domínio:

```text
Presentation  ───►  Domain  ◄───  Data
(Compose + VM)   (Regras/Contratos)  (Room + Retrofit + Jsoup + Conectividade)
```

- **Presentation (`presentation/`):** telas Jetpack Compose, estados imutáveis de UI, `EruditoNavHost`, `CollectionPickerCoordinator`, componentes compartilhados (`ArticleBodyContent`, `StructuredArticleWebView`) e `ViewModel`s com `StateFlow` e `Channel` de eventos. Não possui dependências diretas da camada `Data`.
- **Domain (`domain/`):** modelos puros (`Article`, `Collection`, `CollectionWithArticles`, `WikipediaArticle`, `ArticleContentFormat`, `WikipediaException`), interfaces (`ArticleRepository`, `CollectionRepository`, `WikipediaRepository`, `ConnectivityObserver`, `StructuredArticleHtmlProcessor`), mapeadores de domínio (`ArticleDomainMapper`), políticas de sincronização (`SyncPolicy`, `ArticleSyncMerger`, `SyncResult`) e 17 Use Cases cobrindo todas as regras de negócio.
- **Data (`data/`):** implementação dos repositórios, banco Room v4 (`EruditoDatabase`, DAOs, entidades e migrations), serviços HTTP (`WikipediaApiService`), sanitização e extração HTML/LaTeX (`WikipediaHtmlSanitizer` implementando `StructuredArticleHtmlProcessor`), DTOs Moshi, mappers de persistência/rede e `AndroidConnectivityObserver`.
- **Injeção de Dependências (`di/EruditoHiltModule.kt`):** módulo Hilt `@Singleton` provendo `Moshi`, `OkHttpClient` (com `User-Agent` institucional e `HttpLoggingInterceptor` condicionado a `BuildConfig.DEBUG`), `Retrofit`, `EruditoDatabase`, DAOs, repositórios, `AndroidConnectivityObserver`, `WikipediaHtmlSanitizer` (vinculado a `StructuredArticleHtmlProcessor`), `SyncPolicy` e Use Cases.
- **Configuração Hilt + KSP:** o projeto utiliza `@HiltAndroidApp(Application::class)` em `EruditoApplication` e `@AndroidEntryPoint(ComponentActivity::class)` em `MainActivity`, com `ksp.incremental=false` em `gradle.properties` para evitar falhas de rebuild completo (`--rerun-tasks`) entre Hilt `2.55` e KSP `2.3.5`.

---

## 6. Organização dos Pacotes e Assets

```text
app/src/main/
├── assets/
│   ├── erudito/
│   │   ├── article.css                 # Tipografia, tabelas e temas claro/escuro da WebView
│   │   └── math_renderer.js            # Renderização KaTeX offline, macros e medição de altura
│   └── katex/
│       ├── katex.min.css               # Estilos KaTeX 0.19.1 empacotados offline
│       ├── katex.min.js                # Motor KaTeX 0.19.1 empacotado offline
│       ├── LICENSE                     # Licença MIT do KaTeX
│       └── fonts/                      # 20 arquivos .woff2 das fontes matemáticas KaTeX
└── java/com/example/
    ├── MainActivity.kt                 # Activity principal (edge-to-edge + EruditoNavHost)
    ├── ui/theme/                       # Color.kt, Theme.kt, Type.kt (Material 3)
    └── erudito/
        ├── EruditoApplication.kt       # @HiltAndroidApp
        ├── di/EruditoHiltModule.kt     # Módulo Hilt SingletonComponent
        ├── data/
        │   ├── connectivity/           # AndroidConnectivityObserver
        │   ├── local/
        │   │   ├── dao/                # CollectionDao, ArticleDao
        │   │   ├── database/           # EruditoDatabase (Room v4 + migrations 1→2→3→4)
        │   │   └── entity/             # CollectionEntity, ArticleEntity, CollectionArticleCrossRef, relações
        │   ├── mapper/                 # ArticleMapper, CollectionMapper, WikipediaMapper
        │   ├── remote/
        │   │   ├── api/                # WikipediaApiService
        │   │   ├── dto/                # WikipediaResponseDtos
        │   │   └── parser/             # WikipediaHtmlSanitizer
        │   └── repository/             # ArticleRepositoryImpl, CollectionRepositoryImpl, WikipediaRepositoryImpl
        ├── domain/
        │   ├── connectivity/           # ConnectivityObserver
        │   ├── html/                   # StructuredArticleHtmlProcessor
        │   ├── mapper/                 # ArticleDomainMapper
        │   ├── model/                  # Modelos e exceções de domínio
        │   ├── repository/             # Contratos dos repositórios
        │   ├── sync/                   # SyncPolicy, ArticleSyncMerger, SyncResult
        │   └── usecase/                # 17 casos de uso de coleções, busca, leitura e sync
        └── presentation/
            ├── article_detail/         # Tela e ViewModel de leitura local
            ├── collection_detail/      # Tela e ViewModel de detalhes da coleção
            ├── collections/            # Tela e ViewModel de gerenciamento de coleções
            ├── components/             # ArticleBodyContent, StructuredArticleWebView, ArticleHtmlDocumentBuilder,
            │                           # StructuredArticleSecurityPolicy, CollectionPickerDialog, CollectionPickerCoordinator, etc.
            ├── home/                   # HomeScreen, HomeUiState, HomeViewModel
            ├── navigation/             # Screen, EruditoNavHost
            ├── search/                 # SearchScreen, SearchUiState, SearchViewModel
            └── wikipedia_article/      # WikipediaArticleScreen, WikipediaArticleUiState, WikipediaArticleViewModel
```

---

## 7. Integração com a Wikipédia, Pesquisa e Consulta

A comunicação com `https://pt.wikipedia.org/` ocorre por `WikipediaApiService` (Retrofit + OkHttp + Moshi), sem necessidade de chave de API:
1. **Pesquisa (`w/api.php` — `action=query`, `generator=search`):** busca artigos em namespace principal (`gsrnamespace=0`, até 20 resultados, seguindo redirecionamentos `redirects=1`), trazendo título, `pageid`, URL canônica, miniatura (`pageimages`) e resumo introdutório (`extracts`).
2. **Consulta de metadados (`w/api.php`):** obtém título canônico, descrição, URL e imagem original/miniatura por `pageids`.
3. **Consulta de conteúdo estruturado (`w/rest.php/v1/page/{title}/html`):** solicita o HTML Parsoid da página (`Accept: text/html; charset=utf-8`), normalizando espaços para `_` no título canônico.
4. **Identificação e timeouts:** o `OkHttpClient` envia cabeçalho `User-Agent` identificando `Erudito/<versionName>` com timeouts de conexão (`15s`), leitura (`20s`) e escrita (`15s`).

---

## 8. Coleções e Associação N:N

- **Organização por coleções:** coleções possuem nome único validado na criação e na renomeação (`CreateCollectionUseCase`, `RenameCollectionUseCase`). A exclusão remove a coleção e seus registros na tabela associativa via chave estrangeira com `ON DELETE CASCADE`, mantendo artigos que ainda pertençam a outras coleções ou à biblioteca.
- **Associação a múltiplas coleções:** `CollectionArticleCrossRef` define chave primária composta `(collectionId, articleId)` com índices em ambos os campos, impedindo duplicação do mesmo artigo na mesma coleção.
- **Transações e deduplicação (`ArticleDao`):**
  - `saveArticleAndAssociateCollections` resolve artigo já existente por `wikipediaPageId` único, URL legada (`wikipediaPageId IS NULL`) ou `id`, atualiza os metadados com `@Update` e insere apenas as associações faltantes (`OnConflictStrategy.IGNORE`).
  - `addArticleToCollections` e `removeArticleFromCollection` permitem adicionar ou desvincular coleções de forma isolada tanto em `WikipediaArticleScreen` quanto em `ArticleDetailScreen` e `CollectionDetailScreen`.
- **Coordenação na UI (`CollectionPickerCoordinator`):** centraliza seleção/desseleção, criação inline de coleção, validação de seleção vazia, detecção de alterações nulas e tratamento uniforme de `CancellationException` e erros.

---

## 9. Persistência Local com Room (Versão 4)

O banco `erudito_database.db` utiliza **Room 2.7.0** na **versão 4** (`exportSchema = false`), pré-populado de forma idempotente em transação na primeira criação (`Filosofia & Pensamento Clássico`, `Ciência & Física Moderna` e `História da Arte & Renascimento`, com 3 artigos iniciais offline).

- **Entidades:**
  - `CollectionEntity` (`collections`): `id`, `name`, `createdAt`.
  - `ArticleEntity` (`articles`): `id`, `title`, `url`, `description`, `content`, `imageUrl`, `lastUpdated`, `wikipediaPageId` (índice único), `lastSyncedAt`, `contentFormat` (padrão `'PLAIN_TEXT'`), `extractedFormulaCount` (padrão `0`) e `unextractableFormulaCount` (padrão `0`).
  - `CollectionArticleCrossRef` (`collection_article_cross_ref`): FK em cascata para `collections` e `articles`, evitando duplicidade.
- **Histórico de Migrations:**
  - `MIGRATION_1_2` (`1 → 2`): adiciona `wikipediaPageId` e índice único `index_articles_wikipediaPageId`.
  - `MIGRATION_2_3` (`2 → 3`): adiciona `lastSyncedAt` preservando `NULL` em registros legados.
  - `MIGRATION_3_4` (`3 → 4`): adiciona `contentFormat` (`'PLAIN_TEXT'`), `extractedFormulaCount` (`0`) e `unextractableFormulaCount` (`0`).

---

## 10. Estratégia Offline First e Sincronização

- **Leitura local prioritária:** a biblioteca (`HomeScreen`, `CollectionsScreen`, `CollectionDetailScreen`, `ArticleDetailScreen`) lê exclusivamente do Room por fluxos reativos (`Flow`). Ao abrir um artigo da Wikipédia (`WikipediaArticleViewModel`) sem conexão utilizável ou após `WikipediaException`, o app consulta `GetSavedArticleByWikipediaPageIdUseCase` e exibe a cópia local salva se existir, sem salvar automaticamente artigos apenas visualizados online.
- **Política de sincronização (`SyncPolicy`):**
  - Intervalo mínimo padrão de **6 horas** (`DEFAULT_MINIMUM_REFRESH_INTERVAL_MS = 6 * 60 * 60 * 1000 ms`) desde `lastSyncedAt`. Artigos sem `wikipediaPageId` válido (`null` ou `<= 0`) não disparam chamadas remotas.
  - **Auto-sync na Home:** executa no máximo uma vez por ciclo de vida do `HomeViewModel` quando há artigos sincronizáveis e conectividade válida (com recuperação única caso a tela abra offline e recupere conexão na mesma sessão), sem bloquear a exibição local nem redisparar em recomposições.
  - **Atualização manual (`forceRefresh = true`):** disponível na `ArticleDetailScreen` e na `WikipediaArticleScreen` (quando o artigo possui cópia salva), protegida por `Job` contra execuções simultâneas. Não há agendamento em segundo plano via `WorkManager`.
- **Merge defensivo (`ArticleSyncMerger`):**
  - Conteúdo remoto vazio, `hasExtractedContent == false` ou contadores negativos nunca sobrescrevem conteúdo local aproveitável.
  - Artigo local em `STRUCTURED_HTML` íntegro (`unextractableFormulaCount == 0`) é protegido contra rebaixamento para `PLAIN_TEXT` ou `STRUCTURED_HTML` incompleto.
  - Artigo local em `PLAIN_TEXT` evolui para `STRUCTURED_HTML` quando o HTML remoto é íntegro.
  - A persistência utiliza `@Update` (`ArticleDao.updateArticle`) em vez de `OnConflictStrategy.REPLACE`, preservando os vínculos N:N; `lastUpdated` só avança se houver mudança real nos campos persistidos, e redirecionamentos com colisão de `wikipediaPageId` são tratados em `RefreshSavedArticleUseCase`.

---

## 11. Conteúdo Estruturado (Parsoid), KaTeX e Segurança da WebView

- **Sanitização HTML (`WikipediaHtmlSanitizer`):**
  1. Remove tags executáveis/externas (`script`, `iframe`, `object`, `embed`, `style`, `link`, `meta`, `base`, `template`, etc.) e ruídos editoriais da Wikipédia (`.mw-editsection`, `.navbox`, `.sidebar`, `.reflist`, `.ambox`, `.hatnote`, etc.).
  2. Extrai fórmulas matemáticas a partir de `data-mw` (Parsoid JSON), `<annotation encoding="application/x-tex">` (Mathoid) e `alttext`, evitando duplicação entre MathML e imagens fallback, normalizando comandos LaTeX (`align` → `aligned`, `\rm{...}` → `\mathrm{...}`) e promovendo `<dl><dd>` com fórmula única para blocos centralizados.
  3. Substitui fórmulas extraídas por `<span class="erudito-math-inline" data-tex="...">` ou `<div class="erudito-math-block" data-tex="...">` e expressões irrecuperáveis por marcador `[Fórmula indisponível]` (contabilizados em `extractedFormulaCount` e `unextractableFormulaCount`).
  4. Resolve URLs de links (`http`/`https` com `rel="noopener noreferrer"`) e exige **`https://`** estrito para `<img src>` e `imageUrl`, descartando URLs inseguras antes de aplicar a `Safelist` final do Jsoup.
- **Renderização HTML e KaTeX (`ArticleBodyContent` + `StructuredArticleWebView`):**
  - Artigos `PLAIN_TEXT` são renderizados em `Text` Compose com estilo editorial.
  - Artigos `STRUCTURED_HTML` são preparados fora da main thread (`Dispatchers.Default`, via `StructuredArticleHtmlProcessor`), encapsulados por `ArticleHtmlDocumentBuilder` em HTML5 responsivo com suporte a tema claro/escuro, e servidos em memória pelo `LocalArticleDocumentHandler` acoplado ao `WebViewAssetLoader` na origem `https://appassets.androidplatform.net/`.
  - `assets/erudito/math_renderer.js` e `assets/katex/katex.min.js` + `katex.min.css` + fontes `.woff2` renderizam todas as fórmulas inline e em bloco offline, encapsulam tabelas em container rolável horizontalmente e ajustam a altura da `EruditoArticleWebView` (com tratamento de toque aninhado para rolagem interna e externa no Compose).
  - Em caso de erro no frame principal ou encerramento do renderer (`onRenderProcessGone`), a tela realiza limpeza idempotente e alterna para `StructuredArticleFallbackView`, extraindo texto limpo em background.
- **Segurança da WebView (`StructuredArticleSecurityPolicy` + `WebSettings`):**
  - **CSP restritiva:** `default-src 'none'; script-src https://appassets.androidplatform.net; style-src https://appassets.androidplatform.net 'unsafe-inline'; font-src https://appassets.androidplatform.net; img-src https:; object-src 'none'; frame-src 'none'; base-uri 'none'; form-action 'none';`
  - **Hardening de `WebSettings`:** `allowFileAccess = false`, `allowContentAccess = false`, `allowFileAccessFromFileURLs = false`, `allowUniversalAccessFromFileURLs = false`, `domStorageEnabled = false`, `databaseEnabled = false`, `setGeolocationEnabled(false)`, `javaScriptCanOpenWindowsAutomatically = false`, `setSupportMultipleWindows(false)`, `mixedContentMode = MIXED_CONTENT_NEVER_ALLOW` e `safeBrowsingEnabled = true` (API 26+). JavaScript é habilitado exclusivamente para o KaTeX local autorizado pela CSP.
  - **Interceptação:** sub-recursos locais são servidos pelo `WebViewAssetLoader`, `/favicon.ico` retorna 404 local sem tráfego externo, esquemas não HTTPS em sub-recursos retornam 403 e links externos só abrem via `LocalUriHandler` após validação `http`/`https` fora do host local (bloqueando `javascript:`, `file:`, `intent:`, etc.).

---

## 12. Tratamento de Erros e Conectividade

- **Conectividade real (`AndroidConnectivityObserver`):** compartilhada como singleton via `ConnectivityObserver`, monitora `NetworkCallback` exigindo `NET_CAPABILITY_INTERNET`, `NET_CAPABILITY_VALIDATED` e, no Android P+, `NET_CAPABILITY_NOT_SUSPENDED`.
- **Exceções e Cancelamento:** erros da API/rede são mapeados em `WikipediaException` (`NoConnection`, `Timeout`, `HttpError`, `ApiError`, `InvalidResponse`, `EmptyContent`, `SanitizationError`) com mensagens amigáveis em português; `CancellationException` é sempre restauradora de estado e repropagada em corrotinas sem gerar falsos erros visuais.

---

## 13. Testes e Resultados

A suíte automatizada foi validada integralmente na Etapa 4 do Bundle 5 (incluindo build completo `--rerun-tasks`):

- **Suítes JUnit executadas:** `44`
- **Total de testes:** `288`
- **Aprovados:** `288` (`0` falhas, `0` erros, `0` ignorados)
- **Tecnologias:** JUnit 4, Kotlinx Coroutines Test, Compose UI Test e Robolectric 4.16.1 (`@Config(sdk = [36])`), cobrindo domínio, sanitização HTML/LaTeX, Room com migrations `1→2→3→4`, conectividade, ViewModels, segurança da WebView, telas Compose e navegação ponta a ponta.

Consulte `TEST_RESULTS.md` para o histórico por bundle/etapa e o detalhamento completo das 44 suítes.

---

## 14. Requisitos de Ambiente, Compilação e Execução

### Requisitos de ambiente
- **JDK:** 11 ou superior (`sourceCompatibility` / `targetCompatibility` = `JavaVersion.VERSION_11`)
- **Android SDK:** `compileSdk 36`, `targetSdk 36`, `minSdk 24` (`ANDROID_SDK_ROOT` configurado)
- **Gradle:** `9.3.1` (utilizar o comando `gradle` instalado no ambiente ou abrir no Android Studio)
- **Chaves de API:** não requer chaves ou credenciais; o `.env.example` permanece apenas como template inativo (`GEMINI_API_KEY` comentado)

### Comandos principais (via linha de comando)
```bash
# Limpar saídas de build
gradle clean

# Compilar o código Kotlin de debug
gradle :app:compileDebugKotlin

# Executar toda a suíte de testes unitários e Robolectric
gradle :app:testDebugUnitTest

# Recompilar e reexecutar todos os testes do zero (sem cache de tarefas)
gradle :app:compileDebugKotlin :app:testDebugUnitTest --rerun-tasks

# Gerar o APK de debug
gradle :app:assembleDebug
```

### Instalação em dispositivo ou emulador
- Pelo Android Studio: abrir o diretório raiz do projeto, aguardar o sync do Gradle e executar a configuração `app`.
- Via linha de comando com dispositivo/emulador conectado:
  ```bash
  adb install -r app/build/outputs/apk/debug/app-debug.apk
  ```

---

## 15. Artefatos de Entrega (APK e Pacote ZIP)

- **APK original (debug):** `app/build/outputs/apk/debug/app-debug.apk`
  - `applicationId`: `com.aistudio.erudito.wklvqz` | `versionCode`: `1` | `versionName`: `1.0`
  - `minSdk`: `24` | `targetSdk`: `36` | Assinatura debug v2 válida (`CN=Android Debug`)
  - Tamanho: `21.210.469` bytes (`~20,23 MiB`)
  - SHA-256: `c7757122bef7cd7f55f5acbfc8377f932b17b9ff3ffc7d1a21e9300fd63477a1`
- **Pacote ZIP na raiz do repositório:** `Erudito-1.0-debug.zip`
  - Contém exclusivamente `app-debug.apk` na raiz do arquivo compactado
  - Tamanho: `20.648.877` bytes (`~19,69 MiB`)
  - Integridade validada (`unzip -tq`) e hash SHA-256 extraído idêntico ao APK original

---

## 16. Limitações Conhecidas

- **Validação final da Etapa 4 sem emulador/dispositivo conectado no container:** o ambiente de build não dispõe de emulador em execução nem dispositivos em `adb devices`; a verificação dinâmica final apoiou-se na suíte Robolectric/Compose (288 testes), ficando pendente em hardware/emulador o checklist manual descrito em `TEST_RESULTS.md`.
- **Sincronização em segundo plano:** não utiliza `WorkManager`; a sincronização automática é disparada sob controle de ciclo de vida na `HomeScreen` (janela de 6 horas) e sob demanda manualmente.
- **Fórmulas não extraíveis da Wikipédia:** expressões em que a API Parsoid/MathML não forneça código TeX recuperável são exibidas com o marcador `[Fórmula indisponível]` e sinalizadas ao leitor.
- **Histórico Git no ambiente de build:** o workspace no container não possui diretório `.git` inicializado (`fatal: not a git repository`).

---

## 17. Documentação do Projeto

- `SPECIFICATION.md` — Requisitos funcionais (`RF01`–`RF19`), regras de negócio (`RN01`–`RN11`) e escopo consolidado.
- `PLANNING.md` — Arquitetura Clean Architecture + MVVM, stack técnica e decisões consolidadas.
- `TASKS.md` — Decomposição e rastreamento dos Bundles 1 a 5.
- `TEST_RESULTS.md` — Histórico de execuções, detalhamento das 44 suítes / 288 testes, inspeção de artefatos e checklist manual pendente.
- `AI_USAGE.md` — Registro transparente da utilização de ferramentas de Inteligência Artificial durante o desenvolvimento.
- `public/assets/aistudio/` — Versões iniciais dos documentos SDD preservadas como referência histórica do projeto.

