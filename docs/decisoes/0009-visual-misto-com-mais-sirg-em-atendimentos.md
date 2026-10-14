---
titulo: Visual misto com mais cor do SIRG em /atendimentos
tags:
  - sirg/adr
status: aceita
data: 2026-10-13
substitui:
---

# 0009 — Visual misto com mais cor do SIRG em /atendimentos

## Contexto

O módulo da unidade executante trouxe a tela **Atendimentos** (`/atendimentos`): o operador do
laboratório ou de outra unidade executante passa o dia nela, marcando presença, falta e
cancelamento de dezenas de pacientes. A tela nasceu funcional, com as classes neutras do
Tailwind, e tem cinco regiões: cabeçalho do dia (agenda e data), totais, contagem por unidade
solicitante, lista nominal com as ações de registro e o Relatório de Atendimento em Excel.

O pedido do responsável pelo produto foi aplicar nela "o design misto que será o nosso padrão
IBM + SIRG", usando **mais cores do SIRG** do que as telas mistas atuais. Os ADRs 0005, 0006 e
0007 registraram esse visual em `/admin/cotas`, `/unidade/cotas` e `/agendar`, e o
`regulacao-frontend/DESIGN.md` (seção 10) diz para não copiá-lo para outra tela sem uma decisão
nova. Este ADR é essa decisão.

Nas telas mistas existentes, o emerald aparece só como acento: título de painel, botão primário,
foco e itens selecionados. Cabeçalho de tabela, rótulos, hover e botão secundário são cinza, como
no Carbon.

## Decisão

- `/atendimentos`, incluindo o bloco "Relatório de Atendimento", adota o visual misto SIRG/IBM de
  `/admin/cotas/nova`: tokens `--ibm-*` num `<style>` escopado por `.cotas-ibm`, sem `:global` e
  sem `app.css`. É a **sexta cópia** do CSS, com comentário de origem, só com as classes que a
  tela usa, mais as `at-*`. Fonte só como pilha de fallback, nenhuma dependência nova. O
  cabeçalho da página (`Content.svelte`) e o menu não mudam.
- **Mais SIRG:** nesta tela o emerald sai do acento e entra nas superfícies, por tokens `--sirg-*`
  e `--at-*` declarados numa seção própria do `<style>` ("Mais SIRG"), cada um com o valor de
  `nova` anotado ao lado:
  - cabeçalho do dia em faixa emerald-50 com filete emerald-700 e título emerald-900;
  - faixa de topo dos painéis em emerald-50 com divisor emerald-200;
  - cabeçalho das tabelas em emerald-100 com texto emerald-900 e linha emerald-600; rodapé
    "Município" em emerald-50 com linha emerald-700;
  - hover de linha em emerald-50; unidade que está filtrando em emerald-100 com filete;
  - rótulo dos campos em emerald-800, peso 600; linha inferior do campo emerald no hover;
  - botão secundário com contorno e texto emerald; contador de pacientes em pílula emerald-700.
- Continua IBM: campo cinza com linha inferior e raio zero, rótulo de 12 px acima do campo, foco
  em contorno de 2 px, tipografia e espaçamento.
- **Situação do atendimento sempre com texto e marca, nunca só cor:** confirmado (emerald,
  círculo cheio), agendado (azul, círculo vazado), falta (âmbar, losango) e cancelado (vermelho,
  quadrado). A mesma marca aparece nos cartões de totais e no badge da lista; a linha do paciente
  ganha um filete lateral na cor da situação. Azul, âmbar e vermelho são cores de situação que a
  tela já usava, não um novo acento.
- "Confirmar" é a única ação cheia da linha (emerald, com ícone); "Faltou" e "Cancelar" são
  contorno âmbar e vermelho. O painel de falta ou cancelamento abre na linha de baixo, na cor do
  desfecho.
- CPF e CNS passam a dividir uma coluna ("CPF e CNS", um por linha, cada um com o rótulo ao
  lado), para a tabela caber em mais larguras sem rolagem horizontal. Nenhum dado saiu da tela.
  *(Substituído pela emenda de 2026-10-14, abaixo.)*
- Este é o padrão das telas **deste módulo** (unidade executante). Uma tela nova do módulo copia
  o `<style>` de `/atendimentos`, não o de `nova`.

### Emenda de 2026-10-14: lista agrupada por paciente e tela de Quantitativos

A pedido do responsável pelo produto, a lista de `/atendimentos` passou a ser **agrupada por
paciente** e os números do dia foram para uma tela própria. As razões não visuais (confirmação
em lote, CPF e CNS completos, paginação por paciente) estão no ADR 0010. Aqui fica só o visual.

- **Regiões de `/atendimentos`:** cabeçalho do dia (agenda, data, "Hoje"), "Pacientes do dia"
  (filtros e lista) e "Relatório de Atendimento". Os cartões de totais e a tabela por unidade
  saíram desta tela.
- **Forma escolhida: um bloco por paciente com uma tabela pequena dentro**, e não cartões
  soltos. Os itens continuam em `<table>` com `caption`, `scope` e cabeçalho, porque são dados
  comparáveis entre si (procedimento, turno, coleta, situação). Isso revê a alternativa
  "transformar a tabela em cartões", que o ADR tinha descartado: o bloco agrupa, mas a semântica
  de tabela dos itens foi mantida.
- **Bloco do paciente** (`.at-paciente`): borda de 1 px, filete emerald-700 à esquerda e raio de
  controle, **sem sombra** (não vira cartão dentro de cartão). No topo, o nome em 16 px peso 700
  e uma linha de dados (`<dl>`) com Nascimento, CPF, CNS e Unidade solicitante, cada rótulo em
  12 px cinza ao lado do valor. CPF e CNS aparecem completos, com pontuação só de exibição
  (000.000.000-00 e 000 0000 0000 0000) e algarismos tabulares; o valor vindo do servidor não
  muda.
- **Ação primária:** "Confirmar marcados (N)" é o único botão cheio do bloco, no canto direito do
  topo (largura total no celular), com 44 px de altura. Durante o lote o botão não esmaece: troca
  o ícone por um anel girando e o texto por "Confirmando X de Y…" (`aria-busy`), e o bloco ganha
  contorno emerald. Os outros controles da lista só esmaecem, como antes.
- **Caixas de seleção:** coluna própria à esquerda, só em itens agendados e só para o USER. A
  caixa tem 20 px, cor de acento emerald, e a célula inteira (40 px) é o alvo. Item desmarcado
  troca o filete de situação por um filete cinza tracejado e ganha o texto "Fora da
  confirmação"; abaixo do botão aparece "N desmarcado(s) fica(m) agendado(s)" ou "Nenhum item
  marcado". Bloco sem item agendado mostra "Nenhum item a confirmar" no lugar do botão.
- **Sem accordion:** todos os itens de todos os pacientes da página ficam visíveis.
- **Filtro de situação:** escolhe pacientes e mostra o bloco inteiro; os itens que não casam com
  a situação filtrada ficam com texto cinza e peso normal (contraste AA mantido), sem sumir.
- **Densidade:** o cabeçalho da tabela interna se repete em cada bloco, então é mais baixo
  (6 px de respiro) que o das tabelas de resumo, ainda em emerald-100. A tabela interna rola na
  horizontal dentro do bloco quando não cabe (48rem com a coluna de ações, 36rem sem ela).
- **Quantitativos** (`/atendimentos/quantitativos`, USER e ADMIN): cabeçalho do dia com
  "Atualizado às…", os cinco cartões de totais e a tabela por unidade solicitante com rodapé
  "Município". A unidade é um link para `/atendimentos` já filtrado, com sublinhado sempre
  visível e alvo de 40 px; o destaque de "unidade filtrando" deixou de existir.
- O `<style>` de Quantitativos é uma **cópia podada** do de `/atendimentos` (tokens iguais, só
  as classes que a tela usa). São agora **sete cópias** do CSS do visual misto.

## Alternativas consideradas

- **Manter o padrão das seções 2 a 6 do `DESIGN.md`:** descartado pelo responsável pelo produto,
  que pediu o misto IBM + SIRG nesta tela.
- **Copiar o misto de `nova` sem alteração:** descartado; o pedido foi explícito em ter mais
  presença do emerald.
- **Aplicar o "mais SIRG" também às cinco telas mistas existentes:** descartado nesta rodada. O
  alcance decidido foi só este módulo; as telas de cotas e `/agendar` estão em produção.
- **Estender o redesenho a `/paciente/[id]`, à Agenda do Dia do ADMIN e a `/relatorio/hospital`:**
  descartado pelo mesmo motivo. Essas telas só ganharam conteúdo com o módulo.
- **Extrair o CSS para um arquivo compartilhado agora:** não foi pedido e tocaria as cinco telas
  em produção. Continua sendo pedido à parte.
- **Colorir a linha inteira pela situação:** descartado; com dezenas de linhas a tabela vira um
  mosaico e o texto secundário perde contraste. Ficaram o filete, o badge e a marca.
- **Transformar a tabela em cartões no celular:** descartado; mudaria a estrutura da lista. A
  tabela rola na horizontal dentro do próprio contêiner. *(Revisto na emenda de 2026-10-14: a
  lista virou um bloco por paciente, mas os itens de cada bloco continuam numa tabela.)*
- **Cartões por item, sem tabela, dentro do bloco do paciente** (emenda de 2026-10-14):
  descartado; perde o alinhamento em colunas que permite comparar situação e coleta de relance,
  e a semântica de tabela para leitor de tela.
- **Bloco do paciente recolhível (accordion)** (emenda de 2026-10-14): descartado por
  preferência registrada do usuário; o operador precisa ver todos os itens.

## Consequências

- Existem agora **dois níveis** do visual misto: o de `nova` (emerald só como acento) e o de
  `/atendimentos` (emerald também nas superfícies). A diferença está isolada na seção "Mais SIRG"
  do `<style>`; levar esse nível às outras telas é trocar esses valores, mas exige decisão.
- São sete cópias do CSS do visual misto (seis até a emenda de 2026-10-14, mais
  `/atendimentos/quantitativos`). Mudar um token comum exige conferir as sete.
- Com a lista agrupada, o cabeçalho da tabela se repete a cada paciente; com dezenas de
  pacientes por página isso ocupa altura. Foi aceito para manter a semântica de tabela.
- `/atendimentos` fica visualmente diferente das telas vizinhas no uso diário (`/paciente/[id]`,
  Agenda do Dia), que seguem o padrão das seções 2 a 6 do `DESIGN.md`.
- A cor do filete dos cartões de totais ainda vem de classes `border-*` do Tailwind definidas no
  script da tela (`cartoes`); o âmbar-500 desse filete não chega a 3:1 sobre branco, e por isso o
  rótulo e a marca, não o filete, carregam o significado.
- Com `ocupado`, todos os botões da lista ficam desabilitados durante uma gravação. Eles só
  esmaecem, sem trocar de cor, para a tabela não piscar a cada registro.
- **Em aberto:** a verificação visual da lista agrupada com dados, em 375, 768 e 1280 px, nos
  perfis USER (com caixas e ações) e ADMIN (sem elas). Em 2026-10-14 só o cabeçalho, os filtros,
  o estado de erro e o relatório puderam ser vistos: a API respondeu 403 à sessão local.
