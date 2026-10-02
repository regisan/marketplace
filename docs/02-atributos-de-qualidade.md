# Atributos de qualidade

> Define, para cada atributo de qualidade, as metas, os mecanismos arquiteturais que as sustentam e cenários verificáveis com critérios de aceite. É a referência para os testes de carga e resiliência, para os gates do plano 30/60/90 (`05-plano-30-60-90.md`) e para os alertas de produção.
>
> Base: premissas P-VOL e P-SLO, cálculos C-01 a C-06 (`00-premissas-e-questoes-abertas.md`) e ADR-001 a ADR-008.

## 1. Metas consolidadas

| Jornada ou componente | Disponibilidade mensal | Latência | Origem |
|---|---|---|---|
| Criação de pedido (`POST /v2/orders`) | 99,9% | P95 ≤ 500 ms até 50 itens; P95 ≤ 1,2 s até 300 itens | P-SLO-02 |
| Consulta de pedido (`GET /v2/orders/{id}`) | 99,95% | P95 ≤ 200 ms | P-SLO-03 |
| Listagem e reconciliação (`GET /v2/orders`) | 99,9% | P95 ≤ 500 ms por página de 50 | Derivada de P-SLO-03 |
| Notificação a parceiros | 99,9% entregues em 24 h | P95 ≤ 30 s entre a transição e a entrega | P-SLO-04 |
| Propagação de catálogo | — | P95 ≤ 5 s entre a mudança no Catálogo e o read model | P-SLO-05 |
| Publicação de eventos (outbox) | — | P95 ≤ 2 s entre o commit e a publicação | ADR-006 |
| Recuperação | RPO ≤ 5 min | RTO ≤ 30 min (zona); RTO ≤ 4 h (região) | P-SLO-06 |
| Capacidade | — | 260 pedidos/s e 2.000 consultas/s por toda a plataforma no pico | P-VOL-04, P-VOL-05 |

**Orçamento de erro:** 99,9% em 30 dias equivale a 43,2 minutos de indisponibilidade por jornada e por célula. Disponibilidade é medida no API Gateway regional como a razão entre respostas não-5xx e o total de respostas, excluindo 4xx (P-SLO-01).

## 2. Atributo → mecanismos → evidência

| Atributo | Mecanismos arquiteturais | ADRs | Evidência |
|---|---|---|---|
| **Escalabilidade** | Serviços sem estado escalados horizontalmente; read model local elimina a carga multiplicada no Catálogo; células por país dividem a carga; outbox em lotes; particionamento do tópico por `orderId`; quotas por chamador | 001, 002, 003, 006 | Teste de carga (QA-ESC) |
| **Disponibilidade** | Catálogo fora do caminho crítico; publicação assíncrona; bulkhead e circuit breaker por parceiro; banco multi-AZ; deploy canário com feature flags; células isolam falhas por país | 002, 003, 006, 008 | Testes de resiliência (QA-DIS) e SLIs |
| **Desempenho** | Criação sem chamadas de rede (read model); transação curta e única; consulta por chave; paginação por cursor | 003, 005 | Teste de carga (QA-DES) |
| **Integridade** | Idempotência por restrição única na mesma transação; referência externa única por parceiro; outbox transacional; deduplicação por `eventId`; ordenação por `orderVersion` | 005, 006, 008 | Testes da PoC (QA-INT) |
| **Segurança** | Gateway regional com validação de token e quotas; escopos OAuth2; autorização por recurso (`404` para pedido alheio); webhooks com HMAC e anti-SSRF | 002, 007, 008 | Threat model e testes (QA-SEG) |
| **Privacidade e residência** | Dados pessoais só em Pedidos e na célula do titular; eventos e webhooks sem PII; marcação `x-pii` verificada no CI; roteamento por hostname regional | 002, 008 | Regras Spectral e de IaC (QA-PRI) |
| **Auditabilidade** | Snapshot imutável do item; histórico imutável de transições; `catalogVersion`; rastreamento ponta a ponta por `traceparent` | 004, 006 | Testes e consultas de auditoria (QA-AUD) |
| **Recuperabilidade** | Backups com PITR; outbox e tópico com retenção de 7 dias para republicação; DLQ com reenvio; reconstrução do read model por exportação; reconciliação com parceiros | 003, 006, 008 | Exercícios de recuperação (QA-REC) |
| **Compatibilidade** | v1 e v2 como adaptadores do mesmo domínio; mudanças aditivas; linha de base congelada; leitores tolerantes; `Deprecation` e `Sunset` | 007 | `oasdiff` e teste de consumidor (QA-COM) |

## 3. Cenários

Cada cenário segue o formato de cenário de atributo de qualidade (fonte e estímulo, ambiente, resposta e medida), condensado em colunas. A coluna **Fase** indica quando o cenário passa a ser critério de aceite no plano 30/60/90.

### 3.1 Escalabilidade

| ID | Estímulo e ambiente | Resposta esperada | Critério de aceite | Verificação | Fase |
|---|---|---|---|---|---|
| QA-ESC-01 | 260 criações/s sustentadas por 30 min, mistura de pedidos com 5 itens (média) e 300 itens (1%), em ambiente de carga | Sistema absorve a carga escalando instâncias de Pedidos | Metas de latência de criação atendidas; taxa de erro 5xx < 0,1%; CPU do banco < 70% | Teste de carga (k6 ou Gatling) | 90 |
| QA-ESC-02 | 2.000 consultas/s por chave concorrendo com QA-ESC-01 | Consultas não degradam a criação | P95 de consulta ≤ 200 ms; metas de criação mantidas | Teste de carga | 90 |
| QA-ESC-03 | 800 eventos/s gerados por transições de status | Outbox publica sem acumular | Atraso de publicação P95 ≤ 2 s; `outbox_pending_count` estável | Teste de carga | 60 |
| QA-ESC-04 | Dobro do número de instâncias de Pedidos | Vazão cresce proporcionalmente | Vazão ≥ 1,8x com o mesmo P95 | Teste de carga comparativo | 90 |
| QA-ESC-05 | Um parceiro envia o triplo da sua quota | Excesso é recusado sem afetar outros chamadores | `429` com `Retry-After` para o parceiro; latência dos demais inalterada | Teste de carga | 60 |

### 3.2 Disponibilidade

| ID | Estímulo e ambiente | Resposta esperada | Critério de aceite | Verificação | Fase |
|---|---|---|---|---|---|
| QA-DIS-01 | Serviço de Catálogo indisponível por 30 min, em carga normal | Criação continua para SKUs presentes no read model | Taxa de sucesso de criação com SKUs conhecidos ≥ 99,9%; SKUs ausentes recebem `503` com `Retry-After` em ≤ 250 ms | Teste de resiliência (Catálogo desligado) | 60 |
| QA-DIS-02 | Broker regional indisponível por 15 min | Criação e transições continuam; eventos acumulam no outbox | Zero erros 5xx na criação causados pelo broker; após a volta, backlog zerado em ≤ 15 min, sem perda de eventos | Teste de resiliência | 30 |
| QA-DIS-03 | Endpoint de um parceiro com timeout por 1 h | Entregas desse parceiro ficam em retry; demais seguem normais | P95 de entrega dos demais parceiros ≤ 30 s; circuit breaker do parceiro abre; nenhuma entrega perdida | Teste de integração com parceiro simulado | 60 |
| QA-DIS-04 | Falha da zona onde está o primário do banco de Pedidos | Failover automático para outra zona | Indisponibilidade ≤ 5 min; RPO ≤ 5 min | Exercício de failover em homologação | 90 |
| QA-DIS-05 | Deploy de nova versão de Pedidos em horário de pico | Rollout canário (5%, 25%, 100%) com rollback automático | Nenhuma janela de indisponibilidade; rollback automático se a taxa de 5xx do canário > 1% por 5 min | Pipeline de deploy | 30 |
| QA-DIS-06 | Região Brasil indisponível | Célula B continua operando | Criação de pedidos na célula B mantém SLO; apenas a propagação de catálogo para | Exercício em homologação | 90 |

### 3.3 Desempenho

| ID | Estímulo e ambiente | Resposta esperada | Critério de aceite | Verificação | Fase |
|---|---|---|---|---|---|
| QA-DES-01 | Criação de pedidos de 1 a 50 itens, em carga nominal | Precificação local, sem chamadas ao Catálogo | P95 ≤ 500 ms; nenhuma chamada HTTP ao Catálogo registrada no trace | Teste de carga com tracing | 60 |
| QA-DES-02 | Criação de pedidos de 300 itens | Mesmo caminho, uma única consulta em lote ao read model | P95 ≤ 1,2 s | Teste de carga | 60 |
| QA-DES-03 | Mudança de preço no Catálogo | Read model atualizado | Atraso P95 ≤ 5 s (`catalog_view_lag_seconds`) | Métrica em produção e teste de integração | 60 |
| QA-DES-04 | Transição de status de pedido de parceiro com assinatura ativa | Webhook entregue | P95 ≤ 30 s entre o commit da transição e a resposta 2xx do parceiro | Métrica `webhook_delivery_latency_seconds` | 60 |

### 3.4 Integridade

| ID | Estímulo e ambiente | Resposta esperada | Critério de aceite | Verificação | Fase |
|---|---|---|---|---|---|
| QA-INT-01 | Mesma criação repetida com a mesma `Idempotency-Key` | Um pedido; repetições recebem a resposta original | Exatamente 1 pedido e 1 evento `OrderCreated`; respostas idênticas com `Idempotent-Replayed: true` | Teste da PoC | 30 |
| QA-INT-02 | 20 requisições simultâneas com a mesma chave | Um pedido; demais aguardam ou recebem `409` | Exatamente 1 pedido e 1 evento | Teste da PoC | 30 |
| QA-INT-03 | Mesma chave com corpo diferente | Recusa | `422 idempotency-key-reuse`; nenhum pedido novo | Teste da PoC | 30 |
| QA-INT-04 | Processo de Pedidos encerrado à força entre o commit e a publicação | Evento publicado após o reinício | Evento publicado exatamente uma vez no outbox; nenhum evento sem pedido | Teste de integração | 30 |
| QA-INT-05 | Broker entrega o mesmo evento duas vezes à Integração com Parceiros | Um único efeito | Uma única entrega de webhook enfileirada | Teste de integração | 60 |
| QA-INT-06 | Parceiro repete pedido com nova chave e mesma `externalReference` | Recusa | `409 duplicate-external-reference` com `existingOrderId` | Teste da PoC | 60 |

### 3.5 Segurança

| ID | Estímulo e ambiente | Resposta esperada | Critério de aceite | Verificação | Fase |
|---|---|---|---|---|---|
| QA-SEG-01 | Parceiro A consulta pedido do parceiro B | Recusa sem revelar existência | `404`; tentativa registrada em log de auditoria | Teste de API | 60 |
| QA-SEG-02 | Token emitido para a célula B usado na célula BR | Recusa | `403` no gateway; requisição não chega a Pedidos | Teste de API | 90 |
| QA-SEG-03 | Parceiro cadastra URL de webhook que resolve para IP privado ou de metadados | Recusa | Cadastro rejeitado; nenhuma conexão de saída para o destino | Teste de integração | 60 |
| QA-SEG-04 | Webhook reenviado por terceiro após 10 min (replay) | Parceiro rejeita | Biblioteca de referência de verificação rejeita timestamp fora da janela de 5 min | Teste do exemplo de verificação no CI | 60 |
| QA-SEG-05 | Token sem escopo `orders:write` tenta criar pedido | Recusa | `403` | Teste de API | 60 |
| QA-SEG-06 | Varredura de logs e traces de um dia de teste de carga | Nenhum segredo nem dado pessoal em claro | Zero ocorrências de tokens, segredos HMAC, e-mails ou nomes | Varredura automatizada de logs | 30 |

### 3.6 Privacidade e residência

| ID | Estímulo e ambiente | Resposta esperada | Critério de aceite | Verificação | Fase |
|---|---|---|---|---|---|
| QA-PRI-01 | Pull request adiciona campo `x-pii` a evento ou webhook | Build falha | Regra `eventos-e-webhooks-sem-pii` reprova o PR | Spectral no CI | 30 |
| QA-PRI-02 | Evento serializado em teste | Payload sem dados pessoais | Nenhum campo de `Customer` presente no JSON publicado | Teste da PoC | 30 |
| QA-PRI-03 | Infraestrutura como código cria recurso com dados pessoais fora da região da célula | Plano de infraestrutura falha | Política de IaC reprova o plano | Política no CI | 90 |
| QA-PRI-04 | Titular solicita exclusão de dados | Dados pessoais anonimizados; dados fiscais preservados | Anonimização concluída dentro do prazo definido pelo jurídico (Q-07), com registro da solicitação | Teste de integração e runbook | 90 |

### 3.7 Auditabilidade

| ID | Estímulo e ambiente | Resposta esperada | Critério de aceite | Verificação | Fase |
|---|---|---|---|---|---|
| QA-AUD-01 | Auditor pede preço e descrição vendidos de um pedido após mudança no Catálogo | Resposta com dados de Pedidos apenas | Valores originais retornados por v1 e v2; consulta documentada no runbook | Teste da PoC | 30 |
| QA-AUD-02 | Disputa sobre quando e por quem um pedido mudou de status | Histórico completo | Toda transição tem origem, instante e versão; histórico imutável | Teste de integração | 60 |
| QA-AUD-03 | Investigação de um webhook não recebido pelo parceiro | Rastro ponta a ponta | Do `POST` original à última tentativa de entrega pelo mesmo `traceparent`, com cada tentativa registrada | Trace e registros de `webhook_delivery` | 60 |

### 3.8 Recuperabilidade

| ID | Estímulo e ambiente | Resposta esperada | Critério de aceite | Verificação | Fase |
|---|---|---|---|---|---|
| QA-REC-01 | Banco de Pedidos corrompido por erro operacional | Restauração em ponto no tempo | Restauração ≤ 30 min com perda ≤ 5 min | Exercício trimestral | 90 |
| QA-REC-02 | Read model de catálogo perdido | Reconstrução por exportação do Catálogo | 500 mil SKUs restaurados em ≤ 30 min; criação de pedidos com SKUs ausentes recebe `503` no intervalo | Teste em homologação | 60 |
| QA-REC-03 | Bug de consumidor descartou eventos por 2 dias | Reprocessamento | Eventos republicados do tópico (retenção de 7 dias) após correção, sem efeitos duplicados | Runbook e teste de integração | 60 |
| QA-REC-04 | 50 entregas na DLQ após parceiro passar 2 dias fora | Reenvio | Reenvio manual pelo backoffice entrega todas; parceiro deduplica por `eventId` | Teste de integração | 60 |

### 3.9 Compatibilidade

| ID | Estímulo e ambiente | Resposta esperada | Critério de aceite | Verificação | Fase |
|---|---|---|---|---|---|
| QA-COM-01 | Consumidor escrito para a v1 1.0.0 executa contra o serviço com v1 1.1.0 e v2 | Funciona sem alteração | Criação e consulta passam no teste de consumidor | Teste da PoC | 30 |
| QA-COM-02 | Pull request remove campo ou torna obrigatório um campo opcional da v1 ou v2 | Build falha | `oasdiff breaking` reprova o PR | CI | 30 |
| QA-COM-03 | Pedido criado pela v2 consultado pela v1 | Resposta no formato v1 | Corpo válido segundo o schema da 1.0.0 | Teste da PoC | 30 |
| QA-COM-04 | Novo valor de status adicionado ao domínio | Consumidores v2 não quebram | Nenhum `enum` fechado na v2 nem nos eventos; teste de cliente com valor desconhecido passa | Revisão de contrato e teste | 60 |

## 4. Indicadores de nível de serviço (SLIs)

| SLI | Definição | Fonte |
|---|---|---|
| `availability_ratio` | Respostas não-5xx ÷ total de respostas, por rota, versão e célula | Métricas do API Gateway |
| `http_server_latency_seconds` | Histograma de latência por rota, versão e faixa de itens | Pedidos (Micrometer) |
| `catalog_view_lag_seconds` | Aplicação do evento no read model menos `occurredAt` | Consumidor do read model |
| `outbox_publish_lag_seconds` | Publicação menos `occurred_at` | Publicador do outbox |
| `outbox_pending_count` | Eventos com `published_at` nulo | Consulta periódica |
| `webhook_delivery_latency_seconds` | Resposta 2xx do parceiro menos `occurredAt` do evento | Integração com Parceiros |
| `webhook_delivery_success_ratio` | Entregas concluídas em 24 h ÷ entregas criadas, por parceiro | Integração com Parceiros |
| `idempotent_replay_total` | Respostas com `Idempotent-Replayed: true`, por canal | Pedidos |
| `circuit_breaker_state` | Estado por dependência (Catálogo, cada parceiro) | Resilience4j |

Rastreamento distribuído com OpenTelemetry: o `traceparent` é propagado em chamadas HTTP e como header das mensagens Kafka, ligando a requisição original aos eventos e às entregas de webhook (QA-AUD-03).

## 5. Trade-offs entre atributos

| Tensão | Escolha | Custo aceito |
|---|---|---|
| Consistência × disponibilidade e desempenho | Read model de catálogo com consistência eventual (ADR-003) | Pedido pode usar preço com até alguns segundos de atraso; mitigado por `expectedTotal` e snapshot com `catalogVersion` |
| Integridade × latência | Idempotência, snapshot e outbox na mesma transação (ADR-005, ADR-006) | Escrita adicional por pedido; requisições concorrentes com a mesma chave aguardam até 2 s |
| Privacidade × custo | Célula por país (ADR-002) | Infraestrutura de Pedidos e Integração duplicada |
| Privacidade × conveniência do parceiro | Webhook sem PII (ADR-008) | Parceiro faz uma consulta adicional para obter detalhes |
| Compatibilidade × simplicidade | Duas versões de API por pelo menos 6 meses (ADR-007) | Adaptador v1 e `legacyId` como débito técnico |
| Simplicidade × latência de publicação | Outbox por polling antes de CDC (ADR-006) | Até centenas de milissegundos de atraso e carga de consulta no banco |

## 6. Política de orçamento de erro

- **Consumo acima de 50% na primeira metade do mês:** revisão dos incidentes na reunião semanal do time.
- **Orçamento esgotado:** novas funcionalidades de Pedidos congeladas até o fim da janela; apenas correções de confiabilidade e de segurança são implantadas.
- **Incidente que consome mais de 20% do orçamento:** postmortem sem culpados em até 5 dias úteis, com ações registradas.
