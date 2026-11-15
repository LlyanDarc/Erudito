# Erudito — Especificação do Projeto

## 1. Visão geral

**Nome:** Erudito

**Descrição:** O Erudito é um aplicativo Android de biblioteca pessoal de conhecimento, integrado à Wikipédia, que permite pesquisar assuntos, consultar artigos e organizar conteúdos em coleções personalizadas.

O aplicativo tem como objetivo facilitar o armazenamento e a organização de informações relevantes, permitindo que o usuário construa sua própria biblioteca de conhecimentos e consulte conteúdos previamente salvos mesmo sem conexão com a internet.

## 2. Objetivo

Desenvolver uma aplicação Android funcional que permita pesquisar artigos da Wikipédia, armazená-los localmente e organizá-los em coleções personalizadas.

Além de oferecer uma ferramenta de organização de conhecimento, o projeto busca aplicar conceitos de engenharia de software, incluindo Clean Architecture, desenvolvimento orientado a especificações (SDD), persistência local, consumo de APIs REST, programação assíncrona e testes automatizados.

## 3. Problema a ser resolvido

Durante pesquisas na internet, é comum encontrar diversos conteúdos interessantes que acabam se perdendo entre páginas, favoritos e históricos de navegação.

O Erudito pretende solucionar esse problema oferecendo um espaço centralizado no qual o usuário possa salvar artigos, separá-los por assuntos e consultá-los posteriormente.

## 4. Público-alvo

Usuários interessados em pesquisar, armazenar e organizar informações sobre diferentes assuntos, especialmente estudantes e pessoas que desejam construir uma biblioteca pessoal de conhecimentos.

## 5. Funcionalidades

### 5.1. Pesquisa de artigos (Bundle 2)
- Permitir que o usuário pesquise assuntos utilizando a API da Wikipédia.
- Exibir uma lista de artigos relacionados à pesquisa.
- Apresentar o título, uma breve descrição e uma imagem, quando disponível.
- Permitir a abertura de um artigo para consultar seu conteúdo.
- Apresentar estados de carregamento, resultados, ausência de resultados e erros.

### 5.2. Consulta de artigos (Bundle 1 & 2)
- Exibir o título e o conteúdo do artigo selecionado.
- Apresentar imagens quando disponíveis.
- Permitir que o usuário salve o artigo em uma ou mais coleções.
- Permitir consultar artigos previamente armazenados sem conexão com a internet.
- Disponibilizar uma referência para o artigo original na Wikipédia.

### 5.3. Gerenciamento de coleções (Bundle 1)
- Permitir criar coleções com nomes personalizados.
- Permitir criar múltiplas coleções.
- Permitir renomear coleções existentes.
- Permitir excluir coleções.
- Exibir as coleções criadas pelo usuário.
- Permitir abrir uma coleção e visualizar seus artigos.

### 5.4. Organização de artigos (Bundle 1 & 3)
- Permitir adicionar artigos às coleções.
- Permitir remover artigos de uma coleção.
- Permitir adicionar o mesmo artigo a diferentes coleções.
- Impedir que um mesmo artigo seja duplicado dentro da mesma coleção.
- Manter os artigos armazenados localmente para consulta posterior.

### 5.5. Funcionamento offline (Bundle 1 & 4)
O aplicativo prioriza os dados armazenados localmente.
As seguintes funcionalidades permanecem disponíveis sem conexão com a internet:
- Visualizar as coleções existentes.
- Criar, renomear e excluir coleções.
- Consultar os artigos previamente armazenados.
- Adicionar artigos armazenados a outras coleções.
- Remover artigos das coleções.

## 6. Requisitos funcionais

| Código | Requisito | Status Atual (Bundles 1–3) |
|---|---|---|
| RF01 | O sistema deve permitir pesquisar artigos na Wikipédia. | **Implementado** (Bundle 2) |
| RF02 | O sistema deve apresentar os resultados da pesquisa. | **Implementado** (Bundle 2) |
| RF03 | O sistema deve permitir consultar o conteúdo de um artigo. | **Implementado** (Online e Offline — Bundles 1 e 2) |
| RF04 | O sistema deve permitir criar coleções personalizadas. | **Implementado** (Bundle 1 e criação inline no Bundle 3) |
| RF05 | O sistema deve permitir renomear coleções. | **Implementado** (Bundle 1) |
| RF06 | O sistema deve permitir excluir coleções. | **Implementado** (Bundle 1) |
| RF07 | O sistema deve permitir adicionar artigos às coleções. | **Implementado** (Bundle 3 — Online e Offline) |
| RF08 | O sistema deve permitir remover artigos das coleções. | **Implementado** (Bundle 3 — Coleção e Leitura) |
| RF09 | O sistema deve permitir que um artigo pertença a várias coleções. | **Implementado** (Bundle 1 e Bundle 3) |
| RF10 | O sistema deve impedir artigos duplicados na mesma coleção. | **Implementado** (Bundle 1 e Bundle 3) |
| RF11 | O sistema deve armazenar localmente os artigos salvos. | **Implementado** (Bundle 1 e Bundle 3) |
| RF12 | O sistema deve permitir consultar coleções e artigos salvos offline. | **Implementado** (Bundle 1 e Bundle 3) |
| RF13 | O sistema deve atualizar conteúdos armazenados quando houver conexão. | Planejado (Bundle 4) |
| RF14 | O sistema deve informar o usuário sobre situações de carregamento, ausência de resultados e erros. | **Implementado** (Bundles 1, 2 e 3) |

## 7. Regras de negócio

**RN01 — Nome das coleções:** Uma coleção não pode ser criada com um nome vazio ou composto apenas por espaços.  
**RN02 — Identificação dos artigos:** Cada artigo deve possuir uma identificação que permita reconhecê-lo independentemente da coleção em que esteja armazenado.  
**RN03 — Associação de artigos:** Um artigo pode pertencer a várias coleções simultaneamente.  
**RN04 — Duplicidade:** Um artigo não pode ser associado mais de uma vez à mesma coleção.  
**RN05 — Exclusão de coleção:** Ao excluir uma coleção, suas associações com artigos também devem ser removidas.  
**RN06 — Persistência:** As coleções e os artigos salvos devem permanecer disponíveis após o fechamento e a reabertura do aplicativo.  
**RN07 — Operações offline:** O gerenciamento de coleções e das associações de artigos já armazenados não deve depender de conexão com a internet.
