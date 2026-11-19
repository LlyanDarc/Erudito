# Erudito — Plano de Tarefas

## 1. Objetivo

Este documento organiza o desenvolvimento do Erudito em bundles funcionais, seguindo a especificação e o planejamento técnico definidos em `SPECIFICATION.md` e `PLANNING.md`.

Cada bundle representa um conjunto de funcionalidades relacionadas, desenvolvido de maneira integrada e validado progressivamente.

## 2. Regras de desenvolvimento

- Seguir integralmente os requisitos definidos no `SPECIFICATION.md`.
- Respeitar as decisões arquiteturais do `PLANNING.md`.
- Desenvolver os bundles na sequência estabelecida.
- Preservar o funcionamento das funcionalidades já implementadas.
- Executar os testes pertinentes antes de concluir cada bundle/etapa.
- Atualizar a documentação conforme o desenvolvimento avança, mantendo apenas fatos verificados no código e nos relatórios de execução.

## 3. Bundle 1 — Biblioteca pessoal offline

**Objetivo:** implementar a biblioteca pessoal e o gerenciamento local de coleções, permitindo utilizar essas funcionalidades sem conexão com a internet.

### Tarefas

- [x] Criar a estrutura inicial do aplicativo Android.
- [x] Configurar Kotlin, Jetpack Compose e dependências necessárias.
- [x] Implementar a organização em Clean Architecture.
- [x] Configurar Hilt para injeção de dependências (`@HiltAndroidApp`, `@AndroidEntryPoint`, `@HiltViewModel`, `@Inject`).
- [x] Configurar o banco de dados Room.
- [x] Criar a entidade de coleção.
- [x] Criar a entidade de artigo.
- [x] Criar a estrutura de relacionamento entre artigos e coleções.
- [x] Implementar os DAOs necessários.
- [x] Implementar os repositórios locais.
- [x] Implementar os casos de uso relacionados às coleções.
- [x] Criar a tela inicial da biblioteca.
- [x] Criar a tela de coleções.
- [x] Implementar criação, renomeação e exclusão de coleções.
- [x] Implementar visualização do conteúdo das coleções e de artigos armazenados localmente.
- [x] Configurar Navigation Compose para as telas implementadas.
- [x] Implementar gerenciamento de estado utilizando ViewModels e `StateFlow`.
- [x] Implementar testes unitários das regras de negócio do bundle e testes de integração do Room.

### Critérios de conclusão

- [x] O aplicativo inicia e apresenta a biblioteca.
- [x] O usuário consegue criar, visualizar, renomear e excluir coleções.
- [x] As coleções permanecem armazenadas após fechar e reabrir o aplicativo.
- [x] As telas da biblioteca funcionam sem conexão com a internet.
- [x] A arquitetura respeita a separação entre Presentation, Domain e Data.
- [x] Os testes das regras de negócio implementadas são executados com sucesso.

## 4. Bundle 2 — Pesquisa e consulta à Wikipédia

**Objetivo:** permitir pesquisar artigos na Wikipédia e consultar seus conteúdos.

### Tarefas

- [x] Configurar Retrofit, Moshi e OkHttp (`User-Agent` configurado).
- [x] Implementar o serviço de comunicação com a API MediaWiki (`WikipediaApiService`).
- [x] Criar DTOs e mapeadores para os dados recebidos.
- [x] Implementar a integração com o repositório (`WikipediaRepositoryImpl`) e casos de uso (`SearchWikipediaArticlesUseCase`, `GetWikipediaArticleUseCase`).
- [x] Criar a tela de pesquisa (`SearchScreen`) com campo de busca e resultados (títulos, descrições e miniaturas).
- [x] Criar a tela de leitura do artigo (`WikipediaArticleScreen`) com conteúdo e referência para o artigo original.
- [x] Implementar estados de carregamento, sucesso, ausência de resultados e erro, com tratamento de falhas de comunicação.
- [x] Integrar as telas à navegação existente (`EruditoNavHost`).

### Critérios de conclusão

- [x] Pesquisa, exibição de resultados, imagens e descrições funcionam conforme especificado.
- [x] Falhas de conexão são tratadas com segurança e refletidas na interface.

## 5. Bundle 3 — Integração entre artigos e coleções

**Objetivo:** permitir que os artigos encontrados sejam armazenados e organizados na biblioteca pessoal em relação N:N.

### Tarefas

- [x] Implementar casos de uso para salvar, associar e remover artigos de coleções.
- [x] Evoluir Room para versão 2 (`MIGRATION_1_2`) adicionando `wikipediaPageId` único e reutilização sem duplicidade.
- [x] Implementar `CollectionPickerDialog` reutilizável para seleção múltipla e criação inline de coleções.
- [x] Integrar salvamento e gerenciamento de coleções na leitura online e offline, além de remoção individual na coleção.
- [x] Atualizar automaticamente as telas via `Flow` e implementar testes unitários, de UI e ponta a ponta (`Bundle3EndToEndIntegrationTest`).

### Critérios de conclusão

- [x] Artigos podem pertencer a várias coleções sem duplicação na mesma coleção, com persistência e testes validados.

## 6. Bundle 4 — Offline First, Sincronização e Conteúdo Estruturado

**Objetivo:** garantir funcionamento Offline First completo, sincronização defensiva e renderização segura de conteúdo estruturado e fórmulas LaTeX da Wikipédia.

### Tarefas

- [x] **Etapa 1 (Conectividade e Leitura Local Prioritária):** `ConnectivityObserver` / `AndroidConnectivityObserver` (`NET_CAPABILITY_INTERNET`, `NET_CAPABILITY_VALIDATED`, `NET_CAPABILITY_NOT_SUSPENDED`), leitura prioritária e fallback automático offline/rede sem auto-salvamento.
- [x] **Etapa 2 (Room v3, Sincronização e Merge Defensivo):** `MIGRATION_2_3` (`lastSyncedAt`), `SyncPolicy` (6h), `ArticleSyncMerger` (`hasExtractedContent`), `RefreshSavedArticleUseCase`, `SyncSavedArticlesUseCase` e atualização via `@Update`.
- [x] **Etapa 3 (Integração na UI):** auto-sync controlado na `HomeViewModel` (`HomeSyncStatusBanner`) e atualização manual (`forceRefresh = true`) na `ArticleDetailScreen` e `WikipediaArticleScreen`.
- [x] **Etapa C1/C2 (Parsoid, Sanitização e Room v4):** integração com `w/rest.php/v1/page/{title}/html`, `WikipediaHtmlSanitizer` (Jsoup + extração de fórmulas LaTeX), `ArticleContentFormat` (`PLAIN_TEXT` / `STRUCTURED_HTML`), `MIGRATION_3_4` e proteção de `STRUCTURED_HTML` íntegro no `ArticleSyncMerger`.
- [x] **Etapa D (Renderização Estruturada e KaTeX):** `ArticleBodyContent`, `StructuredArticleWebView`, `EruditoArticleWebView`, `LocalArticleDocumentHandler`, `ArticleHtmlDocumentBuilder`, `StructuredArticleSecurityPolicy`, KaTeX `0.19.1` offline via `WebViewAssetLoader`, `onRenderProcessGone` e fallback assíncrono; validada no emulador Android antes do Bundle 5.
- [x] **Consolidação do Bundle 4:** auditoria de planejamento e avanço para o Bundle 5.

### Critérios de conclusão

- [x] Artigos salvos (incluindo `STRUCTURED_HTML` e fórmulas matemáticas) e coleções funcionam offline.
- [x] Sincronização automática controlada e manual preservam dados locais e vínculos N:N.
- [x] Pipeline de sanitização, renderização segura e testes automatizados validados.

## 7. Bundle 5 — Finalização e Validação

**Objetivo:** revisar arquitetura, dependências, resiliência e interface, validar a suíte completa de testes e preparar a documentação e os entregáveis finais.

### Tarefas por Etapa Executada

- [x] **Auditoria Técnica Inicial:** diagnóstico completo de Clean Architecture, Gradle/Hilt/KSP, concorrência, resiliência, segurança WebView e UI sem alterar arquivos.
- [x] **Etapa 1 — Build, Hilt/KSP e Dependências Gradle:**
  - [x] Corrigir falha de rebuild completo em `:app:kspDebugKotlin` (`ksp.incremental=false` em `gradle.properties` mantendo Configuration Cache e configuração explícita Hilt/KSP).
  - [x] Manter comentadas em `app/build.gradle.kts` as dependências não utilizadas (`accompanist-permissions`, `camera-*`, `datastore-preferences`, `play-services-location`).
- [x] **Etapa 2 — Clean Architecture, Concorrência e Resiliência:**
  - [x] Eliminar dependências de `Domain` e `Presentation` na camada `Data` (`ArticleDomainMapper` no Domain e interface `StructuredArticleHtmlProcessor`).
  - [x] Compartilhar `AndroidConnectivityObserver` como singleton via Hilt e mover processamento HTML para `Dispatchers.Default`.
  - [x] Corrigir tratamento e propagação de `CancellationException` nas corrotinas.
- [x] **Etapa 3 — Interface, Desempenho, Depreciações e Limpeza:**
  - [x] Extrair lógica compartilhada de seleção de coleções para `CollectionPickerCoordinator`.
  - [x] Reforçar segurança HTTPS de imagens e revisar acessibilidade/comportamento da UI.
- [x] **Etapa 4 — Testes e Validação Final:**
  - [x] Auditar cobertura nas 5 áreas centrais e adicionar 3 testes pontuais para casos de borda (`SearchViewModelTest`, `CollectionDetailViewModelTest`, `WikipediaArticleViewModelTest`).
  - [x] Executar na ordem exata `gradle clean`, `gradle :app:compileDebugKotlin`, `gradle :app:testDebugUnitTest`, `gradle :app:assembleDebug` e `gradle :app:compileDebugKotlin :app:testDebugUnitTest --rerun-tasks`.
  - [x] Confirmar **44 suítes e 288 testes aprovados** (0 falhas, 0 erros, 0 ignorados) e inspecionar `app/build/outputs/apk/debug/app-debug.apk`.
- [x] **Etapa 5 — Documentação Final e Preparação da Entrega:**
  - [x] Auditar documentação e atualizar `README.md`, `SPECIFICATION.md`, `PLANNING.md`, `TASKS.md` e `TEST_RESULTS.md` com dados reais (Room v4, Parsoid, KaTeX `0.19.1`, Jsoup `1.18.3`, WebKit `1.18.0`, 44 suítes / 288 testes).
  - [x] Gerar e validar o pacote `Erudito-1.0-debug.zip` na raiz do projeto contendo exclusivamente `app-debug.apk` idêntico ao original (SHA-256 verificado).
  - [x] Registrar limitações conhecidas (ausência de emulador/ADB na Etapa 4–5 e ausência de `.git` no container).
  - [x] Criar e finalizar o `AI_USAGE.md` na raiz do projeto (Registro de Utilização de Inteligência Artificial) e atualizar referências em `README.md` e `TASKS.md`.

### Itens Pendentes Externos (Registro Fiel)

- [ ] **Execução manual final em dispositivo físico ou emulador (pós-Etapa 4/5):** não executada nesta etapa porque o ambiente containerizado não possui emulador nem dispositivo conectado via `adb devices`; checklist objetivo registrado em `TEST_RESULTS.md` (nota: a Etapa D do Bundle 4 havia sido validada anteriormente no emulador).
- [ ] **Revisão de histórico e criação de commits Git:** o diretório do workspace no container não possui repositório `.git` inicializado (`fatal: not a git repository`); nenhuma inicialização ou commit foi criado automaticamente.

### Critérios de conclusão

- [x] Requisitos obrigatórios implementados e limitações devidamente documentadas.
- [x] Testes executados integralmente após ajustes finais e resultados registrados (`44` suítes, `288` aprovados).
- [x] Documentação técnica, de usuário e de uso de IA atualizada (`README.md`, `SPECIFICATION.md`, `PLANNING.md`, `TASKS.md`, `TEST_RESULTS.md`, `AI_USAGE.md`).
- [x] APK debug íntegro em `app/build/outputs/apk/debug/app-debug.apk` e pacote `Erudito-1.0-debug.zip` disponível na raiz.
- [ ] Validação manual em dispositivo pós-Bundle 5 e versionamento Git local pendentes conforme ambiente/autorização do responsável.

## 8. Controle de progresso

| Bundle | Descrição | Status |
|---|---|---|
| 1 | Biblioteca pessoal offline | **Concluído** |
| 2 | Pesquisa e consulta à Wikipédia | **Concluído** |
| 3 | Integração entre artigos e coleções | **Concluído** |
| 4 | Offline First, sincronização e conteúdo estruturado | **Concluído** |
| 5 | Finalização, validação técnica e documentação | **Concluído (aguardando revisão final / pendências externas listadas na Seção 7)** |

---

**Documento:** TASKS.md  
**Projeto:** Erudito  
**Status:** Bundle 5 — Etapa 5 concluída (pronto para revisão final)
