# ADR-003: Read model local de Catálogo em Pedidos

- **Status:** Proposto
- **Data:** 2026-10-02
- **Decisores:** Arquitetura, times de Pedidos e Catálogo
- **Relacionados:** ADR-002, ADR-004, ADR-006

## Contexto

Na criação de pedido, o serviço de Pedidos chama o Catálogo de forma síncrona uma vez por item. Os cálculos de premissas mostram três efeitos:

- **Latência (C-01):** um pedido de 300 itens gasta cerca de 4,5 s em chamadas, contra a meta de P95 ≤ 1,2 s para esse tamanho.
- **Carga (C-02):** no pico alvo, o Catálogo receberia cerca de 1.000 req/s apenas da criação de pedidos, herdando os picos de Pedidos.
- **Disponibilidade (C-03):** cada dependência síncrona no caminho crítico reduz a disponibilidade composta; com três delas, o orçamento de erro mensal é ultrapassado em cerca de três vezes.

## Drivers

- P95 ≤ 500 ms para pedidos de até 50 itens (P-SLO-02).
- Disponibilidade de 99,9% da criação de pedido sem depender da disponibilidade do Catálogo.
- Desacoplamento entre células e a região do Catálogo (ADR-002).
- Atraso aceitável de propagação de preço: P95 ≤ 5 s (P-SLO-05).

## Alternativas consideradas

**A. Endpoint em lote no Catálogo** (uma chamada para todos os SKUs do pedido).
Prós: mudança pequena; consistência forte com o preço vigente. Contras: o Catálogo continua no caminho crítico (C-03 não melhora); entre células, a chamada cruzaria regiões, somando latência de rede internacional.

**B. Cache na frente das chamadas ao Catálogo.**
Prós: reduz a carga em regime normal. Contras: falhas de cache (frio, expiração, rajada de SKUs novos) devolvem o problema exatamente nos picos; invalidação de preço é difícil de acertar; continua havendo dependência síncrona.

**C. Read model local em Pedidos, alimentado por eventos do Catálogo.**
Prós: zero chamadas síncronas na criação; latência de leitura local (sub-milissegundo por item em consulta em lote); Catálogo fora do caminho crítico. Contras: consistência eventual; necessidade de carga inicial e de reconciliação; mais dados armazenados em Pedidos (cerca de 500 MB por país, C-06).

**D. CDC direto do banco do Catálogo para Pedidos.**
Prós: não exige alteração no código do Catálogo. Contras: acopla Pedidos ao esquema interno do Catálogo, violando o ownership; qualquer refatoração no Catálogo quebra Pedidos.

## Decisão

Adotar a **alternativa C**, com a alternativa A apenas como caminho de contingência.

- O Catálogo publica `CatalogItemChanged` via outbox (ADR-006) a cada mudança de preço, descrição ou disponibilidade de venda, contendo `sku`, `country`, `price`, `currency`, `description`, `sellable` e `catalogVersion`.
- Pedidos mantém a tabela `catalog_item_view` por célula, atualizada por um consumidor que aplica o evento apenas se `catalogVersion` for maior que a versão armazenada (descarte de eventos fora de ordem e duplicados).
- **Carga inicial:** endpoint paginado de exportação no Catálogo, usado uma vez por célula e sempre que o read model precisar ser reconstruído.
- **Reconciliação:** job diário compara, por faixa de SKU, um checksum de `(sku, catalogVersion)` entre Catálogo e read model e reprocessa as faixas divergentes.
- **Validação de preço:** o pedido usa o preço do read model. Se o cliente enviar um preço esperado e houver divergência acima da tolerância, a criação retorna `409` com o preço atual (P-DOM-05).
- **SKU ausente no read model** (produto recém-criado ainda não propagado): chamada em lote ao Catálogo com timeout de 200 ms e circuit breaker. Se o Catálogo informar que o SKU não existe, a resposta é `422`. Se o Catálogo estiver indisponível (timeout, erro ou circuit breaker aberto), a resposta é `503` com `Retry-After`. Em nenhum caso a requisição fica bloqueada aguardando o Catálogo, e pedidos com SKUs já presentes no read model não são afetados.

## Consequências

**Positivas**
- Criação de pedido sem chamadas de rede ao Catálogo; latência independente do número de itens, exceto pela escrita no banco.
- Falha do Catálogo deixa de causar falha de criação de pedidos (degrada apenas a propagação de mudanças).
- Pré-requisito natural para a célula do País B operar de forma independente.

**Negativas e riscos**
- Janela de inconsistência: durante alguns segundos um pedido pode usar o preço anterior. Mitigação: tolerância de preço e snapshot com `catalogVersion` para auditoria (ADR-004).
- Novo componente a monitorar: atraso do consumidor e divergências da reconciliação precisam de alerta.
- O Catálogo passa a ter responsabilidade de publicar eventos com contrato estável (evolução regida pelo ADR-007).

## Validação

- Métrica `catalog_view_lag_seconds` (diferença entre `occurredAt` do evento e aplicação) com alerta se P95 > 5 s por 10 minutos.
- Teste de carga: criação de pedidos de 50 e de 300 itens a 260 pedidos/s com P95 dentro do SLO.
- Teste de resiliência: Catálogo desligado em ambiente de teste; criação de pedidos com SKUs conhecidos mantém taxa de sucesso e latência.
- Reconciliação diária com zero divergências persistentes por mais de 24 h.
