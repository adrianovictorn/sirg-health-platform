---
titulo: Decisões (ADRs)
tags:
  - sirg/mapa
gerado: manual
---

# Registros de decisão (ADRs)

Esta pasta guarda decisões de arquitetura ou de produto que valem a pena registrar por
escrito: por que uma abordagem foi escolhida em vez de outra, e o que ficou de fora de
propósito. Não é changelog (isso é `docs/referencia/Changelog.md`, gerado) nem
especificação de feature (isso é `docs/especificacoes/`).

Registre uma decisão aqui quando:

- A escolha não é óbvia lendo o código — alguém vai perguntar "por que não fizemos X?".
- Existiu uma alternativa real, descartada por um motivo concreto (performance, dado
  legado, prazo, compatibilidade com outra instância do sistema).
- A decisão afeta mais de uma área (ex.: schema + service + frontend) e vai guiar decisões
  futuras parecidas.

Não registre aqui decisões triviais, óbvias a partir do código, ou específicas de uma
tarefa isolada sem consequência futura.

## Formato

Um arquivo por decisão: `NNNN-titulo-curto.md`, numeração sequencial (`0001-`, `0002-`,
...). Use este esqueleto:

```markdown
---
titulo: <título curto>
tags:
  - sirg/adr
status: proposta | aceita | substituída
data: AAAA-MM-DD
substitui: <opcional — nome do ADR anterior>
---

# NNNN — <Título>

## Contexto

O que motivou a decisão. Qual problema, restrição ou pedido estava em jogo.

## Decisão

O que foi decidido, em uma ou duas frases diretas.

## Alternativas consideradas

O que mais foi cogitado e por que foi descartado.

## Consequências

O que essa decisão facilita, o que ela custa, e o que fica proibido ou desencorajado
depois dela.
```

Mantenha em Markdown simples, compatível com Obsidian (links `[[assim]]` funcionam para
referenciar outras notas do vault, como mapas ou seções técnicas relevantes).
