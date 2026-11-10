# Stack técnico existente

## Plataforma e compilação

- Plataforma: Android nativo; código de produção Kotlin.
- Gradle Wrapper: **9.6.0** (`gradle/wrapper/gradle-wrapper.properties`).
- Android Gradle Plugin: **9.4.1**.
- Kotlin: **2.2.10**; plugin Kotlin Compose na mesma versão.
- KSP: **2.3.6**.
- Android SDK: `compileSdk` **36.1**, `targetSdk` **36**, `minSdk` **26**.
- `sourceCompatibility`/`targetCompatibility`: Java **11**; o README requer JDK **17 ou mais recente** para executar o build.
- Compose e `BuildConfig` habilitados; minificação habilitada para release.
- Repositórios de dependências: Google Maven e Maven Central; plugins também usam Gradle Plugin Portal.

## Dependências de produção

Versões conforme `gradle/libs.versions.toml`. Bibliotecas Compose sem versão própria herdam o BOM.

| Biblioteca | Versão configurada | Uso observado |
|---|---:|---|
| AndroidX Core KTX | 1.18.0 | Extensões Android |
| Activity Compose | 1.10.1 | Activity Result APIs e integração Compose |
| Compose BOM | 2024.09.00 | Alinhamento de versões Compose |
| Compose Material 3, UI, UI Graphics, Preview e ícones | BOM | UI declarativa |
| Lifecycle Runtime KTX / Compose | 2.8.7 | Lifecycle e coleta de flows |
| Lifecycle ViewModel Compose | 2.8.7 | ViewModels na UI |
| DataStore Preferences | 1.1.7 | Preferências |
| Room Runtime / KTX / Compiler | 2.7.0 | Banco local e geração via KSP |
| Kotlin Coroutines Core / Android | 1.10.2 | Operações assíncronas |
| AndroidX Media3 ExoPlayer / UI / Common | 1.5.1 | Playback/visualização de vídeo |
| ML Kit Text Recognition base e scripts Chinese/Japanese/Korean/Devanagari | 16.0.1 | OCR on-device |
| ML Kit Translate | 17.0.3 | Tradução on-device |

## Dependências de teste

| Biblioteca | Versão configurada |
|---|---:|
| JUnit | 4.13.2 |
| AndroidX Test JUnit | 1.3.0 |
| AndroidX Test Core | 1.6.1 |
| Espresso Core | 3.7.0 |
| AndroidX Test Runner | 1.6.2 |
| Robolectric | 4.16.1 |
| Kotlin Coroutines Test | 1.10.2 |
| Compose UI Test | Versão alinhada pelo Compose BOM |

## Runtime e integrações observadas

- Persistência local: Room (`subtranslate_database`) e DataStore Preferences (`subtranslate_settings`).
- Vídeo: Android `MediaMetadataRetriever` para extração de frames; dependência Media3 também está declarada.
- OCR/tradução local: ML Kit Text Recognition e ML Kit Translate.
- Tradução online: chamada HTTP POST direta com `HttpURLConnection` à API Google Gemini; não há SDK Gemini nem cliente HTTP de terceiros no catálogo apresentado. A chave/modelo são configuráveis pelo usuário nas preferências.
- Exportação/importação: Android Storage Access Framework; compartilhamento de arquivo via `FileProvider`.
- Execução de extração: Android Foreground Service com tipos `dataSync`/`mediaProcessing`, notificações e `PARTIAL_WAKE_LOCK`.

## Configurações de build

- O release exige `KEYSTORE_PATH`, `STORE_PASSWORD` e `KEY_PASSWORD`; alias fixo `upload`; sem `KEYSTORE_PATH`, usa `${rootDir}/my-upload-key.jks`.
- `app/build.gradle.kts` configura bytecode Java 11. O README exige JDK 17+ para o build.
- `gradle.properties` habilita cache e configuration cache, paralelismo, até quatro workers e compilação Kotlin in-process.
- A análise registra versões como declaradas nos manifests; não avalia suporte/EOL contra fontes externas e não encontrou no escopo inspecionado uma API explicitamente marcada como obsoleta.
