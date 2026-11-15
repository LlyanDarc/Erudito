# Erudito — Biblioteca Pessoal de Conhecimento

O **Erudito** é um aplicativo Android desenvolvido em Kotlin e Jetpack Compose que funciona como uma biblioteca pessoal de conhecimento integrada à Wikipédia, permitindo aos usuários organizar temas de estudo em coleções personalizadas e consultar artigos offline.

---

## 1. Status do Projeto

| Bundle | Descrição | Status |
|---|---|---|
| **Bundle 1** | **Biblioteca pessoal offline** | **Concluído** |
| **Bundle 2** | **Pesquisa e consulta à Wikipédia** | **Concluído** |
| **Bundle 3** | **Integração entre artigos e coleções** | **Concluído** |
| Bundle 4 | Offline First e sincronização | Próximo |
| Bundle 5 | Finalização, testes e validação | Planejado |

---

## 2. Funcionalidades Implementadas

### Bundle 1 — Biblioteca Pessoal Offline
- **Biblioteca Pessoal Offline:** Painel inicial da biblioteca exibindo coleções, artigos salvos e indicador de status 100% offline.
- **Gerenciamento de Coleções:** Criar, listar (com contador dinâmico), renomear (validação RN01) e excluir coleções com confirmação e remoção em cascata das associações (RN05).
- **Visualização de Coleções e Artigos:** Telas dedicadas de detalhes da coleção (`CollectionDetailScreen`) e leitura offline confortável (`ArticleDetailScreen`).
- **Persistência Local com Room:** Banco SQLite (`erudito_database.db`) com pré-população inicial atômica e relacionamento N:N via `collection_article_cross_ref`.

### Bundle 2 — Pesquisa e Consulta à Wikipédia
- **Pesquisa na Wikipédia em Português:** Consumo da API MediaWiki (`pt.wikipedia.org`) com Retrofit + Moshi + OkHttp (`User-Agent` configurado com identificação do projeto), estados de carregamento, sucesso, ausência de resultados e tratamento de erros de rede.
- **Leitura Online de Artigos:** Tela `WikipediaArticleScreen` exibindo título, descrição, imagem (via Coil), conteúdo completo e link de referência original na Wikipédia.
- **Navegação Integrada:** Rotas tipadas no `EruditoNavHost` preservando estado da pesquisa ao retornar da leitura.

### Bundle 3 — Integração entre Artigos e Coleções (Relação N:N)
- **Persistência sem Duplicação (RN02, RN03, RN04):** Migração Room `v1 → v2` (`MIGRATION_1_2`) adicionando `wikipediaPageId` com índice único e reutilização transacional de registros existentes (incluindo fallback por URL para artigos legados).
- **Seletor Reutilizável de Coleções (`CollectionPickerDialog`):** Seleção múltipla com indicação `"Já salvo"`, criação inline de novas coleções e validação contra seleção vazia.
- **Salvamento na Leitura Online:** Associação de artigos da Wikipédia a uma ou mais coleções diretamente em `WikipediaArticleScreen` com atualização reativa imediata e feedback via Snackbar.
- **Gerenciamento na Leitura Offline e Remoção Individual:** Reorganização de coleções diretamente em `ArticleDetailScreen` (incluindo exibição de imagem com fallback offline) e remoção individual de vínculos N:N em `CollectionDetailScreen` com diálogo de confirmação, preservando o artigo na biblioteca e nas demais coleções.

---

## 3. Arquitetura

O projeto adota os princípios de **Clean Architecture**:

```text
com.example.erudito/
├── data/
│   ├── local/
│   │   ├── dao/          # CollectionDao, ArticleDao
│   │   ├── database/     # EruditoDatabase (Room v2 + MIGRATION_1_2)
│   │   └── entity/       # CollectionEntity, ArticleEntity, CollectionArticleCrossRef
│   ├── mapper/           # ArticleMapper, CollectionMapper, WikipediaMapper
│   ├── remote/
│   │   ├── api/          # WikipediaApiService (Retrofit)
│   │   └── dto/          # WikipediaResponseDtos (Moshi)
│   └── repository/       # CollectionRepositoryImpl, ArticleRepositoryImpl, WikipediaRepositoryImpl
├── di/                   # Módulo de Injeção de Dependências Hilt (EruditoHiltModule)
├── domain/
│   ├── model/            # Collection, Article, CollectionWithArticles, WikipediaSearchResult, WikipediaArticle, WikipediaException
│   ├── repository/       # Interfaces dos repositórios
│   └── usecase/          # Use Cases (CRUD de coleções, busca/leitura Wikipédia, associação/remoção N:N)
└── presentation/
    ├── article_detail/   # Leitura offline + gerenciamento de coleções do artigo
    ├── collection_detail/# Conteúdo da coleção + remoção individual de vínculo N:N
    ├── collections/      # Gerenciamento de coleções
    ├── components/       # Cards, CollectionPickerDialog, diálogos, TopAppBar, EmptyStateView
    ├── home/             # Painel inicial da biblioteca
    ├── navigation/       # EruditoNavHost e Screen
    ├── search/           # Pesquisa na Wikipédia
    └── wikipedia_article/# Leitura online + salvamento em coleções
```

---

## 4. Tecnologias Utilizadas

- **Linguagem:** Kotlin 2.2.10
- **UI:** Jetpack Compose com Material Design 3
- **Navegação:** Navigation Compose 2.8.9
- **Rede & Imagens:** Retrofit 2.12.0, Moshi 1.15.2, OkHttp 4.10.0, Coil 2.7.0
- **Persistência Local:** Room Database 2.7.0 (com KSP e `MIGRATION_1_2`)
- **Assincronia:** Kotlin Coroutines & Flow / StateFlow
- **Injeção de Dependências:** Hilt 2.55 (`@HiltAndroidApp`, `@AndroidEntryPoint`, `@HiltViewModel`, `@Inject`) e `hilt-navigation-compose`
- **Testes:** JUnit 4, Kotlinx Coroutines Test, Robolectric 4.16.1, Compose UI Test

---

## 5. Como Executar o Aplicativo

### No AI Studio / Streaming Emulator
O aplicativo é compilado e instalado automaticamente no emulador integrado.

### Via Linha de Comando (Gradle)
```bash
# Compilar o APK de debug
gradle assembleDebug

# Executar todos os testes unitários e Robolectric
gradle :app:testDebugUnitTest
```

---

## 6. Resultados de Testes

Todos os testes unitários, de integração Room, de UI Compose e de navegação ponta a ponta (156 testes em 30 suítes) foram executados e aprovados sem falhas ou testes ignorados. Consulte o arquivo `TEST_RESULTS.md` para o relatório completo.
