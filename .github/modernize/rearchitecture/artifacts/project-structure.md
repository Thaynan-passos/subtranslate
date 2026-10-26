# Estrutura do projeto

## Classificação

Aplicativo Android nativo (mobile), de módulo único `:app`. Não é backend, web frontend, biblioteca ou monorepo.

## Módulos e camadas

| Área/pacote | Arquivos Kotlin principais | Responsabilidade |
|---|---:|---|
| `com.example` | 2 | Inicialização do `Application` e Activity principal |
| `com.example.di` | 1 | Composition root manual (`AppContainer`) |
| `com.example.ui.screens` | 6 | Telas Compose para projetos, configuração, processamento, edição, exportação e preferências |
| `com.example.ui.viewmodel` | 4 | Estado e ações das telas via ViewModels, StateFlow e coroutines |
| `com.example.ui.theme` | 3 | Tema, cores e tipografia Compose |
| `com.example.domain.model` | 3 | Modelos e estados de domínio |
| `com.example.domain.engine` | 2 | Contratos para OCR e tradução |
| `com.example.domain.util` | 5 | Pré-processamento de imagem, idioma, similaridade, agrupamento e formatação |
| `com.example.data.engine` | 2 | Adaptadores concretos ML Kit para OCR e tradução |
| `com.example.data.pipeline` | 1 | Pipeline de amostragem de vídeo, OCR, agrupamento e tradução |
| `com.example.data.service` | 1 | Foreground service que mantém a extração ativa fora da tela |
| `com.example.data.repository` | 3 | Acesso e conversão dos dados de projetos, legendas e configurações |
| `com.example.data.local` | 1 | Banco Room |
| `com.example.data.local.dao` | 2 | Consultas e operações persistentes |
| `com.example.data.local.entity` | 2 | Entidades Room para projetos, linhas e cache |
| `com.example.data.util` | 1 | Notificações do processamento |

Contagem estática de `app/src/main/java`: **39 arquivos Kotlin** de produção. Testes: **3 arquivos** em `app/src/test` e **1** em `app/src/androidTest`. A contagem exclui `build/`, `.gradle/` e arquivos gerados.

## Entry points e navegação

- `SubTranslateApp` é a classe `Application`, declarada no Manifest. Inicializa o `AppContainer` e fecha engines em condição de pressão de memória, exceto quando o serviço indica processamento ativo.
- `MainActivity` é a única Activity e o launcher `MAIN`/`LAUNCHER`. Contém a navegação e seleciona as seis telas por rotas string (`projects`, `config`, `processing/{id}`, `editor/{id}`, `export/{id}`, `settings`); não há uma biblioteca Navigation dedicada identificada.
- `SubtitleExtractionService` é registrado no Manifest como serviço não exportado e processa intents de início/parada em foreground. O fluxo de configuração seleciona vídeos com `OpenDocument`; a exportação salva SRT/VTT com `CreateDocument` e permite compartilhamento por `FileProvider`.
- `AndroidManifest.xml` declara permissões de internet, notificações, foreground service (data sync e media processing) e wake lock.

## Fluxo funcional e domínios

1. **Projetos:** lista projetos persistidos, abre edição/processamento conforme estado e permite exclusão.
2. **Configuração:** seleciona vídeo, região da legenda (ROI), idiomas, FPS de amostragem e modo de saída; persiste o projeto.
3. **Extração:** o serviço executa pipeline em background; amostras do vídeo passam por OCR, agrupamento temporal e tradução local. Estado/progresso é observado pela tela e notificação.
4. **Revisão:** corrige texto e timestamps, divide/mescla/remove linhas, adiciona linha manual e pode solicitar nova tradução.
5. **Exportação:** gera formatos SRT/VTT e dá suporte ao compartilhamento.
6. **Preferências:** persiste parâmetros gerais e glossário usando DataStore.

## Configuração e recursos

- `settings.gradle.kts`: inclui somente `:app`, define repositórios e resolução centralizada de dependências.
- `build.gradle.kts`: declara plugins Android, Kotlin Compose e KSP no build raiz.
- `app/build.gradle.kts`: configura Android SDK, signing, minificação release, Compose, KSP/Room e dependências.
- `gradle/libs.versions.toml`: catálogo central de versões e aliases.
- `gradle/wrapper/gradle-wrapper.properties`: fixa Gradle Wrapper em 9.3.1.
- `app/src/main/AndroidManifest.xml`: application, Activity, serviço, FileProvider e permissões.
- `app/schemas/`: schema Room exportado para versionamento.
- `.env.example`: confirma que a aplicação não exige variáveis nem chave de API para a execução normal.

## Observações arquiteturais

- O desenho segue separação de apresentação, domínio e dados, mas a separação é por pacotes dentro de um único módulo Gradle; os limites não são impostos pelo build.
- A navegação, a criação manual de dependências e o estado de algumas rotas estão concentrados na Activity principal.
- O processamento longo está separado da UI por serviço Android, pipeline e contratos de engine, mantendo a persistência local observável por flows.
