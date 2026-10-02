# Contratos da plataforma de Pedidos

Estes arquivos são os **contratos oficiais da arquitetura-alvo**, não apenas documentação da PoC. Ficam junto da PoC por conveniência de build: os testes de contrato e de compatibilidade os usam diretamente. Qualquer mudança passa pelas validações do CI descritas abaixo (ADR-007).

## Índice

| Arquivo | Versão | Descrição | ADRs |
|---|---|---|---|
| `openapi/baseline/orders-v1.0.0.yaml` | 1.0.0 | Contrato AS-IS reconstruído (P-CTR-01, P-CTR-02). Imutável; linha de base de compatibilidade | ADR-007 |
| `openapi/orders-v1.yaml` | 1.1.0 | v1 evoluída apenas com mudanças aditivas: `Idempotency-Key` opcional | ADR-005, ADR-007 |
| `openapi/orders-v2.yaml` | 2.0.0 | API pública: criação, consulta, listagem para reconciliação e webhook `orderStatusChanged` | ADR-003 a ADR-005, ADR-007, ADR-008 |
| `asyncapi/order-events.yaml` | 1.0.0 | Eventos `OrderCreated` e `OrderStatusChanged` no broker de cada célula | ADR-006, ADR-007 |
| `.spectral.yaml` | — | Regras comuns, incluindo proibição de dados pessoais em eventos e webhooks | ADR-002, ADR-008 |
| `.spectral-v2.yaml` | — | Regras da v2: `Idempotency-Key` obrigatória em POST e erros em Problem Details | ADR-005, ADR-007 |

## Onde cada decisão aparece no contrato

| Decisão | Evidência no contrato |
|---|---|
| Idempotência (ADR-005) | Header `Idempotency-Key` obrigatório na v2 e opcional na v1; header de resposta `Idempotent-Replayed`; respostas `409 request-in-progress` e `422 idempotency-key-reuse` |
| Snapshot (ADR-004) | Objeto `ItemSnapshot` na v2 com `unitPrice`, `catalogVersion`, `capturedAt` e `snapshotSource`; na v1, `price` e `description` passam a vir do snapshot sem mudar de formato |
| Read model e contingência (ADR-003) | Campo opcional `expectedTotal`, `409 price-changed` com preços atuais, `422 unknown-sku` e `503 catalog-unavailable` com `Retry-After` |
| Outbox e entrega pelo menos uma vez (ADR-006) | `eventId` para deduplicação e `orderVersion` para ordenação, nos eventos e no webhook |
| Versionamento (ADR-007) | v1 e v2 em arquivos separados; `legacyId` na v2; enums documentados como extensíveis; erros RFC 9457 na v2 |
| Webhooks (ADR-008) | Seção `webhooks` da v2 com headers de assinatura HMAC, payload sem PII, política de retry e `GET /orders?updatedSince=` para reconciliação |
| Residência e LGPD (ADR-002) | Servidores por célula; campos pessoais marcados com `x-pii: true`; regra que impede `x-pii` em eventos e webhooks |
| Isolamento entre parceiros (FC2) | `404` para pedidos de outro chamador; listagem restrita ao chamador; escopos OAuth2 `orders:read` e `orders:write` |

## Evolução da v1 sem quebra

A v1 1.1.0 difere da 1.0.0 apenas por adições:

- novo header de requisição `Idempotency-Key`, **opcional**;
- novo header de resposta `Idempotent-Replayed`;
- novas respostas `409` e `422`, que só ocorrem quando o consumidor envia o novo header;
- descrições atualizadas.

Um consumidor escrito para a 1.0.0 nunca envia o header, então nunca recebe as respostas novas e continua funcionando sem alteração. Isso é verificado de duas formas: estaticamente, pelo `oasdiff` comparando com a linha de base, e em execução, pelo teste de consumidor da PoC.

## Validação

Executado no CI a cada pull request que altere esta pasta. Para rodar localmente (Node.js 18+ e [oasdiff](https://github.com/oasdiff/oasdiff)):

```bash
cd poc/docs

# Lint das especificações
npx @stoplight/spectral-cli lint openapi/baseline/orders-v1.0.0.yaml openapi/orders-v1.yaml asyncapi/order-events.yaml \
  -r .spectral.yaml --fail-severity error
npx @stoplight/spectral-cli lint openapi/orders-v2.yaml -r .spectral-v2.yaml --fail-severity error

# Validação estrutural do AsyncAPI
npx @asyncapi/cli validate asyncapi/order-events.yaml

# Compatibilidade: a v1 atual não pode quebrar a linha de base
oasdiff breaking openapi/baseline/orders-v1.0.0.yaml openapi/orders-v1.yaml --fail-on ERR
```

No CI, além da comparação com a linha de base, o `oasdiff` compara cada especificação alterada com a versão em `main`, bloqueando quebras introduzidas por qualquer pull request.

## Fora destes contratos

- **Transições de status** (`POST /internal/orders/{id}/transitions`): API interna, usada por backoffice e logística, descrita no diagrama de sequência de status. Não é exposta a parceiros.
- **Eventos do Catálogo** (`CatalogItemChanged`): contrato do contexto de Catálogo, consumido pelo read model (ADR-003). Fica fora do escopo da PoC.
- **Cadastro de assinaturas de webhook**: feito pela operação na primeira fase; uma API de autoatendimento para parceiros é evolução futura.
