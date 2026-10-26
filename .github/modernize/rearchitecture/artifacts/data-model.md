# Modelo de dados atual

O banco local Room está em `subtranslate_database`, schema version 1, com export de schema habilitado. O acesso é feito por DAOs e repositories. Não foi encontrado backend relacional/remoto ou migração de schema no escopo lido.

## Entidades persistidas

### `projects` — `ProjectEntity`

PK: `id` (`Long`, autogerada).

| Campo | Tipo | Valor inicial/default do modelo |
|---|---|---|
| `title` | String | obrigatório |
| `videoUri` | String | obrigatório |
| `durationMs` | Long | 0 |
| `videoWidth` / `videoHeight` | Int | 1920 / 1080 |
| `sourceLanguage` / `targetLanguage` | String | `en` / `pt` |
| `samplingFps` | Int | 3 |
| `outputMode` | String | `SRT_ONLY` |
| `status` | String | `IDLE` |
| `progress` | Float | 0 |
| `currentStep` | String | vazio |
| `errorMessage` | String nullable | null |
| `createdAt` | Long | horário atual em epoch millis |
| `roiLeft` / `roiTop` / `roiRight` / `roiBottom` | Float | 0.05 / 0.70 / 0.95 / 0.95 |

### `subtitle_lines` — `SubtitleLineEntity`

PK: `id` (`Long`, autogerada). Índices em `projectId` e `startTimeMs`.

| Campo | Tipo | Default |
|---|---|---|
| `projectId` | Long | obrigatório |
| `lineIndex` | Int | obrigatório |
| `startTimeMs` / `endTimeMs` | Long | obrigatório |
| `originalText` / `translatedText` | String | obrigatório |
| `confidence` | Float | 1.0 |
| `isFlagged` | Boolean | false |

`projectId` referencia `projects.id` com `ON DELETE CASCADE`. Consultas ordenam por `startTimeMs`, `lineIndex`, `id`.

### `translation_cache` — `TranslationCacheEntity`

Chave primária composta: `sourceText`, `sourceLang`, `targetLang`, `engine`.

| Campo | Tipo | Default |
|---|---|---|
| `sourceText`, `sourceLang`, `targetLang`, `engine` | String | obrigatórios; compõem a PK |
| `translatedText` | String | obrigatório |
| `timestamp` | Long | horário atual em epoch millis |

Não há relacionamento declarado entre essa entidade e as outras.

## Persistência de preferências

DataStore Preferences separado, nome `subtranslate_settings`:

- `default_fps` (Int, default 3)
- `min_speech_duration` (Int, default 300 ms)
- `similarity_threshold` (Float, default 0.85)
- `max_chars_per_line` (Int, default 42; atualização limitada a 20–80)
- `cover_original_overlay` (Boolean, default true)
- `overlay_color_hex` (String, default `#E6000000`)
- `subtitle_font_size` (Int, default 18)
- `subtitle_text_color` (String, default `#FFFFFFFF`)
- `glossary_raw` (String, default vazio; analisado em pares separados por `->` ou `=`)

## Relações e transações

- Relação declarada: `projects` 1:N `subtitle_lines`, enforced por FK com cascade delete.
- `translation_cache` é independente; combinação idioma/texto/engine identifica a tradução armazenada.
- `@Transaction` está aplicado à atualização em lote `SubtitleLineDao.updateLines`. Não foi identificado limite transacional englobando todas as fases do pipeline.
- Os modelos de domínio `Project` e `SubtitleLine` são convertidos explicitamente pelas entidades Room.

## Entidades-chave

1. `ProjectEntity`: configuração do vídeo, ROI, idiomas, estado e progresso de um trabalho.
2. `SubtitleLineEntity`: texto original/traduzido e temporização associada ao projeto.
3. `TranslationCacheEntity`: memoização de traduções por texto, idiomas e engine.
4. `AppSettings` (modelo de domínio, não entidade Room): preferências de processamento, glossário e apresentação; armazenadas em DataStore.
