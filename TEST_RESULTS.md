# Erudito — Registro de Resultados de Testes

**Data da Última Revisão:** 19 de Novembro de 2026  
**Ambiente:** Android SDK 36 (`compileSdk 36` / `targetSdk 36` / `minSdk 24`), Gradle `9.3.1`, Kotlin `2.2.10`, AGP `9.1.1`, Robolectric `4.16.1`, JUnit `4.13.2`, Dagger Hilt `2.55` (KSP `2.3.5`), Room `2.7.0` (v4), Jsoup `1.18.3`, AndroidX WebKit `1.18.0`, KaTeX `0.19.1`  
**Comando Principal de Validação (Etapa 4):** `gradle :app:compileDebugKotlin :app:testDebugUnitTest --rerun-tasks`  
**Revalidação Pós-Documentação (Etapa 5):** `gradle :app:compileDebugKotlin` e `gradle :app:testDebugUnitTest`  
**Escopo Validado:** Bundles 1, 2, 3, 4 (incluindo Parsoid, Room v4 e KaTeX/WebView) e **Bundle 5 (Etapas 1 a 5)**

---

## 1. Histórico Consolidado por Marco e Etapa (Execuções Independentes)

> **Nota metodológica:** Cada marco abaixo representa uma execução independente da suíte completa no momento da respectiva validação. Os totais **não devem ser somados** entre si.

| Marco / Etapa | Escopo Validado | Comando Executado | Suítes | Executados | Aprovados | Falhas / Erros | Ignorados | Compilação | Observações Relevantes |
|---|---|---|---|---|---|---|---|---|---|
| **Bundles 1–3** (15/11/2026) | Biblioteca offline, pesquisa/leitura Wikipédia e integração N:N (`v1 → v2`) | `gradle :app:compileDebugKotlin :app:testDebugUnitTest --rerun-tasks` | 30 | 156 | 156 | 0 | 0 | Sucesso | Marco de encerramento do Bundle 3 (`2m 2s`). |
| **Bundle 4 — Etapas 1–4** (16/11/2026) | Conectividade, fallback offline, Room v3 (`MIGRATION_2_3`), `SyncPolicy`, `ArticleSyncMerger` e UI de sync | `gradle :app:compileDebugKotlin :app:testDebugUnitTest --rerun-tasks` | 38 | 219 | 219 | 0 | 0 | Sucesso | `33` tarefas em `2m 32s`. |
| **Bundle 4 — Etapa C2** | Parsoid REST, `WikipediaHtmlSanitizer`, `ArticleContentFormat`, Room v4 (`MIGRATION_3_4`) e merge estruturado | Suíte unitária + Robolectric | — | 256 | 256 | 0 | 0 | Sucesso | Concluída antes da Etapa D conforme histórico do projeto. |
| **Bundle 4 — Etapa D** | Renderização `STRUCTURED_HTML`, `WebViewAssetLoader`, KaTeX local, CSP e estabilidade da WebView | Suíte unitária/Robolectric + validação prévia em emulador Android | — | — | — | 0 | 0 | Sucesso | Validada em emulador antes do início do Bundle 5. |
| **Bundle 5 — Etapas 1–3** | Correção Hilt/KSP (`ksp.incremental=false`), Clean Architecture (`StructuredArticleHtmlProcessor`), `CancellationException`, `CollectionPickerCoordinator` | `gradle :app:compileDebugKotlin :app:testDebugUnitTest --rerun-tasks` | 44 | 285 | 285 | 0 | 0 | Sucesso | Base entregue para auditoria final da Etapa 4. |
| **Bundle 5 — Etapa 4 (Validação Final — 19/11/2026)** | Auditoria de cobertura + 3 testes de borda (`CancellationException` / ID inexistente), `clean`, `assembleDebug` e `--rerun-tasks` | `gradle :app:compileDebugKotlin :app:testDebugUnitTest --rerun-tasks` | **44** | **288** | **288** | **0** | **0** | **Sucesso** (`BUILD SUCCESSFUL`) | `32` tarefas executadas em `3m 44s`; APK `app-debug.apk` gerado e validado. |

---

## 2. Detalhamento Completo da Execução Final (44 Suítes / 288 Testes)

Consolidado diretamente a partir dos relatórios XML JUnit (`app/build/test-results/testDebugUnitTest/TEST-*.xml`) gerados pela execução com `--rerun-tasks` em **19 de Novembro de 2026**:

| Camada | Test Suite | Executados | Aprovados | Reprovados | Ignorados | Tempo (XML) |
|---|---|---|---|---|---|---|
| Domain (Sync) | `ArticleSyncMergerAndPolicyTest` | 15 | 15 | 0 | 0 | 0.006s |
| Domain (Mappers) | `ArticleDomainMapperTest` | 2 | 2 | 0 | 0 | 0.001s |
| Domain | `SyncUseCasesTest` | 13 | 13 | 0 | 0 | 0.890s |
| Domain | `ObserveArticleByIdAndLocalLookupUseCasesTest` | 3 | 3 | 0 | 0 | 0.008s |
| Domain | `ArticleCollectionIntegrationUseCasesTest` | 10 | 10 | 0 | 0 | 0.103s |
| Domain | `CreateCollectionUseCaseTest` | 4 | 4 | 0 | 0 | 0.009s |
| Domain | `RenameCollectionUseCaseTest` | 4 | 4 | 0 | 0 | 0.012s |
| Domain | `DeleteCollectionUseCaseTest` | 1 | 1 | 0 | 0 | 0.006s |
| Domain | `GetCollectionsUseCaseTest` | 2 | 2 | 0 | 0 | 0.006s |
| Domain | `GetCollectionWithArticlesUseCaseTest` | 2 | 2 | 0 | 0 | 0.014s |
| Domain | `GetSavedArticlesUseCaseTest` | 2 | 2 | 0 | 0 | 0.006s |
| Domain | `GetArticleByIdUseCaseTest` | 2 | 2 | 0 | 0 | 0.006s |
| Domain | `WikipediaUseCasesTest` | 7 | 7 | 0 | 0 | 0.015s |
| DI (Hilt) | `EruditoHiltModuleTest` | 1 | 1 | 0 | 0 | 0.001s |
| Data (Connectivity) | `AndroidConnectivityObserverTest` | 6 | 6 | 0 | 0 | 4.309s |
| Data (Room Integration) | `RoomDatabaseIntegrationTest` | 20 | 20 | 0 | 0 | 5.750s |
| Data (Mappers) | `ArticleAndWikipediaMapperTest` | 10 | 10 | 0 | 0 | 0.023s |
| Data (Remote DTOs) | `WikipediaDtoDeserializationTest` | 7 | 7 | 0 | 0 | 0.351s |
| Data (HTML/LaTeX Parser) | `WikipediaHtmlSanitizerTest` | 20 | 20 | 0 | 0 | 0.296s |
| Data (Repositories) | `ArticleRepositoryImplTest` | 6 | 6 | 0 | 0 | 0.557s |
| Data (Repositories) | `CollectionRepositoryImplTest` | 2 | 2 | 0 | 0 | 0.160s |
| Data (Repositories) | `WikipediaRepositoryImplTest` | 14 | 14 | 0 | 0 | 0.311s |
| Presentation (ViewModel) | `HomeSyncTest` | 7 | 7 | 0 | 0 | 0.938s |
| Presentation (ViewModel) | `ArticleDetailSyncTest` | 7 | 7 | 0 | 0 | 1.846s |
| Presentation (ViewModel) | `WikipediaArticleRefreshSavedCopyTest` | 3 | 3 | 0 | 0 | 0.367s |
| Presentation (ViewModel) | `SearchViewModelTest` | 11 | 11 | 0 | 0 | 0.043s |
| Presentation (ViewModel) | `WikipediaArticleViewModelTest` | 18 | 18 | 0 | 0 | 0.138s |
| Presentation (ViewModel) | `CollectionDetailViewModelTest` | 9 | 9 | 0 | 0 | 0.077s |
| Presentation (ViewModel) | `ArticleDetailViewModelTest` | 8 | 8 | 0 | 0 | 0.244s |
| Presentation (UI & Compose) | `HomeConnectivityTest` | 1 | 1 | 0 | 0 | 0.256s |
| Presentation (UI & Compose) | `HomeErrorHandlingTest` | 4 | 4 | 0 | 0 | 0.774s |
| Presentation (UI & Compose) | `HomeScreenDuplicateKeyTest` | 1 | 1 | 0 | 0 | 0.166s |
| Presentation (UI & Compose) | `SearchScreenTest` | 7 | 7 | 0 | 0 | 1.600s |
| Presentation (UI & Compose) | `WikipediaArticleScreenTest` | 12 | 12 | 0 | 0 | 1.912s |
| Presentation (UI & Compose) | `CollectionPickerDialogTest` | 5 | 5 | 0 | 0 | 1.602s |
| Presentation (UI & Compose) | `CollectionDetailScreenTest` | 3 | 3 | 0 | 0 | 1.486s |
| Presentation (UI & Compose) | `ArticleDetailScreenTest` | 8 | 8 | 0 | 0 | 10.899s |
| Presentation (UI & Compose) | `CollectionFeedbackMessagesTest` | 7 | 7 | 0 | 0 | 2.843s |
| Presentation (WebView/HTML) | `ArticleHtmlRenderingUnitTest` | 6 | 6 | 0 | 0 | 0.026s |
| Presentation (WebView/HTML) | `ArticleStructuredRenderingRobolectricTest` | 8 | 8 | 0 | 0 | 2.420s |
| Presentation (Navigation) | `EruditoNavigationTest` | 5 | 5 | 0 | 0 | 1.769s |
| Presentation (E2E Room + Nav) | `Bundle3EndToEndIntegrationTest` | 3 | 3 | 0 | 0 | 4.860s |
| Presentation / Resources | `ExampleRobolectricTest` | 1 | 1 | 0 | 0 | 11.310s |
| Unit | `ExampleUnitTest` | 1 | 1 | 0 | 0 | 0.001s |
| **Total Consolidado** | **44 Suítes** | **288** | **288** | **0** | **0** | **58.42s (soma XML) / 3m 44s (Gradle)** |

---

## 3. Verificação dos Artefatos de Entrega

- **APK original (`app/build/outputs/apk/debug/app-debug.apk`):**
  - Tamanho: `21.210.469` bytes (`~20,23 MiB`)
  - SHA-256: `c7757122bef7cd7f55f5acbfc8377f932b17b9ff3ffc7d1a21e9300fd63477a1`
  - Metadados (`aapt2 dump badging`): `package='com.aistudio.erudito.wklvqz'`, `versionCode='1'`, `versionName='1.0'`, `minSdkVersion='24'`, `targetSdkVersion='36'`, `application-label='Erudito'`.
  - Assinatura (`apksigner verify`): APK Signature Scheme v2 válida (`CN=Android Debug, O=Android, C=US`, RSA 2048-bit).
  - Assets verificados no APK: `assets/erudito/article.css`, `assets/erudito/math_renderer.js`, `assets/katex/katex.min.css`, `assets/katex/katex.min.js` e `20` arquivos `.woff2` em `assets/katex/fonts/`.
- **Pacote ZIP na raiz (`Erudito-1.0-debug.zip`):**
  - Contém exclusivamente `app-debug.apk` (`20.648.877` bytes / `~19,69 MiB`).
  - Integridade ZIP verificada (`unzip -tq`) e hash SHA-256 extraído idêntico ao APK original.

---

## 4. Limitações de Validação e Checklist Manual Pendente para Dispositivo/Emulador

No ambiente containerizado da Etapa 4/5, `adb devices` não possui alvos conectados e o binário `emulator` não está disponível. Embora a Etapa D tenha sido validada anteriormente em emulador, permanecem pendentes de conferência manual final pós-Bundle 5 no Android Studio ou dispositivo físico:

1. **Instalação e inicialização:** instalar `app-debug.apk` (`Erudito-1.0-debug.zip`), abrir o app e confirmar exibição da Home com coleções e artigos iniciais.
2. **Pesquisa e renderização estruturada:** buscar artigo científico/matemático na Wikipédia em Português, confirmar ausência de tags HTML literais, carregamento de imagens HTTPS e renderização das fórmulas via KaTeX `0.19.1` offline.
3. **Rolagem aninhada e estabilidade da WebView:** rolar internamente/externamente artigo longo, rotacionar e reabrir o artigo verificando ausência de `Webpage not available` ou encerramento do renderer.
4. **Links externos:** tocar em link dentro do artigo confirmando abertura controlada em navegador externo.
5. **Coleções e multi-seleção:** criar coleção no seletor, associar artigo a múltiplas coleções, reabrir seletor conferindo `"Já salvo"` e remover vínculo individual sem apagar o artigo.
6. **Offline First e sincronização em rede real:** alternar Wi-Fi/modo avião/portal cativo, confirmar leitura offline e fallback local na busca, depois reconectar e testar atualização manual (`forceRefresh = true`).
7. **Persistência em disco após encerramento:** forçar fechamento do processo Android e reabrir o app confirmando manutenção de artigos `STRUCTURED_HTML`, coleções e vínculos N:N.
