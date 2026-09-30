---
description: Aciona o ux-ui-designer diretamente para ajustar o visual/layout de uma tela ou componente já existente no SIRG, sem passar por prompt-reviewer/architecture-explorer.
argument-hint: <o que ajustar visualmente, ex.: rota ou nome da tela + o que mudar>
---

Você vai acionar o subagente `ux-ui-designer` diretamente, sem o fluxo completo de
`/ajuste` ou `/feature`, para o seguinte pedido:

$ARGUMENTS

Este comando é para ajustes **puramente visuais** (layout, espaçamento, hierarquia,
agrupamento, estados visuais, responsividade, acessibilidade) em telas ou componentes que
já existem. Não passa por `prompt-reviewer` nem `architecture-explorer` — vá direto ao
`ux-ui-designer`.

## 1. Verificar escopo

Se o pedido do usuário envolver, além do visual, qualquer coisa de lógica, validação,
API, nomes/campos de dados ou funcionalidade nova, avise antes de prosseguir: a parte
visual pode ser feita aqui, mas a parte não-visual precisa de `/ajuste` (comportamento
existente) ou `/feature` (algo novo). Não implemente a parte não-visual você mesmo.

## 2. Executar

Invoque o subagente `ux-ui-designer` (Agent tool, `subagent_type: ux-ui-designer`) com o
pedido acima. Ele localiza a tela/componente sozinho (pela rota ou nome informado), lê
`CLAUDE.md` e telas parecidas do mesmo módulo, aplica a mudança com o menor diff visual
possível e roda lint/build do frontend.

## 3. Mostrar resultado

Mostre o relatório final do agente ao usuário: arquivos alterados, o que mudou (com o
princípio de design aplicado), suposições feitas, resultado da verificação, e qualquer
pendência para `/ajuste` ou `/feature`.

## 4. Fechamento

Não crie commit sem o usuário pedir explicitamente.
