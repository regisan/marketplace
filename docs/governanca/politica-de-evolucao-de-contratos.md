# Política de evolução de contratos

> Regras para alterar APIs HTTP, eventos e webhooks sem quebrar consumidores. Detalha a decisão do ADR-007; quando houver conflito, o ADR prevalece até ser substituído.

## 1. Escopo

| Contrato | Arquivo | Consumidores |
|---|---|---|
| API v1 | `poc/docs/openapi/orders-v1.yaml` | Frontend web, backoffice, ERP |
| API v2 | `poc/docs/openapi/orders-v2.yaml` | App móvel, frontend web, parceiros de marketplace, backoffice |
| Webhook `orderStatusChanged` | Seção `webhooks` da API v2 | Parceiros de marketplace |
| Eventos de pedido | `poc/docs/asyncapi/order-events.yaml` | Integração com Parceiros, ERP |

APIs internas (por exemplo, transições de status) seguem as mesmas regras de classificação, com comunicação apenas aos times internos.

## 2. Princípios

1. **O contrato vem antes do código.** Toda mudança começa no arquivo de contrato, no mesmo pull request da implementação ou antes dele.
2. **Dentro de uma versão maior, só mudanças aditivas.**
3. **Consumidores são leitores tolerantes.** Ignoram campos desconhecidos e tratam valores desconhecidos de enum sem falhar. Essa obrigação está escrita nos próprios contratos.
4. **Ninguém é surpreendido.** Toda descontinuação é anunciada nos headers, na documentação e diretamente aos consumidores identificados.

## 3. Classificação de mudanças

| Classe | Exemplos em APIs | Exemplos em eventos e webhooks | Versão | Aprovação |
|---|---|---|---|---|
| **Aditiva** | Novo endpoint; novo campo na resposta; novo parâmetro ou campo opcional na requisição; novo header opcional; nova resposta de erro que só ocorre com uso de algo novo | Novo campo opcional no payload; novo header opcional; novo tipo de evento | MINOR | Revisão normal do PR |
| **Correção** | Descrição, exemplo ou erro de digitação no contrato, sem mudança de comportamento | Idem | PATCH | Revisão normal do PR |
| **Comportamental** | Mesmo formato com semântica diferente: novo valor de enum, novo código de erro para situação já existente, mudança de limite (por exemplo, máximo de itens) | Novo valor de `status`; mudança na frequência ou ordem de publicação | MINOR | Arquitetura; comunicação prévia de 30 dias a consumidores externos |
| **Incompatível** | Remover ou renomear campo, endpoint ou parâmetro; mudar tipo ou formato; tornar obrigatório algo opcional; restringir valores aceitos | Remover ou renomear campo; mudar tipo; mudar a chave de partição | MAJOR: nova rota `/vN` ou novo `eventType` com sufixo de versão | Arquitetura e produto; ADR quando mudar uma decisão |

Mudanças incompatíveis nunca são feitas na versão existente. A versão nova é publicada em paralelo e a anterior segue o ciclo de vida da seção 5. A única via para alterar de forma incompatível uma versão existente é o processo de exceção técnica, e apenas por correção de segurança ou obrigação legal.

## 4. Fluxo de uma mudança

1. Alterar o contrato e incrementar `info.version` de acordo com a classe.
2. Registrar a mudança no histórico do próprio contrato (seção de descrição) e no changelog público, se houver consumidor externo.
3. Abrir o PR com o checklist do template, indicando a classe da mudança.
4. O CI executa as fitness functions de contrato (`fitness-functions.md`, FF-01 a FF-06). Mudança incompatível dentro da mesma versão maior falha o build.
5. Testes de consumidor dos consumidores conhecidos precisam passar (FF-13).
6. Após o merge e o deploy, a versão publicada passa a ser a nova linha de base.

**Linhas de base.** A pasta `openapi/baseline/` guarda a última versão publicada de cada versão maior que tenha consumidores. O `oasdiff` compara o contrato atual com a linha de base (FF-05) e com a versão em `main` (FF-06). A linha de base `orders-v1.0.0.yaml` representa o contrato que os consumidores atuais usam hoje e só é substituída quando todos eles tiverem sido validados contra a versão seguinte.

## 5. Ciclo de vida de uma versão maior

| Estágio | O que acontece | Duração mínima |
|---|---|---|
| **Ativa** | Recebe evolução aditiva | — |
| **Depreciada** | Respostas com header `Deprecation`; documentação marca a versão como depreciada; consumidores identificados recebem comunicação direta com o guia de migração | A partir do lançamento da sucessora |
| **Desligamento anunciado** | Header `Sunset` com a data; lembretes 60, 30 e 7 dias antes | 6 meses após o lançamento da sucessora |
| **Desligada** | Rota retorna `410 Gone` por 30 dias com link para a migração; depois é removida | Exige 30 dias consecutivos sem tráfego antes de entrar neste estágio |

Para eventos, o ciclo é o mesmo: o `eventType` antigo continua sendo publicado em paralelo ao novo até o fim da janela e até que nenhum grupo de consumidores o leia por 30 dias.

## 6. Registro de consumidores

| Contrato | Como os consumidores são identificados |
|---|---|
| APIs | `client_id` do token, registrado pelo gateway por versão e rota |
| Eventos | Grupos de consumidores com permissão de leitura no tópico (ACL do broker) |
| Webhooks | Assinaturas ativas na Integração com Parceiros |

O painel de tráfego por versão e por `client_id` (fase 2 do plano) é a fonte para decidir desligamentos e para encontrar consumidores não catalogados (Q-04).

## 7. Responsabilidades

| Papel | Responsabilidade |
|---|---|
| Time dono do contrato | Classificar a mudança, manter o histórico, implementar a compatibilidade |
| Arquitetura | Aprovar mudanças comportamentais e incompatíveis; manter esta política |
| Produto | Aprovar novas versões maiores e datas de desligamento; comunicação com parceiros |
| Consumidores internos | Manter testes de consumidor atualizados e migrar dentro da janela |
