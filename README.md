# Erudito — Biblioteca Pessoal de Conhecimento

O **Erudito** é um aplicativo Android desenvolvido em Kotlin e Jetpack Compose que funciona como uma biblioteca pessoal de conhecimento integrada à Wikipédia, permitindo aos usuários organizar temas de estudo em coleções personalizadas e consultar artigos offline.

---

## 1. Status do Projeto

| Bundle | Descrição | Status |
|---|---|---|
| **Bundle 1** | **Biblioteca pessoal offline** | **Concluído** |
| Bundle 2 | Pesquisa e consulta à Wikipédia | Próximo |
| Bundle 3 | Integração entre artigos e coleções | Planejado |
| Bundle 4 | Offline First e sincronização | Planejado |
| Bundle 5 | Finalização, testes e validação | Planejado |

---

## 2. Funcionalidades do Bundle 1 (Implementadas)

- **Biblioteca Pessoal Offline:** Painel inicial da biblioteca exibindo coleções, artigos salvos e indicador de status 100% offline.
- **Gerenciamento de Coleções:**
  - Criar coleções personalizadas com validação em tempo real (RN01: proíbe nomes vazios ou compostos apenas por espaços).
  - Listar coleções com contador dinâmico de artigos associados.
  - Renomear coleções existentes com validação RN01.
  - Excluir coleções com diálogo de confirmação seguro e remoção em cascata das associações (RN05), preservando os artigos na biblioteca.
- **Visualização de Coleções e Artigos:**
  - Visualizar artigos associados a cada coleção com contadores e data de criação.
  - Visualizar artigos armazenados localmente na biblioteca.
  - Tela de leitura de artigos offline com tipografia confortável, badges de status, formatação enciclopédica e link de referência para a Wikipédia.
- **Persistência Local com Room:**
  - Banco de dados SQLite via Room (`erudito_database.db`) contendo as tabelas `collections`, `articles` e a tabela intermediária `collection_article_cross_ref`.
  - Pré-população inicial com artigos clássicos e coleções para exploração imediata no primeiro lançamento.
  - Persistência total garantida após fechar e reabrir o aplicativo.
- **Clean Architecture & Injeção de Dependências:**
  - Separação estrita entre camadas: **Presentation** (Compose, ViewModels, Navigation), **Domain** (Modelos puros, Use Cases, Interfaces de Repositório) e **Data** (Room DAOs, Entities, Mappers, Repositórios).
  - Contêiner de Injeção de Dependências (`AppContainer` / `EruditoViewModelFactory`).

---

## 3. Arquitetura

O projeto adota os princípios de **Clean Architecture**:

```text
com.example.erudito/
├── data/
│   ├── local/
│   │   ├── dao/          # CollectionDao, ArticleDao
│   │   ├── database/     # EruditoDatabase (Room)
│   │   └── entity/       # CollectionEntity, ArticleEntity, CollectionArticleCrossRef
│   ├── mapper/           # Mapeadores entre entidades de banco e modelos de domínio
│   └── repository/       # Implementação dos repositórios
├── di/                   # Contêiner de Injeção de Dependências (AppContainer, ViewModelFactory)
├── domain/
│   ├── model/            # Modelos puros: Collection, Article, CollectionWithArticles
│   ├── repository/       # Interfaces dos repositórios
│   └── usecase/          # Regras de negócio e Use Cases (Create, Rename, Delete, Get...)
└── presentation/
    ├── article_detail/   # Tela de leitura offline de artigo e ViewModel
    ├── collection_detail/# Tela de conteúdo da coleção e ViewModel
    ├── collections/      # Tela de gerenciamento de coleções e ViewModel
    ├── components/       # Componentes M3: Cards, Diálogos de criação/exclusão, TopAppBar
    ├── home/             # Painel inicial da biblioteca e ViewModel
    └── navigation/       # Navigation Compose e rotas tipadas
```

---

## 4. Tecnologias Utilizadas

- **Linguagem:** Kotlin 2.2.10
- **UI:** Jetpack Compose com Material Design 3
- **Navegação:** Navigation Compose 2.8.9
- **Persistência Local:** Room Database 2.7.0 (com KSP)
- **Assincronia:** Kotlin Coroutines & Flow / StateFlow
- **Injeção de Dependências:** Hilt 2.55 (@HiltAndroidApp, @AndroidEntryPoint, @HiltViewModel, @Inject) e hilt-navigation-compose
- **Testes Unitários:** JUnit 4, Kotlinx Coroutines Test, Robolectric 4.16.1

---

## 5. Como Executar o Aplicativo

### No AI Studio / Streaming Emulator
O aplicativo é compilado e instalado automaticamente no emulador integrado.

### Via Linha de Comando (Gradle)
```bash
# Compilar o APK de debug
gradle assembleDebug

# Executar os testes unitários
gradle :app:testDebugUnitTest
```

---

## 6. Resultados de Testes

Os testes unitários das regras de negócio (RN01 a RN07) foram executados e aprovados com 100% de sucesso. Consulte o arquivo `TEST_RESULTS.md` para o relatório completo.
