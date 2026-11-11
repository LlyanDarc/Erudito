# Erudito

## 1. Sobre o projeto

O **Erudito** é um aplicativo Android de biblioteca pessoal de conhecimento, integrado à Wikipédia. Seu objetivo é permitir que usuários pesquisem artigos, consultem conteúdos e organizem conhecimentos em coleções personalizadas.

O aplicativo permite salvar artigos em diferentes coleções e acessar os conteúdos armazenados mesmo sem conexão com a internet.

## 2. Objetivo

Desenvolver um aplicativo Android que facilite a organização e a consulta de conhecimentos, oferecendo uma biblioteca pessoal persistente e acessível offline.

O projeto também busca aplicar conceitos de arquitetura de software, desenvolvimento Android moderno, consumo de APIs REST, persistência local e testes automatizados.

## 3. Funcionalidades

- Pesquisar artigos na Wikipédia.
- Consultar artigos e seus conteúdos.
- Visualizar informações como título, descrição e imagem.
- Criar, renomear e excluir coleções personalizadas.
- Salvar artigos em uma ou mais coleções.
- Remover artigos das coleções.
- Impedir duplicação do mesmo artigo dentro de uma coleção.
- Armazenar artigos e coleções localmente.
- Consultar artigos salvos sem conexão com a internet.
- Atualizar conteúdos armazenados quando houver conexão.

## 4. Tecnologias utilizadas

- **Kotlin:** linguagem principal.
- **Jetpack Compose:** construção da interface.
- **Navigation Compose:** navegação entre telas.
- **Clean Architecture:** organização e separação de responsabilidades.
- **Hilt:** injeção de dependências.
- **Retrofit:** comunicação com a API REST.
- **Room:** persistência local.
- **Coroutines e Flow:** operações assíncronas e gerenciamento de estados.
- **JUnit e MockK:** testes automatizados.

## 5. API utilizada

O aplicativo utiliza a API da Wikipédia, baseada na MediaWiki API, para pesquisar e consultar artigos.

A escolha se justifica pela disponibilidade pública, variedade de conteúdos e possibilidade de integração sem necessidade de autenticação para consultas públicas comuns.

Documentação oficial:

- https://www.mediawiki.org/wiki/API:REST_API
- https://www.mediawiki.org/wiki/API:Main_page

## 6. Arquitetura

O projeto utiliza **Clean Architecture**, organizada em três camadas principais:

- **Presentation:** interface, navegação, gerenciamento de estados e ViewModels.
- **Domain:** regras de negócio, modelos, contratos de repositórios e casos de uso.
- **Data:** implementação dos repositórios, acesso à API, persistência local e conversão de dados.

A injeção de dependências será realizada com Hilt.

## 7. Estratégia Offline First

O Erudito utiliza o Room como fonte local dos artigos salvos e das coleções.

As principais funcionalidades de organização e consulta da biblioteca devem funcionar sem conexão com a internet.

A API da Wikipédia será utilizada para pesquisar novos artigos e atualizar conteúdos já armazenados. Caso ocorra uma falha de conexão, os dados locais deverão permanecer disponíveis.

## 8. Estrutura de desenvolvimento

O desenvolvimento será organizado por bundles funcionais:

1. Biblioteca pessoal offline.
2. Pesquisa e consulta de artigos da Wikipédia.
3. Integração entre artigos e coleções.
4. Funcionamento Offline First e sincronização.
5. Finalização, testes e validação.

A implementação seguirá o processo de **Specification-Driven Development (SDD)**, utilizando os documentos de especificação, planejamento e decomposição de tarefas como referência.

## 9. Configuração e execução

As instruções de configuração, execução e dependências serão documentadas após a implementação da estrutura inicial do projeto.

## 10. Testes

Serão desenvolvidos testes unitários para validar as principais regras de negócio, especialmente as operações envolvendo coleções e artigos.

Os resultados dos testes serão registrados em `TEST_RESULTS.md`.

## 11. Evidências e entregáveis

Ao final do desenvolvimento, serão disponibilizados:

- Código-fonte do aplicativo.
- Histórico de commits.
- Documentação SDD.
- Testes automatizados e resultados.
- Capturas de tela das funcionalidades.
- Evidências do funcionamento offline.
- APK de depuração sem assinatura.

## 12. Limitações do escopo

A versão inicial não contempla:

- Cadastro ou autenticação de usuários.
- Sincronização entre dispositivos.
- Compartilhamento de coleções.
- Edição do conteúdo original dos artigos.
- Integração com fontes externas além da Wikipédia.