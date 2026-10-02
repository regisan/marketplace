# Fitness functions

> Verificações automatizadas que impedem a arquitetura de se afastar das decisões registradas. Cada uma protege um ADR, um cenário de qualidade ou uma obrigação de segurança, e roda no CI.

**Legenda de estado:** **Ativa** = implementada neste repositório; **PoC** = será implementada junto com a fatia executável; **Plano** = entra na fase indicada do plano 30/60/90.

## 1. Catálogo

| ID | O que verifica | Ferramenta | Onde roda | Efeito | Protege | Estado |
|---|---|---|---|---|---|---|
| FF-01 | Nenhum campo `x-pii` em payloads de eventos e webhooks | Spectral (`.spectral.yaml`) | `governanca.yml` | Bloqueia | ADR-002, ADR-008, QA-PRI-01 | Ativa |
| FF-02 | POST da v2 com `Idempotency-Key` obrigatório; erros em Problem Details | Spectral (`.spectral-v2.yaml`) | `governanca.yml` | Bloqueia | ADR-005, ADR-007 | Ativa |
| FF-03 | Boas práticas gerais de OpenAPI e AsyncAPI | Spectral (`recommended`) | `governanca.yml` | Avisa | Qualidade dos contratos | Ativa |
| FF-04 | Validade estrutural do contrato de eventos | AsyncAPI CLI | `governanca.yml` | Bloqueia | ADR-006 | Ativa |
| FF-05 | v1 atual compatível com o contrato dos consumidores atuais | `oasdiff breaking` contra a linha de base | `governanca.yml` | Bloqueia | ADR-007, QA-COM-02 | Ativa |
| FF-06 | Nenhuma quebra de contrato em relação à branch de destino | `oasdiff breaking` contra `main` | `governanca.yml` | Bloqueia | ADR-007, QA-COM-02 | Ativa |
| FF-07 | ADRs com nome, título, status e seções obrigatórias, e presentes no índice | `scripts/verificar-adrs.sh` | `governanca.yml` | Bloqueia | Registro de decisões | Ativa |
| FF-08 | Sintaxe dos diagramas PlantUML | `scripts/render-diagramas.sh --check` | `diagramas.yml` | Bloqueia | Documentação como código | Ativa |
| FF-09 | Links internos da documentação | lychee (modo offline) | `governanca.yml` | Bloqueia | Navegabilidade dos artefatos | Ativa |
| FF-10 | Exceções técnicas vencidas ou referências a exceções inexistentes | `scripts/verificar-excecoes.sh` | `governanca.yml` | Bloqueia | Processo de exceção | Ativa |
| FF-11 | Domínio não depende de adaptadores; adaptadores v1 e v2 independentes entre si | ArchUnit | Build da PoC | Bloqueia | ADR-001, ADR-007 | PoC |
| FF-12 | Todo cliente HTTP é criado pela fábrica que exige timeout | ArchUnit | Build da PoC | Bloqueia | `03-resiliencia.md` | PoC |
| FF-13 | Consumidor escrito para a v1 1.0.0 funciona contra o serviço atual; pedido da v2 legível pela v1 | Testes de consumidor (JUnit) | Build da PoC | Bloqueia | ADR-007, QA-COM-01, QA-COM-03 | PoC |
| FF-14 | Mesma chave não cria pedidos duplicados, inclusive com concorrência | Testes de integração com Testcontainers | Build da PoC | Bloqueia | ADR-005, QA-INT-01 a QA-INT-03 | PoC |
| FF-15 | Evento serializado sem dados pessoais | Teste de integração | Build da PoC | Bloqueia | ADR-002, QA-PRI-02 | PoC |
| FF-16 | Vulnerabilidades críticas em dependências; análise estática de código | Dependency review e CodeQL | Build da PoC | Bloqueia em severidade crítica | DEP-01, API-07 | PoC |
| FF-17 | Nenhum dado pessoal ou segredo em logs e traces | Varredura de logs dos testes de integração e de carga | Pipeline de testes | Bloqueia | DAD-02, QA-SEG-06 | Plano, fase 1 |
| FF-18 | Recursos com dados pessoais apenas na região da célula | Política de IaC (por exemplo, OPA ou Checkov) | Pipeline de infraestrutura | Bloqueia | ADR-002, QA-PRI-03 | Plano, fase 3 |

## 2. Como adicionar uma fitness function

1. Identificar a decisão ou o cenário que ela protege e registrar no catálogo acima.
2. Preferir verificações determinísticas e rápidas, que rodem em todo PR.
3. Começar como aviso quando houver risco de falso positivo; promover a bloqueio depois de estabilizada.
4. Toda supressão de uma fitness function bloqueante segue o processo de exceção técnica.

## 3. Execução local

```bash
bash scripts/verificar-adrs.sh
bash scripts/verificar-excecoes.sh
bash scripts/render-diagramas.sh --check
# Contratos: ver poc/docs/README.md
```
