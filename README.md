# Erudito — Biblioteca Pessoal de Conhecimento

O **Erudito** é um aplicativo Android desenvolvido em Kotlin e Jetpack Compose que funciona como uma biblioteca pessoal de conhecimento integrada à Wikipédia, permitindo aos usuários organizar temas de estudo em coleções personalizadas e consultar artigos offline.

---

## 1. Status do Projeto

| Bundle | Descrição | Status |
|---|---|---|
| **Bundle 1** | **Biblioteca pessoal offline** | **Concluído** |
| **Bundle 2** | **Pesquisa e consulta à Wikipédia** | **Concluído** |
| **Bundle 3** | **Integração entre artigos e coleções** | **Concluído** |
| **Bundle 4** | **Offline First e sincronização** | **Concluído** |
| Bundle 5 | Finalização, testes e validação | Planejado |

---

## 2. Funcionalidades Implementadas

### Bundle 1 — Biblioteca Pessoal Offline
- **Biblioteca Pessoal Offline:** Painel inicial da biblioteca exibindo coleções, artigos salvos e disponibilidade local.
- **Gerenciamento de Coleções:** Criar, listar (com contador dinâmico), renomear (validação RN01) e excluir coleções com confirmação e remoção em cascata das associações (RN05).
- **Visualização de Coleções e Artigos:** Telas dedicadas de detalhes da coleção (`CollectionDetailScreen`) e leitura offline (`ArticleDetailScreen`).
- **Persistência Local com Room:** Banco SQLite (`erudito_database.db`) com pré-população inicial atômica e relacionamento N:N via `collection_article_cross_ref`.

### Bundle 2 — Pesquisa e Consulta à Wikipédia
- **Pesquisa na Wikipédia em Português:** Consumo da API MediaWiki (`pt.wikipedia.org`) com Retrofit + Moshi + OkHttp (`User-Agent` configurado com identificação do projeto), estados de carregamento, sucesso, ausência de resultados e tratamento de erros de rede.
- **Leitura Online de Artigos:** Tela `WikipediaArticleScreen` exibindo título, descrição, imagem (via Coil), conteúdo completo e link de referência original na Wikipédia.
- **Navegação Integrada:** Rotas tipadas no `EruditoNavHost` preservando estado da pesquisa ao retornar da leitura.

### Bundle 3 — Integração entre Artigos e Coleções (Relação N:N)
- **Persistência sem Duplicação (RN02, RN03, RN04):** Migração Room `v1 → v2` (`MIGRATION_1_2`) adicionando `wikipediaPageId` com índice único e reutilização transacional de registros existentes (incluindo fallback por URL para artigos legados).
- **Seletor Reutilizável de Coleções (`CollectionPickerDialog`):** Seleção múltipla com indicação `"Já salvo"`, criação inline de novas coleções e validação contra seleção vazia.
- **Salvamento na Leitura Online:** Associação de artigos da Wikipédia a uma ou mais coleções diretamente em `WikipediaArticleScreen` com atualização reativa imediata e feedback via Snackbar.
- **Gerenciamento na Leitura Offline e Remoção Individual:** Reorganização de coleções diretamente em `ArticleDetailScreen` e remoção individual de vínculos N:N em `CollectionDetailScreen` com diálogo de confirmação, preservando o artigo na biblioteca e nas demais coleções.

### Bundle 4 — Offline First e Sincronização
- **Detecção Real de Conectividade (`AndroidConnectivityObserver`):** Monitoramento via `ConnectivityManager.NetworkCallback` exigindo `NET_CAPABILITY_INTERNET`, `NET_CAPABILITY_VALIDATED` e `NET_CAPABILITY_NOT_SUSPENDED` (Android P+), refletido de forma reativa em `HomeScreen`, `SearchScreen`, `WikipediaArticleScreen` e `ArticleDetailScreen`.
- **Leitura Local Prioritária e Fallback Offline:** Ao abrir um artigo da Wikipédia sem conexão ou quando a consulta remota falha, o app consulta automaticamente o Room pelo `wikipediaPageId` e exibe a cópia local salva (sem salvar artigos online automaticamente).
- **Banco Room v3 (`MIGRATION_2_3`):** Adição do campo `lastSyncedAt` em `ArticleEntity` preservando registros existentes (`lastSyncedAt = null`) e mantendo associações N:N intactas.
- **Sincronização Segura e Merge Defensivo (`SyncPolicy` + `ArticleSyncMerger`):**
  - Política de frescor com intervalo mínimo padrão de **6 horas** na sincronização automática da Home.
  - Diferenciação explícita entre conteúdo extraído real (`hasExtractedContent`) e fallback por descrição, impedindo que respostas vazias ou incompletas apaguem conteúdo local válido.
  - Atualização via `@Update` (`ArticleDao.updateArticle`), evitando `OnConflictStrategy.REPLACE` e preservando vínculos com coleções; `lastUpdated` só é alterado quando há mudança efetiva no conteúdo persistido.
- **Integração com a Interface:**
  - Sincronização automática controlada ao iniciar `HomeViewModel` com banner discreto de status (`HomeSyncStatusBanner`: sincronizando, atualizada, sem alterações, falha parcial com botão **"Tentar novamente"**, falha total e modo offline), sem bloquear a leitura local.
  - Atualização manual sob demanda (`forceRefresh = true`) na `ArticleDetailScreen` (exibindo `"Última sincronização em ..."` ou aviso quando não há `wikipediaPageId`) e botão **"Atualizar cópia salva"** na `WikipediaArticleScreen` quando o artigo já está salvo localmente.
  - Proteção contra execuções simultâneas, propagação de `CancellationException` e isolamento de mensagens técnicas de exceção.

---

## 3. Arquitetura

O projeto adota **Clean Architecture** + **MVVM**:

```text
com.example.erudito/
├── data/
│   ├── connectivity/     # AndroidConnectivityObserver (ConnectivityManager.NetworkCallback)
│   ├── local/
│   │   ├── dao/          # CollectionDao, ArticleDao (@Update na sincronização)
│   │   ├── database/     # EruditoDatabase (Room v3 + MIGRATION_1_2 + MIGRATION_2_3)
│   │   └── entity/       # CollectionEntity, ArticleEntity, CollectionArticleCrossRef
│   ├── mapper/           # ArticleMapper, CollectionMapper, WikipediaMapper (hasExtractedContent)
│   ├── remote/
│   │   ├── api/          # WikipediaApiService (Retrofit - API MediaWiki pt.wikipedia.org)
│   │   └── dto/          # WikipediaResponseDtos (Moshi)
│   └── repository/       # CollectionRepositoryImpl, ArticleRepositoryImpl, WikipediaRepositoryImpl
├── di/                   # EruditoHiltModule (Hilt SingletonComponent)
├── domain/
│   ├── connectivity/     # Interface ConnectivityObserver
│   ├── model/            # Collection, Article, CollectionWithArticles, WikipediaSearchResult, WikipediaArticle, WikipediaException
│   ├── repository/       # CollectionRepository, ArticleRepository, WikipediaRepository
│   ├── sync/             # SyncPolicy, ArticleSyncMerger, SyncResult, RefreshArticleOutcome, SkipReason
│   └── usecase/          # Use Cases (coleções, busca/leitura Wikipédia, N:N, observação local, RefreshSavedArticleUseCase, SyncSavedArticlesUseCase)
└── presentation/
    ├── article_detail/   # Leitura offline + atualização manual + gerenciamento de coleções
    ├── collection_detail/# Conteúdo da coleção + remoção individual de vínculo N:N
    ├── collections/      # Gerenciamento de coleções
    ├── components/       # Cards, CollectionPickerDialog, diálogos, TopAppBar, EmptyStateView
    ├── home/             # Painel inicial, conectividade e auto-sync controlado
    ├── navigation/       # EruditoNavHost e Screen
    ├── search/           # Pesquisa na Wikipédia + estado offline
    └── wikipedia_article/# Leitura online/fallback local + salvar/atualizar cópia salva
```

---

## 4. Tecnologias Utilizadas

- **Linguagem:** Kotlin 2.2.10 (JVM 11 / `compileSdk` 36, `minSdk` 24, `targetSdk` 36)
- **UI:** Jetpack Compose com Material Design 3
- **Navegação:** Navigation Compose 2.8.9
- **Rede & Imagens:** Retrofit 2.12.0, Moshi 1.15.2, OkHttp 4.10.0, Coil 2.7.0
- **Persistência Local:** Room Database 2.7.0 (KSP, `MIGRATION_1_2` e `MIGRATION_2_3`)
- **Conectividade:** Android `ConnectivityManager.NetworkCallback` (`NET_CAPABILITY_INTERNET`, `NET_CAPABILITY_VALIDATED`, `NET_CAPABILITY_NOT_SUSPENDED`)
- **Assincronia:** Kotlin Coroutines 1.10.2 & `Flow` / `StateFlow`
- **Injeção de Dependências:** Hilt 2.55 (`@HiltAndroidApp`, `@AndroidEntryPoint`, `@HiltViewModel`, `@Inject`) e `hilt-navigation-compose` 1.2.0
- **Testes:** JUnit 4.13.2, Kotlinx Coroutines Test 1.10.2, Robolectric 4.16.1, Compose UI Test

---

## 5. Como Configurar, Compilar e Executar

### Pré-requisitos
- A API pública da Wikipédia em Português (`https://pt.wikipedia.org/`) é utilizada via Retrofit sem necessidade de chave de API adicional.
- O projeto utiliza o SDK Android configurado via ambiente (`ANDROID_SDK_ROOT`) e Gradle (`gradle` no ambiente ou Android Studio).

### No AI Studio / Streaming Emulator
O aplicativo é compilado e instalado automaticamente no emulador streaming integrado ao finalizar as alterações.

### Via Linha de Comando (Gradle)
```bash
# Compilar o código Kotlin do app (debug)
gradle :app:compileDebugKotlin

# Compilar o APK de debug (quando necessário em ambiente local)
gradle assembleDebug

# Executar toda a suíte de testes unitários e Robolectric
gradle :app:testDebugUnitTest --rerun-tasks
```

---

## 6. Resultados de Testes

A suíte automatizada cobre regras de domínio, política/merge de sincronização, migrações Room `v1 → v2 → v3`, conectividade Android, ViewModels, telas Compose com Robolectric e navegação ponta a ponta. Consulte o arquivo `TEST_RESULTS.md` para o histórico consolidado por etapa do Bundle 4 e o detalhamento completo das suítes executadas.

