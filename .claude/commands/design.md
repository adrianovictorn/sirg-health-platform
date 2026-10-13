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

Antes de invocar o agente, pergunte ao usuário (AskUserQuestion) duas coisas, porque o
subagente não consegue perguntar:

1. Se o ambiente local está no ar para a verificação visual: backend e frontend rodando e a
   sessão `sirg` do Playwright aberta e logada. Quem abre é o usuário, no terminal dele e
   **a partir da raiz do repositório** (é lá que fica `.playwright/cli.config.json`):
   `playwright-cli -s=sirg open http://localhost:5173/login --headed --persistent`, com o
   login feito por ele.
2. Se o banco local tem só dados fictícios e não está ligado à fila de federação nem ao
   WhatsApp reais. O agente pode clicar para abrir modais e abas, e em várias telas um
   clique em "Salvar" grava direto.

Inclua as respostas no prompt do agente, nestes termos: "ambiente local: no ar" ou
"ambiente local: fora do ar"; e "banco local: só dados fictícios" ou "banco local: não
confirmado" (neste caso o agente não usa `click`). Se o pedido vier com uma imagem de
referência, informe o caminho dela no prompt.

Invoque o subagente `ux-ui-designer` (Agent tool, `subagent_type: ux-ui-designer`) com o
pedido acima. Ele localiza a tela/componente sozinho (pela rota ou nome informado), lê
`CLAUDE.md`, `regulacao-frontend/DESIGN.md` e telas parecidas do mesmo módulo, aciona sob
demanda as skills de `.claude/skills/`, aplica a mudança com o menor diff visual possível e
roda lint/build do frontend.

## 3. Mostrar resultado

Mostre o relatório final do agente ao usuário: arquivos alterados, o que mudou (com o
princípio de design aplicado), suposições feitas, skills e ferramentas usadas de fato,
resultado da verificação (incluindo se a verificação visual foi executada ou não, e por
quê), e qualquer pendência para `/ajuste` ou `/feature`.

## 4. Fechamento

Não crie commit sem o usuário pedir explicitamente.
