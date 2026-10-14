# DESIGN.md — SIRG (telas internas)

Descreve o padrão visual **que existe hoje** em `regulacao-frontend/`. É um retrato do
código, não uma proposta: cada afirmação aponta o arquivo de onde saiu, e as contagens são
ocorrências de classes Tailwind nos 92 arquivos `.svelte` de `src/` em 2026-10-12. Salvo
indicação, a contagem inclui as variantes com prefixo (`hover:`, `focus:`, `md:`).

O formato segue a especificação DESIGN.md do Google Stitch
(https://stitch.withgoogle.com/docs/design-md/specification/). O conteúdo é próprio do SIRG;
nenhuma identidade de marca de terceiro foi copiada.

**Quando este arquivo e o código divergirem, o código está certo.** Atualize o arquivo.

A landing pública (`sirg-landing/`) tem identidade própria, definida em
`sirg-landing/src/app.css`, e não é coberta aqui.

## 1. Visual Theme & Atmosphere

Sistema administrativo de saúde pública, de uso diário e prolongado por regulação, recepção
e gestão. O visual é sóbrio e funcional: fundo cinza claro, superfícies brancas com sombra
leve, um único verde institucional (emerald) no cabeçalho e nas ações primárias. Não há
ilustração, gradiente decorativo nem modo escuro.

A densidade é média para alta: predominam `text-sm` (796 ocorrências) e `text-xs` (424).

Tela de referência: `src/routes/paciente/[id]/+page.svelte`.

## 2. Color Palette & Roles

Não existem tokens de design. `src/app.css` contém apenas `@import 'tailwindcss'` e
`@plugin '@tailwindcss/forms'`. As cores são as da paleta padrão do Tailwind 4, usadas
diretamente nas classes.

| Papel                           | Classe                                                  | Origem                           |
| ------------------------------- | ------------------------------------------------------- | -------------------------------- |
| Cabeçalho da página             | `bg-emerald-700 text-white`                             | `src/lib/PageHeader.svelte:6`    |
| Ação primária                   | `bg-emerald-700 hover:bg-emerald-800 text-white`        | `paciente/[id]/+page.svelte:658` |
| Ação primária compacta em lista | `bg-emerald-600 hover:bg-emerald-700 text-white`        | `paciente/[id]/+page.svelte:862` |
| Fundo da aplicação              | `bg-gray-100`                                           | `paciente/[id]/+page.svelte:490` |
| Superfície (cartão, modal)      | `bg-white`                                              | `paciente/[id]/+page.svelte:545` |
| Fundo de apoio (linhas, blocos) | `bg-gray-50`                                            | 75 sem prefixo, 134 no total     |
| Título de seção                 | `text-emerald-800`                                      | `paciente/[id]/+page.svelte:546` |
| Texto de label e corpo          | `text-gray-700`                                         | 513 ocorrências                  |
| Texto secundário                | `text-gray-500`, `text-gray-600`                        | 284 e 230 ocorrências            |
| Texto forte                     | `text-gray-800`, `text-gray-900`                        | 223 e 146 ocorrências            |
| Borda de campo                  | `border-gray-300`                                       | 355 ocorrências                  |
| Borda de divisão                | `border-gray-200`                                       | 119 ocorrências                  |
| Foco                            | `focus:border-emerald-500 focus:ring-emerald-500`       | `paciente/[id]/+page.svelte:551` |
| Erro e ação destrutiva          | `bg-red-50`, `bg-red-100`, `text-red-700`, `bg-red-600` | 34, 49, 44 e 21 ocorrências      |
| Aviso                           | `bg-amber-50 text-amber-900`                            | 20 e 24 ocorrências              |
| Sucesso e destaque positivo     | `bg-emerald-50`, `bg-emerald-100 text-emerald-800`      | 37 e 45 ocorrências              |

### Inconsistências encontradas (não resolvidas aqui)

Estas divergências existem no código. Este arquivo as registra; a escolha de qual vira
regra é do responsável pelo produto.

- **Segundo acento.** O botão "Adicionar" usa `bg-blue-600 hover:bg-blue-700`
  (`paciente/[id]/+page.svelte:648`) na mesma tela em que o primário é emerald. Como fundo
  base (sem contar `hover:`), aparecem ainda `bg-sky-600` (11), `bg-blue-600` (10),
  `bg-green-600` (3) e `bg-blue-700` (2).
- **Dois verdes para destaque positivo.** Na mesma tela, o chip de CID usa
  `bg-emerald-100 text-emerald-800` (linha 609) e um badge de status usa
  `bg-green-100 text-green-800` (linha 714), em `paciente/[id]/+page.svelte`.
- **Dois tons de emerald como fundo base.** `bg-emerald-700` aparece 95 vezes sem prefixo e
  `bg-emerald-600`, 79. O cabeçalho usa o 700; entre os botões há os dois (linhas 658 e 862
  de `paciente/[id]/+page.svelte`).

## 3. Typography Rules

Nenhuma fonte é declarada em `src/app.css` nem em `src/app.html`. Vale a pilha de fontes
padrão do Tailwind (fonte de sistema).

| Uso                             | Classe                                 | Origem                           |
| ------------------------------- | -------------------------------------- | -------------------------------- |
| Título da página                | `text-lg font-semibold tracking-tight` | `src/lib/PageHeader.svelte:9`    |
| Título de seção                 | `text-lg font-bold`                    | `paciente/[id]/+page.svelte:546` |
| Título de modal                 | `text-lg font-semibold`                | `paciente/[id]/+page.svelte:890` |
| Label de campo                  | `block text-sm font-medium mb-1`       | `paciente/[id]/+page.svelte:550` |
| Corpo e tabelas                 | `text-sm`                              | 796 ocorrências                  |
| Metadado, badge, botão compacto | `text-xs`                              | 424 ocorrências                  |

Pesos em uso: `font-medium` (452), `font-semibold` (419), `font-bold` (180).

Inconsistência: `paciente/[id]/+page.svelte` monta o cabeçalho à mão (linha 494) com
título em `text-xl font-semibold` (linha 498), enquanto `PageHeader.svelte` usa `text-lg`.

## 4. Component Stylings

**Campo de formulário.** Estilo base vem do plugin `@tailwindcss/forms`. Classe padrão:

```
w-full border-gray-300 rounded-md shadow-sm focus:border-emerald-500 focus:ring-emerald-500
```

Origem: `paciente/[id]/+page.svelte:551`. Label acima do campo, em
`block text-sm font-medium text-gray-700 mb-1` (linha 550).

**Botão primário.** `bg-emerald-700 text-white px-6 py-2 rounded-md hover:bg-emerald-800 transition-colors shadow`
(`paciente/[id]/+page.svelte:658`), alinhado à direita no fim da seção.

**Botão compacto em lista.** `px-3 py-1 text-xs rounded bg-emerald-600 text-white hover:bg-emerald-700`
(`paciente/[id]/+page.svelte:862`).

**Cartão de seção.** `bg-white rounded-lg shadow p-6`, com título seguido de
`border-b pb-2 mb-4` (`paciente/[id]/+page.svelte:545-546`).

**Modal.** Fundo `fixed inset-0 bg-black/40 flex items-center justify-center z-50`;
caixa `bg-white rounded-lg shadow-xl w-full max-w-md p-6 space-y-4`
(`paciente/[id]/+page.svelte:888-889`).

**Badge de status.** Par `bg-{cor}-100 text-{cor}-800`
(`paciente/[id]/+page.svelte:714`, `797`, `843`, `952`).

**Cabeçalho de página.** Componente `src/lib/PageHeader.svelte`.

**Menu lateral.** `src/lib/RoleBasedMenu.svelte`, alimentado por `src/lib/menuConfig.js`.
O menu é configuração, nunca componente por perfil.

**Avisos.** Toasts com `svelte-sonner` (importado em 21 arquivos). `alert()` e `confirm()`
nativos ainda aparecem em 37 arquivos.

**Ícones.** SVG inline definido em `src/lib/menuConfig.js`; `lucide-svelte` é importado em
3 arquivos.

**Gráficos.** `src/lib/components/ChartBase.svelte` e `ChartSeries.svelte` (chart.js).

**Não trate como padrão:** `bits-ui` e `tailwind-variants` estão em `package.json`, mas
nenhum arquivo de `src/` os importa; `gsap` é importado em 1 arquivo.

## 5. Layout Principles

Casca da tela de referência, resumida de `paciente/[id]/+page.svelte:490-503` (o
`RoleBasedMenu` é usado em 45 arquivos de rota; as demais telas não foram conferidas uma a
uma):

```
<div class="flex h-screen bg-gray-100">
  <RoleBasedMenu />
  <div class="grid grid-cols-1 md:flex md:flex-1 md:flex-col">
    <header>…</header>
    <main class="p-4 md:p-6 lg:p-8 overflow-auto space-y-6">…</main>
  </div>
</div>
```

- O `<main>` **não tem `max-w`**: o conteúdo ocupa toda a largura disponível.
- Seções empilhadas com `space-y-6`.
- Grade de campos: `grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4`, com
  `lg:col-span-2` para campos largos (`paciente/[id]/+page.svelte:548-549`).
- Espaçamentos mais usados: `gap-2` (126), `gap-3` (107), `gap-4` (102), `space-y-4` (78);
  padding `p-2` (223), `p-4` (203), `p-6` (160).
- Raios: `rounded-lg` (545) em cartões, `rounded-md` (112) em campos e botões,
  `rounded-full` (101) em badges e avatares.

## 6. Depth & Elevation

| Nível               | Classe      | Ocorrências |
| ------------------- | ----------- | ----------- |
| Campo               | `shadow-sm` | 72          |
| Cartão e cabeçalho  | `shadow`    | 125         |
| Cabeçalho destacado | `shadow-md` | 29          |
| Painel flutuante    | `shadow-lg` | 51          |
| Modal               | `shadow-xl` | 13          |

Sombras são pretas e neutras, as padrão do Tailwind. Não há borda colorida nem brilho.

## 7. Do's and Don'ts

Preferências do responsável pelo produto e convenções do `CLAUDE.md`:

**Faça**

- Telas de formulário sem `max-w`, usando toda a largura.
- Espelhe a estrutura de `/paciente/[id]` em telas novas de formulário.
- Use a classe padrão do campo (seção 4), sem compactar.
- Label acima do campo, sempre visível.
- Microcopy em português do Brasil, no tom das outras telas.
- Reutilize `PageHeader`, `RoleBasedMenu`, `LoadingSpinner` e os toasts existentes.
- Toda chamada HTTP passa por `src/lib/api.js`.
- Páginas novas usam runes do Svelte 5 (`$state`, `$derived`, `$effect`).

**Não faça**

- Não use accordion (`<details>` ou equivalente) para fazer o conteúdo caber numa tela.
  Existe em `admin/cotas/nova` e `admin/cotas/[id]/editar`; não replique.
- Não adicione dependência nova, fonte externa, biblioteca de ícones ou de animação.
- Não crie um estilo paralelo: nada de paleta, tipografia ou componentes fora dos descritos
  aqui.
- Não crie componente de menu por perfil.
- Não introduza um novo acento de cor; as inconsistências da seção 2 não são licença para
  mais uma.

## 8. Responsive Behavior

Breakpoints em uso: `md:` (255 ocorrências), `sm:` (108), `lg:` (68), `xl:` (32).

- Abaixo de `md` (768px): uma coluna; a casca vira `grid grid-cols-1`.
- `md`: campos em duas colunas; padding do `<main>` sobe para `p-6`.
- `lg` (1024px): campos em quatro colunas; padding `p-8`.

Larguras de verificação do `ux-ui-designer`: 375px, 768px e 1280px ou mais.

## 9. Agent Prompt Guide

- Antes de mexer numa tela, abra `src/routes/paciente/[id]/+page.svelte` e uma ou duas
  telas do mesmo módulo, e siga o que elas fazem.
- Copie as classes das seções 2 a 6 em vez de compor novas.
- As inconsistências da seção 2 não estão resolvidas. Não as "corrija" sem pedido
  explícito: mantenha o que a tela já usa e aponte a inconsistência no relatório.
- Se uma diretriz externa (skill, referência de marca) contrariar este arquivo, este
  arquivo prevalece.

## 10. Exceções documentadas

**As telas de cotas, a tela de agendamento (`/agendar`) e a tela de atendimentos
(`/atendimentos`) usam um visual próprio, que mistura o SIRG com o da IBM.** A decisão é do
responsável pelo produto (ADRs `docs/decisoes/0005-visual-ibm-em-admin-cotas.md`,
`docs/decisoes/0006-visual-misto-em-unidade-cotas.md`,
`docs/decisoes/0007-visual-misto-e-escolha-explicita-do-paciente-em-agendar.md` e
`docs/decisoes/0009-visual-misto-com-mais-sirg-em-atendimentos.md`) e contraria, só nessas
telas, as seções 2 a 6 e a regra "não crie um estilo paralelo".

| Tela                          | Visual                                | Observação                                                                                    |
| ----------------------------- | ------------------------------------- | --------------------------------------------------------------------------------------------- |
| `/admin/cotas/nova`           | Misto (modelo de referência)          | Painéis em `rounded-lg` com sombra leve, título emerald-800 com `border-b`, campos IBM        |
| `/admin/cotas/[id]/editar`    | Misto                                 | Mesmos tokens e classes de `nova`                                                             |
| `/unidade/cotas`              | Misto                                 | Mesmos tokens, mais `--sit-*` e `.ibm-cartao` para as situações                               |
| `/agendar`                    | Misto (cópia de `nova`)               | Só as classes que a tela usa, mais as `ag-*` (passos, sugestões, paciente selecionado)        |
| `/atendimentos`               | Misto com mais SIRG                   | Tokens de `nova` mais `--sirg-*`/`--at-*`: emerald também nas superfícies; classes `at-*`     |
| `/atendimentos/quantitativos` | Misto com mais SIRG (cópia podada)    | Mesmos tokens de `/atendimentos`; só cabeçalho do dia, cartões de totais e tabela por unidade |
| `/admin/cotas` (lista)        | IBM mais pura (raio zero, sem sombra) | Ainda não alinhada: `--ibm-fundo`, `--ibm-ok-*` e os tokens de mistura têm valor diferente    |

Em cada uma, o que vale é o próprio `+page.svelte`:

- Tokens `--ibm-*` num `<style>` escopado pela classe `.cotas-ibm`, sem `:global` e sem
  `app.css`. O acento continua sendo o emerald do SIRG (`--ibm-acento: #047857`), não o
  azul IBM. O bloco "Mistura SIRG/IBM" concentra o que controla o raio, a sombra e o título
  dos painéis.
- Campos cinza com linha inferior, rótulo de 12 px acima, foco em contorno de 2 px; pílula
  só em badges e tags.
- Fonte: pilha `'IBM Plex Sans', 'Helvetica Neue', Arial, sans-serif`, sem carregar nada.
- Situação da cota (`/unidade/cotas`): disponível (emerald, círculo), esgotada (vermelho
  escuro, losango) e inativa (cinza, círculo vazado), sempre com texto e marca, nunca só
  cor. Esgotadas e inativas ficam ao fim da lista; nada é escondido por padrão.
- Paciente em `/agendar`, três estados, sempre com texto e marca, nunca só cor: nenhum
  selecionado (`.ag-vazio`, borda tracejada, "Nenhum paciente selecionado ainda"), carregando
  (`.ag-carregando`, `LoadingSpinner` com a mensagem) e selecionado (`.ag-paciente`, borda e
  filete emerald, ícone, nome em destaque, campos somente leitura e "Trocar paciente"). O
  paciente selecionado é repetido como texto, não como campo, ao lado do botão de envio.
- Item ativo de lista de sugestões (`/agendar`, `.ag-sugestao`): `$lib/sugestoesTeclado.js`
  marca o botão com `aria-selected="true"`; o destaque é barra lateral emerald, nome em
  negrito sublinhado e a etiqueta "Enter para escolher", além do fundo. É o único
  `:global()` do visual misto, só no atributo e preso a uma classe escopada. A lista tem
  cabeçalho fixo com a instrução de escolha, e cada linha mostra nome, CPF, CNS e unidade.
- Painéis de `/agendar` numerados por passo (`.ag-passo`); antes da seleção do paciente, um
  bloco tracejado (`.ag-proximos`) lista os passos que ainda vão aparecer.
- Mais SIRG (`/atendimentos`, padrão das telas do módulo da unidade executante): o emerald
  deixa de ser só acento. Cabeçalho do dia e faixa de topo dos painéis em emerald-50,
  cabeçalho de tabela em emerald-100 com texto emerald-900, rodapé e hover de linha em
  emerald-50, rótulo de campo em emerald-800 e botão secundário com contorno emerald. Campo
  cinza com linha inferior, foco e tipografia continuam IBM. Os valores ficam na seção "Mais
  SIRG" do `<style>`, com o valor de `nova` anotado ao lado. As outras telas mistas não
  foram alteradas.
- Situação do atendimento (módulo da unidade executante), sempre com texto e marca, nunca só
  cor: confirmado (emerald, círculo cheio), agendado (azul, círculo vazado), falta (âmbar,
  losango) e cancelado (vermelho, quadrado), nos cartões de totais (Quantitativos), no badge e
  no filete da linha (`/atendimentos`).
- `/atendimentos` tem três regiões: cabeçalho do dia, "Pacientes do dia" e "Relatório de
  Atendimento". A lista é **um bloco por paciente** (`.at-paciente`: borda de 1 px, filete
  emerald, sem sombra) com uma tabela pequena dos itens dentro, nunca cartões por item nem
  accordion; todos os itens ficam visíveis. No topo do bloco, nome em destaque e linha de
  dados (Nascimento, CPF e CNS completos com pontuação só de exibição, Unidade solicitante).
  "Confirmar marcados (N)" é a única ação cheia do bloco; "Faltou" e "Cancelar" são contorno,
  item a item. Item desmarcado: filete cinza tracejado e o texto "Fora da confirmação". Lote em
  andamento: o botão não esmaece, mostra "Confirmando X de Y…" e o bloco ganha contorno
  emerald. Itens que não casam com o filtro de situação ficam em texto cinza, sem sumir.
- `/atendimentos/quantitativos`: cabeçalho do dia com "Atualizado às…", cinco cartões de
  totais e tabela por unidade com rodapé "Município"; a unidade é link sublinhado para
  `/atendimentos` filtrado.
- O CSS está **copiado** em cada tela (sete cópias, contando `/atendimentos/quantitativos`).
  Extrair para um arquivo compartilhado é pedido à parte, quando o visual estiver estável.
- `GrupoToggleButton` e `GrupoEspecialidadesPainel` aceitam `variante="ibm"`; o padrão
  continua o visual das seções 2 a 6 e é o que `dashboard/unidade` usa.

Não copie este visual para outra tela sem uma decisão nova.
