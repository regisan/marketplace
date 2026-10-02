# Resiliência e prevenção de efeito cascata

> Define como a plataforma impede que a falha ou a lentidão de um componente se propague para os demais, usando timeout, retry com backoff, circuit breaker, bulkhead e degradação controlada. Os valores aqui são o ponto de partida para configuração e devem ser recalibrados com dados dos testes de carga.
>
> Base: ADR-003, ADR-005, ADR-006, ADR-008; diagramas em `arquitetura/sequencia/`; cenários QA-DIS, QA-DES e QA-REC em `02-atributos-de-qualidade.md`.

## 1. O efeito cascata no estado atual

O AS-IS tem o caminho clássico de falha em cascata (F1 em `arquitetura/README.md`):

1. O Catálogo fica lento, por exemplo, respondendo em 2 s em vez de 15 ms.
2. Cada criação de pedido faz uma chamada síncrona por item. Um pedido de 20 itens passa a levar 40 s.
3. As threads e conexões de Pedidos ficam presas esperando o Catálogo. O pool se esgota e Pedidos para de responder também às consultas, que nem dependem do Catálogo.
4. Os clientes recebem timeouts e repetem as requisições. Sem idempotência, alguns pedidos são duplicados; com mais requisições, o Catálogo fica ainda mais lento.
5. Uma lentidão em um serviço de suporte derruba a operação inteira de vendas.

A arquitetura-alvo quebra essa cadeia em cada elo: retira o Catálogo do caminho crítico (ADR-003), limita o tempo e a concorrência de toda chamada remota, concentra retries em um único ponto e torna os retries seguros com idempotência (ADR-005).

## 2. Princípios

1. **Nenhuma chamada remota sem timeout.** Inclui banco, broker, IdP e LLM. O timeout de quem chama é sempre maior que o de quem é chamado.
2. **Retry em uma única camada.** Retries em várias camadas se multiplicam (3 tentativas em 3 camadas viram 27 chamadas). Na criação de pedido, quem repete é o cliente, protegido pela `Idempotency-Key`; as camadas internas não repetem.
3. **Retry só em operações idempotentes e erros transitórios**, sempre com backoff exponencial e jitter, e respeitando `Retry-After`.
4. **Falhar rápido é melhor que falhar devagar.** Um `503` em 5 ms libera recursos; uma espera de 30 s os consome.
5. **Isolar recursos por dependência.** Uma dependência lenta só pode esgotar os recursos reservados para ela.
6. **Degradar funcionalidades, não a plataforma.** Cada modo de falha tem um comportamento degradado definido e sinalizado ao chamador.

## 3. Política por dependência

| Chamador → dependência | Estilo | Timeout | Retry | Circuit breaker | Bulkhead | Comportamento em falha |
|---|---|---|---|---|---|---|
| Cliente ou parceiro → API Gateway | Síncrono | Do cliente (recomendado 10 s) | No cliente, com a mesma `Idempotency-Key` | — | Quota por chamador | Cliente repete com backoff |
| Gateway → Pedidos | Síncrono | 5 s | Não em POST; 1 retry em GET apenas para falha de conexão | — | Limite de concorrência por rota | `502`/`504` ao cliente |
| Gateway → IdP (chaves JWKS) | Síncrono, fora do caminho da requisição | 2 s | Atualização periódica | — | — | Usa chaves em cache (*stale-if-error*) por até 24 h |
| Pedidos → banco | Síncrono | Obter conexão: 250 ms; `statement_timeout`: 2 s; `lock_timeout`: 2 s | Não | — | Pools separados (API, outbox, consumidores) | `503` com `Retry-After`; `409` em `lock_timeout` (ADR-005) |
| Pedidos → Catálogo (contingência) | Síncrono | Conexão 100 ms; leitura 200 ms | Não | Sim | Máx. 20 chamadas simultâneas por instância | `503 catalog-unavailable` com `Retry-After` |
| Publicador do outbox → broker | Assíncrono | `delivery.timeout.ms`: 30 s | Produtor idempotente do Kafka; lote volta ao outbox se falhar | — | Thread dedicada | Eventos acumulam no outbox; criação segue normal |
| Broker → consumidores (read model, Integração) | Assíncrono | Processamento: 5 s por mensagem | 5 tentativas locais com backoff (1 s a 30 s) | — | Grupo de consumidores por finalidade | Mensagem vai para a DLQ do consumidor, com alerta |
| Integração → endpoint do parceiro | Assíncrono | Conexão 2 s; total 5 s | Agenda de ADR-008 por até 24 h | Sim, por parceiro | Fila e pool por parceiro | Entrega em espera; DLQ após 24 h |
| Assistente → Pedidos | Síncrono | 1 s | 1 retry (operação somente leitura) | Sim | Pool próprio | Assistente informa que não conseguiu consultar |
| Assistente → Integração com Parceiros | Síncrono | 1 s | 1 retry (operação somente leitura) | Sim | Pool próprio | Assistente informa que não conseguiu consultar as entregas |
| Assistente → provedor de LLM | Síncrono | 20 s | Não | Sim | Máx. de chamadas simultâneas por instância | Mensagem de indisponibilidade; operador usa o backoffice |
| Catálogo → réplicas nas células | Assíncrono | Da replicação gerenciada | Da replicação | — | — | Read model fica desatualizado; criação segue com preços conhecidos |

## 4. Timeout: orçamento de tempo da criação de pedido

Os timeouts são aninhados para que a camada interna sempre desista antes da externa. Caso contrário, a externa abandona a requisição enquanto a interna continua consumindo recursos para nada.

```
Cliente ........................................................ 10 s (recomendado)
└─ API Gateway → Pedidos ......................................... 5 s
   └─ Pedidos: prazo total de 4,5 s por requisição (P95 alvo ≤ 1,2 s)
      ├─ Obter conexão do pool ................................. 250 ms
      ├─ Consulta em lote ao read model ...................... ≤ 2 s (statement_timeout)
      ├─ Contingência: Catálogo (só para SKUs ausentes) ........ 200 ms
      └─ Transação única (idempotência, pedido, snapshot, outbox)
         ├─ lock_timeout (chave concorrente) ...................... 2 s
         └─ statement_timeout por comando ......................... 2 s
```

Somados, os limites individuais poderiam passar de 5 s em um pior caso improvável. Por isso Pedidos aplica um prazo total de 4,5 s por requisição: ao estourá-lo, desfaz a transação e responde `503` com `Retry-After`. Assim a camada interna sempre desiste antes do gateway (5 s), que desiste antes do cliente (10 s). Como nada foi confirmado, o cliente pode repetir com a mesma `Idempotency-Key` sem risco.

## 5. Retry com backoff

**Onde há retry:**

| Camada | Quem repete | Por que é seguro |
|---|---|---|
| Criação de pedido | O cliente ou parceiro | `Idempotency-Key` garante no máximo um pedido (ADR-005) |
| Consultas | O cliente; o gateway uma vez em falha de conexão | Leitura sem efeito colateral |
| Consumo de eventos | O consumidor, localmente | Deduplicação por `eventId` e `orderVersion` (ADR-006) |
| Webhooks | Integração com Parceiros | Parceiro deduplica por `eventId` (ADR-008) |

**O que é repetível:** falha de conexão, timeout, `429`, `502`, `503` e `504`. Os demais `4xx` não são repetidos, porque repetir a mesma requisição inválida produz o mesmo erro.

**Fórmula de backoff** (*full jitter*): `espera = aleatório(0, min(teto, base × 2^tentativa))`. O jitter evita que milhares de clientes que falharam ao mesmo tempo repitam todos no mesmo instante, o que recriaria o pico que causou a falha. Quando a resposta traz `Retry-After`, ele prevalece.

**Orientação publicada aos parceiros** (documentação da API v2): base de 500 ms, teto de 30 s, no máximo 5 tentativas, sempre com a mesma `Idempotency-Key`.

## 6. Circuit breaker

Implementado com Resilience4j. O circuito abre quando a taxa de falhas ou de chamadas lentas passa do limite, recusa chamadas imediatamente enquanto aberto e, após uma espera, deixa passar algumas chamadas de teste antes de fechar.

| Instância | Janela | Mín. de chamadas | Limite de falhas | Chamada lenta | Espera aberto | Chamadas de teste |
|---|---|---|---|---|---|---|
| `catalogo` | Últimas 50 chamadas | 20 | 50% | > 150 ms, limite 50% | 30 s | 5 |
| `parceiro-{id}` | Últimos 60 s | 10 | 50% | — (timeout já conta como falha) | 60 s, crescendo até 15 min | 1 |
| `llm` | Últimas 20 chamadas | 10 | 50% | > 15 s, limite 50% | 60 s | 2 |
| `pedidos` (no Assistente) | Últimas 50 chamadas | 20 | 50% | > 500 ms, limite 50% | 30 s | 5 |

Configuração de referência para Pedidos:

```yaml
resilience4j:
  circuitbreaker:
    instances:
      catalogo:
        slidingWindowType: COUNT_BASED
        slidingWindowSize: 50
        minimumNumberOfCalls: 20
        failureRateThreshold: 50
        slowCallDurationThreshold: 150ms
        slowCallRateThreshold: 50
        waitDurationInOpenState: 30s
        permittedNumberOfCallsInHalfOpenState: 5
  bulkhead:
    instances:
      catalogo:
        maxConcurrentCalls: 20
        maxWaitDuration: 0ms   # sem fila: excedeu, falha imediatamente
```

O timeout de 200 ms fica no próprio cliente HTTP (timeout de leitura), e não em um `TimeLimiter`, porque a chamada é bloqueante.

Todo circuito publica a métrica `circuit_breaker_state` e gera alerta ao abrir.

## 7. Bulkhead

Bulkhead é reservar recursos separados para cada finalidade, como os compartimentos estanques de um navio: um compartimento inundado não afunda os outros.

| Nível | Isolamento | O que protege |
|---|---|---|
| País | Células BR e B (ADR-002) | Falha regional ou de deploy atinge apenas um país |
| Serviço | Integração com Parceiros separada de Pedidos (ADR-001) | Parceiros lentos não consomem recursos de criação de pedidos |
| Parceiro | Fila, pool de envio e circuit breaker por parceiro (ADR-008) | Um parceiro fora do ar não atrasa os demais |
| Chamador | Quotas por cliente no gateway | Um parceiro com tráfego excessivo não esgota a capacidade dos outros |
| Rota | Limite de concorrência por rota em Pedidos: criação, consulta por id e listagem com limites separados | Reconciliação pesada não impede a criação de pedidos |
| Dependência | Semáforo de 20 chamadas para o Catálogo | Lentidão do Catálogo afeta só os pedidos com SKUs fora do read model |
| Banco | Pools de conexões separados para API, publicador do outbox e consumidores | Atraso no consumo de eventos não deixa a API sem conexões |
| Consumidores | Grupo de consumidores por finalidade (read model, Integração, ERP) | Um consumidor atrasado não atrasa os outros |

## 8. Degradação controlada

Cada modo de falha tem um comportamento definido, conhecido pelos chamadores e observável.

| Situação | O que continua funcionando | O que degrada | Como é sinalizado |
|---|---|---|---|
| Catálogo indisponível | Criação com SKUs no read model; todas as consultas | Pedidos com SKUs novos; propagação de preços | `503 catalog-unavailable` com `Retry-After`; alerta de circuito aberto |
| Replicação de catálogo parada | Tudo, com os últimos preços conhecidos | Mudanças de preço não chegam | Alerta de `catalog_view_lag_seconds`; divergências caem em `409 price-changed` |
| Broker indisponível | Criação, consulta e transições | Notificações a parceiros e eventos ao ERP atrasam | Alerta de `outbox_pending_count`; recuperação automática |
| Endpoint de parceiro fora | Tudo para os demais parceiros | Notificações desse parceiro | Circuito aberto; parceiro pode reconciliar por `updatedSince` |
| Pedidos saturado | Requisições dentro da capacidade | Excedente recusado | `503` com `Retry-After`, priorizando criação e consulta por id sobre listagem |
| IdP indisponível | Requisições com tokens já emitidos | Novos logins e novos tokens de parceiros | Erros no IdP, fora da plataforma |
| Provedor de LLM indisponível | Toda a operação de pedidos | Assistente Operacional | Mensagem ao operador |

**Controles operacionais** (feature flags, acionáveis pela operação sem deploy):

- desligar a contingência síncrona ao Catálogo, recusando de imediato pedidos com SKUs ausentes;
- pausar e retomar entregas de um parceiro específico;
- reduzir temporariamente o limite de concorrência da listagem;
- desligar o Assistente Operacional.

## 9. Os mesmos padrões nos diagramas de sequência

| Padrão | Onde aparece | Diagrama |
|---|---|---|
| Timeout e circuit breaker do Catálogo | Bloco "algum SKU ausente no read model" | `seq-criacao-de-pedido` |
| Degradação controlada | Resposta `503 catalog-unavailable` e nota de degradação | `seq-criacao-de-pedido` |
| Retry seguro pelo cliente | Ramos de repetição da mesma `Idempotency-Key` | `seq-criacao-de-pedido` |
| Desacoplamento por outbox | Seção "Publicação assíncrona" | Ambos |
| Retry com backoff e jitter | Loop de tentativas de entrega | `seq-atualizacao-e-notificacao-de-status` |
| Circuit breaker por parceiro e bulkhead | Ramo "circuit breaker do parceiro aberto" e nota sobre recursos isolados | `seq-atualizacao-e-notificacao-de-status` |
| Recuperação por reconciliação | Seção "Reconciliação pelo parceiro" | `seq-atualizacao-e-notificacao-de-status` |

## 10. Como verificar

| Verificação | Ferramenta | Cenários |
|---|---|---|
| Latência e falhas injetadas no Catálogo e no parceiro em testes de integração | Testcontainers com Toxiproxy ou WireMock com atraso configurado | QA-DIS-01, QA-DIS-03 |
| Broker indisponível durante criação de pedidos | Testcontainers (parar o contêiner do Kafka) | QA-DIS-02 |
| Comportamento sob carga com dependência degradada | k6 ou Gatling em homologação | QA-ESC-05, QA-DES-01 |
| Failover de banco e perda de região | Exercício programado em homologação | QA-DIS-04, QA-DIS-06 |
| Configuração presente para toda dependência remota | Teste de arquitetura (ArchUnit): todo cliente HTTP é criado pela fábrica que exige timeout | — |
