# Erudito — Registro de Resultados de Testes

**Data da Última Revisão:** 15 de Novembro de 2026  
**Ambiente:** Android SDK 36, Gradle 9.3.1, Kotlin 2.2.10, Robolectric 4.16.1, JUnit 4.13.2, Dagger Hilt 2.55 (KSP), Room 2.7.0  
**Comando Executado:** `gradle :app:compileDebugKotlin :app:testDebugUnitTest --rerun-tasks`  
**Escopo Validado:** Bundles 1, 2 e 3 (Etapas 1–5 concluídas)

---

## 1. Resumo da Execução

| Camada | Test Suite | Executados | Aprovados | Reprovados | Ignorados | Tempo |
|---|---|---|---|---|---|---|
| Domain | `ArticleCollectionIntegrationUseCasesTest` | 10 | 10 | 0 | 0 | 0.07s |
| Domain | `CreateCollectionUseCaseTest` | 4 | 4 | 0 | 0 | 0.01s |
| Domain | `RenameCollectionUseCaseTest` | 4 | 4 | 0 | 0 | 0.01s |
| Domain | `DeleteCollectionUseCaseTest` | 1 | 1 | 0 | 0 | < 0.01s |
| Domain | `GetCollectionsUseCaseTest` | 2 | 2 | 0 | 0 | < 0.01s |
| Domain | `GetCollectionWithArticlesUseCaseTest` | 2 | 2 | 0 | 0 | 0.01s |
| Domain | `GetSavedArticlesUseCaseTest` | 2 | 2 | 0 | 0 | < 0.01s |
| Domain | `GetArticleByIdUseCaseTest` | 2 | 2 | 0 | 0 | < 0.01s |
| Domain | `WikipediaUseCasesTest` | 7 | 7 | 0 | 0 | 0.03s |
| Data (Room Integration) | `RoomDatabaseIntegrationTest` | 16 | 16 | 0 | 0 | 8.88s |
| Data (Mappers) | `ArticleAndWikipediaMapperTest` | 4 | 4 | 0 | 0 | < 0.01s |
| Data (Remote DTOs) | `WikipediaDtoDeserializationTest` | 7 | 7 | 0 | 0 | 0.27s |
| Data (Repositories) | `ArticleRepositoryImplTest` | 5 | 5 | 0 | 0 | 0.39s |
| Data (Repositories) | `WikipediaRepositoryImplTest` | 9 | 9 | 0 | 0 | 0.12s |
| Presentation (ViewModel) | `SearchViewModelTest` | 9 | 9 | 0 | 0 | 0.04s |
| Presentation (ViewModel) | `WikipediaArticleViewModelTest` | 14 | 14 | 0 | 0 | 0.09s |
| Presentation (ViewModel) | `CollectionDetailViewModelTest` | 6 | 6 | 0 | 0 | 0.03s |
| Presentation (ViewModel) | `ArticleDetailViewModelTest` | 7 | 7 | 0 | 0 | 0.06s |
| Presentation (UI & Compose) | `SearchScreenTest` | 6 | 6 | 0 | 0 | 1.44s |
| Presentation (UI & Compose) | `WikipediaArticleScreenTest` | 8 | 8 | 0 | 0 | 1.51s |
| Presentation (UI & Compose) | `CollectionPickerDialogTest` | 5 | 5 | 0 | 0 | 1.74s |
| Presentation (UI & Compose) | `CollectionDetailScreenTest` | 3 | 3 | 0 | 0 | 0.91s |
| Presentation (UI & Compose) | `ArticleDetailScreenTest` | 6 | 6 | 0 | 0 | 5.88s |
| Presentation (UI & Compose) | `CollectionFeedbackMessagesTest` | 5 | 5 | 0 | 0 | 1.32s |
| Presentation (UI & Compose) | `HomeErrorHandlingTest` | 2 | 2 | 0 | 0 | 0.55s |
| Presentation (UI & Compose) | `HomeScreenDuplicateKeyTest` | 1 | 1 | 0 | 0 | 0.31s |
| Presentation (Navigation) | `EruditoNavigationTest` | 4 | 4 | 0 | 0 | 1.31s |
| Presentation (E2E Room + Nav) | `Bundle3EndToEndIntegrationTest` | 3 | 3 | 0 | 0 | 4.59s |
| Presentation / Resources | `ExampleRobolectricTest` | 1 | 1 | 0 | 0 | 9.23s |
| Unit | `ExampleUnitTest` | 1 | 1 | 0 | 0 | < 0.01s |
| **Total** | **30 Suítes** | **156** | **156** | **0** | **0** | **2m 2s (build + rerun-tasks)** |

**Resultado Geral:** **SUCESSO (156 aprovados, 0 reprovados, 0 ignorados)**

---

## 2. Cobertura por Bundle e Camada

### 2.1. Bundle 1 — Biblioteca Pessoal Offline
- Regras de negócio RN01–RN07 na camada Domain (`CreateCollectionUseCaseTest`, `RenameCollectionUseCaseTest`, `DeleteCollectionUseCaseTest`, `GetCollectionsUseCaseTest`, `GetCollectionWithArticlesUseCaseTest`, `GetSavedArticlesUseCaseTest`, `GetArticleByIdUseCaseTest`).
- Persistência SQLite, integridade referencial, remoção em cascata (RN05) e pré-população atômica (`RoomDatabaseIntegrationTest`).
- Feedbacks visuais, tratamento de erros e chaves únicas em listas (`CollectionFeedbackMessagesTest`, `HomeErrorHandlingTest`, `HomeScreenDuplicateKeyTest`).

### 2.2. Bundle 2 — Pesquisa e Consulta à Wikipédia
- Desserialização de DTOs da API MediaWiki e mapeamento para modelos de domínio (`WikipediaDtoDeserializationTest`, `ArticleAndWikipediaMapperTest`, `WikipediaRepositoryImplTest`, `WikipediaUseCasesTest`).
- Fluxo de pesquisa e leitura online (`SearchViewModelTest`, `SearchScreenTest`, `WikipediaArticleViewModelTest`, `WikipediaArticleScreenTest`, `EruditoNavigationTest`).

### 2.3. Bundle 3 — Integração entre Artigos e Coleções (Relação N:N)
- **Banco Room & Migração v1 → v2 (`RoomDatabaseIntegrationTest`):** migração `MIGRATION_1_2` preservando dados legados, índice único para `wikipediaPageId` não nulo, reutilização de artigos por `wikipediaPageId` e por fallback de URL legado, rollback atômico em caso de coleção inexistente e remoção individual de `CollectionArticleCrossRef` preservando artigo e demais coleções.
- **Repositório & Casos de Uso (`ArticleRepositoryImplTest`, `ArticleCollectionIntegrationUseCasesTest`):** persistência transacional, associação múltipla sem duplicidade, remoção isolada e observação reativa de coleções por artigo (`ID`, `wikipediaPageId` e fallback URL).
- **Componentes & Telas (`CollectionPickerDialogTest`, `WikipediaArticleViewModelTest`, `WikipediaArticleScreenTest`, `CollectionDetailViewModelTest`, `CollectionDetailScreenTest`, `ArticleDetailViewModelTest`, `ArticleDetailScreenTest`):** seleção múltipla, badge `"Já salvo"`, criação inline de coleções, confirmação de remoção individual de artigo na coleção, gerenciamento de coleções, exibição de imagem com fallback offline e abertura da URL persistida da Wikipédia (botão `"Abrir na Wikipédia"` e endereço clicável via `LocalUriHandler`) na leitura offline.
- **Integração Ponta a Ponta (`Bundle3EndToEndIntegrationTest` + `EruditoNavigationTest`):** validação completa com `EruditoDatabase` real em memória, repositórios reais, UseCases reais, ViewModels reais e `EruditoNavHost` cobrindo:
  1. `Home → Search → WikipediaArticle → Salvar (com criação inline) → Voltar para Search/Home → Collections → CollectionDetail → ArticleDetail → Modificar associações → Voltar para CollectionDetail → Remover vínculo individual`;
  2. Reutilização de registro legado/local ao reabrir artigo na Wikipédia sem duplicar artigo ou vínculos;
  3. Desvinculação total de coleções em `ArticleDetailScreen` preservando o artigo na biblioteca (`HomeScreen`) e permitindo re-associação posterior.

---

## 3. Pendências para Validação em Ambiente Android Studio / Dispositivo Real

- Execução manual em emulador streaming ou dispositivo físico com conexão real à internet para validar:
  - Resposta real da API MediaWiki (`pt.wikipedia.org`) com o `User-Agent` configurado;
  - Carregamento real de imagens via rede pelo Coil na leitura online e comportamento de cache em modo avião na leitura offline;
  - Persistência em disco do arquivo `erudito_database.db` após encerramento completo (*force stop*) e reabertura do processo Android;
  - Abertura de link externo via navegador (`LocalUriHandler`) no botão `"Abrir na Wikipédia"` e no endereço clicável da `WikipediaArticleScreen` e da `ArticleDetailScreen`.
