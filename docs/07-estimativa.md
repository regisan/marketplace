# Estimativa técnica

> Estima esforço, composição da equipe, custos principais, premissas e riscos para executar a **fase 1 (dias 1 a 30)** do plano. Inclui, como referência para o resumo executivo, uma ordem de grandeza das fases 2 e 3 e do custo mensal da arquitetura-alvo na AWS.
>
> Base: escopo da fase 1 em `05-plano-30-60-90.md`; premissas P-TIME-01 a P-TIME-04; riscos em `06-riscos-e-fora-de-escopo.md`.

## 1. Premissas da estimativa

| ID | Premissa | Valor |
|---|---|---|
| E-01 | Duração da fase 1 | 30 dias corridos, equivalentes a 22 dias úteis de 8 horas |
| E-02 | Custo-hora médio por pessoa, custo total para a empresa | R$ 130 a R$ 220 (P-TIME-02); R$ 175 no cenário esperado |
| E-03 | Fator de foco | 70% para desenvolvedores e 80% para os demais papéis; o restante cobre cerimônias, revisões, suporte e imprevistos |
| E-04 | Conhecimento do sistema | A equipe já conhece o sistema atual (P-TIME-03) |
| E-05 | Infraestrutura existente | Broker em produção (P-ASIS-03); serviços em contêineres na AWS, região `sa-east-1` (São Paulo) |
| E-06 | Preços de nuvem | Sob demanda, sem descontos de reserva ou Savings Plans; quando não há preço publicado para `sa-east-1`, aplica-se fator de 1,5x a 2x sobre `us-east-1` |
| E-07 | Câmbio | R$ 5,20 por dólar (referência arredondada da cotação de setembro de 2026) |
| E-08 | Alteração no frontend web | Envio do `Idempotency-Key` feito pelo time do frontend (cerca de 2 dias-pessoa), fora desta estimativa |
| E-09 | Ferramentas | Sem novas licenças: flags com AWS AppConfig, observabilidade com OpenTelemetry, CloudWatch e X-Ray, CI no GitHub Actions |

## 2. Composição da equipe

| Papel | Quantidade | Dedicação | Responsabilidades na fase 1 |
|---|---|---|---|
| Desenvolvedor backend | 4 | 100% | Idempotência, snapshot, outbox, observabilidade na aplicação, contratos e CI |
| QA / SDET | 1 | 100% | Automação dos cenários do gate 30, testes de falha, validação durante o canário |
| SRE / DevOps | 1 | 50% | Coletor de telemetria, painéis, alertas, pipeline canário com rollback automático |
| Arquiteto de software | 1 | 50% | Refinamento técnico, revisões, gates de go/no-go, alinhamento com consumidores |
| Product owner | 1 | Do cliente | Prioridade, aceite e comunicação com áreas de negócio |

## 3. Esforço por frente de trabalho

| Frente | Dev | QA | SRE | Arq. | Total (dias-pessoa) |
|---|---|---|---|---|---|
| Observabilidade (tracing, métricas, painéis, alertas) | 6 | — | 4 | — | 10 |
| Feature flags, canário e rollback automático | 5 | — | 4 | — | 9 |
| Contratos e fitness functions no CI | 6 | — | — | 1 | 7 |
| Idempotência (migração, implementação, header na v1) | 12 | 3 | — | 1 | 16 |
| Snapshot de itens | 8 | 2 | — | 1 | 11 |
| Outbox e troca do dual write por flag | 12 | 3 | — | 1 | 16 |
| Segurança de base (logs sem PII e segredos, DTOs explícitos) | 4 | 1 | — | — | 5 |
| Testes de resiliência e de falha (broker fora, queda entre commit e publicação) | — | 5 | 1 | — | 6 |
| Rollout canário, acompanhamento e ajustes | 6 | 4 | — | 2 | 12 |
| Documentação e runbooks | 3 | — | — | 1 | 4 |
| Alinhamento com consumidores, questões abertas e gates | — | — | — | 2 | 2 |
| **Total** | **62** | **18** | **9** | **9** | **98** |

**Verificação de capacidade**

| Papel | Capacidade nominal | Capacidade com fator de foco | Demanda | Ocupação |
|---|---|---|---|---|
| Desenvolvedores | 88 | 62 | 62 | 100% |
| QA | 22 | 18 | 18 | 100% |
| SRE (50%) | 11 | 9 | 9 | 100% |
| Arquiteto (50%) | 11 | 9 | 9 | 100% |

A demanda cabe na capacidade efetiva, sem folga além do fator de foco. Por isso o plano define antes uma ordem de corte de escopo: se a fase atrasar, a remoção do dual write e parte da documentação passam para a fase 2, preservando idempotência, snapshot e outbox ativos.

## 4. Custo de pessoas da fase 1

Horas da fase: 4 desenvolvedores × 176 h + 1 QA × 176 h + SRE 88 h + arquiteto 88 h = **1.056 horas**.

| Cenário | Horas | Custo-hora | Custo | Condição |
|---|---|---|---|---|
| Otimista | 1.056 | R$ 130 | **R$ 137.280** | Premissas confirmadas; custo-hora no piso da faixa |
| Esperado | 1.056 | R$ 175 | **R$ 184.800** | Premissas confirmadas; custo-hora no meio da faixa |
| Pessimista | 1.320 | R$ 220 | **R$ 290.400** | Uma semana adicional da equipe (onboarding, R-12, ou provisionamento de broker, R-06); custo-hora no teto da faixa |

## 5. Custo de infraestrutura da fase 1

A fase 1 não cria nenhum serviço novo de grande porte. O custo incremental vem de telemetria, configuração de flags, execução de CI e, possivelmente, de aumento do banco de Pedidos.

| Item (AWS, `sa-east-1`) | Base do cálculo | Custo mensal incremental |
|---|---|---|
| Tracing (X-Ray ou equivalente via OpenTelemetry) | Amostragem de 10% sobre cerca de 33 milhões de requisições por mês | US$ 30 a 80 |
| Métricas, painéis, alarmes e logs adicionais (CloudWatch) | Cerca de 100 métricas customizadas, painéis dos SLIs, aumento de logs estruturados | US$ 100 a 300 |
| Feature flags (AWS AppConfig) | Consultas de configuração em cache pelas instâncias | Menos de US$ 20 |
| CI (GitHub Actions) | Testes com Testcontainers, lint de contratos, análise de dependências | US$ 50 a 150 |
| Instâncias temporárias do canário | Tarefas duplicadas durante os rollouts | US$ 20 a 50 |
| **Subtotal** | | **US$ 200 a 600 (R$ 1.040 a R$ 3.120)** |
| Contingência: banco de Pedidos de `db.r6g.large` para `db.r6g.xlarge`, Multi-AZ | Somente se a CPU passar de 70% com as novas escritas (R-10) | Até US$ 700 (R$ 3.640) |

## 6. Total da fase 1

| Componente | Otimista | Esperado | Pessimista |
|---|---|---|---|
| Pessoas | R$ 137.280 | R$ 184.800 | R$ 290.400 |
| Infraestrutura (1 mês) | R$ 1.040 | R$ 2.080 | R$ 6.760 |
| **Total** | **R$ 138.320** | **R$ 186.880** | **R$ 297.160** |

**Recomendação de orçamento:** aprovar o cenário esperado com reserva de 15%, totalizando cerca de **R$ 215 mil**. O custo é dominado por pessoas (cerca de 99%), então a principal alavanca de risco é o escopo, não a infraestrutura.

## 7. Referência para as fases 2 e 3 (ordem de grandeza)

Não fazem parte do escopo desta estimativa, mas são necessários para a decisão de investimento.

**Pessoas.** O plano assume ampliação da equipe para as fases 2 e 3 (R-01): 6 desenvolvedores, 1 QA, 1 SRE em tempo integral e o arquiteto a 50%, por 60 dias. São cerca de 2.990 horas, ou **R$ 389 mil a R$ 658 mil** na faixa de custo-hora.

**Outros custos a cotar:** teste de intrusão externo antes da abertura a parceiros e eventuais adaptações jurídicas e contratuais (Q-07, Q-13).

## 8. Custo mensal da arquitetura-alvo após 90 dias (ordem de grandeza)

Estimativa do custo total de operação na AWS, com volume de 10x, para apoiar a decisão de investimento. Deve ser refinada na AWS Pricing Calculator com dados reais de volume.

| Componente | Configuração de referência | Célula BR (US$/mês) |
|---|---|---|
| Pedidos (ECS Fargate) | 8 tarefas de 1 vCPU e 2 GB em média, com autoescala no pico | 500 a 900 |
| Integração com Parceiros (ECS Fargate) | 2 a 4 tarefas | 100 a 250 |
| Banco de Pedidos (RDS PostgreSQL, Multi-AZ) | `db.r6g.xlarge` a `db.r6g.2xlarge`, cerca de 1 TB | 1.600 a 3.000 |
| Banco de Integração (RDS PostgreSQL, Multi-AZ) | `db.m6g.large` | 300 |
| Broker (Amazon MSK) | 3 brokers `kafka.m7g.large` e armazenamento | 600 a 1.000 |
| API Gateway e WAF | Cerca de 330 milhões de requisições por mês | 500 a 1.500 |
| Observabilidade | Tracing amostrado, métricas, logs e alarmes no volume de 10x | 500 a 1.500 |
| Rede e transferência | NAT, saída de webhooks, replicação de catálogo | 200 a 600 |
| Segredos, chaves e configuração | Secrets Manager, KMS, AppConfig | até 100 |
| **Total da célula BR** | | **4.400 a 9.150** |

| Escopo | US$/mês | R$/mês |
|---|---|---|
| Célula BR | 4.400 a 9.150 | 22.900 a 47.600 |
| Célula B (cerca de 20% do volume, com a mesma base de alta disponibilidade) | 2.500 a 5.000 | 13.000 a 26.000 |
| Incremento no Catálogo central (outbox e replicação entre regiões) | 200 a 600 | 1.000 a 3.100 |
| **Total** | **7.100 a 14.750** | **36.900 a 76.700** |

Os preços da célula B dependem da região do País B (Q-06) e podem diferir significativamente de `sa-east-1`. Compromissos de uso (instâncias reservadas, Savings Plans) reduzem o custo de banco e computação em 30% a 50% após a estabilização do volume.

**Fontes de preço consultadas em outubro de 2026** (confirmar na AWS Pricing Calculator antes da aprovação):
- RDS PostgreSQL por região e instância: https://www.bytebase.com/dbcost/rds/instance/db.r6g.large/
- Amazon MSK: https://aws.amazon.com/msk/pricing/
- AWS Fargate: https://aws.amazon.com/fargate/pricing/

## 9. Riscos da estimativa

| Risco | Efeito na estimativa | Referência |
|---|---|---|
| Ocupação de 100% da capacidade efetiva | Qualquer imprevisto acima do fator de foco exige corte de escopo | Seção 3 |
| Equipe sem conhecimento do sistema | Cenário pessimista (+25% de horas) | R-12 |
| Broker inexistente | Cenário pessimista; provisionamento na semana 1 | R-06 |
| Custo-hora fora da faixa | Variação proporcional no custo de pessoas | E-02 |
| Variação cambial | Afeta apenas a infraestrutura, menos de 3% do total da fase 1 | E-07 |
| Preços regionais diferentes dos estimados | Afeta a referência de custo mensal, não a fase 1 | E-06 |
| Volume diferente do premissado | Altera a referência de custo mensal, principalmente banco, gateway e observabilidade | R-05 |
