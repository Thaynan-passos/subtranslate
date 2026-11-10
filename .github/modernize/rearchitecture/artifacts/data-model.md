# Modelo de dados atual

O armazenamento persistente local usa Room em `subtranslate_database`, schema version **1**, com export de schema habilitado. O schema exportado está em `app/schemas/com.example.data.local.SubTranslateDatabase/1.json`. Preferências ficam em DataStore separado; não foi observado banco remoto.

## Entidades Room

### `projects` — `ProjectEntity`

Chave primária `id` (`Long`, autogerada). Colunas e defaults declarados:

| Campo | Tipo Kotlin | Default |
|---|---|---|
| `id` | Long | 0 antes da geração |
| `title`, `videoUri` | String | obrigatórios |
| `durationMs` | Long | 0 |
| `videoWidth`, `videoHeight` | Int | 1920, 1080 |
| `sourceLanguage`, `targetLanguage` | String | `en`, `pt` |
| `samplingFps` | Int | 2 |
| `outputMode` | String | `SRT_ONLY` |
| `status` | String | `IDLE` |
| `progress` | Float | 0 |
| `currentStep` | String | vazio |
| `errorMessage` | String nullable | null |
| `createdAt` | Long | epoch millis atual |
| `roiLeft`, `roiTop`, `roiRight`, `roiBottom` | Float | 0.05, 0.70, 0.95, 0.95 |

O schema Room declara `TEXT` para strings, `INTEGER` para inteiros/longs e `REAL` para floats. Não há conversores de enum: `outputMode` e `status` são persistidos como nomes string.

### `subtitle_lines` — `SubtitleLineEntity`

Chave primária `id` autogerada. Índices não únicos em `projectId` e `startTimeMs`.

| Campo | Tipo Kotlin | Default |
|---|---|---|
| `id` | Long | 0 antes da geração |
| `projectId` | Long | obrigatório |
| `lineIndex` | Int | obrigatório |
| `startTimeMs`, `endTimeMs` | Long | obrigatórios |
| `originalText`, `translatedText` | String | obrigatórios |
| `confidence` | Float | 1.0 |
| `isFlagged` | Boolean | false |

`projectId` referencia `projects.id`; a foreign key declara `ON DELETE CASCADE` e `ON UPDATE NO ACTION`.

### `translation_cache` — `TranslationCacheEntity`

Chave primária composta por `sourceText`, `sourceLang`, `targetLang` e `engine`.

| Campo | Tipo Kotlin | Default |
|---|---|---|
| `sourceText`, `sourceLang`, `targetLang`, `engine` | String | obrigatórios; formam a PK |
| `translatedText` | String | obrigatório |
| `timestamp` | Long | epoch millis atual |

Não há FK ou relacionamento Room declarado para o cache. O valor especial de `engine` igual a `user` recebe prioridade na consulta de melhor tradução.

## Preferências DataStore

DataStore Preferences usa o nome `subtranslate_settings`. As chaves e defaults efetivos usados pelo `SettingsRepository` são:

| Chave | Tipo | Default |
|---|---|---|
| `default_fps` | Int | 2 |
| `min_speech_duration` | Int (ms) | 300 |
| `similarity_threshold` | Float | 0.85 |
| `max_chars_per_line` | Int | 42 |
| `cover_original_overlay` | Boolean | true |
| `overlay_color_hex` | String | `#E6000000` |
| `subtitle_font_size` | Int | 18 |
| `subtitle_text_color` | String | `#FFFFFFFF` |
| `glossary_raw` | String | vazio |
| `filter_watermarks` | Boolean | true |
| `ignored_terms_raw` | String | vazio |
| `translation_engine` | String | `gemini` |
| `gemini_api_key` | String | vazio |
| `gemini_model` | String | `gemini-2.5-flash` |

`max_chars_per_line` é limitado a 20–80; duração mínima é limitada a 100–5000 ms; similaridade é limitada a 0.50–0.98. O `AppSettings` usado como valor inicial fora do DataStore declara `textSimilarityThreshold = 0.80`, enquanto o valor de leitura inicial do repositório é 0.85.

## Relações e operações transacionais

- `projects` 1:N `subtitle_lines`, enforced pela foreign key com exclusão em cascata.
- `translation_cache` é independente das outras entidades.
- `ProjectDao` consulta projetos com contagem de linhas por subquery; linhas são ordenadas por `startTimeMs`, `lineIndex` e `id`.
- `SubtitleLineDao.updateLines` está anotado com `@Transaction`; não foi identificada uma transação que envolva o pipeline inteiro.
- Os repositories convertem explicitamente entidades Room em modelos de domínio.

## Entidades-chave

1. `ProjectEntity`: vídeo, idiomas, modalidade, ROI, status e progresso de cada projeto.
2. `SubtitleLineEntity`: texto fonte/destino, tempos, confiança e sinalização de revisão por projeto.
3. `TranslationCacheEntity`: traduções por texto, par de idiomas e engine, inclusive memória de tradução do usuário.
4. `AppSettings` (modelo de domínio, não entidade Room): configurações do pipeline, engine, glossário, chave/modelo Gemini e apresentação, persistidas em DataStore.
