# AI_USAGE.md — Registro de Utilização de Inteligência Artificial

**Projeto:** Erudito — Biblioteca Pessoal de Conhecimento  
**Plataforma:** Android (Kotlin + Jetpack Compose)  
**Metodologia:** Spec-Driven Development (SDD)  
**Data da Última Atualização:** 19 de Novembro de 2026

---

## 1. Introdução

Este documento registra de maneira transparente, objetiva e verificável a utilização de ferramentas de Inteligência Artificial (IA) ao longo do ciclo de desenvolvimento do aplicativo **Erudito**.

No projeto, a IA foi empregada exclusivamente como **ferramenta de apoio à engenharia de software** — auxiliando na estruturação da documentação SDD, implementação incremental por bundles, refatorações arquiteturais, depuração, elaboração de testes automatizados e preparação da entrega final — sempre sob direcionamento, revisão crítica e decisão do desenvolvedor responsável.

---

## 2. Ferramentas Utilizadas

| Ferramenta | Papel Principal no Projeto | Fonte de Registro |
|---|---|---|
| **Google AI Studio** | Ferramenta principal de apoio ao desenvolvimento, implementação de código Kotlin/Compose, correção de falhas, evolução arquitetural, criação/execução de testes, auditoria técnica, atualização documental e preparação do pacote de entrega. | Previsto em `public/assets/aistudio/planning.md_92288048.txt` (Seções 2 e 12) e refletido nos artefatos e relatórios do projeto. |
| **ChatGPT** | Apoio complementar ao desenvolvedor no planejamento técnico, formulação e refinamento de solicitações (prompts), análise de relatórios técnicos e de execução, revisão de decisões arquiteturais, apoio à auditoria de código e acompanhamento sequencial das etapas. | Fluxo de trabalho declarado pelo desenvolvedor responsável (observação: transcrições de sessões do ChatGPT não estão armazenadas no repositório). |

> **Nota sobre precisão dos registros:** Os arquivos preservados no repositório não registram contagem de prompts, histórico integral de conversas ou percentuais numéricos de autoria entre desenvolvedor e ferramentas de IA. Tais métricas não são estimadas nem inferidas neste documento.

---

## 3. Forma de Utilização

Ao longo do projeto, o uso das ferramentas de IA concentrou-se nas seguintes frentes de engenharia:

1. **Planejamento e decomposição (SDD):** estruturação inicial dos documentos de especificação (`SPECIFICATION.md`), arquitetura e decisões técnicas (`PLANNING.md`) e plano incremental de tarefas (`TASKS.md`) em cinco bundles funcionais, preservando versões iniciais em `public/assets/aistudio/`.
2. **Elaboração e refinamento de requisitos:** expansão progressiva dos requisitos funcionais (`RF01`–`RF19`) e regras de negócio (`RN01`–`RN11`) conforme o projeto evoluiu de leitura simples para sincronização defensiva, conteúdo estruturado Parsoid e renderização matemática offline.
3. **Implementação de funcionalidades:** geração e modificação incremental de entidades Room, DAOs, migrations, serviços Retrofit/Moshi, casos de uso, ViewModels e telas Jetpack Compose, mantendo compatibilidade entre bundles.
4. **Apoio à arquitetura e organização do código:** aplicação e posterior correção estrita da **Clean Architecture** (`Presentation → Domain ← Data`) com padrão **MVVM** e injeção de dependências via **Dagger Hilt**, incluindo extração de contratos (`StructuredArticleHtmlProcessor`), mapeadores de domínio (`ArticleDomainMapper`) e coordenadores de interface (`CollectionPickerCoordinator`).
5. **Diagnóstico e correção de erros:** investigação de falhas de rebuild (`--rerun-tasks`) entre Hilt `2.55` e KSP `2.3.5`, tratamento adequado de `CancellationException`, eliminação de dependências cruzadas entre camadas e diagnóstico de encerramento do processo renderer da `WebView` durante validação em emulador.
6. **Criação e revisão de testes:** ampliação progressiva da suíte automatizada com JUnit 4, Kotlinx Coroutines Test, Compose UI Test e Robolectric `4.16.1`, alcançando **44 suítes** e **288 testes aprovados**, incluindo cenários de borda e integração Room/WebView/Navegação.
7. **Auditoria técnica:** execução de inspeções somente leitura sobre arquitetura, dependências Gradle, concorrência, resiliência de rede, segurança da `WebView` e cobertura de testes antes das etapas finais de correção e entrega.
8. **Documentação técnica e empacotamento:** consolidação final de `README.md`, `SPECIFICATION.md`, `PLANNING.md`, `TASKS.md`, `TEST_RESULTS.md`, deste `AI_USAGE.md` e geração/verificação do pacote `Erudito-1.0-debug.zip`.

---

## 4. Histórico por Etapas

### 4.1. Bundle 1 — Biblioteca Pessoal Offline (Estrutura Inicial e Funcionalidades Locais)
*Data individual de execução: não documentada separadamente nos registros do repositório (consolidado na validação dos Bundles 1–3 em 15/11/2026).*

- **Objetivo:** estabelecer a base do aplicativo Android em Kotlin/Jetpack Compose com Clean Architecture, Hilt e Room v1, permitindo criar, visualizar, renomear e excluir coleções e consultar artigos armazenados localmente sem conexão com a internet.
- **Como a IA auxiliou:** o Google AI Studio apoiou a geração da estrutura inicial de pacotes, entidades e DAOs do Room, regras de negócio no Domain, telas iniciais em Compose e testes das regras de coleção; o ChatGPT apoiou o desenvolvedor na organização do plano SDD e na revisão das etapas iniciais.
- **Principais atividades realizadas:** configuração de Kotlin, Compose Material 3, Hilt (`EruditoHiltModule`), `EruditoDatabase` v1 com pré-população idempotente, entidades `CollectionEntity`, `ArticleEntity` e `CollectionArticleCrossRef`, casos de uso de coleções, telas (`HomeScreen`, `CollectionsScreen`, `CollectionDetailScreen`, `ArticleDetailScreen`), `EruditoNavHost` e testes unitários/Room.
- **Resultado documentado:** funcionalidades locais implementadas e integradas à base validada no marco conjunto dos Bundles 1–3 (`15/11/2026`, `30` suítes, `156` testes aprovados).

### 4.2. Bundle 2 — Pesquisa e Consulta à Wikipédia
*Data individual de execução: não documentada separadamente nos registros do repositório (consolidado na validação dos Bundles 1–3 em 15/11/2026).*

- **Objetivo:** integrar o aplicativo à MediaWiki Action API da Wikipédia em Português (`pt.wikipedia.org`) para pesquisar assuntos, exibir resultados com título/descrição/miniatura e consultar artigos com tratamento de estados e referência externa.
- **Como a IA auxiliou:** o Google AI Studio auxiliou na implementação da camada remota (Retrofit, OkHttp, Moshi), mapeamento para modelos de domínio, casos de uso de busca/leitura e construção das telas de pesquisa e leitura online; o ChatGPT atuou no apoio ao planejamento da etapa e na conferência dos critérios do bundle.
- **Principais atividades realizadas:** implementação de `WikipediaApiService`, DTOs e mappers, `WikipediaRepositoryImpl`, `SearchWikipediaArticlesUseCase`, `GetWikipediaArticleUseCase`, `SearchScreen`, `WikipediaArticleScreen`, carregamento de imagens com Coil e mapeamento de erros em `WikipediaException`.
- **Resultado documentado:** pesquisa e consulta online operacionais e consolidadas no marco de encerramento dos Bundles 1–3 (`15/11/2026`, `30` suítes, `156` testes aprovados).

### 4.3. Bundle 3 — Integração entre Artigos e Coleções
*Data individual de execução: não documentada separadamente nos registros do repositório (consolidado na validação dos Bundles 1–3 em 15/11/2026).*

- **Objetivo:** permitir salvar artigos encontrados na Wikipédia na biblioteca local em relacionamento muitos-para-muitos (N:N), reutilizando registros por `wikipediaPageId` e impedindo duplicidades na mesma coleção.
- **Como a IA auxiliou:** o Google AI Studio auxiliou na evolução do banco Room para versão 2 (`MIGRATION_1_2`), na escrita de transações seguras em `ArticleDao`, no componente reutilizável `CollectionPickerDialog` e na cobertura de testes ponta a ponta; o ChatGPT apoiou a revisão de regras N:N e dos relatórios de fechamento do bundle.
- **Principais atividades realizadas:** criação de `SaveWikipediaArticleToCollectionsUseCase`, `AddArticleToCollectionsUseCase`, `RemoveArticleFromCollectionUseCase`, criação inline de coleções no seletor, remoção individual de vínculos sem apagar o artigo da biblioteca, reatividade via `Flow` e `Bundle3EndToEndIntegrationTest`.
- **Resultado documentado:** marco consolidado dos Bundles 1–3 validado em **15/11/2026** com `gradle :app:compileDebugKotlin :app:testDebugUnitTest --rerun-tasks`: **30 suítes**, **156 testes executados** e **156 aprovados** (`0` falhas, `0` erros, `0` ignorados).

### 4.4. Bundle 4 — Offline First, Sincronização e Conteúdo Estruturado
*Validação intermediária das Etapas 1–4 de Offline First/Sync registrada em 16/11/2026; consolidação final após Etapas A–D antes do Bundle 5.*

- **Objetivo:** garantir funcionamento Offline First completo, detecção confiável de conectividade, política de frescor de 6 horas, atualização defensiva de artigos salvos e evolução para leitura estruturada com conteúdo rico da Wikipédia.
- **Como a IA auxiliou:** o Google AI Studio implementou a infraestrutura de conectividade, migrations, políticas e lógica de merge defensivo, além da integração progressiva na interface e da auditoria final de alinhamento entre `SPECIFICATION.md`, `PLANNING.md` e `TASKS.md`; o ChatGPT auxiliou o desenvolvedor na análise dos diagnósticos, na formulação das etapas corretivas e na conferência de aderência ao escopo.
- **Principais atividades realizadas:** implementação de `ConnectivityObserver` / `AndroidConnectivityObserver`, fallback local automático sem auto-salvamento na leitura online, `MIGRATION_2_3` (`lastSyncedAt`), `SyncPolicy` (intervalo padrão de 6h), `ArticleSyncMerger` (`hasExtractedContent`), `RefreshSavedArticleUseCase`, `SyncSavedArticlesUseCase`, `HomeSyncStatusBanner` com auto-sync único por ciclo de vida, atualização manual (`forceRefresh = true`) e conclusão das etapas de conteúdo estruturado listadas na Seção 4.5.
- **Resultado documentado:** marco intermediário **Bundle 4 — Etapas 1–4** aprovado em **16/11/2026** (`38` suítes, `219` testes aprovados, `0` falhas), seguido pela conclusão das etapas de conteúdo estruturado e pela auditoria de planejamento que autorizou o início do Bundle 5.

### 4.5. Etapas A, B, C1, C2 e D — Diagnóstico, Sanitização HTML, Integração Parsoid, Persistência e Renderização KaTeX
*Datas individuais não registradas nos arquivos do repositório; sequência executada entre a validação de sync do Bundle 4 (16/11/2026) e o início do Bundle 5.*

- **Objetivo:** substituir limitações da extração textual simples pela obtenção, sanitização, persistência e renderização offline fiel do conteúdo estruturado da Wikipédia (títulos, tabelas, listas, imagens HTTPS e fórmulas matemáticas em LaTeX).
- **Como a IA auxiliou:** o Google AI Studio apoiou o diagnóstico técnico do pipeline de conteúdo, a criação do sanitizador Jsoup e do extrator de fórmulas LaTeX, a integração com o endpoint Parsoid, a evolução do Room para versão 4, a construção do componente `StructuredArticleWebView` com KaTeX local e a investigação/correção de falhas de renderização identificadas em emulador; o ChatGPT auxiliou na análise dos sintomas reportados pelo emulador, na revisão de segurança e na estruturação incremental das sub-etapas.
- **Principais atividades realizadas:**
  - **Etapas A e B:** diagnóstico e preparação da transição de conteúdo textual para conteúdo estruturado e sanitização HTML *(nota de transparência: o detalhamento granular separado e as datas exatas das Etapas A e B não constam isolados nos registros preservados do repositório)*.
  - **Etapas C1 e C2 (Parsoid, Sanitização e Room v4):** integração ao endpoint REST Parsoid (`w/rest.php/v1/page/{title}/html`), criação do `WikipediaHtmlSanitizer` (Jsoup `Safelist`, remoção de scripts e ruído editorial, extração/normalização de LaTeX via `data-mw`, MathML `<annotation>` e `alttext`), introdução de `ArticleContentFormat` (`PLAIN_TEXT` / `STRUCTURED_HTML`), `MIGRATION_3_4` (`contentFormat`, `extractedFormulaCount`, `unextractableFormulaCount`) e proteção de `STRUCTURED_HTML` íntegro no `ArticleSyncMerger`.
  - **Etapa D (Renderização Estruturada e KaTeX):** desenvolvimento de `ArticleBodyContent`, `StructuredArticleWebView`, `EruditoArticleWebView`, `LocalArticleDocumentHandler`, `ArticleHtmlDocumentBuilder`, `StructuredArticleSecurityPolicy`, empacotamento offline do **KaTeX 0.19.1** (`katex.min.css`, `katex.min.js`, 20 fontes `.woff2`, `article.css`, `math_renderer.js`) servido via `WebViewAssetLoader`, CSP restritiva, `WebSettings` endurecidas e fallback textual assíncrono com `onRenderProcessGone`; investigação e correção de falhas ocorridas durante testes iniciais no emulador (`Webpage not available` / encerramento do processo renderer da `WebView`).
- **Resultado documentado:**
  - **Etapa C2:** `256` testes unitários e Robolectric aprovados conforme histórico em `TEST_RESULTS.md`.
  - **Etapa D:** validada integralmente pelo desenvolvedor no **emulador Android** antes do Bundle 5 (renderização de texto estruturado, carregamento de imagens, fórmulas matemáticas com KaTeX, rolagem interna da WebView, rolagem externa do Compose, reabertura de artigos e ausência de travamentos ou telas de erro).

### 4.6. Bundle 5 — Auditoria Técnica, Correções Arquiteturais, Testes e Preparação da Entrega
*Etapa 4 e Etapa 5 registradas em 19/11/2026; datas individuais da Auditoria Inicial e Etapas 1–3 não registradas separadamente.*

- **Objetivo:** auditar integralmente o código-fonte e a configuração Gradle, corrigir falhas de build e desvios de arquitetura/concorrência/interface, validar toda a suíte de testes, produzir a documentação final e gerar o pacote entregável.
- **Como a IA auxiliou:** o Google AI Studio conduziu a auditoria técnica inicial (somente leitura), implementou as correções das Etapas 1 a 3, auditou a cobertura e escreveu testes de borda na Etapa 4 e elaborou a documentação e o empacotamento final na Etapa 5; o ChatGPT apoiou o desenvolvedor na preparação dos prompts de cada etapa, na análise crítica dos relatórios de auditoria/execução e no controle sequencial do encerramento.
- **Principais atividades realizadas:**
  - **Auditoria Técnica Inicial:** mapeamento de acoplamentos entre camadas, falha de rebuild `--rerun-tasks`, tratamento de `CancellationException`, instâncias de conectividade, processamento HTML síncrono e duplicação de seletor de coleções.
  - **Etapa 1 (Build e Hilt/KSP):** correção da falha em `:app:kspDebugKotlin` (`FileNotFoundException: MainActivity_GeneratedInjector.java`) com `ksp.incremental=false` em `gradle.properties` mantendo Configuration Cache e herança explícita Hilt/KSP, além da manutenção de dependências não utilizadas comentadas em `app/build.gradle.kts`.
  - **Etapa 2 (Clean Architecture, Concorrência e Resiliência):** eliminação de dependências da camada `Data` em `Domain` e `Presentation` (`StructuredArticleHtmlProcessor` e `ArticleDomainMapper`), compartilhamento singleton de `AndroidConnectivityObserver`, processamento HTML assíncrono em `Dispatchers.Default` e repropagação correta de `CancellationException` (`43` suítes / `280` testes aprovados).
  - **Etapa 3 (Interface, Desempenho e Limpeza):** extração de `CollectionPickerCoordinator`, reforço de exigência `https://` para imagens e revisões de UI/acessibilidade (`44` suítes / `285` testes aprovados).
  - **Etapa 4 (Testes e Validação Final — 19/11/2026):** auditoria de cobertura das 5 áreas centrais, adição de 3 testes de casos de borda (`SearchViewModelTest`, `CollectionDetailViewModelTest`, `WikipediaArticleViewModelTest`), execução sequencial de `clean`, `compileDebugKotlin`, `testDebugUnitTest`, `assembleDebug` e `--rerun-tasks`, além de inspeção do APK via `aapt2` e `apksigner`.
  - **Etapa 5 (Documentação e Entrega — 19/11/2026):** atualização de `README.md`, `SPECIFICATION.md`, `PLANNING.md`, `TASKS.md` e `TEST_RESULTS.md`, criação do pacote `Erudito-1.0-debug.zip` na raiz e elaboração posterior deste `AI_USAGE.md` com atualização das referências em `README.md` e `TASKS.md`.
- **Resultado documentado:** **44 suítes** e **288 testes aprovados** (`0` falhas, `0` erros, `0` ignorados) em **19/11/2026** com `--rerun-tasks` (`BUILD SUCCESSFUL`), APK `app/build/outputs/apk/debug/app-debug.apk` (`21.210.469` bytes, `com.aistudio.erudito.wklvqz`, `versionCode 1`, `versionName 1.0`, SHA-256 `c7757122bef7cd7f55f5acbfc8377f932b17b9ff3ffc7d1a21e9300fd63477a1`) e pacote `Erudito-1.0-debug.zip` (`20.648.877` bytes) íntegros e verificados.

---

## 5. Participação e Responsabilidade do Desenvolvedor

O desenvolvimento do Erudito não foi um processo autônomo da IA. Coube integralmente ao desenvolvedor responsável:

- Definir os objetivos do aplicativo, os requisitos funcionais, as regras de negócio e as fronteiras do escopo inicial.
- Estruturar o fluxo SDD, redigir e direcionar as solicitações por etapas/bundles e impor restrições para evitar alterações fora de escopo ou perda de funcionalidades já validadas.
- Avaliar criticamente sugestões técnicas, relatórios de auditoria e propostas de correção (como a política de sincronização de 6 horas, proteção contra rebaixamento de `STRUCTURED_HTML`, configuração `ksp.incremental=false` e ausência de `WorkManager`).
- Acompanhar e executar validações em emulador Android quando aplicável (como a validação completa da Etapa D do Bundle 4), reportando falhas observadas para correção.
- Verificar resultados de build, suítes de testes, documentação e critérios de aceitação antes de considerar cada etapa concluída.

---

## 6. Validação e Controle de Qualidade

Todas as entregas técnicas do projeto passaram por verificações concretas no ambiente de build e/ou em emulador, diferenciando-se explicitamente o alcance de cada tipo de validação:

### 6.1. Verificações Automatizadas e de Build Confirmadas (Pós-Bundle 5 — 19/11/2026)
- **Compilação e rebuild completo:** `gradle clean`, `gradle :app:compileDebugKotlin`, `gradle :app:assembleDebug` e `gradle :app:compileDebugKotlin :app:testDebugUnitTest --rerun-tasks` executados com `BUILD SUCCESSFUL`.
- **Suíte de testes automatizados:** **44 suítes JUnit** e **288 testes executados**, com **288 aprovados**, **0 falhas**, **0 erros** e **0 ignorados**, verificados diretamente nos relatórios XML em `app/build/test-results/testDebugUnitTest/TEST-*.xml` (cobrindo Domain, Room v4 com migrations `1→2→3→4`, `WikipediaHtmlSanitizer`, repositórios, conectividade, ViewModels, segurança da `WebView`, UI Compose com Robolectric `4.16.1` e navegação).
- **APK debug gerado e inspecionado:** `app/build/outputs/apk/debug/app-debug.apk` (`21.210.469` bytes / `~20,23 MiB`, assinado com esquema v2 debug `CN=Android Debug`, contendo assets locais do KaTeX `0.19.1`).
- **Pacote ZIP validado:** `Erudito-1.0-debug.zip` (`20.648.877` bytes / `~19,69 MiB`) contendo exclusivamente `app-debug.apk`, verificado com `unzip -tq` e hash SHA-256 extraído idêntico ao APK original (`c7757122bef7cd7f55f5acbfc8377f932b17b9ff3ffc7d1a21e9300fd63477a1`).

### 6.2. Distinção sobre Execução em Emulador / Dispositivo Físico
- **Antes do Bundle 5 (Etapa D do Bundle 4):** o aplicativo foi validado manualmente no emulador Android em renderização estruturada, imagens, KaTeX, rolagem interna/externa e reabertura de artigos.
- **Após o Bundle 5 (Etapas 4 e 5):** **não houve validação final em dispositivo físico ou emulador Android**, pois o ambiente containerizado de build não possui binário `emulator` em execução nem dispositivos conectados em `adb devices`. O checklist manual pós-Bundle 5 permanece registrado na Seção 4 do `TEST_RESULTS.md` para conferência no Android Studio ou em aparelho físico pelo avaliador/desenvolvedor.

---

## 7. Limitações e Transparência

- **Necessidade de revisão humana:** código, configurações e textos técnicos sugeridos por IA podem conter acoplamentos indevidos, suposições incorretas ou depreciações se não forem auditados e testados — como evidenciado pela própria necessidade da Auditoria Técnica e das Etapas 1–3 do Bundle 5.
- **Verificação empírica obrigatória:** declarações de conclusão somente têm validade após compilação efetiva, execução dos testes reais e inspeção direta dos artefatos gerados.
- **Não atribuição de validações não realizadas:** nenhuma validação em dispositivo físico pós-Bundle 5, nem existência de histórico de commits Git no container (onde `.git` não está inicializado), é atribuída ou assumida como realizada.
- **Lacunas documentais declaradas:** este documento foi reconstruído a partir de `README.md`, `SPECIFICATION.md`, `PLANNING.md`, `TASKS.md`, `TEST_RESULTS.md`, dos arquivos iniciais preservados em `public/assets/aistudio/` e do relato de uso do desenvolvedor. Não constam nos registros do repositório: datas isoladas dos Bundles 1, 2 e 3, datas individuais e escopo granular separado das Etapas A e B, datas individuais das Etapas C1, C2, D e das Etapas 1–3 do Bundle 5, logs completos de prompts ou métricas quantitativas de uso das ferramentas de IA.
