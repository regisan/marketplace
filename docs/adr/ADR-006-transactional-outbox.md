# ADR-006: Transactional outbox para publicação de eventos

- **Status:** Proposto
- **Data:** 2026-10-02
- **Decisores:** Arquitetura, times de Pedidos e Catálogo
- **Relacionados:** ADR-003, ADR-005, ADR-008

## Contexto

Pedidos grava no banco e, em seguida, publica o evento no broker em uma chamada separada (dual write, P-ASIS-04). Se o processo cair entre as duas operações, o pedido existe sem evento; se a publicação acontecer e a transação for desfeita, existe evento sem pedido. Com parceiros recebendo notificações (ADR-008) e o read model de catálogo dependendo de eventos (ADR-003), perder ou inventar eventos passa a ter impacto direto em clientes externos.

Volume esperado no alvo: cerca de 4 milhões de eventos de pedido por dia, com pico de cerca de 800 por segundo (P-VOL-09).

## Drivers

- Nenhum evento perdido e nenhum evento sem fato correspondente no banco.
- Ordem por pedido preservada para os consumidores.
- Simplicidade operacional na fase de 30 dias.
- Capacidade de atender o pico alvo com folga.

## Alternativas consideradas

**A. Manter o dual write com retry na publicação.**
Prós: nenhuma mudança estrutural. Contras: não resolve a queda do processo entre as duas escritas; inconsistência continua possível.

**B. Outbox com publicador por polling.**
Prós: atomicidade pela própria transação do banco; implementação simples, sem nova infraestrutura; fácil de testar. Contras: consultas periódicas ao banco; latência adicional de publicação (intervalo de polling).

**C. Outbox com CDC (Debezium lendo o log do banco).**
Prós: latência baixa e nenhuma consulta periódica. Contras: novo componente (Kafka Connect, Debezium) para operar e monitorar; configuração de replicação lógica no banco gerenciado; prazo de 30 dias apertado para colocar isso em produção com segurança.

**D. Event sourcing.**
Prós: o evento é a fonte de verdade. Contras: mudança profunda de modelo de persistência e de consultas; desproporcional ao problema.

## Decisão

Adotar a **alternativa B** agora, com **migração para C** se os gatilhos abaixo forem atingidos. A tabela de outbox é a mesma nas duas alternativas, então a troca não afeta produtores nem consumidores.

**Produção do evento**
- Tabela `outbox_event (event_id, aggregate_type, aggregate_id, aggregate_version, event_type, payload, occurred_at, published_at)`.
- A gravação no outbox acontece na mesma transação da mudança de estado (criação do pedido, transição de status, mudança no Catálogo).
- `event_id` é UUIDv7, gerado na aplicação.

**Publicação**
- Publicador em cada instância busca lotes de até 500 eventos com `SELECT ... FOR UPDATE SKIP LOCKED`, ordenados por `occurred_at`, publica no broker e marca `published_at`.
- Intervalo de polling de 100 ms quando há eventos e recuo até 1 s quando a tabela está vazia.
- Chave de partição no broker = `aggregate_id`, garantindo ordem por pedido (ou por SKU, no Catálogo).
- Eventos publicados são removidos após 7 dias, permitindo republicação manual em incidentes.

**Garantia de entrega:** pelo menos uma vez (*at-least-once*). Duplicatas são possíveis, por exemplo, se o publicador cair após publicar e antes de marcar.

**Obrigações dos consumidores**
- Deduplicar por `event_id` (tabela `processed_event` com TTL de 7 dias, na mesma transação do efeito).
- Ignorar eventos com `aggregate_version` menor ou igual à última versão aplicada.
- Após 5 falhas de processamento, enviar para DLQ com alerta; reprocessamento manual pelo runbook.

**Gatilhos para migrar para CDC:** atraso de publicação com P95 acima de 2 s por mais de 15 minutos em pico, ou vazão sustentada acima de 2 mil eventos por segundo por célula.

## Consequências

**Positivas**
- Consistência entre estado e eventos garantida pelo banco.
- Nenhuma infraestrutura nova na fase de 30 dias.
- Mesmo padrão aplicado a Pedidos e Catálogo, reduzindo variação entre times.

**Negativas e riscos**
- Carga adicional de escrita e leitura no banco de Pedidos. Mitigação: índice parcial em `published_at IS NULL` e limpeza periódica.
- Latência de publicação de até algumas centenas de milissegundos, compatível com o SLO de notificação (P-SLO-04).
- Todos os consumidores precisam implementar deduplicação; regra verificada em revisão e por testes.

## Validação

- Teste de integração: falha simulada após o commit e antes da publicação; ao reiniciar, o evento é publicado uma vez.
- Teste de integração: transação desfeita não deixa evento no outbox.
- Teste do consumidor: o mesmo evento entregue duas vezes produz um único efeito.
- Métricas `outbox_pending_count` e `outbox_publish_lag_seconds` com alerta se o atraso P95 passar de 2 s.
