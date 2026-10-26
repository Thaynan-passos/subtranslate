# Guia de implementação — SubTranslate

Este índice navega os contratos arquiteturais registrados para o estado atual do app.

## Implementation Guide

**This index is not the full contract. Do not implement from this file alone; follow the artifact paths below.**

### Artefatos globais

- `unit_graph.yaml`
  - quem lê: todos os workers.
  - uso: limites dos oito units, gatilhos Android/UI, assinaturas, dependências e módulos compartilhados.
  - filtro: encontre a unidade atribuída; use `source_anchors` como evidência de comportamento, não como lista automática de mudanças.
- `wire_contracts.yaml`
  - quem lê: workers de service e exportação.
  - uso: contrato do foreground service e formatos/fluxos de exportação.
  - filtro: `unit` igual ao unit atribuído.
- `shared_modules.yaml`
  - quem lê: workers que referenciam dados, engines ou composição compartilhados.
  - uso: superfície e estratégia de tratamento dos módulos reutilizados.
  - filtro: `used_by_units` contém o nome do unit; os campos listados em `shared_refs.used_fields` limitam a superfície relevante.
- `cross_unit_state.yaml`
  - quem lê: workers do app launcher e do serviço de extração.
  - uso: estado estático que atravessa a fronteira Activity/service.
  - filtro: `writer.unit` ou `reader.unit` corresponde ao unit atribuído.
- `project-structure.md`, `tech-stack.md`, `data-model.md`
  - quem lê: todos os workers e designers.
  - uso: contexto de camadas/fluxos, dependências e schema persistido existente.

### Unit: `app_launcher`

- gatilho externo: Android `MAIN`/`LAUNCHER`, `MainActivity`.
- ler: `units/app_launcher/behavior.yaml` (inicialização, roteamento e recuperação), `units/app_launcher/bindings.yaml` (Manifest e rotas), `units/app_launcher/unit_decomposition.yaml` (somente candidatos).
- globais relevantes: `unit_graph.yaml` (entrypoint/dependências), `shared_modules.yaml` (AppContainer/repositories), `cross_unit_state.yaml` (marcador do serviço).
- antes de concluir: reportar artefatos lidos, comportamentos preservados, contratos pendentes e evidências de teste/build/runtime.

### Unit: `project_library`

- gatilho externo: tela de listagem de projetos.
- ler: `units/project_library/behavior.yaml`, `units/project_library/bindings.yaml`, `units/project_library/unit_decomposition.yaml` (somente candidatos).
- globais relevantes: filtrar `unit_graph.yaml` por `project_library`; `shared_modules.yaml` por `used_by_units`; `wire_contracts.yaml` por `unit`.
- antes de concluir: reportar leitura, comportamentos/ramificações preservados, pendências e evidências de verificação.

### Unit: `project_setup`

- gatilho externo: ação de criar/configurar projeto e selecionar vídeo.
- ler: `units/project_setup/behavior.yaml`, `units/project_setup/bindings.yaml`, `units/project_setup/unit_decomposition.yaml` (somente candidatos).
- globais relevantes: `shared_modules.yaml` por `used_by_units`; `unit_graph.yaml` para dependência com processamento; `wire_contracts.yaml` apenas se tocar os contratos aplicáveis.
- antes de concluir: reportar leitura, validações e seleção de vídeo/ROI preservadas, pendências e verificação.

### Unit: `processing_screen`

- gatilho externo: rota `processing/{id}`.
- ler: `units/processing_screen/behavior.yaml`, `units/processing_screen/bindings.yaml`, `units/processing_screen/unit_decomposition.yaml` (somente candidatos).
- globais relevantes: `shared_modules.yaml` por `used_by_units`; `wire_contracts.yaml` para o contrato do serviço; `cross_unit_state.yaml` se tocar o estado do processamento.
- antes de concluir: reportar os controles de execução preservados e evidências de teste de estado/ciclo de vida.

### Unit: `extraction_service`

- gatilho externo: intent Android `ACTION_START`/`ACTION_STOP`.
- ler: `units/extraction_service/behavior.yaml`, `units/extraction_service/bindings.yaml`, `units/extraction_service/unit_decomposition.yaml` (somente candidatos).
- globais relevantes: filtrar `wire_contracts.yaml` por `extraction_service`; `shared_modules.yaml` por `used_by_units`; `cross_unit_state.yaml` por writer/reader; `unit_graph.yaml` para engines e repositórios.
- antes de concluir: reportar efeitos, estados, tratamento de cancelamento/timeout preservados, contratos pendentes e evidência runtime.

### Unit: `subtitle_editor`

- gatilho externo: rota `editor/{id}`.
- ler: `units/subtitle_editor/behavior.yaml`, `units/subtitle_editor/bindings.yaml`, `units/subtitle_editor/unit_decomposition.yaml` (somente candidatos).
- globais relevantes: `shared_modules.yaml` por `used_by_units`; `unit_graph.yaml` para repositórios e tradutor.
- antes de concluir: reportar operações de edição e persistência preservadas, pendências e testes/evidência.

### Unit: `subtitle_export`

- gatilho externo: rota `export/{id}`.
- ler: `units/subtitle_export/behavior.yaml`, `units/subtitle_export/bindings.yaml`, `units/subtitle_export/unit_decomposition.yaml` (somente candidatos).
- globais relevantes: filtrar `wire_contracts.yaml` por `subtitle_export`; `unit_graph.yaml` para localização da rota.
- antes de concluir: reportar conteúdo/formatos de saída preservados e evidência de exportação/share.

### Unit: `settings`

- gatilho externo: rota `settings`.
- ler: `units/settings/behavior.yaml`, `units/settings/bindings.yaml`, `units/settings/unit_decomposition.yaml` (somente candidatos).
- globais relevantes: `shared_modules.yaml` por `used_by_units`; `data-model.md` para chaves DataStore e defaults.
- antes de concluir: reportar preferências/glossário preservados, pendências e evidência de persistência.

## Evidência final

Cada worker deve listar artefatos lidos, itens `must_preserve` implementados, contratos não resolvidos/deferidos e resultados reais de testes/build/runtime. `unit_decomposition.yaml` é exploratório e tem `commit: false`; não define a estrutura-alvo.
