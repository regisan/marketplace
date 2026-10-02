# PoC de Pedidos: idempotência e compatibilidade de contrato

Este arquivo orienta a implementação da fatia executável. Leia-o inteiro antes de planejar.

## 1. O que a PoC precisa provar

O critério crítico do desafio:

> Chamadas repetidas com a mesma chave não podem criar pedidos duplicados, e a evolução de contrato não pode quebrar um consumidor atual demonstrado no teste.

A PoC prova duas decisões, e nada além do necessário para elas:

1. **Idempotência** da criação de pedido, inclusive com requisições concorrentes (ADR-005).
2. **Compatibilidade** da v1 evoluída com um consumidor escrito para o contrato atual, com v1 e v2 como adaptadores do mesmo domínio (ADR-007).

Snapshot de item (ADR-004) e gravação no outbox (ADR-006) entram de forma mínima, porque fazem parte da mesma transação que a idempotência protege.

## 2. Fontes de verdade

Leia antes de escrever código. **Não altere** esses arquivos; se encontrar inconsistência, pare e relate.

| Arquivo | Uso |
|---|---|
| `docs/openapi/orders-v2.yaml` | Contrato da API v2 implementada |
| `docs/openapi/orders-v1.yaml` | Contrato da v1 1.1.0 implementada |
| `docs/openapi/baseline/orders-v1.0.0.yaml` | Contrato do consumidor atual; o teste de consumidor é escrito **somente** contra ele |
| `docs/asyncapi/order-events.yaml` | Formato do evento `OrderCreated` gravado no outbox |
| `../docs/adr/ADR-005-idempotencia-na-criacao-de-pedido.md` | Algoritmo de idempotência e tabela de cenários |
| `../docs/adr/ADR-007-versionamento-de-api-e-compatibilidade.md` | v1 e v2 sobre o mesmo domínio; `legacyId` |
| `../docs/adr/ADR-004-snapshot-de-item-do-pedido.md` | Campos do snapshot |
| `../docs/01-mapa-de-dominios.md` | Linguagem ubíqua: nomes em inglês no código seguem a tabela da seção 4 |
| `../docs/02-atributos-de-qualidade.md` | Cenários QA citados nos testes |
| `../docs/governanca/fitness-functions.md` | FF-11 a FF-16 são implementadas aqui |

## 3. Stack

| Item | Escolha |
|---|---|
| Linguagem | Java 21 |
| Framework | Spring Boot 4.x, última versão estável (a linha 3.x encerrou o suporte OSS em junho de 2026); Spring MVC, bloqueante |
| Build | Maven com wrapper (`./mvnw`); o `pom.xml` fica em `poc/` |
| Banco | PostgreSQL 16 |
| Acesso a dados | `JdbcClient` com SQL explícito; sem JPA, para controlar a transação e o tratamento de violação de unicidade |
| Migrações | Flyway |
| Testes | JUnit 5, AssertJ, Testcontainers (PostgreSQL), ArchUnit |
| Validação de contrato nos testes | Biblioteca com suporte a OpenAPI 3.1. Se nenhuma suportar 3.1 de forma confiável, validar as respostas contra os schemas JSON do contrato com um validador JSON Schema 2020-12 e registrar a decisão nas notas |

Use as versões gerenciadas pelo Spring Boot sempre que possível. **Não adicione dependências fora desta lista sem perguntar.**

## 4. Escopo funcional

### Endpoints

| Rota | Comportamento |
|---|---|
| `POST /v2/orders` | Conforme `orders-v2.yaml`. `Idempotency-Key` obrigatório |
| `GET /v2/orders/{orderId}` | Conforme `orders-v2.yaml`; pedido de outro chamador retorna `404` |
| `POST /orders` e `POST /v1/orders` | Conforme `orders-v1.yaml`. `Idempotency-Key` opcional; sem o header, comportamento da 1.0.0 |
| `GET /orders/{id}` e `GET /v1/orders/{id}` | Conforme `orders-v1.yaml`, pelo `legacyId`; pedido de outro chamador retorna `404` |

`GET /v2/orders` (listagem) fica fora da PoC.

### Regras de negócio

- **Precificação:** pelo read model `catalog_item_view` do país do chamador. Preço nunca vem da requisição. SKU ausente ou não vendável retorna `422 unknown-sku` na v2 e `400` na v1. Não há chamada ao serviço de Catálogo na PoC.
- **`expectedTotal` (v2):** se diferente do total calculado acima da tolerância configurada (`pedidos.preco.tolerancia`, padrão `0.00`), retorna `409 price-changed` com os preços atuais.
- **Snapshot:** cada item grava preço, moeda, descrição, `catalogVersion`, `capturedAt` e `snapshotSource = CAPTURED`. A v1 lê `price` e `description` do snapshot.
- **Outbox:** a criação grava um `OrderCreated` em `outbox_event` na mesma transação, com payload conforme `order-events.yaml` e **sem nenhum dado do comprador**. Não há publicador nem Kafka na PoC.
- **Identificadores:** `id` UUIDv7 gerado na aplicação; `legacyId` por sequência do banco, presente em todo pedido.
- **Cliente:** a v1 envia apenas `customerId`; nome e e-mail são opcionais no domínio e obrigatórios na v2.
- **Erros:** Problem Details (RFC 9457) na v2, com os valores de `type` do contrato; `{ "message": ... }` na v1.

### Idempotência (ADR-005)

Implementar exatamente o algoritmo do ADR-005:

1. Chamador identificado pelo token; escopo da chave = (chamador, chave).
2. `requestHash` = SHA-256 do método, da rota e do corpo JSON canonizado (chaves ordenadas, sem espaços). Incluir a rota impede que a mesma chave usada na v1 e na v2 seja tratada como repetição.
3. Uma única transação: `SET LOCAL lock_timeout = '2s'`; `INSERT` em `idempotency_record` com a resposta já serializada; `INSERT` do pedido, dos itens e do evento; `COMMIT`.
4. Violação de unicidade na chave: `ROLLBACK` e nova leitura do registro. Mesmo hash: devolver status e corpo armazenados com `Idempotent-Replayed: true`. Hash diferente: `422 idempotency-key-reuse`.
5. `lock_timeout` excedido: `409 request-in-progress` com `Retry-After: 1`.
6. Violação de unicidade em (`partner_id`, `external_reference`): `409 duplicate-external-reference` com `existingOrderId`.
7. Validade de 24 h (`expires_at`); registro vencido é tratado como inexistente. A limpeza periódica fica fora da PoC.
8. Na v1 sem header, nenhum registro de idempotência é criado.

### Autenticação simplificada

Não há IdP na PoC. Um filtro lê `Authorization: Bearer <token>` e resolve o token por uma tabela de tokens sintéticos na configuração de teste (por exemplo, `token-cliente-ana` → cliente final; `token-parceiro-acme` → parceiro `acme`; todos com país `BR`). Token ausente ou desconhecido retorna `401`. Documentar no README que isso substitui a validação real de JWT.

## 5. Modelo de dados

| Tabela | Colunas principais | Restrições |
|---|---|---|
| `orders` | `id` uuid, `legacy_id` bigint, `country`, `caller_id`, `partner_id`, `external_reference`, `channel`, `status`, `version`, `customer_id`, `customer_name`, `customer_email`, `total_amount` numeric(19,4), `currency`, `created_at`, `updated_at` | PK `id`; único `legacy_id`; único parcial (`partner_id`, `external_reference`) quando `partner_id` não é nulo |
| `order_items` | `order_id`, `line_no`, `sku`, `quantity`, `unit_price` numeric(19,4), `currency`, `description`, `catalog_version`, `captured_at`, `snapshot_source` | PK (`order_id`, `line_no`) |
| `idempotency_record` | `caller_id`, `idem_key`, `request_hash`, `order_id`, `response_status`, `response_body` jsonb, `created_at`, `expires_at` | PK (`caller_id`, `idem_key`) |
| `outbox_event` | `event_id` uuid, `aggregate_type`, `aggregate_id`, `aggregate_version`, `event_type`, `payload` jsonb, `occurred_at`, `published_at` | PK `event_id` |
| `catalog_item_view` | `country`, `sku`, `price`, `currency`, `description`, `sellable`, `catalog_version` | PK (`country`, `sku`) |

**Dados sintéticos:** migração Flyway separada, carregada nos testes e no perfil `local`, com 1.000 SKUs gerados (`generate_series`) para o país `BR`. Nenhum dado real.

## 6. Estrutura de código (ADR-001)

Pacote base `com.exemplo.pedidos`:

```
domain/                    Order, OrderItem, ItemSnapshot, Money, OrderStatus, regras; sem Spring Web nem JDBC
application/               CreateOrderUseCase, GetOrderUseCase, IdempotencyService, portas (interfaces)
adapters/in/web/v1/        controller, DTOs e mapeamento da v1
adapters/in/web/v2/        controller, DTOs, mapeamento e Problem Details da v2
adapters/in/web/security/  filtro de autenticação simplificada
adapters/out/persistence/  implementações das portas com JdbcClient
config/
```

DTOs de entrada explícitos por versão: campos não previstos (por exemplo, `status`, `unitPrice`) são ignorados e nunca chegam ao domínio.

## 7. Testes obrigatórios

Os testes são a evidência da PoC. Cada um cita o cenário que prova no nome ou no `@DisplayName`.

| Classe | Caso | Cenário |
|---|---|---|
| `IdempotenciaIT` | Mesma chave e corpo, em sequência: 1 pedido, 1 evento; segunda resposta idêntica com `Idempotent-Replayed: true` | QA-INT-01 |
| | 20 requisições simultâneas com a mesma chave: exatamente 1 pedido e 1 evento; respostas idênticas ou `409` | QA-INT-02 |
| | Mesma chave, corpo diferente: `422`, nenhum pedido novo | QA-INT-03 |
| | Mesma chave, chamadores diferentes: 2 pedidos | ADR-005 |
| | v2 sem `Idempotency-Key`: `400` | ADR-005 |
| | Chave vencida: novo pedido | ADR-005 |
| | Parceiro com chaves diferentes e mesma `externalReference`: `409` com `existingOrderId` | QA-INT-06 |
| `CompatibilidadeV1IT` | Consumidor escrito só contra `baseline/orders-v1.0.0.yaml` cria e consulta sem header; respostas válidas segundo a linha de base | QA-COM-01 |
| | Pedido criado pela v2 consultado pela v1 pelo `legacyId`; resposta válida segundo a linha de base | QA-COM-03 |
| | v1 com `Idempotency-Key` repetida: 1 pedido | ADR-005, ADR-007 |
| `ContratoV2IT` | Respostas `201`, `400`, `401`, `404`, `409`, `422` válidas segundo `orders-v2.yaml` | ADR-007 |
| `SnapshotIT` | Após mudar preço e descrição no read model, pedido existente mantém os valores originais na v1 e na v2 | QA-AUD-01 |
| `EventoIT` | `OrderCreated` no outbox válido segundo o schema do AsyncAPI e sem nome, e-mail ou `customerId` | QA-PRI-02 |
| `SegurancaIT` | Sem token: `401`. Pedido de outro chamador: `404` na v2 e na v1. Campos `status` e `unitPrice` no corpo não alteram o resultado | API-01, API-02, API-03 |
| `ArquiteturaTest` | `domain` não depende de `adapters`, Spring Web nem JDBC; `web.v1` e `web.v2` não dependem um do outro | FF-11 |

O **consumidor v1** é uma classe de teste própria (por exemplo, `ConsumidorV1`) que monta requisições e lê respostas usando apenas o que existe na linha de base 1.0.0. Ela representa o frontend, o backoffice e o ERP atuais e não pode ser alterada para acompanhar a evolução do serviço.

Testes unitários do domínio (cálculo de total, canonização do hash) complementam os de integração.

## 8. Execução

- **Comando único:** `./mvnw verify` a partir de `poc/`. Requisito: Docker em execução (Testcontainers).
- **Execução manual (opcional):** `docker compose up -d` com PostgreSQL e `./mvnw spring-boot:run -Dspring-boot.run.profiles=local`; arquivo `requests.http` com exemplos de chamadas, incluindo repetição com a mesma chave.
- **CI:** criar `../.github/workflows/poc.yml`, disparado por mudanças em `poc/**`, com Java 21 (Temurin), cache do Maven, `./mvnw -B verify`, publicação dos relatórios de teste e, em pull requests, `actions/dependency-review-action` e CodeQL para Java (FF-16).

## 9. Fora do escopo da PoC

Gateway, JWT e IdP reais; células e roteamento por país; Kafka e publicador do outbox; Integração com Parceiros e webhooks; read model alimentado por eventos e contingência ao Catálogo; listagem e reconciliação; transições de status; Resilience4j; limpeza de registros de idempotência; Assistente Operacional.

## 10. Forma de trabalho

1. Antes de codificar, apresente um plano em etapas e aguarde aprovação.
2. Sugestão de etapas: (1) esqueleto Maven com Testcontainers, Flyway e ArchUnit passando; (2) schema e dados sintéticos; (3) domínio e v2 sem idempotência, com testes de contrato; (4) idempotência e `IdempotenciaIT`; (5) snapshot, outbox, `SnapshotIT` e `EventoIT`; (6) adaptador v1 e `CompatibilidadeV1IT`; (7) autenticação simplificada e `SegurancaIT`; (8) CI, `docker-compose.yml`, `requests.http` e README.
3. Ao fim de cada etapa, rode `./mvnw verify` e só avance com tudo passando.
4. Commits pequenos, mensagens em português no formato Conventional Commits.
5. Código, identificadores e nomes de teste em inglês ou conforme a linguagem ubíqua; README e notas em português.
6. Não altere arquivos fora de `poc/`, exceto `.github/workflows/poc.yml`.
7. Mantenha `NOTAS-DE-IMPLEMENTACAO.md` com decisões tomadas, desvios desta especificação e problemas encontrados. Esse registro alimenta `../docs/ia/uso-de-ia-na-proposta.md`.

## 11. Definição de pronto

- [ ] `./mvnw verify` passa em máquina limpa com Docker, sem passos manuais.
- [ ] Todos os testes da seção 7 existem, citam o cenário e passam.
- [ ] Testes negativos e positivos presentes para idempotência, compatibilidade e segurança.
- [ ] `poc.yml` executa o build no CI.
- [ ] `README.md` da PoC com: o que a PoC prova, como executar, mapa teste → cenário, simplificações e limites.
- [ ] `NOTAS-DE-IMPLEMENTACAO.md` atualizado.
