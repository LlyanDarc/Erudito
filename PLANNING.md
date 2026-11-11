# Erudito — Planejamento Técnico

## 1. Visão geral

Este documento define o planejamento técnico e a estratégia de desenvolvimento do aplicativo Erudito, utilizando a abordagem Spec-Driven Development (SDD).

## 2. Tecnologias e Ferramentas

| Tecnologia | Finalidade |
|---|---|
| Kotlin | Linguagem principal do aplicativo |
| Jetpack Compose | Desenvolvimento das interfaces |
| Navigation Compose | Navegação entre telas |
| Retrofit | Consumo da API da Wikipédia (Bundle 2) |
| Room | Persistência local |
| Kotlin Coroutines | Execução de operações assíncronas |
| Flow / StateFlow | Observação e comunicação de dados |
| Hilt | Injeção de dependências |
| JUnit | Testes unitários |
| Robolectric | Testes locais no JVM |

## 3. Arquitetura do Projeto

O aplicativo utiliza Clean Architecture, organizada em três camadas principais:
- **Presentation:** Interfaces em Compose, componentes reutilizáveis, gerenciamento de estado via ViewModels e StateFlow, e Navigation Compose.
- **Domain:** Modelos de negócio puros (`Collection`, `Article`, `CollectionWithArticles`), contratos de repositórios e Use Cases com regras de negócio (RN01, RN05).
- **Data:** Implementação de repositórios, Room Database (`EruditoDatabase`), DAOs (`CollectionDao`, `ArticleDao`), entidades e mappers.
