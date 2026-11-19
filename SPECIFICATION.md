# Erudito — Especificação do Projeto

## 1. Visão geral

**Nome:** Erudito

**Descrição:** O Erudito é um aplicativo Android de biblioteca pessoal de conhecimento, integrado à Wikipédia em Português (`pt.wikipedia.org`), que permite pesquisar assuntos, consultar artigos com renderização estruturada e fórmulas matemáticas, e organizar conteúdos em coleções personalizadas para acesso offline.

O aplicativo tem como objetivo facilitar o armazenamento e a organização de informações relevantes, permitindo que o usuário construa sua própria biblioteca de conhecimentos e consulte conteúdos previamente salvos mesmo sem conexão com a internet.

## 2. Objetivo

Desenvolver uma aplicação Android funcional que permita pesquisar artigos da Wikipédia, armazená-los localmente e organizá-los em coleções personalizadas.

Além de oferecer uma ferramenta de organização de conhecimento, o projeto aplica conceitos de engenharia de software, incluindo Clean Architecture, desenvolvimento orientado a especificações (SDD), persistência local, consumo de APIs REST, sanitização e renderização segura de HTML/LaTeX, programação assíncrona e testes automatizados.

## 3. Problema a ser resolvido

Durante pesquisas na internet, é comum encontrar diversos conteúdos interessantes que acabam se perdendo entre páginas, favoritos e históricos de navegação.

O Erudito soluciona esse problema oferecendo um espaço centralizado no qual o usuário pode salvar artigos, separá-los por assuntos em múltiplas coleções e consultá-los posteriormente, inclusive offline.

## 4. Público-alvo

Usuários interessados em pesquisar, armazenar e organizar informações sobre diferentes assuntos, especialmente estudantes e pessoas que desejam construir uma biblioteca pessoal de conhecimentos.

## 5. Funcionalidades

### 5.1. Pesquisa de artigos (Bundle 2)
- Pesquisar assuntos utilizando a API MediaWiki da Wikipédia em Português (`pt.wikipedia.org`).
- Exibir uma lista de artigos relacionados à pesquisa.
- Apresentar o título, uma breve descrição e uma miniatura, quando disponível.
- Permitir a abertura de um artigo para consultar seu conteúdo.
- Apresentar estados de carregamento, resultados, ausência de resultados, modo offline e erros.

### 5.2. Consulta de artigos e conteúdo estruturado (Bundles 1, 2 e 4 — Etapa D)
- Consultar metadados pela MediaWiki Action API e o corpo HTML estruturado pela API REST Parsoid (`w/rest.php/v1/page/{title}/html`).
- Sanitizar o HTML recebido (`WikipediaHtmlSanitizer` / `StructuredArticleHtmlProcessor`), removendo elementos executáveis e ruídos editoriais e extraindo expressões LaTeX de blocos matemáticos Parsoid/Mathoid.
- Diferenciar explicitamente o formato do conteúdo entre `PLAIN_TEXT` e `STRUCTURED_HTML`, contabilizando `extractedFormulaCount` e `unextractableFormulaCount`.
- Exibir artigos `STRUCTURED_HTML` em `WebView` isolada com `WebViewAssetLoader`, CSP restritiva, imagens `https://` e renderização offline de fórmulas matemáticas com **KaTeX local**, com fallback textual assíncrono caso a visualização formatada falhe.
- Permitir que o usuário salve o artigo em uma ou mais coleções.
- Permitir consultar artigos previamente armazenados sem conexão com a internet.
- Disponibilizar referência segura para o artigo original na Wikipédia.

### 5.3. Gerenciamento de coleções (Bundle 1)
- Criar coleções com nomes personalizados (validando duplicidade e nomes vazios/em branco).
- Criar múltiplas coleções, inclusive de forma inline pelo seletor de coleções.
- Renomear coleções existentes e excluir coleções com diálogo de confirmação.
- Exibir as coleções criadas pelo usuário com contagem de artigos.
- Abrir uma coleção e visualizar seus artigos.

### 5.4. Organização de artigos (Bundles 1 e 3)
- Adicionar artigos às coleções e remover vínculos individualmente sem apagar o artigo da biblioteca nem de outras coleções.
- Permitir adicionar o mesmo artigo a diferentes coleções (`CollectionArticleCrossRef`), impedindo duplicação na mesma coleção.
- Manter os artigos armazenados localmente para consulta posterior.

### 5.5. Funcionamento offline e sincronização (Bundles 1 e 4)
O aplicativo adota arquitetura **Offline First**, priorizando os dados armazenados localmente no Room v4 e tratando a sincronização remota de forma segura e não bloqueante:
- **Disponibilidade offline:** visualizar coleções, criar/renomear/excluir coleções, ler artigos previamente salvos (incluindo conteúdo `STRUCTURED_HTML` e fórmulas matemáticas via KaTeX local) e gerenciar associações N:N permanecem 100% funcionais sem conexão com a internet.
- **Detecção real de conectividade (`ConnectivityObserver` / `AndroidConnectivityObserver`):** instância compartilhada via Hilt que monitora a rede ativa via `ConnectivityManager.NetworkCallback`, exigindo `NET_CAPABILITY_INTERNET`, `NET_CAPABILITY_VALIDATED` e `NET_CAPABILITY_NOT_SUSPENDED` (Android P+) para considerar o dispositivo online.
- **Leitura local prioritária e fallback offline:** na abertura de um artigo da Wikipédia sem conexão ou após falha de rede (`WikipediaException`), o sistema consulta o Room por `wikipediaPageId` e exibe a cópia local salva quando existente, indicando tratar-se de cópia local offline. Abrir um artigo online sem salvamento explícito não o persiste automaticamente.
- **Observação reativa:** telas de biblioteca e leitura observam o Room via `Flow`, refletindo atualizações locais e de sincronização imediatamente na interface.
- **Sincronização automática controlada na Home:** ao iniciar a `HomeViewModel` com conectividade ativa e artigos sincronizáveis (`wikipediaPageId > 0`), executa no máximo uma tentativa automática por ciclo de vida, respeitando a política de frescor de **6 horas** (`SyncPolicy`), sem bloquear a exibição imediata dos artigos locais e sem disparar novas chamadas em recomposições ou novas emissões do Room. Caso a Home abra offline e a conectividade retorne na mesma sessão, executa uma única tentativa automática.
- **Atualização manual sob demanda:** o usuário pode forçar a atualização (`forceRefresh = true`) diretamente na tela de leitura offline (`ArticleDetailScreen`) ou atualizar a cópia salva durante a leitura de um artigo na Wikipédia (`WikipediaArticleScreen`) quando já houver vínculo local. Artigos locais sem `wikipediaPageId` informam que a atualização remota está indisponível.
- **Merge defensivo e integridade (`ArticleSyncMerger`):** distingue conteúdo extraído real (`hasExtractedContent`) de fallback por descrição e compara formato (`STRUCTURED_HTML` vs `PLAIN_TEXT`) e integridade de fórmulas (`unextractableFormulaCount`), impedindo que respostas vazias, `PLAIN_TEXT` ou `STRUCTURED_HTML` incompleto sobrescrevam conteúdo estruturado íntegro local. Atualiza `lastUpdated` exclusivamente quando há alteração efetiva dos dados persistidos e grava `lastSyncedAt` após consulta remota bem-sucedida.
- **Preservação em falhas e vínculos N:N:** falhas totais, parciais ou perda de rede preservam integralmente os dados locais e as associações N:N (`@Update` em vez de `OnConflictStrategy.REPLACE`).
- **Estados visuais distintos:** a interface separa claramente conectividade (`Online` / `Modo Offline` / `Sem conexão`) de sincronização (`Syncing`, `CompletedWithUpdates`, `CompletedNoChanges`, `PartialFailure` com tentativa manual, `Failed` e `Offline`), sem exibir mensagens técnicas de exceção ao usuário.

## 6. Requisitos funcionais

| Código | Requisito | Status Atual |
|---|---|---|
| RF01 | O sistema deve permitir pesquisar artigos na Wikipédia. | **Implementado** (Bundle 2) |
| RF02 | O sistema deve apresentar os resultados da pesquisa. | **Implementado** (Bundle 2) |
| RF03 | O sistema deve permitir consultar o conteúdo de um artigo. | **Implementado** (Online, Offline e Estruturado — Bundles 1, 2 e 4) |
| RF04 | O sistema deve permitir criar coleções personalizadas. | **Implementado** (Bundle 1 e criação inline no Bundle 3) |
| RF05 | O sistema deve permitir renomear coleções. | **Implementado** (Bundle 1) |
| RF06 | O sistema deve permitir excluir coleções. | **Implementado** (Bundle 1) |
| RF07 | O sistema deve permitir adicionar artigos às coleções. | **Implementado** (Bundle 3 — Online e Offline) |
| RF08 | O sistema deve permitir remover artigos das coleções. | **Implementado** (Bundle 3 — Coleção e Leitura) |
| RF09 | O sistema deve permitir que um artigo pertença a várias coleções. | **Implementado** (Bundle 1 e Bundle 3) |
| RF10 | O sistema deve impedir artigos duplicados na mesma coleção. | **Implementado** (Bundle 1 e Bundle 3) |
| RF11 | O sistema deve armazenar localmente os artigos salvos. | **Implementado** (Bundle 1 e Bundle 3) |
| RF12 | O sistema deve permitir consultar coleções e artigos salvos offline. | **Implementado** (Bundles 1, 3 e 4) |
| RF13 | O sistema deve atualizar conteúdos armazenados quando houver conexão. | **Implementado** (Bundle 4 — Automática controlada e Manual) |
| RF14 | O sistema deve informar o usuário sobre situações de carregamento, ausência de resultados, conectividade, sincronização e erros. | **Implementado** (Bundles 1, 2, 3 e 4) |
| RF15 | O sistema deve detectar conectividade utilizável e aplicar fallback local para artigos já salvos quando offline ou em falha de rede. | **Implementado** (Bundle 4 — Etapa 1) |
| RF16 | O sistema deve respeitar política de frescor de 6 horas na sincronização automática e permitir atualização manual sob demanda (`forceRefresh`). | **Implementado** (Bundle 4 — Etapas 2 e 3) |
| RF17 | O sistema deve preservar dados locais válidos, vínculos N:N e registrar `lastSyncedAt` em atualizações bem-sucedidas. | **Implementado** (Bundle 4 — Etapa 2) |
| RF18 | O sistema deve obter HTML estruturado da API Parsoid, sanitizá-lo, extrair fórmulas matemáticas LaTeX e persistir o formato (`PLAIN_TEXT`/`STRUCTURED_HTML`) e contadores no Room v4. | **Implementado** (Bundle 4 — Etapas C1/C2) |
| RF19 | O sistema deve renderizar artigos estruturados e fórmulas matemáticas offline via KaTeX em `WebView` isolada com política de segurança estrita e fallback textual assíncrono. | **Implementado** (Bundle 4 — Etapa D) |

## 7. Regras de negócio

**RN01 — Nome das coleções:** Uma coleção não pode ser criada ou renomeada com nome vazio, composto apenas por espaços ou duplicado.  
**RN02 — Identificação dos artigos:** Cada artigo possui `id` local e `wikipediaPageId` único quando vinculado à Wikipédia.  
**RN03 — Associação de artigos:** Um artigo pode pertencer a várias coleções simultaneamente.  
**RN04 — Duplicidade:** Um artigo não pode ser associado mais de uma vez à mesma coleção.  
**RN05 — Exclusão de coleção:** Ao excluir uma coleção, suas associações com artigos também devem ser removidas em cascata, preservando artigos ainda úteis na biblioteca.  
**RN06 — Persistência:** As coleções e os artigos salvos permanecem disponíveis após o fechamento e a reabertura do aplicativo.  
**RN07 — Operações offline:** O gerenciamento de coleções, leitura de artigos estruturados e associações já armazenadas não dependem de conexão com a internet.  
**RN08 — Política de sincronização e frescor:** A sincronização automática respeita o intervalo mínimo padrão de 6 horas desde `lastSyncedAt`, enquanto a atualização manual explícita força nova consulta (`forceRefresh = true`) quando houver conectividade e `wikipediaPageId` válido.  
**RN09 — Integridade no merge e em falhas:** Respostas remotas vazias, nulas ou parciais, bem como `PLAIN_TEXT` ou `STRUCTURED_HTML` com fórmulas indisponíveis diante de `STRUCTURED_HTML` local íntegro, não sobrescrevem o conteúdo local válido nem removem associações N:N existentes.  
**RN10 — Controle de datas:** `lastSyncedAt` registra o instante da última verificação/sincronização bem-sucedida com a Wikipédia (`null` em registros migrados), enquanto `lastUpdated` só é alterado quando houver mudança efetiva no conteúdo persistido.  
**RN11 — Segurança de conteúdo estruturado:** HTML exibido passa por sanitização com `Safelist` restritiva; apenas imagens `https://`, navegação externa explícita `http`/`https` e assets locais servidos via `WebViewAssetLoader` sob CSP restrita são permitidos.
