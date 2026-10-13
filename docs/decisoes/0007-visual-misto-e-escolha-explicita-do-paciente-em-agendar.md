---
titulo: Visual misto e escolha explícita do paciente em /agendar
tags:
  - sirg/adr
status: aceita
data: 2026-10-13
substitui:
---

# 0007 — Visual misto e escolha explícita do paciente em /agendar

## Contexto

Na tela de agendamento (`/agendar`), o operador busca uma solicitação pendente, escolhe o
paciente e só então vê os exames, a data, o local e o botão de envio. Três dificuldades foram
observadas no uso:

1. O operador digita o nome e não percebe que precisa **escolher** o paciente na lista de
   sugestões para ele ficar selecionado.
2. Antes de salvar, não fica claro **qual** paciente está selecionado.
3. Os dados do paciente e o restante do formulário só aparecem depois da seleção, sem nenhum
   aviso disso antes.

A tela mostrava o paciente como "Nome - CPF", e quem digitava ou colava esse mesmo formato na
busca não encontrava ninguém, porque o servidor recebe um único `termo`.

O pedido do responsável pelo produto foi reconstruir o visual da tela inteira "mesclando IBM +
SIRG". Os ADRs 0005 e 0006 registraram esse visual nas telas de cotas, e o
`regulacao-frontend/DESIGN.md` (seção 10) diz para não copiá-lo para outra tela sem uma decisão
nova. Este ADR é essa decisão, e registra também as decisões de comportamento do mesmo ajuste.

## Decisão

**Visual**

- `/agendar` adota o visual misto SIRG/IBM de `/admin/cotas/nova`: tokens `--ibm-*` num
  `<style>` escopado por `.cotas-ibm`, aplicado dentro do `<main>`. É a quinta cópia do CSS,
  com comentário de origem; só vieram as classes que a tela usa. Acento emerald, fonte só como
  pilha de fallback, nenhuma dependência nova. O cabeçalho emerald com `UserMenu` e o menu não
  mudam. O diálogo de lote parcial não foi alterado.
- A tela passa a ter quatro passos numerados: **1. Paciente**, **2. Exames e data**,
  **3. Local e orientações** e **4. Conferir e agendar**. Nenhum campo foi removido nem
  acrescentado; a data e o turno subiram para antes da contagem de vagas, que depende da data.
- A busca traz o texto de ajuda entre o rótulo e o campo, a lista tem um cabeçalho ("Escolha o
  paciente: clique ou setas e Enter") e cada sugestão mostra nome, CPF, CNS e unidade, para
  distinguir homônimos e recém-nascidos sem CPF.
- O painel do paciente tem três estados, sempre com texto e marca, nunca só cor: "Nenhum
  paciente selecionado ainda" (borda tracejada), "Carregando dados do paciente..." e "Paciente
  selecionado" (borda e filete emerald, ícone, nome em destaque e "Trocar paciente"). Antes da
  seleção, um bloco lista os passos 2 a 4 e avisa que eles aparecem depois da escolha.
- O paciente selecionado é repetido, como texto e não como campo, ao lado do botão de envio.

**Comportamento (implementado no mesmo ajuste)**

- A escolha do paciente é **sempre explícita**: clique na sugestão ou setas + Enter. Não há
  seleção automática, nem quando a busca devolve um único resultado. Enter no campo de busca
  não envia o agendamento.
- A busca aceita "Nome - documento", separando o texto **no cliente**
  (`src/lib/buscaPaciente.js`): documento é o bloco de dígitos no fim do texto; com nome e
  documento, a sugestão precisa casar com os dois. O servidor continua recebendo um único
  `termo`, e o contrato de `GET /api/agendamentos/pendentes/buscar` não mudou.
- Depois de selecionar, o campo de busca é limpo e o paciente aparece em bloco próprio, com
  "Trocar paciente". O campo tem `autocomplete="off"`, para "Nome - CPF" não ficar no histórico
  do navegador de máquinas compartilhadas.
- Da verificação de vagas até a resposta do agendamento, o paciente não pode ser trocado
  ("Trocar paciente" e a escolha na lista ficam bloqueados).

## Alternativas consideradas

- **Selecionar automaticamente quando a busca devolve um só resultado:** descartado; com
  homônimos e a lista limitada a 20 itens, "um resultado" não garante que seja o paciente
  certo, e o erro seria agendar para outra pessoa.
- **Manter o paciente escrito no campo de busca depois da seleção:** descartado; o campo
  parecia editável, o texto "Nome - CPF" voltava a ser enviado como busca e ficava no histórico
  do navegador.
- **Parâmetro `documento` no endpoint de busca:** adiado; muda o contrato do backend. Ficou
  como pendência (ver Consequências).
- **Manter o visual padrão do `DESIGN.md` (seções 2 a 6):** descartado pelo responsável pelo
  produto, que pediu a mistura IBM + SIRG nesta tela.
- **Extrair o CSS compartilhado agora:** descartado; tocaria as quatro telas de cotas, que
  estão em produção.
- **Trocar os dados do paciente por texto simples, sem campos:** descartado nesta rodada, para
  não remover campos da tela; os campos ficaram como somente leitura dentro do bloco.
- **Rodapé fixo com o paciente e o botão de envio:** descartado; exigiria trocar a casca da
  página (hoje `min-h-screen`, com rolagem no documento), o que afeta o menu.

## Consequências

- `/agendar` fica visualmente diferente das outras telas de atendimento (`/cadastrar`,
  `/exames`, `/paciente/[id]`), que seguem o padrão das seções 2 a 6 do `DESIGN.md`.
- São cinco cópias do CSS do visual misto. Mudar um token exige conferir as cinco; a extração
  continua sendo pedido à parte.
- O item ativo da lista de sugestões é marcado em tempo de execução por
  `$lib/sugestoesTeclado.js` (`aria-selected="true"`). O estilo usa `:global()` só nesse
  atributo, preso a uma classe escopada da tela. Em `sugestoesTeclado.js`, Enter e as setas
  partem do item marcado, não da posição: a lista de `/agendar` muda de ordem quando a resposta
  do servidor chega, e Enter escolheria outro paciente. Vale para as outras telas que usam a
  action.
- **Limite conhecido da busca:** o endpoint devolve 20 itens, sem ordenação. Quando o filtro do
  cliente zera a lista e o servidor diz que há mais páginas, a tela faz uma segunda busca pelo
  documento (ou pelo texto inteiro, quando o fim tem só um ou dois dígitos). Isso atenua, mas não elimina, o falso "Nenhuma solicitação encontrada".
- **Pendência (backend):** parâmetro `documento` e `order by` em
  `GET /api/agendamentos/pendentes/buscar`. Busca por dígitos em formato diferente do gravado e
  por nome sem acento continuam limitadas pelo servidor.
- **Pendência (outra tela):** `/exames` tem o mesmo defeito "Nome - documento" e ficou para
  ajuste separado.
- Os avisos continuam em `alert()` nativo; trocá-los por toast é mudança de comportamento e
  não entrou.
- **Em aberto:** enquanto a data não é informada, cada exame marcado mostra "Carregando
  contagem...", embora nada esteja carregando. A tela agora avisa, acima dos exames, que a
  contagem depende da data; corrigir o texto em si mexe em estado e ficou de fora.
- **Em aberto:** a verificação visual com sessão autenticada em 375, 768 e 1280 px.
