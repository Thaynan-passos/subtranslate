# Estrutura do projeto

## Classificação

Aplicativo Android nativo (mobile), em um único módulo Gradle (`:app`). Não é backend, frontend web, biblioteca, CLI ou monorepo.

## Módulos, pacotes e camadas

Há **50 arquivos Kotlin de produção** em `app/src/main/java`, organizados em pacotes dentro do módulo `:app`:

| Pacote | Arquivos | Responsabilidade |
|---|---:|---|
| `com.example` | 2 | `Application` e Activity launcher |
| `com.example.di` | 1 | Composition root manual (`AppContainer`) |
| `com.example.ui.screens` | 7 | Telas Compose |
| `com.example.ui.viewmodel` | 5 | Estado e ações da UI via ViewModels, StateFlow e coroutines |
| `com.example.ui.components` | 2 | Componentes Compose compartilhados |
| `com.example.ui.theme` | 3 | Tema, cores e tipografia |
| `com.example.domain.model` | 3 | Modelos e estados de domínio |
| `com.example.domain.engine` | 2 | Contratos de OCR e tradução |
| `com.example.domain.util` | 10 | Formatação, OCR, idioma, glossário, agrupamento e utilitários |
| `com.example.data.engine` | 4 | Engines ML Kit e Gemini, incluindo seleção/fallback |
| `com.example.data.local` | 1 | Banco Room |
| `com.example.data.local.dao` | 2 | Consultas e operações; três interfaces DAO em dois arquivos |
| `com.example.data.local.entity` | 2 | Entidades Room |
| `com.example.data.pipeline` | 1 | Extração, OCR, agrupamento temporal e tradução |
| `com.example.data.repository` | 3 | Acesso a projetos, legendas/cache e preferências |
| `com.example.data.service` | 1 | Foreground service de extração |
| `com.example.data.util` | 1 | Notificações do processamento |

Os limites de apresentação, domínio e dados são convenções de pacote, não módulos Gradle separados. A injeção de dependências é manual em `AppContainer`.

Testes existentes: **12 arquivos Kotlin unitários** em `app/src/test` e **1 arquivo instrumentado** em `app/src/androidTest`. Builds e código gerado não entram nas contagens.

## Entry points e navegação

- `SubTranslateApp` é a classe `Application` indicada no Manifest. Inicializa `AppContainer` e fecha engines sob pressão de memória se não houver extração ativa.
- `MainActivity` é a única Activity, exportada como `MAIN`/`LAUNCHER`. Mantém back stack salvável e despacha rotas por strings, sem Navigation Compose:
  - `projects`
  - `config` e `config_extract` (duas modalidades da mesma tela)
  - `processing/{id}`
  - `editor/{id}`
  - `manual/{id}`
  - `export/{id}`
  - `settings`
- `SubtitleExtractionService` é registrado como não exportado e executa em foreground. Recebe intents de início, pausa, retomada e cancelamento.
- A seleção de vídeo usa Storage Access Framework (`OpenDocument`); configuração de ROI e parâmetros do projeto ocorre na UI Compose.
- A exportação e a importação de tradução manual usam `CreateDocument`/`OpenDocument`; o compartilhamento de arquivos de legenda usa `FileProvider`.
- `AndroidManifest.xml` declara permissões de internet, notificações, foreground service, wake lock, a Activity, o service e o provider.

## Áreas funcionais

1. **Biblioteca de projetos:** lista, estado, entrada nas modalidades de tradução automática ou tradução manual e exclusão.
2. **Configuração do projeto:** seleção do vídeo, ROI, idiomas, FPS e modalidade de saída; detecção automática de ROI via OCR.
3. **Extração e tradução:** serviço em foreground executa amostragem de frames, OCR, limpeza/agrupamento e tradução; grava progresso e linhas em Room.
4. **Processamento:** acompanha progresso e oferece pausa, retomada, cancelamento, retry e navegação para revisão.
5. **Revisão de legendas:** edição de texto/tempos, divisão, mesclagem, remoção, inclusão manual e retradução.
6. **Tradução manual:** exporta texto OCR editável e importa traduções de texto/SRT/VTT para as linhas do projeto.
7. **Exportação:** gera SRT ou WebVTT e compartilha o arquivo.
8. **Preferências:** DataStore para glossário, filtros, engine, dados da API Gemini e apresentação.

## Arquivos de configuração principais

- `settings.gradle.kts`: inclui `:app`, define repositórios e resolução centralizada.
- `build.gradle.kts`: plugins Android, Kotlin Compose e KSP no build raiz.
- `app/build.gradle.kts`: SDKs Android, signing, release/minificação, Compose, KSP/Room e dependências da aplicação.
- `gradle/libs.versions.toml`: catálogo de plugins e bibliotecas.
- `gradle/wrapper/gradle-wrapper.properties`: versão do Gradle Wrapper.
- `gradle.properties`: opções de execução/caching do Gradle e estilo Kotlin.
- `app/src/main/AndroidManifest.xml`: permissões e componentes Android registrados.
- `app/schemas/`: schema Room exportado.
- `README.md`: pré-requisitos, execução local, comportamento de processamento e testes.

## Fluxo principal

`MainActivity` cria/observa ViewModels e chama repositories em `AppContainer`. O serviço aciona `SubtitleExtractionPipeline`; o pipeline obtém frames via `MediaMetadataRetriever`, chama `OcrEngine`, agrupa texto e usa o `Translator` híbrido (Gemini com fallback ML Kit ou ML Kit local), persistindo progresso e linhas por repositories/Room. A revisão/exportação observam as linhas por `Flow`.
