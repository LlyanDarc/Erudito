# Erudito — Registro de Resultados de Testes

**Data da Última Revisão:** 11 de Novembro de 2026  
**Ambiente:** Android SDK 36 (VanillaIceCream), Gradle 9.3.1, Kotlin 2.2.10, Robolectric 4.16.1, JUnit 4.13.2, Dagger Hilt 2.55 (KSP)  
**Bundle Validado:** Bundle 1 — Biblioteca pessoal offline (Revisão Técnica Concluída)  

---

## 1. Resumo da Execução

| Camada | Test Suite | Testes Executados | Sucesso | Falhas | Tempo de Execução |
|---|---|---|---|---|---|
| Domain | `CreateCollectionUseCaseTest` | 4 | 4 | 0 | 0.10s |
| Domain | `RenameCollectionUseCaseTest` | 4 | 4 | 0 | 0.01s |
| Domain | `DeleteCollectionUseCaseTest` | 1 | 1 | 0 | 0.01s |
| Domain | `GetCollectionsUseCaseTest` | 2 | 2 | 0 | < 0.01s |
| Domain | `GetCollectionWithArticlesUseCaseTest` | 2 | 2 | 0 | 0.01s |
| Domain | `GetSavedArticlesUseCaseTest` | 2 | 2 | 0 | < 0.01s |
| Domain | `GetArticleByIdUseCaseTest` | 2 | 2 | 0 | 0.01s |
| Data (Room Integration) | `RoomDatabaseIntegrationTest` | 6 | 6 | 0 | 9.10s |
| Presentation / Resources | `ExampleRobolectricTest` | 1 | 1 | 0 | 9.33s |
| Unit | `ExampleUnitTest` | 1 | 1 | 0 | < 0.01s |
| **Total** | **10 Suites** | **25** | **25** | **0** | **46s (build completo)** |

**Resultado Geral:** **SUCESSO (100% de aprovação — 25 testes)**

---

## 2. Detalhamento dos Testes Executados

### 2.1. Testes de Regras de Negócio e Casos de Uso (Domain)

#### `CreateCollectionUseCaseTest` (Regra RN01)
- `invoke with valid name creates collection successfully`:
  - **Cenário:** Usuário cria uma coleção com nome válido ("Ciência e Astronomia").
  - **Resultado:** Coleção persistida com ID válido e contagem inicial 0. (**APROVADO**)
- `invoke trims whitespace from collection name`:
  - **Cenário:** Usuário insere espaços excedentes ("   História do Brasil   ").
  - **Resultado:** Espaços higienizados antes da persistência ("História do Brasil"). (**APROVADO**)
- `invoke with empty name throws IllegalArgumentException per RN01`:
  - **Cenário:** Criação com string vazia `""`.
  - **Resultado:** Lança `IllegalArgumentException` ("O nome da coleção não pode ser vazio ou composto apenas por espaços."). (**APROVADO**)
- `invoke with whitespace only throws IllegalArgumentException per RN01`:
  - **Cenário:** Criação composta exclusivamente por espaços `"     "`.
  - **Resultado:** Lança `IllegalArgumentException` conforme RN01. (**APROVADO**)

#### `RenameCollectionUseCaseTest` (Regra RN01)
- `invoke with valid name renames collection successfully`:
  - **Cenário:** Renomear coleção existente para "Novo Nome Atualizado".
  - **Resultado:** Nome alterado com sucesso no repositório. (**APROVADO**)
- `invoke trims whitespace when renaming`:
  - **Cenário:** Renomear fornecendo espaços no início e no fim.
  - **Resultado:** Espaços higienizados no repositório. (**APROVADO**)
- `invoke with empty name throws IllegalArgumentException per RN01`:
  - **Cenário:** Tentativa de renomear para `""`.
  - **Resultado:** Lança `IllegalArgumentException` conforme RN01. (**APROVADO**)
- `invoke with whitespace only throws IllegalArgumentException per RN01`:
  - **Cenário:** Tentativa de renomear para `"     "`.
  - **Resultado:** Lança `IllegalArgumentException` conforme RN01. (**APROVADO**)

#### `DeleteCollectionUseCaseTest` (Regra RN05)
- `invoke removes collection from repository`:
  - **Cenário:** Excluir coleção por ID existente.
  - **Resultado:** Coleção e suas associações removidas do repositório. (**APROVADO**)

#### `GetCollectionsUseCaseTest`
- `invoke with empty repository emits empty list`:
  - **Cenário:** Nenhuma coleção cadastrada.
  - **Resultado:** Emite lista vazia reativamente. (**APROVADO**)
- `invoke emits all stored collections`:
  - **Cenário:** Coleções cadastradas no repositório.
  - **Resultado:** Emite a lista com todas as coleções. (**APROVADO**)

#### `GetCollectionWithArticlesUseCaseTest`
- `invoke returns collection with its associated articles`:
  - **Cenário:** Recuperar detalhes de uma coleção e seus artigos associados.
  - **Resultado:** Retorna modelo agregado `CollectionWithArticles` com a lista de artigos. (**APROVADO**)
- `invoke with non-existing collection returns null`:
  - **Cenário:** Consulta com ID inexistente.
  - **Resultado:** Retorna `null`. (**APROVADO**)

#### `GetSavedArticlesUseCaseTest`
- `invoke with empty repository emits empty list`:
  - **Cenário:** Nenhum artigo salvo.
  - **Resultado:** Emite lista vazia. (**APROVADO**)
- `invoke emits all saved articles`:
  - **Cenário:** Artigos salvos na biblioteca.
  - **Resultado:** Emite todos os artigos salvos no Flow. (**APROVADO**)

#### `GetArticleByIdUseCaseTest`
- `invoke with existing id returns article`:
  - **Cenário:** Recuperar artigo salvo por ID válido.
  - **Resultado:** Retorna os detalhes completos do artigo para leitura offline. (**APROVADO**)
- `invoke with non-existing id returns null`:
  - **Cenário:** Consulta de artigo com ID inexistente.
  - **Resultado:** Retorna `null`. (**APROVADO**)

---

### 2.2. Testes de Integração com o Banco de Dados Room (`RoomDatabaseIntegrationTest`)

Executados no JVM via Robolectric com banco de dados SQLite em memória (`Room.inMemoryDatabaseBuilder`):

1. **`insertAndRetrieveCollection`**:
   - Criação de `CollectionEntity` e consulta via DAO.
   - **Resultado:** Dados gravados e lidos corretamente do SQLite. (**APROVADO**)
2. **`renameCollectionInDatabase`**:
   - Atualização do nome da coleção no SQLite.
   - **Resultado:** Registro atualizado com sucesso. (**APROVADO**)
3. **`articleCanBelongToMultipleCollectionsPerRN03` (RN03)**:
   - Um mesmo artigo associado a duas coleções diferentes ("História" e "Artes").
   - **Resultado:** Artigo recuperado com sucesso nas consultas de ambas as coleções. (**APROVADO**)
4. **`preventDuplicateArticleInSameCollectionPerRN04` (RN04)**:
   - Inserção duplicada da associação (mesmo `collectionId` e `articleId`).
   - **Resultado:** Chave primária composta com `OnConflictStrategy.IGNORE` impede duplicação da linha. (**APROVADO**)
5. **`deletingCollectionCascadesCrossRefWithoutDeletingArticlePerRN05` (RN05)**:
   - Exclusão de uma coleção que possui artigo compartilhado com outra coleção.
   - **Resultado:** Associação removida via CASCADE sem apagar o artigo, que continua preservado na outra coleção. (**APROVADO**)
6. **`getAllCollectionsWithCountComputesCorrectAggregate`**:
   - Validação da query SQL com `LEFT JOIN` e `COUNT(cr.articleId)`.
   - **Resultado:** Projeção retorna o número exato de artigos associados a cada coleção. (**APROVADO**)

---

## 3. Conformidade com os Critérios do Bundle 1

| Critério de Conclusão | Evidência de Validação | Status |
|---|---|---|
| Iniciar e apresentar a biblioteca | `MainActivity` carrega `HomeScreen` com hero banner e estatísticas | **Aprovado** |
| Criar coleções | Diálogo com validação RN01 e persistência Room | **Aprovado** |
| Listar coleções | Lista reativa com contagem dinâmica em `HomeScreen` e `CollectionsScreen` | **Aprovado** |
| Renomear coleções | Validação RN01 e atualização no Room | **Aprovado** |
| Excluir coleções | Diálogo de confirmação e exclusão em cascata (RN05) | **Aprovado** |
| Visualizar artigos por coleção | `CollectionDetailScreen` exibe os artigos associados | **Aprovado** |
| Visualizar artigos offline | `ArticleDetailScreen` permite leitura offline completa de artigos | **Aprovado** |
| Persistência local com Room | Validada com 6 testes de integração em SQLite | **Aprovado** |
| Funcionamento 100% offline | Sem dependência de rede nas telas da biblioteca | **Aprovado** |
| Clean Architecture & DI | Camadas desacopladas com Hilt 2.55 (`@HiltAndroidApp`, `@AndroidEntryPoint`, `@HiltViewModel`, `@Inject`) | **Aprovado** |
| Testes unitários e de integração | 25/25 testes aprovados | **Aprovado** |
