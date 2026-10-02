## O que muda

<!-- Resumo da mudança e motivação. -->

## Checklist de arquitetura

- [ ] **Contratos:** esta mudança altera `poc/docs/openapi` ou `poc/docs/asyncapi`?
  - [ ] Não
  - [ ] Sim, e a classe é: aditiva / correção / comportamental / incompatível (ver `docs/governanca/politica-de-evolucao-de-contratos.md`)
  - [ ] `info.version` e o histórico do contrato foram atualizados
- [ ] **Decisões:** esta mudança altera ou contradiz algum ADR? Se sim, há um ADR novo neste PR.
- [ ] **Dados pessoais:** nenhum dado pessoal novo em eventos, webhooks ou logs.
- [ ] **Exceções:** se alguma regra foi suprimida, o registro `EXC-nnn` está neste PR e foi aprovado.
- [ ] **Rollback:** a mudança pode ser desligada por flag ou revertida sem migração destrutiva.
