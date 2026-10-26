# Stack técnico existente

## Plataforma e compilação

- Android nativo; código de produção Kotlin.
- Gradle Wrapper **9.3.1**; Android Gradle Plugin **9.1.1**.
- Kotlin **2.2.10**, plugin Compose correspondente e KSP **2.3.5**.
- `compileSdk` **36.1**, `targetSdk` **36**, `minSdk` **26**.
- Compatibilidade de bytecode Java source/target configurada para **Java 11**. O README exige JDK 17 ou mais recente para o build; não há configuração explícita de `jvmTarget` neste arquivo Gradle.
- Android Compose habilitado; buildConfig habilitado; minificação ativa no release.
- Repositórios: Google Maven, Maven Central e Gradle Plugin Portal (para plugins).

## Frameworks e bibliotecas

Versões conforme `gradle/libs.versions.toml`; Compose UI/Material sem versão individual porque herdam o BOM.

| Biblioteca/grupo | Versão configurada | Uso visível |
|---|---:|---|
| AndroidX Core KTX | 1.18.0 | Extensões Android |
| Activity Compose | 1.10.1 | Activity e integração Compose |
| Compose BOM | 2024.09.00 | Alinhamento das versões Compose |
| Compose Material 3, UI, UI Graphics, Preview, ícones core/extended | pelo BOM | UI declarativa |
| Lifecycle Runtime KTX / Compose | 2.8.7 | Lifecycle e coleta de flows |
| Lifecycle ViewModel Compose | 2.8.7 | ViewModels na UI Compose |
| DataStore Preferences | 1.1.7 | Preferências persistentes |
| Room Runtime / KTX / Compiler | 2.7.0 | Banco local, coroutines e geração via KSP |
| Kotlin Coroutines Core / Android | 1.10.2 | Execução assíncrona |
| Media3 ExoPlayer / UI / Common | 1.5.1 | Playback/visualização de vídeo |
| ML Kit Text Recognition base e CJK/Devanagari | 16.0.1 | OCR on-device |
| ML Kit Translate | 17.0.3 | Tradução on-device, com download de modelos de idioma |
| JUnit | 4.13.2 | Testes unitários |
| AndroidX Test JUnit | 1.3.0 | Testes Android |
| AndroidX Test Core | 1.6.1 | Utilitários de teste |
| Espresso Core | 3.7.0 | Automação UI |
| AndroidX Test Runner | 1.6.2 | Execução de testes instrumentados |
| Robolectric | 4.16.1 | Testes Android em JVM |
| Coroutines Test | 1.10.2 | Testes de coroutines |

## Runtime e integrações

- A execução OCR/tradução é local após aquisição dos modelos. O README informa que o primeiro uso de tradução pode precisar de internet para baixar os modelos.
- Não foi encontrada dependência de backend próprio, chave Gemini ou API key no `.env.example`.
- Persistência em Room (`subtranslate_database`) e DataStore (`subtranslate_settings`).
- Android Foreground Service com tipos `dataSync`/`mediaProcessing`, notificações e `FileProvider`.

## Restrições e pontos observáveis

- `compileSdk`/`targetSdk` 36 e `minSdk` 26 delimitam a plataforma Android suportada pelo manifesto de build.
- Release requer `KEYSTORE_PATH`, `STORE_PASSWORD` e `KEY_PASSWORD`; na ausência de caminho usa `my-upload-key.jks` na raiz. Alias configurado: `upload`.
- O repositório fixa bibliotecas por versão no catálogo; não foi feita avaliação de status de suporte/EOL externo. Não há sinal no código lido de API explicitamente marcada como obsoleta.
- Gradle configura `googleServices.missing.passthrough=true`; não há plugin Google Services declarado nos plugins do build observados.
