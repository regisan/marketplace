# Repositório: evolução arquitetural da plataforma de Pedidos e Catálogo

Proposta de arquitetura para um desafio técnico. A maior parte do repositório é documentação já revisada; o código fica em `poc/`.

## Mapa

| Caminho | Conteúdo | Pode alterar? |
|---|---|---|
| `docs/` | Premissas, mapa de domínios, atributos de qualidade, resiliência, threat model, plano, riscos, estimativa | Somente se pedido |
| `docs/adr/` | ADRs | Somente se pedido; nunca editar a decisão de um ADR aceito |
| `docs/arquitetura/` | Diagramas C4 e de sequência (PlantUML) | Somente se pedido |
| `docs/governanca/` | Política de contratos, exceções, fitness functions | Somente se pedido |
| `poc/docs/` | Contratos OpenAPI e AsyncAPI, fontes de verdade da PoC | **Não**: relatar inconsistências |
| `poc/` (demais) | Implementação da PoC | Sim |
| `.github/workflows/poc.yml` | CI da PoC | Sim |

## Regras gerais

- Comunicação e documentação em português; código conforme a linguagem ubíqua de `docs/01-mapa-de-dominios.md`.
- Verificações do repositório: `bash scripts/verificar-adrs.sh`, `bash scripts/verificar-excecoes.sh`, `bash scripts/render-diagramas.sh --check`.
- Ao trabalhar na PoC, siga a especificação abaixo.

@poc/CLAUDE.md
