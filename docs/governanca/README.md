# Governança

Mecanismos leves para manter a arquitetura coerente com as decisões registradas, sem depender apenas de revisão manual.

| Documento | Conteúdo |
|---|---|
| [Política de evolução de contratos](politica-de-evolucao-de-contratos.md) | Classificação de mudanças, versionamento, ciclo de vida e registro de consumidores de APIs, eventos e webhooks |
| [Processo de exceção técnica](processo-de-excecao-tecnica.md) | Quando e como desviar temporariamente de uma regra, com aprovação, validade e controle automático |
| [Fitness functions](fitness-functions.md) | Catálogo das verificações automatizadas, o que protegem e onde rodam |
| [Exceções registradas](excecoes/) | Registros `EXC-nnn` e o template |

## Pontos de controle no repositório

| Arquivo | Papel |
|---|---|
| `.github/workflows/governanca.yml` | Executa as fitness functions de contratos, ADRs, exceções e links |
| `.github/workflows/diagramas.yml` | Valida e renderiza os diagramas |
| `.github/pull_request_template.md` | Checklist de arquitetura em todo PR |
| `.github/CODEOWNERS` | Revisão obrigatória da arquitetura em ADRs, contratos e governança |
| `scripts/verificar-adrs.sh` | FF-07 |
| `scripts/verificar-excecoes.sh` | FF-10 |
