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
| D-17 | Antes de precificar, a criação lê o registro de idempotência válido; se existir, responde direto (repetição ou `422`). O `INSERT` dentro da transação continua sendo a garantia sob concorrência | Sem isso, uma repetição após mudança de preço ou de SKU vendável receberia `409 price-changed` ou `422 unknown-sku` em vez da resposta original, contrariando o ADR-005 |
| D-18 | `lock_timeout` configurável (`pedidos.idempotencia.lock-timeout`, padrão `2s`); `IdempotenciaIT` usa `5s` | Dá folga ao portão do QA-INT-02 em máquinas lentas; o mecanismo é o mesmo |
| D-19 | "Respostas idênticas" é verificado como igualdade semântica do JSON | `response_body` é `jsonb` (seção 5), que não preserva ordem de chaves nem espaços. Para igualdade byte a byte, a coluna teria de ser `text` |
| D-20 | `RequestHasher` fica em `adapters.in.web`, compartilhado por v1 e v2, e não em `application` | A canonização depende do DTO de entrada e do Jackson, que são detalhes do adaptador |
| D-21 | Um único contêiner PostgreSQL para todos os contextos Spring de teste | `IdempotenciaIT` tem propriedades próprias e gera outro contexto |
| D-22 | Lock timeout no `INSERT` do pedido (corrida pela mesma `externalReference` entre chaves diferentes) também responde `409 request-in-progress` | Item A-8 do plano: a requisição concorrente ainda não sabe se a outra vai confirmar |
| D-23 | O payload do `OrderCreated` é montado por um record com lista explícita de campos, sem nenhuma referência a `Customer` | Ausência de dados pessoais por construção (QA-PRI-02), e não por filtro |
| D-24 | `partnerId` e `externalReference` saem como `null` no evento de cliente final | O schema do AsyncAPI declara `type: [string, "null"]` |
| D-25 | O schema do AsyncAPI é validado como JSON Schema draft-07, com o documento registrado em memória no networknt 2.0.1 | O loader do networknt 2.x não abre URIs `file:`; o draft-07 é o formato padrão de schema do AsyncAPI 3.0 |
| D-26 | `ConsumidorV1` lê as respostas em modo estrito (falha com campo desconhecido) | Além de validar contra o schema, detecta qualquer campo novo na resposta da v1, o que um consumidor estrito da 1.0.0 não toleraria |
| D-27 | A v1 não impõe limites que a 1.0.0 não tinha (máximo de itens e de quantidade); só limita `customerId` e `sku` a 64 caracteres, o tamanho das colunas | Uma regra nova recusaria requisições que consumidores atuais já enviam |
| D-28 | `GET /orders/{id}` com identificador não numérico responde `404` | A 1.0.0 declara apenas `200`, `401` e `404` |

## Problemas encontrados

- O `poc/CLAUDE.md` versionado no primeiro commit era cópia do `CLAUDE.md` da raiz, sem a especificação. Foi substituído pelo autor antes do início da implementação.
- Na primeira execução do `IdempotenciaIT`, o pool Hikari se esgotou: `JdbcClient...query().stream()` mantém a conexão aberta até o stream ser fechado. Trocado por `.list()`. O teste de concorrência expôs o vazamento, que passava despercebido nas requisições sequenciais.
