// Agendamento em lote parcial — parte pura usada por routes/agendar/+page.svelte.
//
// Antes de gravar, a tela pergunta ao backend (POST agendamentos/{id}/verificar)
// o que aconteceria com cada item. Aqui se decide o que fazer com a resposta e
// se monta o corpo do POST só com os itens que podem ser agendados.
//
// A verificação é consultiva: quem decide é o POST, que revalida tudo.

/**
 * Lê a resposta da verificação e diz em que situação o lote está.
 *
 * - TUDO_OK: todos os itens entram — a tela grava direto, sem etapa extra.
 * - CORRIGIR: há item que só não entra por um dado que o operador ajusta na
 *   tela (profissional, hora) — a tela pede a correção, não oferece seguir.
 * - NENHUM: nenhum item pode ser agendado.
 * - BLOQUEADO: os itens possíveis não passam por uma regra do agendamento
 *   inteiro (teto financeiro) — nada seria gravado.
 * - PARCIAL: parte entra, parte fica de fora — a tela pede confirmação.
 */
export function analisarVerificacao(resultado) {
  const itens = Array.isArray(resultado?.itens) ? resultado.itens : [];
  const viaveis = itens.filter((i) => i.podeAgendar);
  const corrigiveis = itens.filter((i) => !i.podeAgendar && i.corrigivel);
  const recusados = itens.filter((i) => !i.podeAgendar && !i.corrigivel);
  const bloqueioDoLote = resultado?.bloqueioDoLote || null;

  let situacao;
  if (corrigiveis.length > 0) situacao = "CORRIGIR";
  else if (viaveis.length === 0) situacao = "NENHUM";
  else if (bloqueioDoLote) situacao = "BLOQUEADO";
  else if (recusados.length > 0) situacao = "PARCIAL";
  else situacao = "TUDO_OK";

  return { situacao, viaveis, corrigiveis, recusados, bloqueioDoLote };
}

function filtrarMapa(mapa, codigos) {
  if (!mapa) return null;
  const filtrado = Object.fromEntries(
    Object.entries(mapa).filter(([codigo]) => codigos.includes(codigo)),
  );
  return Object.keys(filtrado).length > 0 ? filtrado : null;
}

/**
 * Corpo do POST só com os itens viáveis: mesma requisição, sem os exames que
 * ficaram de fora e sem as escolhas (cota, hora, profissional) deles.
 * Não altera o corpo original.
 */
export function montarBodyViaveis(body, codigosViaveis) {
  return {
    ...body,
    examesSelecionados: body.examesSelecionados.filter((codigo) =>
      codigosViaveis.includes(codigo),
    ),
    cotasSelecionadas: filtrarMapa(body.cotasSelecionadas, codigosViaveis),
    horariosSelecionados: filtrarMapa(
      body.horariosSelecionados,
      codigosViaveis,
    ),
    profissionaisSelecionados: filtrarMapa(
      body.profissionaisSelecionados,
      codigosViaveis,
    ),
  };
}
