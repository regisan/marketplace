# Uso de IA na elaboração da proposta

> Atende ao entregável "documento explicativo da ferramenta de IA utilizada, motivo da escolha, prompts relevantes, validações e cuidados com dados" e ao critério "uso eficaz de IA". A capacidade de IA proposta para o produto está em outro documento: [assistente-de-pedidos.md](assistente-de-pedidos.md).

## 1. Ferramentas e motivo da escolha

| Ferramenta | Uso | Por que foi escolhida |
|---|---|---|
| **Claude (claude.ai)** | Análise crítica do enunciado, premissas, ADRs, diagramas como código, contratos, documentos de qualidade, segurança, plano e estimativa; revisão cruzada entre artefatos; especificação da PoC | Raciocínio longo sobre muitos documentos inter-relacionados; geração direta de arquivos (Markdown, PlantUML, YAML, scripts); execução de validações em ambiente isolado; pesquisa na web para fatos que mudam com o tempo |
| **Claude Code** | Implementação da PoC a partir de uma especificação escrita antes do código | Trabalha diretamente no repositório, executa build e testes a cada etapa, faz commits pequenos; o modo de planejamento permite revisar o plano antes de qualquer edição |

As duas ferramentas foram usadas em papéis distintos e encadeados: o Claude produziu a especificação (`poc/CLAUDE.md`), e o Claude Code a executou. A separação força as decisões de arquitetura a ficarem registradas em documentos, e não implícitas no código.

## 2. Divisão de responsabilidades

| O autor decidiu | A IA propôs ou produziu |
|---|---|
| Aceitar ou ajustar cada premissa e cada ADR | Rascunhos de documentos, alternativas e trade-offs |
| Ferramentas e padrões (C4-PlantUML, local dos contratos, AWS como referência, faixa de custo-hora) | Estrutura dos artefatos e conteúdo inicial |
| Aprovar o plano da PoC e cada etapa da implementação | Código, testes e notas de implementação |
| Resolver inconsistências que envolviam contratos | Identificação das inconsistências e opções de resolução |
| O que entra na entrega | Revisões cruzadas e correções |

Nenhum artefato foi aceito sem revisão. Contratos e ADRs foram marcados como fontes de verdade que a IA de implementação não podia alterar.

## 3. Fluxo de trabalho

A proposta foi construída em etapas, cada uma usando as anteriores como insumo, o que permitiu verificar a coerência a cada passo:

1. Crítica do enunciado e levantamento de lacunas e contradições
2. Premissas explícitas, com impacto caso estejam erradas, e questões ao cliente
3. Mapa de domínios, antes dos ADRs, para fixar o vocabulário
4. Oito ADRs, em ordem de dependência
5. Diagramas C4 e de sequência, usados também como teste de consistência dos ADRs
6. Contratos OpenAPI e AsyncAPI, com linha de base de compatibilidade e regras Spectral
7. Atributos de qualidade, resiliência, threat model, plano 30/60/90, riscos e estimativa
8. Governança, capacidade de IA e especificação da PoC
9. Implementação da PoC com o Claude Code, etapa por etapa
10. README e este documento

## 4. Prompts relevantes

### No Claude

| Etapa | Prompt (resumido) | Resultado |
|---|---|---|
| Início | "Este é um desafio técnico para uma vaga de arquiteto. Quero orientações de como resolvê-lo. Faça críticas ao enunciado e veja como posso contornar inconsistências ou falta de informações." | Onze críticas ao enunciado (volumes ausentes, país não nomeado, descomissionamento em 90 dias contra compatibilidade de 6 meses, PoC com "uma" decisão contra critério crítico duplo, dois sentidos de "IA") e a estratégia de premissas explícitas |
| Premissas | "Monte o arquivo de premissas." | Premissas com ID, justificativa, impacto e status; cálculos derivados (N+1, disponibilidade composta) que depois sustentaram os ADRs |
| Ordem | "Assumindo que as premissas estão corretas, podemos criar as ADRs ou é necessário avaliar outros pontos?" | Identificação do mapa de domínios como pré-requisito real, para evitar vocabulário inconsistente entre ADRs |
| Sequenciamento | "É melhor tratar o diagrama de sequência ou os contratos de APIs? Qual ordem faz mais sentido?" | Sequência antes dos contratos: os desfechos desenhados viraram as respostas da OpenAPI |
| Estimativa | Manter a faixa de custo-hora; usar a AWS como referência | Estimativa de baixo para cima com verificação de capacidade e preços pesquisados para `sa-east-1` |
| PoC | "Crie a especificação da PoC para o Claude Code. O CLAUDE.md deve estar na raiz ou pode estar em subpasta?" | Especificação em `poc/CLAUDE.md`, importada pelo `CLAUDE.md` da raiz, com base na documentação do Claude Code sobre carregamento de arquivos de memória |

### No Claude Code

| Momento | Prompt-base | Objetivo |
|---|---|---|
| Plano, em modo de planejamento | Ler a especificação, contratos e ADRs; listar inconsistências; indicar versões e biblioteca de validação OpenAPI 3.1; propor plano em 8 etapas; explicar como o teste de concorrência garante simultaneidade real | Revisar decisões antes de qualquer edição |
| Cada etapa | Implementar apenas a etapa N; rodar `./mvnw verify`; atualizar as notas; commitar; parar para revisão | Incrementos pequenos e verificáveis |
| Idempotência | Escrever os testes antes e mostrá-los falhando; depois implementar | Evidência de que os testes detectam o problema |
| Final | Verificar a definição de pronto item por item, com evidência | Fechamento auditável |

O registro completo da implementação, com 4 inconsistências e 31 decisões, está em [`poc/NOTAS-DE-IMPLEMENTACAO.md`](../../poc/NOTAS-DE-IMPLEMENTACAO.md).

## 5. Validação das saídas

A IA acelera a produção, mas a maior parte do valor veio da validação. Foram usadas quatro formas.

### 5.1 Validações automatizadas

| O que | Como |
|---|---|
| YAML dos contratos e workflows | Parse e resolução de todas as referências internas (`$ref`) |
| Regras de governança | Scripts `verificar-adrs.sh` e `verificar-excecoes.sh` testados no repositório real e em uma cópia com erros propositais (seção ausente, ADR fora do índice, exceção vencida, referência inexistente) |
| Links entre documentos | Verificação de todos os links relativos dos Markdown |
| Diagramas | Renderização local de todos os arquivos PlantUML pelo autor |
| PoC | `./mvnw verify` ao fim de cada etapa; respostas validadas contra os contratos OpenAPI 3.1 e o schema do AsyncAPI dentro dos próprios testes |

### 5.2 Revisão cruzada entre artefatos

Cada documento novo foi confrontado com os anteriores. Isso encontrou falhas que nenhum documento isolado revelava:

| Falha encontrada | Como foi detectada | Correção |
|---|---|---|
| Roteamento por claim em gateway global processaria dados pessoais do País B no Brasil | Desenho do C4 TO-BE | ADR-002: hostname regional e gateway por célula |
| "Erro explícito" na contingência do Catálogo misturava SKU inexistente e Catálogo indisponível | Desenho do diagrama de sequência | ADR-003: `422` e `503` com `Retry-After` |
| Transação "curta" da idempotência dependia de um read model que só existe na fase 2 | Revisão do ADR-005 contra o plano | ADR-005: chamadas ao Catálogo antes da transação na fase 1 |
| No PostgreSQL, a transação fica inválida após a violação de unicidade | Revisão técnica do algoritmo | ADR-005: desfazer e ler em nova leitura |
| Rastreamento ponta a ponta exigido por cenário de auditoria sem suporte no contrato de eventos | Cenário QA-AUD-03 contra o AsyncAPI | Header `traceparent` opcional |
| Soma dos timeouts internos poderia passar do timeout do gateway | Montagem do orçamento de tempo | Prazo total de 4,5 s por requisição |
| IDs sequenciais da v1 permitem sondar pedidos mesmo com verificação de dono | Threat model contra o ADR-007 | Risco residual API-02 aceito com prazo |
| Papel dos parceiros perante a LGPD indefinido | Seção de LGPD do threat model | Questão Q-13 ao cliente |
| Assistente consultava entregas sem relação no C4 nem política de resiliência | Proposta de IA contra o C4 | Novas relações no C4 e linha na política |
| Valor de exemplo com dois-pontos tornava o YAML da v2 inválido | Parse automatizado | Valor entre aspas |

### 5.3 Pesquisa para fatos que mudam com o tempo

Informações sujeitas a mudança não foram tiradas da memória do modelo:

| Fato | Efeito na proposta |
|---|---|
| Versão atual do Spring Boot | A linha 3.x estava sem suporte OSS desde junho de 2026; especificação, C4 e plano passaram para Spring Boot 4 |
| Preços da AWS em `sa-east-1` e câmbio | Estimativa com fontes citadas e fator regional explícito onde não havia preço publicado |
| Carregamento do `CLAUDE.md` e modo de planejamento do Claude Code | Arquivo na raiz com importação da especificação; roteiro de uso baseado na documentação oficial |

### 5.4 Achados durante a implementação

O Claude Code encontrou lacunas na especificação, registrou-as sem alterar os contratos e propôs resoluções revisadas pelo autor:

| Achado | Tratamento |
|---|---|
| Hash pela rota literal trataria `/orders` e `/v1/orders` como operações diferentes (I-3) | Hash pela operação lógica; incorporado ao ADR-005 |
| Hash pelo corpo bruto faria campos ignorados gerarem `422` (I-4) | Hash pelo DTO desserializado; incorporado ao ADR-005 |
| Chave vencida colidiria com a chave primária (I-2) | Remoção do registro vencido na mesma transação; incorporado ao ADR-005 |
| Repetição após mudança de preço receberia `409` em vez da resposta original (D-17) | Leitura prévia do registro válido; incorporada ao ADR-005 |
| Pedido criado pela v1 não tem nome e e-mail exigidos pela resposta da v2 (I-1) | Pendente: ajuste do contrato v2 pelo processo de governança |
| Pool de 10 conexões serializaria parte das 20 requisições do teste de concorrência (D-6) | Pool de 30 no perfil de teste, com verificação em `pg_stat_activity` de que as 20 transações disputam a mesma linha |
| `query().stream()` mantinha conexões abertas | Exposto pelo teste de concorrência; trocado por `.list()` |

Dois desses achados merecem destaque: o teste de concorrência foi desenhado para provar disputa real no banco, e não apenas envio simultâneo, e foi justamente ele que revelou um vazamento de conexões invisível nos testes sequenciais.

### 5.5 O que não foi validado

- Execução dos workflows `governanca.yml` e `poc.yml` no GitHub Actions (validados apenas localmente ou quanto à sintaxe).
- Caminho de instalação do `oasdiff` e comando de desativação de métricas da AsyncAPI CLI, que variam entre versões.
- Preços da AWS obtidos de agregador; devem ser confirmados na AWS Pricing Calculator.

## 6. Cuidados com dados

| Cuidado | Como foi aplicado |
|---|---|
| Nenhum dado real | Apenas o texto público do desafio foi compartilhado; a PoC usa catálogo e tokens sintéticos, criados por migração separada e carregados só nos perfis de teste e local |
| Nenhum segredo | Não foram usadas credenciais reais; os tokens da PoC são fictícios e o filtro que os resolve está documentado como inadequado para produção |
| Contexto mínimo | Cada ferramenta recebeu apenas os arquivos necessários à etapa |
| Controle sobre ações | Claude Code iniciado em modo de planejamento, com aprovação de plano e de comandos; trabalho em branch separada; nenhum push feito pela ferramenta |
| Fontes de verdade protegidas | Contratos e ADRs declarados como não editáveis pela IA de implementação; inconsistências relatadas em vez de corrigidas silenciosamente |
| Uso corporativo | Em um projeto real, verificar as configurações de privacidade e retenção da conta e a política da empresa antes de compartilhar código ou dados internos com qualquer ferramenta de IA |

## 7. Lições

1. **Especificar antes de gerar código.** A especificação escrita antes da implementação deu ao Claude Code critérios objetivos e permitiu que ele apontasse lacunas em vez de improvisar.
2. **A revisão cruzada é onde a IA mais ajuda.** A maioria das correções não veio de erros factuais, mas de incoerências entre documentos, encontradas ao produzir um artefato a partir de outro.
3. **Fatos voláteis exigem pesquisa.** Versões de framework e preços de nuvem mudam; usar a memória do modelo teria levado a uma PoC iniciada em versão sem suporte.
4. **Testes precisam provar que detectam o problema.** Exigir disputa real no banco, verificada em `pg_stat_activity`, tornou a evidência de idempotência confiável e ainda revelou um defeito que os testes sequenciais não mostravam.
5. **A decisão continua humana.** Em todos os pontos de bifurcação (premissas, ferramentas, localização de arquivos, resolução de inconsistências), a escolha foi do autor.
