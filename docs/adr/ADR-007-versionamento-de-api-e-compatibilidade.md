# ADR-007: Versionamento da API pública e política de compatibilidade

- **Status:** Proposto
- **Data:** 2026-10-02
- **Decisores:** Arquitetura, times de Pedidos e Integração com Parceiros, responsáveis pelos consumidores v1
- **Relacionados:** ADR-004, ADR-005, ADR-008; política de evolução de contratos em `docs/governanca/`

## Contexto

A API atual de Pedidos não tem versão explícita (`/orders`), expõe IDs sequenciais e preço sem moeda, e não padroniza erros (P-CTR-01, P-CTR-02). Seus consumidores (frontend web, backoffice, ERP) precisam continuar funcionando por pelo menos 6 meses. Parceiros de marketplace pedem uma API pública versionada, e o app móvel e o País B precisam de capacidades que o contrato atual não comporta (moeda, idempotência, referência externa).

Eventos também são contratos: `OrderCreated`, `OrderStatusChanged` e `CatalogItemChanged` têm consumidores em outros contextos e, indiretamente, nos parceiros.

## Drivers

- Nenhuma quebra para consumidores atuais por pelo menos 6 meses.
- Contrato claro e estável para parceiros externos.
- Evolução sem coordenação de deploy entre produtor e consumidores.
- Detecção automática de quebra antes do merge.

## Alternativas consideradas

**A. Versão maior na URI (`/v1/...`, `/v2/...`).**
Prós: explícita, fácil de rotear no gateway, de documentar e de observar por versão; padrão familiar para parceiros. Contras: URIs diferentes para o mesmo recurso.

**B. Versão por header ou media type** (`Accept: application/vnd.empresa.order.v2+json`).
Prós: URI estável. Contras: menos visível para parceiros; mais difícil de testar manualmente, cachear e observar; erros de negociação são confusos.

**C. Sem versionamento, apenas evolução aditiva.**
Prós: um único contrato. Contras: as mudanças necessárias (moeda obrigatória, novo formato de ID, idempotência obrigatória) são incompatíveis por natureza; não há como fazê-las sem versão nova.

## Decisão

Adotar a **alternativa A** para a API HTTP, com uma política de compatibilidade única para APIs e eventos.

**Rotas e implementação**
- A rota atual `/orders` continua respondendo como **v1**, sem nenhuma mudança de comportamento para quem não envia headers novos. O gateway também a expõe como `/v1/orders`.
- A **v2** fica em `/v2/orders`: IDs UUIDv7, preço com moeda, `Idempotency-Key` obrigatório (ADR-005), `externalReference` para parceiros, erros no formato RFC 9457 (Problem Details).
- Ambas as versões são adaptadores sobre o **mesmo domínio**. O adaptador v1 traduz o modelo novo para o formato antigo. O pedido guarda também o `legacyId` numérico, para que um pedido criado pela v2 possa ser consultado pela v1 e vice-versa.
- A API v1 existe apenas na célula BR (ADR-002).

**Política de compatibilidade (APIs e eventos)**
- Dentro de uma versão maior, somente mudanças aditivas: novos campos opcionais na requisição, novos campos na resposta, novos endpoints, novos valores de enum em campos documentados como extensíveis.
- São mudanças incompatíveis, que exigem nova versão maior: remover ou renomear campo, mudar tipo ou formato, tornar obrigatório um campo opcional, mudar semântica de status HTTP ou de código de erro.
- Consumidores devem ser leitores tolerantes: ignorar campos desconhecidos (P-CTR-06).
- Eventos seguem a mesma regra; o schema JSON de cada evento é versionado no repositório e uma mudança incompatível gera novo `eventType` com sufixo de versão, publicado em paralelo durante a transição.

**Ciclo de vida**
- Ao lançar uma nova versão maior, a anterior recebe os headers `Deprecation` e `Sunset` e entra em comunicação ativa com os consumidores identificados.
- Desligamento somente quando **as duas** condições forem atendidas: pelo menos 6 meses desde o lançamento da sucessora e 30 dias consecutivos sem tráfego (P-CTR-04).
- Tráfego medido por versão e por `client_id` no gateway, o que também revela consumidores desconhecidos (Q-04).

**Exceção técnica:** uma mudança incompatível dentro da mesma versão só pode ser feita por correção de segurança ou obrigação legal, seguindo o processo de exceção técnica (`docs/governanca/processo-de-excecao-tecnica.md`), com aprovação da arquitetura e de produto e comunicação prévia aos consumidores afetados.

## Consequências

**Positivas**
- Consumidores atuais continuam funcionando sem nenhuma alteração.
- Parceiros recebem um contrato moderno e estável desde o início.
- Quebras são detectadas no CI, não em produção.

**Negativas e riscos**
- Duas versões a manter por pelo menos 6 meses, com adaptador de tradução e testes para ambas.
- O campo `legacyId` e o adaptador v1 são débito técnico aceito, com prazo de remoção vinculado ao desligamento da v1.
- Consumidores v1 não identificados podem atrasar o desligamento; isso é preferível a quebrá-los.

## Validação

- **CI, a cada pull request:** `oasdiff breaking` compara a especificação OpenAPI alterada com a versão em `main`; qualquer quebra dentro da mesma versão maior falha o build.
- **CI:** validação de schemas de eventos contra a versão anterior (somente mudanças aditivas permitidas).
- **Teste de consumidor (PoC):** um cliente escrito contra o contrato v1 reconstruído executa criação e consulta contra o serviço que já expõe a v2, e passa sem alterações.
- **Teste cruzado (PoC):** pedido criado pela v2 é consultado pela v1 com formato v1 válido.
- **Produção:** dashboard de tráfego por versão e por `client_id`.
