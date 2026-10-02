# ADR-004: Snapshot de preço e descrição no item do pedido

- **Status:** Proposto (alternativa C condicionada a Q-08)
- **Data:** 2026-10-02
- **Decisores:** Arquitetura, time de Pedidos, área de auditoria/financeiro
- **Relacionados:** ADR-003, ADR-007

## Contexto

Hoje o pedido referencia apenas o SKU. Preço e descrição são obtidos do Catálogo no momento da consulta, então, depois de uma mudança no Catálogo, não é possível saber por quanto e com qual descrição um item foi vendido. Isso compromete auditoria, atendimento e disputas com parceiros.

O mapa de domínios separa **preço vigente** (Catálogo) de **preço praticado** (Pedidos). O preço praticado precisa ser imutável e pertencer ao contexto de Pedidos.

## Drivers

- Auditabilidade: reconstruir exatamente o que foi vendido, a qualquer tempo, sem depender do Catálogo.
- Retenção de 5 anos (P-PAIS-06).
- Independência do Catálogo na leitura de pedidos (coerente com ADR-003).
- Compatibilidade com o contrato v1, que expõe preço como número sem moeda (P-CTR-02).

## Alternativas consideradas

**A. Manter só a referência ao SKU e exigir histórico versionado no Catálogo.**
Prós: nenhum dado duplicado. Contras: o Catálogo passa a ser responsável por guardar todas as versões de preço por anos; a leitura de qualquer pedido antigo depende do Catálogo; a auditoria de Pedidos fica sob ownership de outro contexto.

**B. Snapshot imutável no item do pedido.**
Prós: o pedido é autossuficiente para auditoria; leitura sem dependências; ownership correto. Contras: duplicação de dados (incluída no cálculo C-04, cerca de 3 KB por pedido).

**C. Cotação de preço assinada com validade (*price quote*).**
Prós: permite honrar o preço exibido ao cliente mesmo se mudar antes da confirmação. Contras: novo fluxo e novo endpoint; só faz sentido se o negócio exigir essa garantia (Q-08).

## Decisão

Adotar a **alternativa B**. A alternativa C fica registrada como evolução futura, a ser decidida em novo ADR se Q-08 confirmar a necessidade.

Cada `OrderItem` grava um `ItemSnapshot` imutável no momento da criação, na mesma transação do pedido:

| Campo | Origem | Observação |
|---|---|---|
| `sku` | Requisição | — |
| `quantity` | Requisição | — |
| `unitPrice` | Read model de catálogo | Decimal com escala fixa, nunca ponto flutuante |
| `currency` | Read model de catálogo | ISO 4217 |
| `description` | Read model de catálogo | Texto vigente no momento da venda |
| `catalogVersion` | Read model de catálogo | Permite cruzar com o histórico de eventos do Catálogo |
| `capturedAt` | Pedidos | Instante da captura |
| `snapshotSource` | Pedidos | `CAPTURED` para pedidos novos, `RECONSTRUCTED` para backfill |

- **Imutabilidade:** não há operação de atualização sobre `ItemSnapshot`. Correções de preço após a venda são novos fatos de domínio (por exemplo, ajuste ou estorno), fora do escopo atual.
- **API v2** expõe o snapshot completo, com `currency`. **API v1** continua expondo `price` como número, agora lido do snapshot, mantendo o formato atual.
- **Pedidos históricos:** backfill de melhor esforço usando o preço vigente ou o histórico disponível, marcado como `RECONSTRUCTED` (P-DOM-07).
- **Fase de 30 dias:** antes do read model (ADR-003, previsto para 60 dias), o snapshot é capturado a partir das respostas das chamadas atuais ao Catálogo. O formato gravado é o mesmo, então a troca da fonte é transparente.

## Consequências

**Positivas**
- Auditoria completa de pedidos novos desde a fase de 30 dias.
- Leitura de pedidos totalmente independente do Catálogo.
- O campo `catalogVersion` permite investigar divergências com precisão.

**Negativas e riscos**
- Pedidos anteriores à mudança têm auditoria limitada (risco aceito, explícito no campo `snapshotSource`).
- Correção de descrições erradas não se propaga para pedidos já feitos; isso é intencional, mas precisa ser comunicado ao atendimento.

## Validação

- Teste de integração: após um `CatalogItemChanged` alterando preço e descrição, um pedido criado antes mantém os valores originais em v1 e v2.
- Restrição no banco impedindo `UPDATE` na tabela de snapshots (permissão revogada para o usuário da aplicação ou trigger).
- Consulta de auditoria documentada no runbook: "valor vendido do SKU X entre as datas A e B" respondida apenas com dados de Pedidos.
