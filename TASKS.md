# Erudito — Plano de Tarefas

## 1. Objetivo

Este documento organiza o desenvolvimento do Erudito em bundles funcionais, seguindo a especificação e o planejamento técnico definidos nos documentos `SPECIFICATION.md` e `PLANNING.md`.

Cada bundle representa um conjunto de funcionalidades relacionadas, desenvolvido de maneira integrada.

A implementação deverá ocorrer progressivamente, com revisão, validação e versionamento após a conclusão de cada bundle.

## 2. Regras de desenvolvimento

- Seguir integralmente os requisitos definidos no `SPECIFICATION.md`.
- Respeitar as decisões arquiteturais do `PLANNING.md`.
- Desenvolver os bundles na sequência estabelecida.
- Implementar cada funcionalidade de maneira integrada, evitando separar artificialmente telas, regras de negócio e persistência.
- Não antecipar funcionalidades de bundles futuros.
- Preservar o funcionamento das funcionalidades já implementadas.
- Executar os testes pertinentes antes de concluir cada bundle.
- Registrar a evolução do projeto utilizando commits Git.
- Atualizar a documentação conforme o desenvolvimento avança.

## 3. Bundle 1 — Biblioteca pessoal offline

**Objetivo:** implementar a biblioteca pessoal e o gerenciamento local de coleções, permitindo utilizar essas funcionalidades sem conexão com a internet.

### Tarefas

- [x] Criar a estrutura inicial do aplicativo Android.
- [x] Configurar Kotlin, Jetpack Compose e dependências necessárias.
- [x] Implementar a organização em Clean Architecture.
- [x] Configurar Hilt para injeção de dependências (@HiltAndroidApp, @AndroidEntryPoint, @HiltViewModel, @Inject).
- [x] Configurar o banco de dados Room.
- [x] Criar a entidade de coleção.
- [x] Criar a entidade de artigo.
- [x] Criar a estrutura de relacionamento entre artigos e coleções.
- [x] Implementar os DAOs necessários.
- [x] Implementar os repositórios locais.
- [x] Implementar os casos de uso relacionados às coleções.
- [x] Criar a tela inicial da biblioteca.
- [x] Criar a tela de coleções.
- [x] Implementar criação de coleções.
- [x] Implementar renomeação de coleções.
- [x] Implementar exclusão de coleções.
- [x] Implementar visualização do conteúdo das coleções.
- [x] Implementar visualização de artigos armazenados localmente.
- [x] Configurar Navigation Compose para as telas implementadas.
- [x] Implementar gerenciamento de estado utilizando ViewModels e StateFlow.
- [x] Implementar testes unitários das regras de negócio do bundle e testes de integração do Room.

### Critérios de conclusão

- [x] O aplicativo deve iniciar e apresentar a biblioteca.
- [x] O usuário deve conseguir criar, visualizar, renomear e excluir coleções.
- [x] As coleções devem permanecer armazenadas após fechar e reabrir o aplicativo.
- [x] As telas da biblioteca devem funcionar sem conexão com a internet.
- [x] A arquitetura deve respeitar a separação entre Presentation, Domain e Data.
- [x] Os testes das regras de negócio implementadas devem ser executados com sucesso.

**Commit sugerido:**

`feat: implementa biblioteca pessoal e gerenciamento offline de coleções`

## 4. Bundle 2 — Pesquisa e consulta à Wikipédia

**Objetivo:** permitir pesquisar artigos na Wikipédia e consultar seus conteúdos.

### Tarefas

- [x] Configurar Retrofit.
- [x] Implementar o serviço de comunicação com a API MediaWiki.
- [x] Criar DTOs para os dados recebidos.
- [x] Implementar os mapeadores necessários.
- [x] Implementar a integração com os repositórios.
- [x] Implementar o caso de uso de pesquisa.
- [x] Implementar o caso de uso de consulta de artigo.
- [x] Criar a tela de pesquisa.
- [x] Implementar o campo de pesquisa.
- [x] Exibir os resultados encontrados.
- [x] Apresentar títulos e descrições.
- [x] Apresentar imagens quando disponíveis.
- [x] Criar a tela de leitura do artigo.
- [x] Implementar a exibição do conteúdo.
- [x] Implementar referência para o artigo original na Wikipédia.
- [x] Implementar estados de carregamento, sucesso, ausência de resultados e erro.
- [x] Implementar tratamento de falhas de comunicação.
- [x] Integrar as telas à navegação existente.

### Critérios de conclusão

- [x] O usuário deve conseguir pesquisar artigos.
- [x] Os resultados devem ser apresentados corretamente.
- [x] O usuário deve conseguir abrir e consultar os artigos.
- [x] Imagens e descrições devem ser exibidas quando disponíveis.
- [x] Falhas de conexão não devem provocar crashes previsíveis.
- [x] Os estados da interface devem representar corretamente o resultado das operações.

**Commit sugerido:**

`feat: implementa pesquisa e consulta de artigos da Wikipedia`

## 5. Bundle 3 — Integração entre artigos e coleções

**Objetivo:** permitir que os artigos encontrados sejam armazenados e organizados na biblioteca pessoal.

### Tarefas

- [x] Implementar o caso de uso para salvar artigos.
- [x] Implementar associação de artigos às coleções.
- [x] Implementar seleção de uma ou mais coleções.
- [x] Permitir adicionar um artigo a várias coleções.
- [x] Impedir duplicação do mesmo artigo na mesma coleção.
- [x] Implementar remoção de artigos das coleções.
- [x] Persistir os artigos e suas associações no Room.
- [x] Reutilizar artigos já armazenados, evitando duplicação dos dados.
- [x] Integrar a pesquisa da Wikipédia com a biblioteca local.
- [x] Integrar a tela de leitura com as opções de salvamento.
- [x] Atualizar automaticamente as telas quando os dados locais forem alterados.
- [x] Implementar testes unitários das regras de associação e remoção.

### Critérios de conclusão

- [x] O usuário deve conseguir salvar artigos encontrados na Wikipédia.
- [x] Um artigo deve poder pertencer a várias coleções.
- [x] Um artigo não deve aparecer duplicado dentro da mesma coleção.
- [x] Os artigos devem permanecer armazenados localmente.
- [x] As associações devem ser mantidas após reiniciar o aplicativo.
- [x] Os testes das regras de negócio devem apresentar resultados satisfatórios.

**Commit sugerido:**

`feat: integra artigos da Wikipedia com coleções pessoais`

## 6. Bundle 4 — Offline First e sincronização

**Objetivo:** garantir que os artigos e as coleções armazenados possam ser utilizados sem conexão e implementar a atualização dos conteúdos locais.

### Tarefas

- [ ] Definir e implementar o fluxo de leitura priorizando os dados locais.
- [ ] Garantir a consulta dos artigos salvos sem internet.
- [ ] Garantir o gerenciamento de coleções sem internet.
- [ ] Garantir a organização dos artigos armazenados sem internet.
- [ ] Implementar identificação da disponibilidade de conexão.
- [ ] Definir a estratégia de atualização dos artigos.
- [ ] Implementar atualização de conteúdos quando houver conexão.
- [ ] Preservar os dados locais em caso de falha na API.
- [ ] Implementar tratamento de falhas de sincronização.
- [ ] Evitar sobrescrita indevida de dados locais.
- [ ] Validar a consistência dos artigos e associações.
- [ ] Realizar testes de funcionamento com e sem conexão.

### Critérios de conclusão

- Os artigos salvos devem continuar disponíveis offline.
- As coleções devem continuar funcionando offline.
- O aplicativo não deve depender da API para apresentar dados já armazenados.
- A atualização dos artigos deve ocorrer quando houver conexão e conforme a estratégia definida.
- Falhas de sincronização não devem comprometer os dados locais.
- O funcionamento offline deve ser demonstrável.

**Commit sugerido:**

`feat: implementa funcionamento offline e sincronização de artigos`

## 7. Bundle 5 — Finalização e validação

**Objetivo:** revisar o aplicativo, verificar os requisitos e preparar os entregáveis da atividade.

### Tarefas

- [ ] Revisar a implementação da Clean Architecture.
- [ ] Revisar o gerenciamento de dependências.
- [ ] Revisar o tratamento de erros e exceções.
- [ ] Verificar possíveis vazamentos de memória.
- [ ] Verificar recomposições desnecessárias.
- [ ] Revisar os estados das interfaces.
- [ ] Executar os testes unitários.
- [ ] Corrigir falhas identificadas.
- [ ] Verificar a navegação entre todas as telas.
- [ ] Testar a persistência após fechar e reabrir o aplicativo.
- [ ] Testar o comportamento sem conexão com a internet.
- [ ] Verificar os requisitos do `SPECIFICATION.md`.
- [ ] Atualizar o README.md.
- [ ] Finalizar o TEST_RESULTS.md.
- [ ] Atualizar o AI_USAGE.md.
- [ ] Registrar limitações conhecidas.
- [ ] Capturar evidências das funcionalidades.
- [ ] Capturar evidências do funcionamento offline.
- [ ] Gerar o APK debug sem assinatura de distribuição.
- [ ] Revisar o histórico de commits e a organização do repositório.

### Critérios de conclusão

- Todos os requisitos obrigatórios devem estar implementados ou suas limitações devidamente documentadas.
- Os testes devem ter sido executados e seus resultados registrados.
- A documentação deve estar atualizada.
- O APK deve estar disponível.
- As evidências devem demonstrar as principais funcionalidades.
- O projeto deve estar organizado para avaliação.

**Commit sugerido:**

`docs: finaliza documentação e evidências do projeto`

## 8. Controle de progresso

| Bundle | Descrição | Status |
|---|---|---|
| 1 | Biblioteca pessoal offline | **Concluído** |
| 2 | Pesquisa e consulta à Wikipédia | **Concluído** |
| 3 | Integração entre artigos e coleções | **Concluído** |
| 4 | Offline First e sincronização | Pendente |
| 5 | Finalização e validação | Pendente |

---

**Documento:** TASKS.md  
**Projeto:** Erudito  
**Etapa SDD:** Decomposição  
**Status:** Bundle 3 Concluído
