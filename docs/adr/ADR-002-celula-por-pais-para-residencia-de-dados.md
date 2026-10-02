# ADR-002: Célula por país para residência de dados

- **Status:** Proposto (depende de Q-01 e Q-06)
- **Data:** 2026-10-02
- **Decisores:** Arquitetura, Segurança/DPO, Jurídico
- **Relacionados:** ADR-001, ADR-003, ADR-006; documento de IA

## Contexto

A operação no País B exige que dados pessoais dos titulares desse país sejam armazenados e processados em região do próprio país (P-PAIS-03). No Brasil, a LGPD se aplica. Hoje a plataforma roda em uma única região no Brasil (P-ASIS-05).

Os dados pessoais da plataforma estão todos em Pedidos (mapa de domínios, seção 3). O Catálogo não contém dados pessoais e pode cruzar fronteiras (P-PAIS-04). A Integração com Parceiros só manipula identificadores de pedido e dados de parceiros.

## Drivers

- Conformidade com residência de dados no País B e com a LGPD.
- Disponibilidade: falha de uma região não deve afetar o outro país.
- Custo e esforço operacional de manter várias implantações.
- Prazo de 90 dias para operar no País B.

## Alternativas consideradas

**A. Implantação única no Brasil com particionamento lógico por país.**
Prós: menor custo e uma só operação. Contras: dados pessoais do País B seriam armazenados e processados no Brasil, violando P-PAIS-03. Descartada por conformidade.

**B. Célula completa por país** (Pedidos, Catálogo, Integração e broker duplicados em cada país).
Prós: isolamento total; nenhuma dependência entre países. Contras: o Catálogo, sem dados pessoais, seria duplicado sem necessidade; a gestão de produtos passaria a ter duas fontes.

**C. Célula por país para tudo que contém ou processa dados pessoais; Catálogo central no Brasil, replicado por eventos.**
Prós: dados pessoais nunca saem do país; o Catálogo continua com uma fonte de verdade. Contras: replicação de eventos entre regiões; a edição de catálogo do País B depende da região Brasil.

## Decisão

Adotar a **alternativa C**.

- Cada célula (`BR`, `B`) contém: instâncias de Pedidos e de Integração com Parceiros, banco de Pedidos, broker regional e o Assistente Operacional, se habilitado para o país.
- O Catálogo permanece na região Brasil como fonte de verdade de produtos e preços de todos os países. Seus eventos `CatalogItemChanged` são replicados para o broker de cada célula, filtrados pelo país do preço.
- Pedidos de cada célula lê o catálogo apenas pelo read model local (ADR-003). Por isso, a indisponibilidade da região Brasil não impede a criação de pedidos no País B, apenas atrasa a propagação de mudanças de catálogo.
- O roteamento para a célula correta é feito por hostname regional (`api-br`, `api-b`), resolvido por DNS antes de qualquer terminação TLS. Cada célula tem seu próprio API Gateway, que rejeita tokens cuja claim `country` não corresponda à célula. Um gateway global único foi descartado porque terminaria TLS, e portanto processaria dados pessoais, fora do país do titular. A API v1 legada existe apenas na célula BR.
- Dados que cruzam fronteira: eventos de catálogo e métricas agregadas sem PII. Nenhum evento de pedido sai da célula de origem.
- Relatórios consolidados entre países usam apenas dados agregados ou pseudonimizados exportados por cada célula.

## Consequências

**Positivas**
- Conformidade com residência por desenho, não por configuração.
- Falha de uma célula não afeta a outra (blast radius limitado ao país).
- Preparação natural para um terceiro país: nova célula, mesmo artefato de deploy.

**Negativas e riscos**
- Custo de infraestrutura e de operação aproximadamente dobrado para Pedidos e Integração.
- Pipeline de deploy precisa promover a mesma versão para todas as células, com canário por célula.
- Dependência de existir região do provedor no País B (P-INF-01). Se não existir, esta decisão precisa ser revista com provedor local, com impacto alto em prazo.
- Gestão de catálogo do País B indisponível durante falha da região Brasil (risco aceito: não afeta vendas).

## Validação

- Política de infraestrutura como código verificada no CI: recursos com tag `data-classification=personal` só podem ser criados na região da célula correspondente.
- Teste de contrato dos eventos que cruzam fronteira: o schema de `CatalogItemChanged` não pode conter campos marcados como PII.
- Teste de recuperação: com a região Brasil indisponível em ambiente de teste, a criação de pedido no País B continua atendendo o SLO.
