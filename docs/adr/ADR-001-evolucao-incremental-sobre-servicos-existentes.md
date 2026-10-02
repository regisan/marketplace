# ADR-001: Evolução incremental sobre os serviços existentes

- **Status:** Proposto
- **Data:** 2026-10-02
- **Decisores:** Arquitetura, liderança técnica de Pedidos e Catálogo
- **Relacionados:** ADR-002, ADR-003, ADR-006, ADR-008; [`01-mapa-de-dominios.md`](../01-mapa-de-dominios.md)

## Contexto

A plataforma tem dois serviços, Pedidos e Catálogo, cada um com seu banco (P-ASIS-01). Em 90 dias precisa suportar app móvel, parceiros e um segundo país, com volume 10x maior, sem interromper os consumidores atuais. A primeira melhoria precisa estar em produção em 30 dias (P-TIME-01: time de 4 desenvolvedores).

Os problemas do enunciado (N+1, ausência de snapshot, falta de idempotência, publicação sem garantia) são de **comportamento dentro dos serviços**, não de granularidade dos serviços. A única capacidade nova com perfil de carga e falha realmente diferente é a entrega de notificações a parceiros: faz chamadas HTTP de saída para sistemas de terceiros, cuja lentidão não pode afetar a criação de pedidos.

## Drivers

- Prazo de 30 dias para a primeira melhoria e de 90 dias para os novos canais.
- Time pequeno; cada novo serviço implantável adiciona pipeline, observabilidade, on-call e contratos.
- Critério de avaliação explícito contra superdimensionamento.
- Isolamento de falhas exigido pelo SLO de 99,9% (C-03).

## Alternativas consideradas

**A. Evoluir os dois serviços existentes e criar apenas o serviço de Integração com Parceiros.**
Prós: menor número de peças novas; o time trabalha sobre código que já conhece; a falha de parceiros fica isolada em um processo próprio. Contras: o serviço de Pedidos continua concentrando várias responsabilidades e pode precisar ser dividido no futuro.

**B. Decompor em microsserviços finos** (pedidos, precificação, status, idempotência, notificações, cadastro de parceiros).
Prós: escalabilidade independente por capacidade. Contras: transações distribuídas onde hoje há transação local (snapshot, idempotência e outbox perderiam atomicidade); multiplica pontos de falha síncronos (C-03); inviável em 30 dias com 4 desenvolvedores.

**C. Reescrever a plataforma em paralelo e migrar no dia 90.**
Prós: liberdade de desenho. Contras: entrega de valor apenas ao final, migração "big bang" e risco máximo de quebra de contrato; contraria a exigência de evolução incremental.

## Decisão

Adotar a **alternativa A**. Pedidos e Catálogo evoluem internamente por etapas, protegidas por feature flags. É criado um único novo serviço, **Integração com Parceiros**, que consome eventos de Pedidos e entrega webhooks. Infraestrutura gerenciada (API Gateway, broker) não conta como serviço de domínio.

Dentro de Pedidos, o código é organizado em módulos por responsabilidade (API v1, API v2, domínio, idempotência, outbox, read model de catálogo), com regras de dependência verificadas por ArchUnit. Isso preserva a opção de extrair módulos para serviços no futuro sem pagar esse custo agora.

**Gatilhos para revisitar a decisão:** um módulo de Pedidos passa a exigir escala ou cadência de deploy muito diferente das demais (por exemplo, leitura acima de 20x a escrita), ou um segundo time passa a trabalhar no mesmo serviço com conflitos frequentes.

## Consequências

**Positivas**
- Snapshot, idempotência e outbox ficam na mesma transação de banco, sem coordenação distribuída.
- Menor custo operacional e menor superfície de falha.
- Entrega viável em 30 dias, com valor desde a primeira fase.

**Negativas e riscos**
- Pedidos continua sendo um serviço relevante e concentrado; um bug de deploy afeta todas as suas capacidades. Mitigação: rollout canário e feature flags.
- A disciplina modular depende de regras automatizadas; sem elas, o serviço se degrada em monólito acoplado.

## Validação

- Fitness function com ArchUnit no CI: o pacote de domínio não depende de adaptadores (web, mensageria, persistência); os adaptadores v1 e v2 não dependem um do outro.
- Revisão trimestral dos gatilhos listados acima.
