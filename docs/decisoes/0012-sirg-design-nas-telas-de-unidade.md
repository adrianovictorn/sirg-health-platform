---
titulo: sirg-design (fonte única) nas telas de Unidade e tag de papel da unidade
tags:
  - sirg/adr
status: aceita
data: 2026-10-14
substitui:
---

# 0012 — sirg-design nas telas de Unidade e tag de papel da unidade

## Contexto

O visual que mistura as cores do SIRG com a estrutura do Carbon/IBM nasceu em `/admin/cotas`
(ADR 0005) e foi copiado, tela a tela, para `/unidade/cotas` (0006), `/agendar` (0007) e o
módulo da unidade executante (0009, nível "mais SIRG"). São sete cópias de `<style>` escopado
(~4.150 linhas) que **não são idênticas**: há três níveis (IBM pura, misto de `nova`, mais
SIRG) e a mesma classe (`.ibm-painel`, `.ibm-rotulo`, `.ibm-grupo-titulo`) tem corpo diferente
entre telas.

O responsável pelo produto pediu: dar nome próprio a esse visual (**sirg-design**), levá-lo às
telas de Unidade e mostrar em cada unidade o seu papel — solicitante, executante ou ambas —,
que com a parametrização da unidade executante (ADR 0011) deixou de ser só o `tipo` declarado.

## Decisão

1. **sirg-design** é o nome do visual misto, documentado em `regulacao-frontend/DESIGN.md` §10
   com os três níveis. As seções 2 a 6 continuam sendo o padrão das demais telas; o
   sirg-design não vira padrão geral.
2. **Fonte única do nível "mais SIRG"**: `src/lib/sirg-design/SirgDesign.svelte`, uma casca
   que envolve o conteúdo da página. Tokens `--sd-*` e classes `sd-*` escritas como
   `.sirg-design :global(.sd-*)`. É a **única exceção autorizada à regra "sem `:global`"**:
   a raiz leva o hash do componente, então o estilo só alcança o que está dentro da casca.
3. Adotam a casca as quatro telas de Unidade: `/admin/unidades`, `/nova`, `/[id]/editar` e
   `/[id]/executante`.
4. **As sete telas mistas existentes não mudam** e não passam a usar a casca agora. Migrar
   cada uma é pedido à parte, uma tela por vez, com verificação visual.
5. **Tag de papel da unidade — só exibição** (`src/lib/papelUnidade.js`,
   `TagPapelUnidade.svelte`). Nada é gravado; o `tipo` continua escolhido à mão:
   - executante **não** configurada (qualquer tipo) → "Unidade solicitante";
   - configurada e tipo `EXECUTANTE` → "Unidade executante";
   - configurada e tipo `AMBOS` → "Ambas";
   - unidade ativa e não-solicitante, não configurada, com algum vínculo (grupo,
     especialidade, local ou operador) → também "Parametrização incompleta".
   "Configurada" é a do ADR 0011 e vem pronta do backend (`GET /api/unidades-executantes` na
   lista; `GET /api/unidades-executantes/{id}` em editar e na parametrização). Em editar a tag
   segue o estado **salvo**. Em `nova` não há tag, só a nota "o papel aparece depois de
   parametrizar". Se o resumo de executantes não carregar, a lista não mostra a tag e avisa.

## Alternativas consideradas

- **Migrar as sete telas para a fonte única agora**: diff grande, exigiria modificador de
  nível e override por classe divergente, e a única rede seria verificação visual manual em
  sete telas de produção (que já ficou "em aberto" nos ADRs 0005, 0007 e 0009). Descartada.
- **Arquivo `.css` com namespace importado pelas páginas**: é CSS global de fato depois da
  navegação; um seletor fora do namespace atingiria outras telas. Descartada em favor da casca.
- **Componentes por elemento (`SdPainel`, `SdCampo`…)**: sem `:global`, mas reescreve todo o
  markup dos formulários e encarece a migração futura das sete telas.
- **Tag só do `tipo` gravado**: hoje toda unidade é `AMBOS` (V86), então todas mostrariam
  "Ambas" — o oposto do que o pedido quer distinguir.
- **Gravar o `tipo` ao parametrizar / migration reclassificando**: trocar o tipo liga e
  desliga o recorte de `/atendimentos` (ADR 0011). Fora de escopo de propósito.

## Consequências

- Tela nova que adotar o sirg-design usa `<SirgDesign>`, nunca uma cópia do CSS. Classe comum
  nova entra no `SirgDesign.svelte`.
- Até a migração, convivem a fonte única (telas de Unidade) e as sete cópias (`ibm-*`,
  `at-*`, `cotas-ibm`). Não misture os prefixos.
- Com os dados de produção (todas `AMBOS`, quase nenhuma parametrizada), a lista mostra
  "Unidade solicitante" para quase todas, enquanto o seletor de editar mostra "Solicitante e
  executante". O texto de ajuda da lista explica que o papel reflete a parametrização.
- A tag não altera nada: confirmações de desativar/mudar tipo, botão "Parametrizar
  executante" (`tipo != SOLICITANTE`), contratos de API e recorte de `/atendimentos` seguem
  iguais. A lógica das telas está fixada por `src/lib/telasUnidade.characterization.test.mjs`.
- **Em aberto:** verificação visual autenticada em 375, 768 e 1280 px (o navegador da sessão
  de verificação estava preso num diálogo nativo).
- Ver [[0011-unidade-executante-parametrizada]], [[0009-visual-misto-com-mais-sirg-em-atendimentos]].
