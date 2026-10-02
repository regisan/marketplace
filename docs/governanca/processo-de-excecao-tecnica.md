# Processo de exceção técnica

> Forma leve e rastreável de desviar temporariamente de uma regra (fitness function, política ou ADR) quando cumpri-la naquele momento custa mais do que o desvio. Toda exceção tem dono, justificativa, mitigação e data de expiração.

## 1. Quando usar

| Situação | Usa exceção? |
|---|---|
| Uma fitness function bloqueia uma entrega urgente e a correção definitiva leva mais tempo | Sim |
| Uma regra não se aplica a um caso específico (falso positivo) | Sim, e a regra deve ser ajustada depois |
| Correção de segurança ou obrigação legal exige mudança incompatível em contrato existente | Sim, com aprovação ampliada |
| A decisão de um ADR deixou de fazer sentido | Não. Escrever um novo ADR que substitua o anterior |
| Desligar uma regra de forma permanente | Não. Alterar a regra por PR, com justificativa |

**Não admitem exceção:** dados pessoais em eventos, webhooks ou logs (FF-01, FF-17) e dados pessoais fora da célula do titular (FF-18). São obrigações de LGPD e de residência de dados, não escolhas técnicas.

## 2. Fluxo

1. **Abrir** o registro `docs/governanca/excecoes/EXC-nnn-titulo.md` a partir do `TEMPLATE.md`, no mesmo PR que precisa da exceção.
2. **Referenciar** o identificador `EXC-nnn` no ponto de supressão da regra: comentário no ruleset do Spectral, arquivo de erros ignorados do `oasdiff` ou anotação no teste do ArchUnit.
3. **Aprovar** no próprio PR:

   | Tipo de exceção | Aprovadores |
   |---|---|
   | Regra de qualidade ou de arquitetura | Arquitetura |
   | Contrato com consumidor externo | Arquitetura e produto |
   | Segurança | Arquitetura e segurança |

4. **Prazo de decisão:** até 2 dias úteis após a abertura.
5. **Validade:** até 90 dias, renovável uma única vez com nova aprovação. Após isso, ou a regra é cumprida ou é formalmente alterada.
6. **Encerrar** ao cumprir a regra: remover a supressão e mudar o status do registro para `Encerrada`.

**Emergência.** Para correção em produção fora do horário, o PR pode ser integrado com a supressão e o registro aberto em até 1 dia útil depois, com aprovação retroativa.

## 3. Controle automático

A fitness function FF-10 (`scripts/verificar-excecoes.sh`) roda no CI e falha o build quando:

- uma exceção com status `Ativa` passou da data de expiração;
- um arquivo do repositório referencia um `EXC-nnn` que não existe ou que não está `Ativa`.

Assim, nenhuma exceção vira permanente por esquecimento.

## 4. Registro

Os registros ficam em `docs/governanca/excecoes/`. A lista de exceções ativas é revisada na reunião mensal de arquitetura, junto com a tendência: muitas exceções da mesma regra indicam que a regra precisa mudar.

| ID | Título | Regra | Status | Expira em |
|---|---|---|---|---|
| — | Nenhuma exceção registrada | — | — | — |
