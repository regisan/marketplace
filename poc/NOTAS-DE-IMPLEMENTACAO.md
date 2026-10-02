# Notas de implementação da PoC

Registro de decisões, desvios da especificação (`CLAUDE.md`) e problemas encontrados. Alimenta `../docs/ia/uso-de-ia-na-proposta.md`.

## Inconsistências entre especificação e contratos

Os contratos em `docs/` não foram alterados. Cada item traz a resolução adotada na PoC.

| # | Inconsistência | Resolução na PoC | Ação sugerida fora da PoC |
|---|---|---|---|
| I-1 | A especificação torna nome e e-mail opcionais no domínio (a v1 envia só `customerId`), mas `Order.customer` da v2 exige `name` e `email`. Um pedido criado pela v1 e lido pela v2 viola o contrato | O mapeador v2 omite os campos ausentes; a direção v1 → v2 não é testada | Tornar `name` e `email` opcionais no `Customer` da resposta da v2 antes do lançamento (ainda sem consumidores) |
| I-2 | "Registro vencido é tratado como inexistente", mas a PK (`caller_id`, `idem_key`) faria o `INSERT` colidir com a linha vencida | Na mesma transação, antes do `INSERT`, apaga o registro vencido daquela chave. O `INSERT` continua sendo o ponto de disputa do ADR-005 | Nenhuma |
| I-3 | O hash inclui "a rota", mas `/orders` e `/v1/orders` são a mesma operação | O hash usa a operação lógica (`operationId` do contrato: `createOrderV1` ou `createOrder`) em vez do caminho literal | Explicitar no ADR-005 |
| I-4 | "Corpo canonizado" não define o tratamento de campos desconhecidos; pelo corpo bruto, `status` ou `unitPrice` extras gerariam `422` | O hash é calculado sobre o DTO de entrada desserializado (só campos conhecidos), com chaves ordenadas e sem espaços | Explicitar no ADR-005 |

## Decisões

| # | Decisão | Motivo |
|---|---|---|
| D-1 | Spring Boot 4.1.1 (GA mais recente em 2026-10-02), Testcontainers 2.0.5 e Flyway 12.4.0 gerenciados; ArchUnit 1.5.1 | Seção 3 da especificação |
| D-2 | JUnit Jupiter 6.0.3 (gerenciado pelo Boot 4.1.1), e não JUnit 5 | A especificação diz "JUnit 5", mas também manda usar as versões gerenciadas; a 6 é a gerenciada |
| D-3 | Validação de contrato com `com.atlassian.oai:openapi-request-validator-core` 3.0.0 | Valida a resposta inteira contra a operação: status declarado, `Content-Type`, headers e corpo. Usa o dialeto OpenAPI 3.1 do networknt. `ContractValidationSpikeTest` prova `type: [string, "null"]`, `pattern`, `format: uuid`, status e media type. O plano B (swagger-parser + networknt) não foi necessário |
| D-4 | AsyncAPI validado com `networknt json-schema-validator` 2.0.1, a mesma versão usada pelo validador OpenAPI | Não há validador AsyncAPI confiável em Java |
| D-5 | Maven Wrapper 3.3.4 (`only-script`) com Maven 3.9.16 | Maven 4 ainda está em RC |
| D-7 | Trigger que rejeita `UPDATE` em `order_items` | Validação do ADR-004 (snapshot imutável, invariante 2 do agregado); não consta da especificação, adição de baixo custo |
| D-8 | Dados sintéticos em `db/testdata`, aplicados só nos perfis `test` e `local` via `spring.flyway.locations` | Mantém o perfil padrão sem dados fictícios |
| D-6 | Pool Hikari de 30 conexões no perfil de teste | Com o padrão de 10, parte das 20 requisições do QA-INT-02 ficaria enfileirada no pool e não chegaria ao banco ao mesmo tempo |
| D-9 | O filtro de tokens sintéticos definitivo entrou na etapa 3, no lugar do resolvedor provisório planejado | Evita retrabalho; a etapa 7 só acrescenta `SegurancaIT` |
| D-10 | `400` e `404` da v2 usam `type: about:blank` com `title` igual ao status (RFC 9457, seção 4.2.1); `400` traz `errors[]` | O contrato só define `type` para `409`, `422` e `503` |
| D-11 | `GET /v2/orders/{orderId}` com identificador malformado responde `404` | O contrato declara apenas `200`, `401`, `404` e `429` para a consulta |
| D-12 | Corpo ausente, JSON malformado e `Content-Type` não suportado respondem `400` na v2 | `415` não está declarado no contrato |
| D-13 | Instantes truncados em milissegundos na criação | A resposta é serializada antes do commit (para ser armazenada no registro de idempotência) e precisa ser idêntica à lida depois do banco, que guarda microssegundos |
| D-14 | Validação de entrada manual (`RequestValidator`), sem Bean Validation | Hibernate Validator não está na lista de dependências da seção 3 |
| D-15 | Parceiro sem `externalReference` recebe `400`; para cliente final o campo é ignorado | O texto do contrato diz "obrigatório para parceiros; ignorado para clientes finais", mas o schema não marca o campo como obrigatório |
| D-16 | `customerId` do corpo não é comparado com a identidade do token; a posse do pedido é sempre o chamador do token | Não há IdP real na PoC; limite registrado |

## Problemas encontrados

- O `poc/CLAUDE.md` versionado no primeiro commit era cópia do `CLAUDE.md` da raiz, sem a especificação. Foi substituído pelo autor antes do início da implementação.
