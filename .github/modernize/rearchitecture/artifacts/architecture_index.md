# Guia de implementação — SubTranslate

Este índice navega os contratos arquiteturais registrados no estado atual do app.

## Implementation Guide

**This index is not the full contract. Do not implement from this file alone; follow the artifact paths below.**

### Artefatos globais

- `unit_graph.yaml`
  - quem lê: todos os workers.
  - uso: unidades, gatilhos, dependências, assinaturas, entrypoints dinâmicos e referências compartilhadas.
  - filtro: encontre o nome da unidade atribuída; `source_anchors` comprovam comportamento, mas não determinam por si só escopo de alterações.
- `wire_contracts.yaml`
  - quem lê: workers que tocam chamadas externas, serviço ou importação/exportação de arquivos.
  - uso: contrato de intents do foreground service, Gemini `generateContent` e documentos SRT/VTT/TXT.
  - filtro: selecione as linhas com `unit` correspondente à unidade.
- `shared_modules.yaml`
  - quem lê: workers que referenciam repositories, engines, pipeline ou composition root compartilhados.
  - uso: superfície e estratégia existente dos módulos usados por mais de uma unidade.
  - filtro: `used_by_units` contém a unidade; `shared_refs.used_fields` restringe os membros relevantes.
- `cross_unit_state.yaml`
  - quem lê: workers do launcher e do serviço.
  - uso: marcador estático que cruza a fronteira Activity/service.
  - filtro: fluxos onde `writer.unit` ou `reader.unit` corresponde à unidade.
- `project-structure.md`, `tech-stack.md`, `data-model.md`
  - quem lê: todos os workers/designers.
  - uso: estrutura e fluxos funcionais, stack declarado e schema persistido.

Esta análise não define uma migração, boundary de reescrita ou seam declarado; nenhum desses contratos deve ser inferido a partir das âncoras de código.

### Unidade: `app_launcher`

- gatilho externo: Android `MAIN`/`LAUNCHER`, `MainActivity`.
- ler: `units/app_launcher/behavior.yaml` (inicialização, recuperação e navegação), `units/app_launcher/bindings.yaml` (Manifest, Activity e rotas), `units/app_launcher/unit_decomposition.yaml` (candidatos apenas).
- globais: `unit_graph.yaml` (dependências e entrada), `shared_modules.yaml` (composition root/repositories), `cross_unit_state.yaml` (estado do serviço).
- antes de concluir: relatar artefatos lidos, comportamento preservado, pendências e evidências de teste/build/runtime.

### Unidade: `project_library`

- gatilho: lista e seleção de projetos.
- ler: `units/project_library/behavior.yaml`, `units/project_library/bindings.yaml`, `units/project_library/unit_decomposition.yaml` (candidatos apenas).
- globais: filtrar `unit_graph.yaml` por `project_library`; `shared_modules.yaml` por `used_by_units`; considerar rotas para processamento, editor e tradução manual.
- antes de concluir: relatar rotas/ações e comportamentos preservados, pendências e evidências de verificação.

### Unidade: `project_setup`

- gatilho: criação/configuração de projeto e seleção de vídeo.
- ler: `units/project_setup/behavior.yaml`, `units/project_setup/bindings.yaml`, `units/project_setup/unit_decomposition.yaml` (candidatos apenas).
- globais: `data-model.md` para campos persistidos; `shared_modules.yaml` por `used_by_units`; `unit_graph.yaml` para a dependência de processamento.
- antes de concluir: relatar seleção de vídeo, configuração de ROI/modalidade e validações preservadas, pendências e evidências.

### Unidade: `processing_screen`

- gatilho: rota `processing/{id}`.
- ler: `units/processing_screen/behavior.yaml`, `units/processing_screen/bindings.yaml`, `units/processing_screen/unit_decomposition.yaml` (candidatos apenas).
- globais: filtrar `shared_modules.yaml` por `used_by_units`; `wire_contracts.yaml` para intents do serviço; `cross_unit_state.yaml` se tocar o estado da extração.
- antes de concluir: relatar controles de execução preservados e evidências do ciclo de vida/processamento.

### Unidade: `extraction_service`

- gatilho: intents `ACTION_START`, `ACTION_STOP`, `ACTION_PAUSE` e `ACTION_RESUME`.
- ler: `units/extraction_service/behavior.yaml`, `units/extraction_service/bindings.yaml`, `units/extraction_service/unit_decomposition.yaml` (candidatos apenas).
- globais: filtrar `wire_contracts.yaml` por `extraction_service` (service e Gemini); `shared_modules.yaml` por `used_by_units`; `cross_unit_state.yaml` por writer/reader.
- antes de concluir: relatar efeitos, estados, cancelamento/pausa, erros e contratos externos preservados, pendências e evidência runtime.

### Unidade: `subtitle_editor`

- gatilho: rota `editor/{id}`.
- ler: `units/subtitle_editor/behavior.yaml`, `units/subtitle_editor/bindings.yaml`, `units/subtitle_editor/unit_decomposition.yaml` (candidatos apenas).
- globais: filtrar `shared_modules.yaml` por `used_by_units`; `data-model.md` para persistência das linhas; `unit_graph.yaml` para dependências.
- antes de concluir: relatar edição, persistência e retradução preservadas, pendências e testes/evidência.

### Unidade: `subtitle_export`

- gatilho: rota `export/{id}`.
- ler: `units/subtitle_export/behavior.yaml`, `units/subtitle_export/bindings.yaml`, `units/subtitle_export/unit_decomposition.yaml` (candidatos apenas).
- globais: filtrar `wire_contracts.yaml` por `subtitle_export`; consultar `unit_graph.yaml` para rota e origem das linhas.
- antes de concluir: relatar formatos e fluxos de destino/compartilhamento preservados, pendências e evidência de exportação.

### Unidade: `settings`

- gatilho: rota `settings`.
- ler: `units/settings/behavior.yaml`, `units/settings/bindings.yaml`, `units/settings/unit_decomposition.yaml` (candidatos apenas).
- globais: filtrar `shared_modules.yaml` por `settings`; `data-model.md` para chaves/defaults DataStore.
- antes de concluir: relatar preferências, engine/chave Gemini, glossário/filtros preservados, pendências e evidência de persistência.

### Unidade: `manual_translation`

- gatilho: rota `manual/{id}` para projetos `EXTRACT_ONLY`.
- ler: `units/manual_translation/behavior.yaml` (exportação/importação e parsing), `units/manual_translation/bindings.yaml` (rota, SAF e FileProvider), `units/manual_translation/unit_decomposition.yaml` (candidatos apenas).
- globais: filtrar `wire_contracts.yaml` por `manual_translation`; `shared_modules.yaml` por `used_by_units`; `unit_graph.yaml` para dependências de editor/exportação.
- antes de concluir: relatar formatos de arquivo e regras de associação/aplicação preservados, pendências e evidência de round-trip.

## Evidência final

Cada worker deve listar artefatos lidos, itens `must_preserve` implementados, contratos não resolvidos/deferidos e resultados reais de testes/build/runtime. Os `unit_decomposition.yaml` são exploratórios (`commit: false`), não definem estrutura-alvo. Esta análise documenta o estado atual, não especifica uma migração.
