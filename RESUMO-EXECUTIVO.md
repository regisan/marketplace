# Resumo executivo

## Situação

A plataforma de Pedidos e Catálogo atende hoje o canal web nacional. Em 90 dias precisa suportar aplicativo móvel, parceiros de marketplace e um segundo país, com volume 10 vezes maior e sem interromper quem já a utiliza. Na arquitetura atual, isso não é possível com segurança: pedidos podem ser duplicados em novas tentativas, o preço vendido não fica registrado para auditoria, eventos podem se perder e uma lentidão no Catálogo derruba a criação de pedidos.

## Recomendação

**Evoluir a plataforma existente em três fases de 30 dias, sem reescrevê-la**, criando um único serviço novo. Cada mudança entra desligada, é ativada gradualmente e pode ser revertida em minutos.

| Fase | O que entrega ao negócio |
|---|---|
| **Dias 1 a 30** | Fim de pedidos duplicados, preço vendido auditável e eventos confiáveis ao ERP, sem mudar nenhuma integração existente |
| **Dias 31 a 60** | Criação de pedidos independente do Catálogo, nova API para app e parceiros, notificações automáticas aos parceiros, piloto com 1 ou 2 parceiros e beta do app |
| **Dias 61 a 90** | Operação no segundo país com dados pessoais mantidos no próprio país; capacidade para 10 vezes o volume comprovada em teste |

Três compromissos atravessam todas as fases: **nenhuma janela de indisponibilidade**, **nenhum consumidor atual afetado por pelo menos 6 meses** e **dados pessoais nunca enviados a outro país, a parceiros em notificações ou a ferramentas de IA**.

## O que já está comprovado

A decisão mais crítica foi provada em código executável, com um único comando:

- **Nenhum pedido duplicado**, mesmo com 20 tentativas simultâneas do mesmo pedido, verificadas disputando o mesmo registro no banco de dados.
- **Nenhuma integração atual quebrada**: um cliente escrito para a API de hoje cria e consulta pedidos na versão evoluída sem qualquer alteração.

Os contratos de API e as regras de arquitetura são verificados automaticamente a cada mudança, impedindo regressões.

## Investimento

| Item | Faixa | Esperado |
|---|---|---|
| Fase 1 (30 dias, 6 pessoas, 2 delas em meio período) | R$ 138 mil a R$ 297 mil | **R$ 187 mil** |
| Fases 2 e 3 (60 dias, equipe ampliada para 9 pessoas) | R$ 389 mil a R$ 658 mil | R$ 524 mil |
| **Total dos 90 dias** | **R$ 527 mil a R$ 955 mil** | **R$ 711 mil** |
| Operação na AWS após 90 dias, com volume 10x | R$ 37 mil a R$ 77 mil por mês | — |

**Solicitação imediata:** aprovar **R$ 215 mil** para a fase 1 (cenário esperado com reserva de 15%). Cerca de 99% do custo da fase 1 é de pessoas; a infraestrutura adicional é inferior a R$ 7 mil. Os valores usam custo-hora de R$ 130 a R$ 220 e preços públicos da AWS em São Paulo, a confirmar na calculadora oficial.

## Principais riscos

| Risco | Nível | Mitigação |
|---|---|---|
| Escopo das fases 2 e 3 maior que a equipe inicial | Alto | Decidir a ampliação até o dia 25; ordem de corte de escopo já definida |
| Provedor de nuvem sem região no segundo país | Alto | Confirmar até o dia 30; alternativa com provedor local, com impacto em prazo |
| Requisitos de residência de dados do segundo país diferentes dos assumidos | Alto | Desenho para o cenário mais rígido; ajustável quando o país for definido |
| Integrações atuais desconhecidas | Médio | Medição de tráfego por cliente antes de qualquer desligamento |
| Volume real diferente do projetado | Médio | Arquitetura escala horizontalmente; testes recalibrados com dados reais |

Débitos técnicos aceitos conscientemente (como manter duas versões da API por 6 meses) e o registro completo de 18 riscos estão em `docs/06-riscos-e-fora-de-escopo.md`.

## Decisões que precisam de validação

| Decisão ou informação | Responsável | Prazo |
|---|---|---|
| Confirmar se já existe infraestrutura de mensageria em produção | Tecnologia | Dia 3 |
| Aprovar o orçamento da fase 1 e a data de início, evitando períodos de congelamento comercial | Diretoria | Antes do dia 0 |
| Confirmar a composição e a experiência da equipe; decidir a ampliação para as fases 2 e 3 | Diretoria e Tecnologia | Dia 25 |
| Definir o segundo país e seus requisitos de residência de dados | Negócio e Jurídico | Dia 30 |
| Confirmar disponibilidade de região de nuvem nesse país | Tecnologia | Dia 30 |
| Informar o volume atual real de pedidos | Negócio | Dia 30 |
| Decidir se o preço exibido ao comprador deve ser honrado quando muda antes da confirmação | Negócio | Dia 30 |
| Selecionar 1 ou 2 parceiros para o piloto | Negócio | Dia 40 |
| Definir o papel dos parceiros perante a LGPD e o tratamento de pedidos de exclusão de dados | Jurídico e DPO | Dia 60 |
| Listar todos os sistemas que usam a API atual | Tecnologia | Dia 60 |

## Diferencial: IA no suporte operacional

A arquitetura já prevê um assistente para a equipe de atendimento consultar pedidos e notificações em linguagem natural, apenas para leitura, com as mesmas permissões do operador e sem enviar dados pessoais ao provedor de IA. A proposta é um piloto com 10 operadores após os 90 dias, condicionado a critérios de qualidade medidos em avaliação automatizada.

## Próximos passos

1. Responder às três primeiras validações da tabela acima.
2. Iniciar a fase 1, com primeira entrega em produção na semana 3.
3. Revisão de go/no-go ao fim de cada fase, com base nos critérios de aceite automatizados.
