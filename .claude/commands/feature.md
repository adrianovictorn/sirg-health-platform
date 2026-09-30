---
description: Orquestra prompt-reviewer → architecture-explorer → aprovação → implementação → code-reviewer para algo NOVO no SIRG.
argument-hint: <descrição da funcionalidade nova>
---

Você vai orquestrar a criação de algo novo no SIRG a partir da descrição do usuário:

$ARGUMENTS

Siga estas etapas **na ordem**, sem pular nenhuma e sem implementar antes da etapa 5.

Após a implementação (etapa 5), se a tela/componente novo precisar de acabamento visual
(layout, espaçamento, hierarquia, agrupamento, estados visuais, responsividade,
acessibilidade), acione o subagente `ux-ui-designer` sobre os arquivos recém-criados antes
da revisão final (etapa 6). Ele preserva toda a lógica implementada e ajusta só o visual.

## 1. Revisão do pedido

Invoque o subagente `prompt-reviewer` (Agent tool, `subagent_type: prompt-reviewer`) com o
pedido acima. Mostre o resultado completo ao usuário (pedido reescrito, critérios de
aceite, ambiguidades, riscos).

## 2. Pausa

Pare aqui. Peça ao usuário para responder as ambiguidades levantadas (ou confirmar que não
há nenhuma). Não prossiga sem essa resposta.

## 3. Exploração de arquitetura

Com o pedido revisado e as respostas do usuário, invoque o subagente
`architecture-explorer` (modo padrão — algo novo, não ajuste). Mostre o plano completo ao
usuário (arquivos afetados, padrões a seguir, plano passo a passo, impacto em
banco/migrations, o que pode quebrar).

## 4. Pausa

Pare aqui. Peça ao usuário para escrever "aprovado" ou pedir ajustes ao plano. Se pedir
ajustes, repita a etapa 3 com o plano corrigido antes de prosseguir. Não implemente nada
antes de receber "aprovado" (ou equivalente claro).

## 5. Implementação

Implemente exatamente o plano aprovado. Não adicione escopo que não estava no plano; se
notar necessidade de algo a mais durante a implementação, pare e avise o usuário em vez de
decidir sozinho.

## 6. Revisão final

Invoque o subagente `code-reviewer`, passando o plano aprovado como referência. Mostre o
relatório completo ao usuário.

## 7. Fechamento

Liste os arquivos criados/alterados e sugira uma mensagem de commit. Não crie o commit sem
o usuário pedir explicitamente.
