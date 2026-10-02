# PoC de Pedidos: idempotência e compatibilidade de contrato

Fatia executável da proposta de arquitetura. Ela prova o critério crítico do desafio:

> Chamadas repetidas com a mesma chave não podem criar pedidos duplicados, e a evolução de contrato não pode quebrar um consumidor atual demonstrado no teste.

## O que a PoC prova

1. **Idempotência na criação de pedido**, inclusive com 20 requisições simultâneas ([ADR-005](../docs/adr/ADR-005-idempotencia-na-criacao-de-pedido.md)). A chave é registrada na **mesma transação** do pedido, dos itens com snapshot e do evento no outbox. A restrição de unicidade do PostgreSQL resolve a concorrência, sem nenhuma infraestrutura nova.
2. **Compatibilidade da v1 evoluída** com um consumidor escrito para o contrato atual, com v1 e v2 como adaptadores do mesmo domínio ([ADR-007](../docs/adr/ADR-007-versionamento-de-api-e-compatibilidade.md)). O `ConsumidorV1` conhece apenas a [linha de base 1.0.0](docs/openapi/baseline/orders-v1.0.0.yaml) e cria e consulta pedidos sem alteração.

De forma mínima, porque fazem parte da transação que a idempotência protege:

- **Snapshot do item** ([ADR-004](../docs/adr/ADR-004-snapshot-de-item-do-pedido.md)): preço, moeda, descrição e versão de catálogo gravados na criação e imutáveis (um trigger rejeita `UPDATE`).
- **Outbox** ([ADR-006](../docs/adr/ADR-006-transactional-outbox.md)): `OrderCreated` gravado em `outbox_event`, conforme o [contrato de eventos](docs/asyncapi/order-events.yaml) e sem dados do comprador.

## Como executar

Requisitos: **JDK 21** e **Docker** em execução. Não é preciso instalar o Maven: o wrapper baixa a versão certa.

```bash
cd poc
./mvnw verify
```

O comando compila, roda os testes unitários (Surefire) e os de integração (Failsafe) contra PostgreSQL 16 em Testcontainers, e valida as respostas contra os contratos OpenAPI 3.1 e AsyncAPI. Leva cerca de 1 minuto depois do primeiro download de dependências e imagens.

### Execução manual (opcional)

```bash
cd poc
docker compose up -d --wait
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

A aplicação sobe em `http://localhost:8080` com o catálogo sintético carregado. O arquivo [`requests.http`](requests.http) tem exemplos de chamadas (REST Client do VS Code ou HTTP Client do IntelliJ): criação, repetição com a mesma chave, chave com outro corpo, v1 sem e com header e consulta cruzada v2 → v1. Para encerrar: `docker compose down -v`.

### CI

[`.github/workflows/poc.yml`](../.github/workflows/poc.yml) roda `./mvnw -B verify` a cada mudança em `poc/**` e publica os relatórios de teste. Em pull requests, roda também revisão de dependências e CodeQL (FF-16). As validações estáticas dos contratos (Spectral, AsyncAPI CLI, `oasdiff`) ficam em `governanca.yml` (FF-01 a FF-06).

## Mapa teste → cenário

Cenários em [`02-atributos-de-qualidade.md`](../docs/02-atributos-de-qualidade.md), fitness functions em [`fitness-functions.md`](../docs/governanca/fitness-functions.md) e ameaças em [`04-threat-model.md`](../docs/04-threat-model.md).

| Teste | Caso | Cenário |
|---|---|---|
| `IdempotenciaIT` | Mesma chave e corpo em sequência: 1 pedido, 1 evento; repetição idêntica com `Idempotent-Replayed: true` | QA-INT-01, FF-14 |
| | 20 requisições simultâneas, comprovadamente bloqueadas na mesma linha do banco: 1 pedido, 1 evento | QA-INT-02, FF-14 |
| | Chave presa além do `lock_timeout`: as 20 recebem `409 request-in-progress` com `Retry-After: 1` | QA-INT-02 |
| | 20 requisições simultâneas sem portão, 5 repetições | QA-INT-02 |
| | Mesma chave com corpo diferente: `422`, nenhum pedido novo | QA-INT-03, FF-14 |
| | Campos ignorados (`status`, `unitPrice`) não alteram o conteúdo da requisição | ADR-005, API-03 |
| | Mesma chave, chamadores diferentes: 2 pedidos | ADR-005 |
| | v2 sem `Idempotency-Key`: `400` | ADR-005 |
| | Chave vencida: novo pedido | ADR-005 |
| | Parceiro com chaves diferentes e mesma `externalReference`: `409` com `existingOrderId` | QA-INT-06 |
| `CompatibilidadeV1IT` | `ConsumidorV1` (só a 1.0.0, leitura estrita) cria e consulta sem header; respostas válidas na linha de base | QA-COM-01, FF-13 |
| | Sem header, nenhum registro de idempotência; erro no formato da 1.0.0 | QA-COM-01 |
| | Pedido da v2 consultado pela v1 (`/orders` e `/v1/orders`) pelo `legacyId` | QA-COM-03, FF-13 |
| | v1 com `Idempotency-Key` repetida: 1 pedido; `/orders` e `/v1/orders` são a mesma operação | ADR-005, ADR-007 |
| | Mesma chave na v1 e na v2 não é repetição; reúso na v1 dá `422` | ADR-005, ADR-007 |
| `ContratoV2IT` | `201`, `200`, `400`, `401`, `404`, `409 price-changed`, `422 unknown-sku` e `422 idempotency-key-reuse` válidos em `orders-v2.yaml` | ADR-007 |
| `SnapshotIT` | Depois de mudar preço e descrição no read model, o pedido mantém os valores na v1 e na v2 | QA-AUD-01 |
| `EventoIT` | `OrderCreated` válido segundo `order-events.yaml`, sem nome, e-mail ou `customerId`; o validador rejeita payloads inválidos | QA-PRI-02, FF-15 |
| `SegurancaIT` | Sem token ou com token desconhecido: `401` em v1 e v2 | Autenticação |
| | Pedido de outro chamador: `404` na v2 e na v1, igual a inexistente | API-01, API-02 |
| | `status`, `price` e `unitPrice` no corpo não alteram o pedido | API-03 |
| `ArquiteturaTest` | `domain` sem Spring, JDBC nem adaptadores; `application` sem adaptadores; `web.v1` e `web.v2` independentes | FF-11 |
| | Clientes HTTP só pela fábrica com timeout | FF-12 |
| `SchemaIT` | Catálogo sintético; unicidade parcial de `externalReference`; imutabilidade de `order_items` | P-POC-03, ADR-004, ADR-005 |
| `OrderTest`, `UuidV7Test`, `RequestHasherTest` | Total em decimal exato, snapshot, tolerância de preço, UUIDv7 e canonização do hash | Unitários |
| `ContractValidationSpikeTest` | O validador aplica a semântica OpenAPI 3.1 usada nos contratos | Decisão D-3 das notas |

### Como o QA-INT-02 garante simultaneidade no banco

Uma barreira no cliente só alinha o envio. Rede, threads e pool de conexões podem serializar as requisições antes do banco, e o teste passaria sem ter havido disputa. Por isso, o teste:

1. abre uma transação própria que insere e **segura** a linha `(chamador, chave)` em `idempotency_record`, sem confirmar;
2. dispara as 20 requisições a partir de uma barreira;
3. consulta `pg_stat_activity` até ver **20 transações** bloqueadas (`wait_event = 'transactionid'`) no `INSERT INTO idempotency_record`, a prova de que as 20 estão dentro do PostgreSQL disputando a mesma linha única;
4. faz `ROLLBACK`: as 20 disputam de novo, uma grava e as outras 19 recebem a resposta original.

O pool de conexões do perfil de teste tem 30 conexões, para que nenhuma requisição fique retida no pool. O portão é só código de teste: não há gancho no código de produção.

## Estrutura

```
src/main/java/com/exemplo/pedidos/
  domain/                    Order, OrderItem, ItemSnapshot, Money, OrderCreated, UuidV7; sem Spring nem JDBC
  application/               CreateOrderUseCase, GetOrderUseCase, IdempotencyService e portas
  adapters/in/web/v1/        API v1 (/orders e /v1/orders)
  adapters/in/web/v2/        API v2 (/v2/orders) e Problem Details
  adapters/in/web/security/  autenticação simplificada por tokens sintéticos
  adapters/out/persistence/  JdbcClient com SQL explícito
src/main/resources/db/
  migration/                 schema (Flyway)
  testdata/                  1.000 SKUs sintéticos, só nos perfis test e local
src/test/java/com/exemplo/pedidos/
  consumidor/ConsumidorV1    consumidor atual, escrito só contra a 1.0.0
  support/                   Testcontainers, cliente HTTP e validadores de contrato
```

## Simplificações e limites

A PoC prova as duas decisões e nada além delas. Fora do escopo: gateway, células e roteamento por país, Kafka e publicador do outbox, Integração com Parceiros e webhooks, read model alimentado por eventos e contingência ao Catálogo, listagem e reconciliação (`GET /v2/orders`), transições de status, Resilience4j, limpeza de registros de idempotência vencidos e Assistente Operacional.

- **Autenticação simulada.** Não há IdP nem validação de JWT. O filtro `SyntheticTokenFilter` resolve `Authorization: Bearer <token>` por uma tabela de tokens sintéticos (`pedidos.auth.tokens`), que existe só nos perfis `test` e `local`. Isso **substitui a validação real de JWT** e não deve ir para produção.
- **Catálogo local estático.** `catalog_item_view` é carregado por migração de dados sintéticos; mudanças de catálogo nos testes são feitas direto na tabela.
- **Dono do pedido é o chamador do token.** O `customerId` do corpo não é cruzado com a identidade do token.
- **Respostas idênticas são JSON semanticamente igual.** O registro de idempotência guarda a resposta em `jsonb`, que não preserva a ordem das chaves.
- **Pedido criado pela v1 lido pela v2.** A v2 exige `customer.name` e `customer.email`, que a v1 não coleta; essa direção fica fora dos testes (inconsistência I-1 das notas).

Decisões, desvios desta especificação e problemas encontrados estão em [`NOTAS-DE-IMPLEMENTACAO.md`](NOTAS-DE-IMPLEMENTACAO.md). As inconsistências entre especificação e contratos (I-1 a I-4) aparecem ali com a resolução adotada; os contratos em [`docs/`](docs/README.md) não foram alterados.
