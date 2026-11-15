# Erudito — Planejamento Técnico

## 1. Visão geral

Este documento define o planejamento técnico e a estratégia de desenvolvimento do aplicativo Erudito, utilizando a abordagem Spec-Driven Development (SDD).

## 2. Tecnologias e Ferramentas

| Tecnologia | Finalidade |
|---|---|
| Kotlin | Linguagem principal do aplicativo |
| Jetpack Compose | Desenvolvimento das interfaces (Material 3) |
| Navigation Compose | Navegação entre telas |
| Retrofit + Moshi + OkHttp | Consumo e desserialização da API MediaWiki da Wikipédia (Bundle 2) |
| Coil | Carregamento assíncrono de imagens com fallback offline |
| Room | Persistência local e migração de schema (`v1 → v2` com `MIGRATION_1_2`) |
| Kotlin Coroutines | Execução de operações assíncronas |
| Flow / StateFlow | Observação reativa e comunicação de estado entre camadas |
| Hilt | Injeção de dependências (`EruditoHiltModule`) |
| JUnit | Testes unitários |
| Robolectric | Testes locais de UI Compose, navegação e integração Room no JVM |

## 3. Arquitetura do Projeto

O aplicativo utiliza Clean Architecture, organizada em três camadas principais:
- **Presentation:** Interfaces em Compose (`HomeScreen`, `CollectionsScreen`, `CollectionDetailScreen`, `ArticleDetailScreen`, `SearchScreen`, `WikipediaArticleScreen`), componentes reutilizáveis (`CollectionPickerDialog`, `ArticleCard`, `CollectionCard`, `SearchResultCard`, diálogos), ViewModels com `StateFlow` / `Channel` e Navigation Compose (`EruditoNavHost`).
- **Domain:** Modelos de negócio puros (`Collection`, `Article`, `CollectionWithArticles`, `WikipediaSearchResult`, `WikipediaArticle`, `WikipediaException`), contratos de repositórios (`CollectionRepository`, `ArticleRepository`, `WikipediaRepository`) e Use Cases das regras RN01–RN07 (`CreateCollectionUseCase`, `RenameCollectionUseCase`, `DeleteCollectionUseCase`, `GetCollectionsUseCase`, `GetCollectionWithArticlesUseCase`, `GetSavedArticlesUseCase`, `GetArticleByIdUseCase`, `SearchWikipediaArticlesUseCase`, `GetWikipediaArticleUseCase`, `SaveWikipediaArticleToCollectionsUseCase`, `AddArticleToCollectionsUseCase`, `RemoveArticleFromCollectionUseCase`, `GetCollectionIdsForArticleUseCase`).
- **Data:** Implementação dos repositórios (`CollectionRepositoryImpl`, `ArticleRepositoryImpl`, `WikipediaRepositoryImpl`), Room Database v2 (`EruditoDatabase`, `MIGRATION_1_2`), DAOs (`CollectionDao`, `ArticleDao`), API MediaWiki (`WikipediaApiService`, DTOs), entidades e mappers (`CollectionMapper`, `ArticleMapper`, `WikipediaMapper`).
