# ADR-005: Idempotência na criação de pedido

- **Status:** Proposto
- **Data:** 2026-10-02
- **Decisores:** Arquitetura, time de Pedidos
- **Relacionados:** ADR-003, ADR-006, ADR-007; PoC em `poc/`

## Contexto

Clientes e integrações repetem a criação de pedido em caso de timeout ou erro de rede, e não há chave de idempotência. Uma repetição após um timeout em que o servidor já tinha gravado o pedido gera pedido duplicado. Com app móvel (redes instáveis) e parceiros (retries automáticos), o problema cresce junto com o volume.

O critério crítico do desafio exige que **chamadas repetidas com a mesma chave não criem pedidos duplicados**, inclusive sob concorrência.

## Drivers

- Zero duplicidade para requisições com a mesma chave, sequenciais ou simultâneas.
- Compatibilidade: consumidores v1 não enviam chave hoje e não podem quebrar (P-CTR-04).
- Não introduzir nova infraestrutura no caminho crítico.
- Latência dentro do SLO de criação (P-SLO-02).

## Alternativas consideradas

**A. Registro de idempotência em armazenamento externo (Redis, `SET NX`).**
Prós: rápido; TTL nativo. Contras: o registro e o pedido ficam em sistemas diferentes, sem atomicidade; uma falha entre as duas escritas gera chave "presa" ou duplicidade; adiciona dependência no caminho crítico (C-03).

**B. Registro de idempotência na mesma transação do pedido, no banco de Pedidos.**
Prós: atomicidade entre chave, pedido e evento (outbox); sem nova infraestrutura; a restrição de unicidade do banco resolve a concorrência. Contras: carga adicional no banco (cerca de 1 GB de registros ativos, C-05); exige que a transação seja curta.

**C. Deduplicação apenas por chave de negócio** (`externalReference` do parceiro).
Prós: natural para parceiros. Contras: clientes web e app não têm chave de negócio própria; não cobre o caso geral.

## Decisão

Adotar a **alternativa B**, complementada pela **alternativa C** para parceiros.

**Contrato**
- Header `Idempotency-Key` (UUID ou string de até 64 caracteres). **Obrigatório na v2**, **opcional na v1**: se um consumidor v1 enviar o header, recebe a mesma garantia; se não enviar, o comportamento atual é mantido.
- Escopo da chave: par (identidade do chamador extraída do token, chave). Chaves iguais de chamadores diferentes não colidem.
- Validade: 24 horas. Depois disso, a chave pode ser reutilizada e cria novo pedido.

**Algoritmo**
0. Antes de precificar, ler o registro válido da chave, se existir, e responder diretamente (repetição ou `422`). Sem essa leitura, uma repetição feita depois de uma mudança de preço receberia `409 price-changed` em vez da resposta original. A leitura é apenas um atalho; a garantia sob concorrência continua sendo a restrição única do passo 2.
1. Calcular `requestHash`: SHA-256 da **operação lógica** (o `operationId` do contrato, de modo que `/orders` e `/v1/orders` sejam a mesma operação) e do corpo **desserializado no DTO de entrada**, serializado com chaves ordenadas e sem espaços. Campos desconhecidos, que a API ignora, não alteram o hash.
2. Em uma única transação: remover o registro **vencido** da mesma chave, se houver, e inserir em `idempotency_record (caller_id, idem_key, request_hash, order_id, response_status, response_body, expires_at)`, com chave única `(caller_id, idem_key)`, junto com o pedido, os itens com snapshot e o evento no outbox.
3. Se a inserção violar a unicidade, desfazer a transação e ler o registro existente em uma nova leitura:
   - mesmo `requestHash`: devolver a resposta armazenada (mesmo status e corpo) com header `Idempotent-Replayed: true`;
   - `requestHash` diferente: `422 Unprocessable Entity` (chave reutilizada com outro conteúdo).

**Concorrência:** duas requisições simultâneas com a mesma chave competem pela mesma linha única. A segunda fica bloqueada até a primeira confirmar ou desfazer a transação, e então segue o passo 3. Para não segurar conexões indefinidamente, `lock_timeout` de 2 s; ao expirar, a resposta é `409 Conflict` com `Retry-After: 1`.

A transação é curta porque não há chamadas de rede dentro dela: o catálogo é lido do read model local (ADR-003) e o evento vai para o outbox (ADR-006). Na fase de 30 dias, antes do read model, as chamadas ao Catálogo são feitas **antes** de abrir a transação, e só o resultado entra nela. Nessa fase, repetições simultâneas podem gerar chamadas duplicadas ao Catálogo, mas nunca pedidos duplicados.

**Parceiros:** restrição única adicional `(partner_id, external_reference)`. Mesmo que o parceiro gere chaves de idempotência diferentes para o mesmo pedido, não haverá duplicidade; a resposta nesse caso é `409` com o identificador do pedido existente.

**Limpeza:** job remove registros com `expires_at` vencido, em lotes, fora do pico.

## Consequências

**Positivas**
- Garantia atômica de no máximo um pedido por chave, sem nova infraestrutura.
- O mesmo mecanismo serve web, app, parceiros e consumidores v1 que adotarem o header.
- Respostas repetidas são idênticas, simplificando a lógica dos clientes.

**Negativas e riscos**
- Consumidores v1 que não enviarem o header continuam sujeitos a duplicidade até migrarem (risco aceito, com prazo pelo ADR-007). O frontend web é migrado para enviar o header já na fase de 30 dias.
- Armazenar o corpo da resposta aumenta o tamanho do registro; limitado pela resposta de criação, que é pequena.
- Uma chave expirada reutilizada após 24 h cria novo pedido; documentado no contrato.

## Validação

Testes automatizados da PoC (executados por `./mvnw verify`):

| Cenário | Resultado esperado |
|---|---|
| Mesma chave e mesmo corpo, enviados duas vezes em sequência | Um pedido; segunda resposta idêntica com `Idempotent-Replayed: true` |
| Mesma chave e mesmo corpo, 20 requisições simultâneas | Exatamente um pedido e um evento no outbox; demais respostas idênticas ou `409` |
| Mesma chave com corpo diferente | `422`; nenhum pedido novo |
| Mesma chave usada por dois chamadores diferentes | Dois pedidos distintos |
| v2 sem header | `400` com erro descritivo |
| v1 sem header | Comportamento atual preservado (`201`) |
| Parceiro com chaves diferentes e mesma `externalReference` | Um pedido; segunda resposta `409` |

Em produção: métrica de respostas com `Idempotent-Replayed` por canal e alerta para qualquer par `(partner_id, external_reference)` duplicado em reconciliação.
