# ADR-008: Notificação a parceiros por webhooks assinados com reconciliação

- **Status:** Proposto
- **Data:** 2026-10-02
- **Decisores:** Arquitetura, time de Integração com Parceiros, Segurança
- **Relacionados:** ADR-001, ADR-002, ADR-006, ADR-007

## Contexto

Parceiros de marketplace pedem notificações assíncronas de mudança de status. No alvo, cerca de 30% dos pedidos vêm de parceiros, o que gera em torno de 1,2 milhão de notificações por dia (P-VOL-10), com meta de entrega de 99,9% em até 30 s no P95 (P-SLO-04).

Os endpoints dos parceiros estão fora do nosso controle: podem ficar lentos, indisponíveis ou responder com erro por horas. Isso não pode afetar a criação de pedidos nem a notificação de outros parceiros.

## Drivers

- Isolamento de falhas entre parceiros e em relação ao núcleo de Pedidos.
- Autenticidade e integridade das notificações.
- Nenhum dado pessoal enviado a terceiros sem necessidade (LGPD, ADR-002).
- Capacidade de o parceiro recuperar notificações perdidas.

## Alternativas consideradas

**A. Somente polling pelos parceiros.**
Prós: nenhuma infraestrutura de saída. Contras: não atende o pedido dos parceiros; multiplica a carga de leitura (P-VOL-05); latência de descoberta depende do intervalo de polling de cada parceiro.

**B. Webhooks HTTP assinados.**
Prós: padrão de mercado, simples para o parceiro implementar; entrega ativa. Contras: exige retry, controle de falhas e proteção do lado emissor.

**C. Acesso direto dos parceiros a tópicos do broker.**
Prós: alta vazão. Contras: expõe infraestrutura interna a terceiros; acopla parceiros à tecnologia de mensageria; gestão de credenciais, ACLs e residência de dados muito mais complexa.

**D. WebSocket ou Server-Sent Events.**
Prós: baixa latência. Contras: exige conexão permanente do parceiro, inadequado para integrações de servidor a servidor com disponibilidade variável.

## Decisão

Adotar a **alternativa B**, combinada com endpoints de consulta para reconciliação. A entrega é responsabilidade do serviço **Integração com Parceiros** (ADR-001), que roda em cada célula (ADR-002).

**Conteúdo da notificação (payload "fino")**
- Campos: `eventId`, `eventType` (`order.status_changed`), `occurredAt`, `orderId`, `externalReference`, `status`, `orderVersion`.
- Sem dados pessoais. Para detalhes, o parceiro consulta `GET /v2/orders/{id}`, sujeito à mesma autorização da API.

**Segurança**
- Header `Webhook-Signature` com HMAC-SHA256 sobre `timestamp + "." + corpo`, e header `Webhook-Timestamp`. Parceiros rejeitam mensagens com mais de 5 minutos, evitando *replay*.
- Rotação de segredo com dois segredos válidos simultaneamente durante a troca.
- URLs cadastradas somente em HTTPS; resolução de DNS validada a cada entrega, bloqueando IPs privados, de loopback e de metadados de nuvem (proteção contra SSRF).

**Entrega**
- Garantia de pelo menos uma vez; o parceiro deduplica por `eventId`.
- Ordem não garantida entre entregas; o parceiro usa `orderVersion` para descartar notificações antigas.
- Timeout de 5 s por tentativa. Sucesso apenas com resposta 2xx.
- Retry com backoff exponencial e *jitter* (aproximadamente 10 s, 30 s, 2 min, 10 min, 1 h, repetindo a cada 2 h) por até 24 h. Depois disso, a entrega vai para a DLQ do parceiro, com alerta e reenvio manual pelo backoffice.

**Isolamento (bulkhead)**
- Fila e pool de envio separados por parceiro, com limite de concorrência por parceiro.
- Circuit breaker por endpoint: após falhas consecutivas, as entregas daquele parceiro ficam em espera e são retomadas por sondagem periódica, sem consumir recursos dos demais.
- Falhas de entrega nunca retornam a Pedidos; Pedidos apenas publica eventos (ADR-006).

**Reconciliação**
- `GET /v2/orders?updatedSince={timestamp}&cursor=...` permite ao parceiro recuperar todas as mudanças de um período, independentemente dos webhooks.
- Recomendação documentada aos parceiros: executar reconciliação periódica (por exemplo, a cada hora).

**Contrato:** o webhook é documentado na especificação OpenAPI 3.1 da v2 (seção `webhooks`); o evento interno de origem, `OrderStatusChanged`, é documentado em AsyncAPI.

## Consequências

**Positivas**
- Parceiros recebem notificações sem polling e podem se recuperar de qualquer perda pela reconciliação.
- Um parceiro com problemas não afeta os demais nem a criação de pedidos.
- Nenhum dado pessoal trafega para terceiros pelo webhook.

**Negativas e riscos**
- Novo serviço a operar, com fila por parceiro e DLQ.
- O payload fino gera consultas adicionais à API; dimensionado dentro da razão de leitura (P-VOL-05) e limitado por quotas.
- Parceiros precisam implementar validação de assinatura e deduplicação; mitigado por documentação, exemplos de código e ambiente de testes.

## Validação

- Teste de integração: endpoint de parceiro respondendo com erro por 10 minutos; entregas a outros parceiros mantêm o P95 dentro do SLO.
- Teste de segurança: cadastro de URL apontando para IP privado ou endereço de metadados é rejeitado.
- Teste de contrato: exemplo de verificação de assinatura incluído na documentação é executado no CI contra o emissor real.
- Métricas por parceiro: taxa de sucesso, latência de entrega, tamanho da fila e da DLQ, estado do circuit breaker.
