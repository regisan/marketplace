# Premissas, interpretações e questões abertas

> **Propósito.** O enunciado não traz números de carga, o segundo país, o contrato atual de API nem dados de custo. Este documento registra cada premissa adotada para preencher essas lacunas, a justificativa, o impacto caso esteja errada e a questão que a validaria. Todos os demais artefatos (ADRs, C4, plano 30/60/90, estimativa, PoC) referenciam as premissas pelo ID.
>
> **Regra de uso.** Nenhuma decisão arquitetural deve depender de uma premissa que não esteja aqui. Quando uma premissa for validada ou refutada, atualize o status e revise os ADRs que a citam.

**Legenda de status**

| Status | Significado |
|---|---|
| Assumida | Adotada pelo autor, ainda não confirmada pelo cliente |
| Validada | Confirmada pelo cliente; registrar data e fonte |
| Refutada | Contrariada pelo cliente; registrar o novo valor e os ADRs revisados |

**Convenção de IDs:** `P-<área>-<nn>`. Áreas: VOL (volume), DOM (domínio), PAIS (segundo país e dados), ASIS (estado atual), CTR (contratos), SLO (qualidade), INF (infraestrutura), SEG (segurança), IA, TIME (equipe e custos), POC (fatia executável).

---

## 1. Volume e carga

A unidade de referência é o volume atual do canal web nacional, chamado de **1u**. O "10x" do enunciado foi interpretado como 10x o volume atual (ver I-01), decomposto por canal para permitir dimensionar cada fronteira separadamente.

| ID | Premissa | Valor assumido | Justificativa | Impacto se errada | Status |
|---|---|---|---|---|---|
| P-VOL-01 | Volume atual de pedidos | 100 mil pedidos/dia (média ≈ 1,2/s) | Ordem de grandeza típica de e-commerce nacional de porte médio | Escala linear: o desenho suporta de 0,5x a 3x sem mudança estrutural; acima disso, revisar particionamento do banco de pedidos | Assumida |
| P-VOL-02 | Perfil de pico | Pico diário = 6x a média (≈ 7/s); pico sazonal = 3x o pico diário (≈ 20/s) | Concentração em horário comercial e campanhas | Afeta dimensionamento de pool de conexões, partições do broker e quotas | Assumida |
| P-VOL-03 | Composição do 10x por canal | Web BR 2u, app móvel 3u, marketplace 3u, País B 2u (total 10u) | Separa crescimento orgânico de novos canais | Se marketplace for maior, quotas por parceiro e webhooks passam a ser o gargalo dominante | Assumida |
| P-VOL-04 | Meta de capacidade de escrita | 200 pedidos/s no pico sazonal alvo, testado a 260/s (30% de folga) | 20/s × 10, mais margem | Base dos testes de carga e do critério de aceite de escalabilidade | Assumida |
| P-VOL-05 | Razão leitura/escrita | 10:1 (consultas de pedido por criação), ≈ 2.000 GET/s no pico alvo | Usuário e parceiros consultam status repetidamente | Se parceiros fizerem polling agressivo, a razão pode chegar a 50:1; mitigação por quotas e webhooks | Assumida |
| P-VOL-06 | Itens por pedido | Média 5, P95 15, máximo 300 (pedidos B2B e marketplace) | Cauda longa é o que expõe o N+1 | Base do cálculo C-01; se o máximo for maior, adotar limite de itens por requisição | Assumida |
| P-VOL-07 | Tamanho do catálogo | 500 mil SKUs por país, ≈ 1 KB por SKU no read model | Varejo com sortimento amplo | Read model local de ≈ 500 MB por país; viável mesmo a 10x | Assumida |
| P-VOL-08 | Mudanças de preço | 50 mil/dia em média, 100 mil/dia em campanhas | Precificação dinâmica moderada | Define a vazão de eventos de catálogo e o atraso aceitável do read model | Assumida |
| P-VOL-09 | Mudanças de status por pedido | 4 em média (criado, confirmado, enviado, entregue) | Ciclo de vida simplificado | ≈ 4 milhões de eventos/dia no alvo; pico ≈ 800/s | Assumida |
| P-VOL-10 | Pedidos originados por parceiros | 30% do volume alvo | Coerente com P-VOL-03 (3u de 10u) | ≈ 1,2 milhão de webhooks/dia; define o dimensionamento do despachante de webhooks | Assumida |

## 2. Domínio e escopo funcional

| ID | Premissa | Valor assumido | Justificativa | Impacto se errada | Status |
|---|---|---|---|---|---|
| P-DOM-01 | Contextos no escopo | Pedidos e Catálogo, mais Notificação a Parceiros como capacidade de suporte | Únicos citados no enunciado | Novos contextos entram como dependências externas no mapa de contexto | Assumida |
| P-DOM-02 | Pagamento, estoque, frete e fiscal | Fora do escopo; tratados como sistemas externos já existentes | Não citados no enunciado | Se precisarem ser orquestrados, será necessária uma saga e um ADR adicional | Assumida |
| P-DOM-03 | Dono do preço | O Catálogo é dono do preço vigente; Pedidos é dono do preço praticado (snapshot) | Separa a fonte de verdade de cada conceito | Se houver motor de promoções separado, o snapshot precisa registrar também as regras aplicadas | Assumida |
| P-DOM-04 | Preço por país | Preço e moeda são definidos por país; não há conversão cambial em tempo real | Simplifica o snapshot e a residência | Se houver conversão, o snapshot precisa registrar a taxa e a fonte | Assumida |
| P-DOM-05 | Divergência de preço no checkout | Pedidos valida o preço enviado contra o read model; divergência acima de tolerância retorna erro e o cliente reconsulta | Evita aceitar preço desatualizado ou manipulado | Se o negócio quiser honrar o preço exibido, adotar *price quote* com validade (alternativa no ADR de snapshot) | Assumida |
| P-DOM-06 | Máquina de estados do pedido | CRIADO, CONFIRMADO, ENVIADO, ENTREGUE, CANCELADO; transições monotônicas | Suficiente para a notificação de status | Estados adicionais não quebram o contrato se o enum for documentado como extensível | Assumida |
| P-DOM-07 | Pedidos históricos sem snapshot | Recebem backfill de melhor esforço, marcado como `snapshotSource = RECONSTRUCTED` | O histórico de preço do Catálogo pode não existir | Auditoria de pedidos antigos continua limitada; risco aceito e registrado | Assumida |

## 3. Segundo país, privacidade e residência de dados

| ID | Premissa | Valor assumido | Justificativa | Impacto se errada | Status |
|---|---|---|---|---|---|
| P-PAIS-01 | Identidade do segundo país | "País B", não nomeado; tratado de forma parametrizada | O enunciado não informa | Ao nomear o país, revisar P-PAIS-02 a P-PAIS-05 | Assumida |
| P-PAIS-02 | Regime de privacidade do País B | Equivalente ao GDPR em rigor (base legal, direitos do titular, notificação de incidente) | Desenhar para o pior caso razoável | Se for mais brando, a solução continua conforme, apenas com custo maior que o necessário | Assumida |
| P-PAIS-03 | Residência de dados | Dados pessoais de titulares do País B são armazenados e processados apenas em região do País B | Leitura estrita de "requisitos de residência" | Base do ADR de célula por país; se a residência for apenas de armazenamento, o processamento central fica permitido e o custo cai | Assumida |
| P-PAIS-04 | Dados que podem cruzar fronteira | Catálogo (não pessoal), métricas agregadas e eventos sem PII | Minimização de dados | Se o catálogo também tiver restrição, cada país terá catálogo isolado | Assumida |
| P-PAIS-05 | Operação no País B | Moeda, idioma e fuso próprios; mesma marca e mesmo backoffice | Expansão típica | Afeta internacionalização e formatação, não a estrutura | Assumida |
| P-PAIS-06 | Retenção de dados de pedido | 5 anos para fins fiscais no Brasil; mesmo prazo assumido no País B | Prazo prescricional tributário brasileiro | Define custo de armazenamento (C-04) e política de expurgo/anonimização | Assumida |
| P-PAIS-07 | Direitos do titular | Exclusão é atendida por anonimização dos dados pessoais do pedido, preservando os dados fiscais | Conflito entre eliminação e obrigação legal de guarda | Requer validação jurídica; registrada como questão Q-07 | Assumida |

## 4. Estado atual (AS-IS)

Necessário para desenhar o C4 AS-IS e o plano de convivência.

| ID | Premissa | Valor assumido | Justificativa | Impacto se errada | Status |
|---|---|---|---|---|---|
| P-ASIS-01 | Topologia | Dois serviços (Pedidos e Catálogo), cada um com seu PostgreSQL | O enunciado fala em "serviço de Pedidos consulta o Catálogo" | Se for monólito com banco compartilhado, o primeiro passo passa a ser separar o acesso ao esquema do Catálogo | Assumida |
| P-ASIS-02 | Stack | Java 17 e Spring Boot 3, implantados em contêineres em nuvem pública | Coerente com a exigência de Java 21 na PoC | Se for outra stack, a PoC continua válida como prova de padrão, não de código reaproveitável | Assumida |
| P-ASIS-03 | Mensageria | Existe um broker (Kafka gerenciado) usado para publicar eventos de pedido | O enunciado cita publicação de eventos | Se não houver broker, a fase de 30 dias inclui provisioná-lo, o que aumenta o risco do prazo | Assumida |
| P-ASIS-04 | Publicação de eventos | Feita após o commit, em chamada separada (dual write) | É o que "sem garantia transacional" sugere | Confirma a necessidade de outbox | Assumida |
| P-ASIS-05 | Região | Uma única região no Brasil, multi-AZ | Operação nacional | Se for single-AZ, a meta de 99,9% exige mudança de infraestrutura já nos primeiros 30 dias | Assumida |
| P-ASIS-06 | Observabilidade | Logs centralizados e métricas de infraestrutura; sem tracing distribuído | Situação comum | Tracing entra na fase de 30 dias como pré-requisito do rollout canário | Assumida |
| P-ASIS-07 | Deploy | Pipeline CI/CD existente com deploy contínuo; sem feature flags | Situação comum | Feature flags entram na fase de 30 dias | Assumida |

## 5. Contratos e consumidores atuais

| ID | Premissa | Valor assumido | Justificativa | Impacto se errada | Status |
|---|---|---|---|---|---|
| P-CTR-01 | Contrato atual | API REST JSON sem versão explícita (`/orders`), reconstruída em `poc/docs/openapi/baseline/orders-v1.0.0.yaml` | O enunciado não fornece o contrato | Ao receber o contrato real, substituir o arquivo e reexecutar os testes de compatibilidade | Assumida |
| P-CTR-02 | Características do v1 | IDs sequenciais expostos, preço como número sem moeda, sem paginação, erros sem formato padronizado | Vícios realistas que a v2 corrige de forma aditiva | Nenhum na estrutura; apenas nos exemplos | Assumida |
| P-CTR-03 | Consumidores atuais | Frontend web e duas integrações internas (backoffice e ERP) | O enunciado fala em "consumidores atuais", no plural | Consumidores desconhecidos são o maior risco de compatibilidade; mitigação por telemetria por cliente (Q-04) | Assumida |
| P-CTR-04 | Janela de compatibilidade | v1 mantida por no mínimo 6 meses a partir do lançamento da v2, com headers `Deprecation` e `Sunset` | Requisito explícito do enunciado | Desligamento da v1 condicionado a tráfego zero por 30 dias, não apenas à data | Assumida |
| P-CTR-05 | Estratégia de versão | Versão na URI (`/v1`, `/v2`) para a API pública; evolução aditiva dentro da mesma versão maior | Simples para parceiros e para roteamento no gateway | Detalhado no ADR de versionamento | Assumida |
| P-CTR-06 | Consumidores de eventos | Toleram campos desconhecidos (leitores tolerantes) | Prática necessária para evolução aditiva de schema | Se não toleram, cada evolução de evento exige novo tópico ou nova versão do schema | Assumida |

## 6. Atributos de qualidade e SLOs

O enunciado define 99,9% e P95 ≤ 500 ms de forma global. Abaixo, a interpretação por jornada (ver I-04).

| ID | Premissa | Valor assumido | Justificativa | Impacto se errada | Status |
|---|---|---|---|---|---|
| P-SLO-01 | Escopo do SLA de 99,9% | Por jornada, medido no API gateway, janela mensal, excluindo erros 4xx | Medição verificável e sob controle da plataforma | Se medido no cliente, a rede móvel entra no orçamento de erro | Assumida |
| P-SLO-02 | Criação de pedido | Disponibilidade 99,9%; P95 ≤ 500 ms para pedidos de até 50 itens; P95 ≤ 1,2 s até 300 itens | Pedidos grandes não cabem em 500 ms com validação item a item | Se 500 ms valer para qualquer tamanho, impor limite de itens por requisição | Assumida |
| P-SLO-03 | Consulta de pedido | Disponibilidade 99,95%; P95 ≤ 200 ms | Leitura por chave, sem dependências síncronas | Folga em relação à meta global | Assumida |
| P-SLO-04 | Notificação de status a parceiros | 99,9% dos eventos entregues em até 30 s (P95), com retry por até 24 h | O enunciado não define meta para fluxos assíncronos | Define a política de retry e o tamanho da DLQ | Assumida |
| P-SLO-05 | Atraso do read model de catálogo | P95 ≤ 5 s entre mudança no Catálogo e reflexo em Pedidos | Compatível com a tolerância de preço (P-DOM-05) | Atraso maior aumenta rejeições por divergência de preço | Assumida |
| P-SLO-06 | Recuperação | RPO ≤ 5 min; RTO ≤ 30 min para perda de uma zona; RTO ≤ 4 h para perda de região | Coerente com 99,9% (orçamento de 43,2 min/mês) | Perda de região consome vários meses de orçamento; risco aceito e registrado | Assumida |

## 7. Infraestrutura e plataforma

| ID | Premissa | Valor assumido | Justificativa | Impacto se errada | Status |
|---|---|---|---|---|---|
| P-INF-01 | Provedor | Nuvem pública com região no Brasil e no País B | Necessário para P-PAIS-03 | Se não houver região no País B, considerar provedor local ou colocation, com impacto alto em prazo e custo | Assumida |
| P-INF-02 | Serviços gerenciados | Banco relacional, broker, gateway de API e observabilidade gerenciados | Time pequeno e prazo curto | Autogerenciar aumenta o esforço de SRE em ≈ 1 pessoa | Assumida |
| P-INF-03 | Orquestração | A plataforma de contêineres atual é mantida; nenhuma migração de orquestrador está no escopo | Evitar mudança de plataforma junto com mudança de arquitetura | Nenhum | Assumida |

## 8. Segurança e identidade

| ID | Premissa | Valor assumido | Justificativa | Impacto se errada | Status |
|---|---|---|---|---|---|
| P-SEG-01 | Identidade de clientes finais | Existe um IdP que emite tokens OIDC para web e app | Canal web já autentica usuários | Se não houver, o app móvel passa a depender de um projeto de identidade fora deste escopo | Assumida |
| P-SEG-02 | Identidade de parceiros | OAuth2 *client credentials* emitido pelo mesmo IdP, com escopos por recurso | Padrão de mercado para B2B | mTLS pode ser exigido por parceiros maiores; tratado como opção | Assumida |
| P-SEG-03 | Isolamento entre parceiros | Cada parceiro só acessa pedidos que originou | Previne BOLA/IDOR | Se houver parceiros com visão agregada, criar escopo específico | Assumida |
| P-SEG-04 | Segredos | Cofre de segredos gerenciado disponível | Requisito básico | Nenhum na estrutura | Assumida |

## 9. Inteligência artificial

| ID | Premissa | Valor assumido | Justificativa | Impacto se errada | Status |
|---|---|---|---|---|---|
| P-IA-01 | Público do assistente | Uso interno (atendimento e operação), não exposto a clientes finais na primeira versão | Menor superfície de risco | Exposição a clientes exige guardrails e avaliação adicionais | Assumida |
| P-IA-02 | Acesso a dados | Somente leitura, via ferramentas com escopo restrito, nunca acesso direto ao banco | Minimização e auditabilidade | Nenhum | Assumida |
| P-IA-03 | Provedor de LLM | Contrato sem retenção nem treino com dados enviados, processamento em região compatível com P-PAIS-03 | Requisito de residência e LGPD | Se não houver provedor na região do País B, o assistente opera apenas sobre dados pseudonimizados ou fica restrito ao Brasil | Assumida |
| P-IA-04 | Volume de uso | Até 5 mil consultas/dia | Uso interno | Custo de inferência irrelevante frente à infraestrutura principal | Assumida |

## 10. Equipe e custos

| ID | Premissa | Valor assumido | Justificativa | Impacto se errada | Status |
|---|---|---|---|---|---|
| P-TIME-01 | Composição para os primeiros 30 dias | 4 devs backend, 1 QA/SDET, 1 SRE (50%), 1 arquiteto (50%); PO do cliente | Escopo da fase 1 do plano | Base da estimativa; menos pessoas implica cortar escopo, não estender prazo | Assumida |
| P-TIME-02 | Custo-hora médio | Faixa de R$ 130 a R$ 220, parametrizado na planilha de estimativa | Mercado brasileiro para perfis plenos e sêniores | A estimativa é apresentada em cenários; o valor exato é do cliente | Assumida |
| P-TIME-03 | Conhecimento do domínio | O time já conhece o sistema atual | Time interno ou alocado há mais tempo | Time novo exige 1 a 2 semanas de onboarding, inviabilizando a fase de 30 dias | Assumida |
| P-TIME-04 | Custos de infraestrutura | Estimados por ordem de grandeza e por componente, com base em preços públicos de nuvem | Sem acesso à conta do cliente | Faixas, não valores fechados | Assumida |

## 11. Fatia executável (PoC)

| ID | Premissa | Valor assumido | Justificativa | Impacto se errada | Status |
|---|---|---|---|---|---|
| P-POC-01 | Ambiente do avaliador | Docker e JDK 21 disponíveis; sem acesso a nuvem | Execução local com Testcontainers | Se não houver Docker, oferecer perfil com H2, com a ressalva de que não prova concorrência real | Assumida |
| P-POC-02 | Escopo da prova | Idempotência (incluindo concorrência) e compatibilidade v1/v2 com teste de consumidor | Exigidas juntas pelo critério crítico (ver I-03) | Outbox fica como extensão opcional | Assumida |
| P-POC-03 | Dados sintéticos | Seed com catálogo de 1 mil SKUs e clientes fictícios, sem nenhum dado real | Reprodutibilidade e privacidade | Nenhum | Assumida |
| P-POC-04 | Comando único | `./mvnw verify` executa build, testes e validações de contrato | Requisito do enunciado | Nenhum | Assumida |

---

## 12. Cálculos derivados

Contas que sustentam decisões dos ADRs. Se uma premissa mudar, refazer a conta correspondente.

**C-01: Custo do N+1 na latência** (P-VOL-06, P-SLO-02)
Com 15 ms por chamada síncrona ao Catálogo, um pedido médio de 5 itens gasta 75 ms só em consultas. Um pedido de 300 itens gasta 4,5 s em série, nove vezes a meta de P95. Paralelizar reduz a latência, mas multiplica a carga instantânea sobre o Catálogo. Conclusão: o N+1 precisa sair do caminho crítico, não apenas ficar mais rápido.

**C-02: Carga gerada no Catálogo** (P-VOL-04, P-VOL-06)
No pico alvo, 200 pedidos/s × 5 itens = 1.000 req/s ao Catálogo apenas para criação de pedidos, com rajadas muito maiores vindas de pedidos grandes. O Catálogo herdaria o pico de Pedidos e um incidente em um derrubaria o outro.

**C-03: Disponibilidade composta** (P-SLO-01)
O orçamento de erro mensal de 99,9% é de 43,2 min (30 dias). Três dependências síncronas de 99,9% em série resultam em 0,999³ ≈ 99,7%, ou cerca de 130 min de indisponibilidade esperada por mês, o triplo do orçamento. Conclusão: a criação de pedido não pode depender sincronamente de mais de um componente além do próprio banco.

**C-04: Armazenamento de pedidos** (P-VOL-01, P-VOL-03, P-PAIS-06)
Com ≈ 3 KB por pedido (cabeçalho e itens com snapshot) e 1 milhão de pedidos/dia no alvo, são ≈ 3 GB/dia, ≈ 1,1 TB/ano e ≈ 5,5 TB em 5 anos de retenção, somados os dois países. Volume confortável para PostgreSQL gerenciado com particionamento por data.

**C-05: Chaves de idempotência** (P-VOL-04)
Com TTL de 24 h e ≈ 1 milhão de criações/dia, a tabela mantém cerca de 1 milhão de registros de ≈ 1 KB, ou ≈ 1 GB. Cabe no próprio banco de Pedidos, sem necessidade de armazenamento dedicado.

**C-06: Read model de catálogo** (P-VOL-07, P-VOL-08)
500 mil SKUs × 1 KB ≈ 500 MB por país, atualizado por até 100 mil eventos/dia (≈ 1,2/s em média). Custo de manter a réplica local é baixo frente ao ganho em C-01, C-02 e C-03.

---

## 13. Interpretações do enunciado

Pontos ambíguos ou contraditórios e a leitura adotada.

**I-01: Base do "10x".** O texto diz "crescimento em 10x o tráfego previsto", mas também "volume projetado dez vezes maior". Adotado: 10x o volume atual, incluindo os novos canais e o País B (P-VOL-03).

**I-02: Descomissionamento em 90 dias versus compatibilidade por 6 meses.** O plano 30/60/90 pede descomissionamento, mas os contratos atuais vivem por pelo menos 6 meses. Adotado: nos 90 dias saem apenas componentes internos (caminho síncrono N+1, publicação sem outbox). A API v1 entra em *deprecation* no dia 90 e só é desligada após o sexto mês e 30 dias sem tráfego (P-CTR-04).

**I-03: "Uma" prova versus critério crítico duplo.** A seção da PoC lista opções alternativas, mas o critério crítico exige ausência de duplicidade e compatibilidade com consumidor atual. Adotado: a PoC prova as duas coisas (P-POC-02).

**I-04: SLA e latência globais.** 99,9% e P95 ≤ 500 ms foram desdobrados em SLOs por jornada (seção 6), porque escrita, leitura e notificação assíncrona têm perfis incomparáveis.

**I-05: Notificação de status em OpenAPI e AsyncAPI.** Adotado: o evento interno `OrderStatusChanged` é documentado em AsyncAPI; o webhook para parceiros é documentado em OpenAPI 3.1 (`webhooks`), com endpoint de consulta para reconciliação.

**I-06: Dois sentidos de "IA".** A seção de arquitetura pede uma capacidade de IA no produto; os critérios e entregáveis pedem a documentação da IA usada para produzir a proposta. Adotado: dois documentos separados, `docs/ia/assistente-de-pedidos.md` e `docs/ia/uso-de-ia-na-proposta.md`.

**I-07: Origem dos retries.** O enunciado não diz quem faz os retries sem chave de idempotência. Adotado: tratar as três origens, cada uma com seu mecanismo. Clientes e parceiros usam `Idempotency-Key`; consumidores de eventos deduplicam por `eventId`; chamadas internas usam timeout e retry limitado apenas em operações idempotentes.

---

## 14. Questões abertas para o cliente

Ordenadas por impacto na arquitetura. Cada questão valida ou refuta as premissas indicadas.

| ID | Questão | Premissas afetadas | Prioridade |
|---|---|---|---|
| Q-01 | Qual é o segundo país e quais são seus requisitos de residência (armazenamento, processamento ou ambos)? | P-PAIS-01 a P-PAIS-04, P-INF-01, P-IA-03 | Alta |
| Q-02 | Qual é o volume atual (pedidos/dia, pico, itens por pedido) e qual a fonte do "10x"? | P-VOL-01 a P-VOL-06 | Alta |
| Q-03 | Existe especificação ou documentação do contrato atual de Pedidos? | P-CTR-01, P-CTR-02 | Alta |
| Q-04 | Quem são todos os consumidores atuais da API, inclusive os não catalogados? | P-CTR-03 | Alta |
| Q-05 | Já existe broker de mensageria em produção? Qual? | P-ASIS-03 | Alta |
| Q-06 | Há região do provedor de nuvem atual no segundo país? | P-INF-01 | Alta |
| Q-07 | Como o jurídico concilia pedidos de exclusão (LGPD) com a guarda fiscal? Anonimização é aceita? | P-PAIS-06, P-PAIS-07 | Média |
| Q-08 | O preço exibido ao cliente deve ser honrado se mudar antes da confirmação? | P-DOM-05 | Média |
| Q-09 | O SLA é contratual com parceiros? Há penalidade associada? | P-SLO-01 a P-SLO-04 | Média |
| Q-10 | Pagamento, estoque e frete participam da criação do pedido de forma síncrona hoje? | P-DOM-02 | Média |
| Q-11 | Qual é a composição e a senioridade do time disponível? | P-TIME-01, P-TIME-03 | Média |
| Q-12 | Há restrição de provedor ou política corporativa para uso de LLMs? | P-IA-03 | Baixa |
| Q-13 | Qual o papel dos parceiros de marketplace perante a LGPD em relação aos dados dos compradores (controladores independentes, controladores conjuntos ou operadores)? | P-PAIS-02, P-SEG-03 | Média |

---

## 15. Fora do escopo

Itens explicitamente excluídos desta proposta. Cada um aparece no mapa de contexto como sistema externo, quando aplicável.

Pagamento, antifraude, estoque e reserva, frete e cálculo de prazo, emissão fiscal, fluxo completo de cancelamento e devolução (apenas o status CANCELADO é modelado), busca e navegação de catálogo, gestão de identidade dos usuários finais, migração de plataforma de orquestração e reconstrução exata de snapshot para pedidos históricos (P-DOM-07).

---

## 16. Histórico de revisões

| Data | Autor | Alteração |
|---|---|---|
| AAAA-MM-DD | (seu nome) | Versão inicial com premissas assumidas |
