# Riscos, premissas, débitos aceitos e fora do escopo

> Consolida em um só lugar os riscos, as premissas críticas, os débitos técnicos aceitos e as exclusões que aparecem ao longo da proposta. Os detalhes continuam nos documentos de origem, indicados em cada linha.
>
> Escala de risco: probabilidade e impacto em baixa, média e alta; o nível resulta da combinação dos dois.

## 1. Riscos

### 1.1 Matriz

| Probabilidade \ Impacto | Baixo | Médio | Alto |
|---|---|---|---|
| **Alta** | — | — | R-01 |
| **Média** | R-07 | R-04, R-05, R-08, R-11, R-13, R-14, R-15 | R-02, R-03 |
| **Baixa** | R-17 | R-09, R-10, R-16, R-18 | R-06, R-12 |

### 1.2 Registro

| ID | Risco | Categoria | Prob. | Impacto | Nível | Mitigação | Contingência | Indicador ou gatilho | Origem |
|---|---|---|---|---|---|---|---|---|---|
| R-01 | Escopo das fases 2 e 3 maior que a capacidade da equipe dimensionada para a fase 1 | Entrega | Alta | Alto | **Alto** | Decidir a ampliação da equipe até o dia 25 (Q-11) | Aplicar a ordem de corte de escopo definida no plano | Velocidade da fase 1 abaixo do planejado | Plano, seção 9 |
| R-02 | Provedor de nuvem sem região no País B | Conformidade, prazo | Média | Alto | **Alto** | Confirmar até o dia 30 (Q-06) | Rever o ADR-002 com provedor local; fase 3 replanejada | Resposta negativa a Q-06 | P-INF-01, ADR-002 |
| R-03 | Requisitos de residência do País B diferentes dos assumidos | Conformidade | Média | Alto | **Alto** | Desenho para o pior caso razoável (P-PAIS-02, P-PAIS-03) | Ajustar o que cruza a fronteira, por exemplo isolar também o Catálogo | Resposta a Q-01 | ADR-002 |
| R-04 | Consumidores da v1 não identificados | Compatibilidade | Média | Médio | **Médio** | Tráfego por `client_id` no gateway; comunicação ativa | Estender a vida da v1 | Chamadas de `client_id` desconhecidos | P-CTR-03, Q-04 |
| R-05 | Volume real diferente do premissado | Capacidade | Média | Médio | **Médio** | Arquitetura escala horizontalmente; recalibrar testes com dados reais | Particionar o banco de Pedidos se o volume passar de 3x o premissado | Resposta a Q-02; métricas da fase 1 | P-VOL-01 a P-VOL-06 |
| R-06 | Não existe broker de mensageria em produção | Prazo | Baixa | Alto | **Médio** | Confirmar até o dia 3 (Q-05) | Provisionar broker gerenciado na semana 1, consumindo a folga da fase 1 | Resposta a Q-05 | P-ASIS-03 |
| R-07 | ERP não migra para o novo tópico de eventos dentro da janela | Compatibilidade | Média | Baixo | **Baixo** | Tópico legado mantido em paralelo | Estender a manutenção do tópico legado | Consumo ainda ativo no tópico legado no mês 6 | Plano, fase 2 |
| R-08 | Parceiros piloto sem disponibilidade para integrar na fase 2 | Entrega | Média | Médio | **Médio** | Selecionar parceiros piloto até o dia 40; ambiente de testes com parceiro simulado | Piloto desliza para a fase 3 | Nenhum parceiro confirmado no dia 40 | Plano, seção 9 |
| R-09 | Divergência de preço entre read model e Catálogo acima do aceitável | Negócio | Baixa | Médio | **Médio** | Modo sombra antes da troca; tolerância e `409 price-changed` | Voltar à precificação síncrona por flag | Divergência > 0,1% no modo sombra | ADR-003 |
| R-10 | Carga de idempotência e outbox satura o banco de Pedidos | Capacidade | Baixa | Médio | **Médio** | Índices parciais, limpeza periódica, testes de carga | Migrar para CDC conforme gatilhos | CPU do banco > 70% no pico | ADR-005, ADR-006 |
| R-11 | Congelamento de mudanças (Black Friday, fim de ano) dentro das janelas de rollout | Prazo | Média | Médio | **Médio** | Alinhar o D0 ao calendário comercial antes de iniciar | Deslocar o rollout; manter flags desligadas durante o congelamento | Calendário comercial | Plano, seção 1 |
| R-12 | Equipe sem conhecimento prévio do sistema atual | Entrega | Baixa | Alto | **Médio** | Confirmar premissa P-TIME-03 | Reservar 1 a 2 semanas de onboarding e reduzir o escopo da fase 1 | Resposta a Q-11 | P-TIME-03 |
| R-13 | Parceiros implementam incorretamente a verificação de assinatura ou a deduplicação | Integração | Média | Médio | **Médio** | Exemplo de verificação testado no CI; documentação e ambiente de testes | Suporte dedicado na integração do piloto | Reclamações de duplicidade ou de rejeição | ADR-008 |
| R-14 | Definições jurídicas pendentes atrasam a fase 3 | Conformidade | Média | Médio | **Médio** | Encaminhar Q-07 e Q-13 ao jurídico no início | Go-live do País B sem fluxo automatizado de anonimização, com atendimento manual | Questões sem resposta no dia 60 | Threat model, seção 7 |
| R-15 | Custo das células acima do orçamento | Custo | Média | Médio | **Médio** | Dimensionamento por medição; serviços gerenciados com escala automática | Rever o tamanho mínimo da célula B | Custo mensal acima da estimativa | ADR-002, estimativa |
| R-16 | Riscos de segurança residuais de nível médio se materializam | Segurança | Baixa | Médio | **Médio** | Controles e detecção do threat model | Resposta a incidentes conduzida pelo DPO e por segurança | Alertas de anomalia | Threat model, seção 8 |
| R-17 | Snapshots reconstruídos de pedidos antigos questionados em auditoria | Conformidade | Baixa | Baixo | **Baixo** | Marcação `RECONSTRUCTED` explícita | Consultar fontes externas (ERP, documentos fiscais) | Solicitação de auditoria sobre pedido antigo | ADR-004 |
| R-18 | Limite de 300 itens por pedido afeta clientes B2B | Negócio | Baixa | Médio | **Médio** | Limite definido por premissa e documentado no contrato | Elevar o limite após teste de carga específico | Pedidos rejeitados por tamanho | P-VOL-06 |

## 2. Premissas críticas

A lista completa está em `00-premissas-e-questoes-abertas.md`. Estas são as premissas que, se falsas, mudam uma decisão arquitetural e não apenas um parâmetro.

| Premissa | Se for falsa | Decisão afetada | Validar até |
|---|---|---|---|
| P-PAIS-03: residência estrita de dados pessoais | Se for mais branda, a célula B pode ser simplificada; se mais rígida, o Catálogo também precisa ser isolado | ADR-002 | Dia 30 (Q-01) |
| P-INF-01: região de nuvem no País B | Necessidade de provedor local | ADR-002, fase 3 | Dia 30 (Q-06) |
| P-ASIS-01: Pedidos e Catálogo com bancos separados | Se houver banco compartilhado, separar o acesso vem antes de tudo | ADR-001, ADR-003 | Dia 3 |
| P-ASIS-03: broker existente | Provisionamento na fase 1 | ADR-006, plano | Dia 3 (Q-05) |
| P-DOM-05: divergência de preço retorna erro | Se o negócio quiser honrar o preço exibido, entra a cotação com validade | ADR-003, ADR-004 | Dia 30 (Q-08) |
| P-CTR-03: consumidores da v1 conhecidos | Descontinuação mais longa e arriscada | ADR-007 | Dia 60 (Q-04) |
| P-TIME-01 e P-TIME-03: equipe e conhecimento | Escopo das fases reduzido | Plano, estimativa | Dia 0 (Q-11) |

## 3. Débitos técnicos aceitos

Débitos assumidos conscientemente para cumprir prazo, compatibilidade ou simplicidade, cada um com plano de quitação.

| ID | Débito | Por que foi aceito | Custo de carregar | Quitação | Origem |
|---|---|---|---|---|---|
| D-01 | Adaptador v1 e coluna `legacyId` | Compatibilidade de 6 meses exigida | Duas versões a manter e testar | Remoção após o desligamento da v1 | ADR-007 |
| D-02 | IDs sequenciais expostos pela v1 | Faz parte do contrato atual | Permite sondagem de IDs (API-02) | Desligamento da v1 | Threat model |
| D-03 | Tópico de eventos legado em paralelo ao novo | Compatibilidade do ERP | Dois formatos publicados | Desligamento após migração do ERP | Plano, fase 2 |
| D-04 | Outbox por polling em vez de CDC | Simplicidade e prazo da fase 1 | Carga de consulta no banco; atraso de publicação | Migração para CDC se os gatilhos forem atingidos | ADR-006 |
| D-05 | Pedidos concentra várias responsabilidades | Evitar decomposição prematura | Deploy único para várias capacidades | Extração de módulos se os gatilhos do ADR-001 forem atingidos | ADR-001 |
| D-06 | Contingência síncrona ao Catálogo para SKUs ausentes | Produtos novos vendáveis antes da propagação | Acoplamento residual ao Catálogo | Avaliar remoção após 3 meses de métricas de uso | ADR-003 |
| D-07 | Edição de catálogo do País B depende da região Brasil | Uma única fonte de verdade de catálogo | Indisponibilidade da edição durante falha regional no Brasil | Revisitar se o País B exigir gestão de catálogo própria | ADR-002 |
| D-08 | Snapshots reconstruídos para pedidos antigos | O histórico de preços não existe | Auditoria limitada de pedidos antigos | Permanente; documentado pelo campo `snapshotSource` | ADR-004 |
| D-09 | Cadastro de assinaturas de webhook feito pela operação | Prazo da fase 2 | Esforço operacional por parceiro novo | API de autoatendimento após os 90 dias | Contratos, README |

## 4. Fora do escopo

| Item | Motivo | Onde aparece ou quando revisitar |
|---|---|---|
| Pagamento, antifraude, estoque e reserva, frete e emissão fiscal | Não citados no enunciado; sistemas existentes | Sistemas externos no C4; Q-10 |
| Fluxo completo de cancelamento e devolução | Fora do problema descrito | Apenas o status `CANCELLED` é modelado |
| Busca e navegação de catálogo | Fora do problema descrito | — |
| Gestão de identidade de usuários finais | Responsabilidade do IdP existente | P-SEG-01 |
| Desenvolvimento do app móvel | Consumidor da API, não parte da plataforma | O app usa a API v2 e OIDC com PKCE |
| Conversão cambial | Preço definido por país e moeda | P-DOM-04 |
| Reconstrução exata de preços de pedidos antigos | Histórico não disponível | D-08 |
| Ativo-ativo entre regiões para o mesmo país | Custo desproporcional ao SLO de 99,9% | Perda de região com RTO de 4 h é risco aceito (P-SLO-06) |
| Terceiro país | Fora do horizonte de 90 dias | A célula por país prepara essa expansão |
| Mitigação de DDoS volumétrico | Fornecida pelo provedor de nuvem | Threat model, seção 6 |
| Migração de plataforma de orquestração | Evitar mudança de plataforma junto com mudança de arquitetura | P-INF-03 |
| Assistente Operacional em produção nos 90 dias | Prioridade às exigências do enunciado | Piloto após os 90 dias (plano, seção 8) |
| Cotação de preço com validade | Depende de decisão de negócio | Q-08 |
| API de autoatendimento para parceiros | Prazo | D-09 |

**Escopo da PoC:** a fatia executável prova apenas idempotência e compatibilidade de contrato. Gateway, OAuth2 real, células, broker e webhooks ficam fora da PoC; os limites exatos serão registrados no README de `poc/`.
