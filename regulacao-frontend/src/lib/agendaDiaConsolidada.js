// Logica pura da Agenda do Dia consolidada (visao do ADMIN): data no fuso da
// Bahia, achatamento da hierarquia unidade -> grupo -> especialidade em linhas
// e geracao de CSV. Fica fora do componente para ser testavel com `node --test`.

/** Hoje (YYYY-MM-DD) no fuso da Bahia — `toISOString()` usa UTC e vira o dia as 21h. */
export function dataHojeBahia(agora = new Date()) {
  return new Intl.DateTimeFormat("en-CA", {
    timeZone: "America/Bahia",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(agora);
}

export const COLUNAS_EXPORTACAO = [
  "Unidade",
  "Grupo",
  "Especialidade",
  "Pacientes",
  "Itens",
  "Agendados",
  "Realizados",
  "Faltas/Cancelados",
  "Outros",
  "Cotas (livres/total)",
];

function resumoCotas(cotas) {
  if (!cotas || cotas.length === 0) return "";
  return cotas.map((c) => `${c.livres}/${c.total}`).join(" + ");
}

function linha(unidade, grupo, especialidade, ind, cotas) {
  return [
    unidade,
    grupo,
    especialidade,
    ind.pacientes,
    ind.itens,
    ind.agendados,
    ind.realizados,
    ind.faltasCancelados,
    ind.outros,
    resumoCotas(cotas),
  ];
}

/** Uma linha por unidade, por grupo da unidade e por especialidade (agregado, sem dado de paciente). */
export function achatarConsolidado(view) {
  const linhas = [];
  for (const u of view?.unidades ?? []) {
    linhas.push(linha(u.nome, "", "", u.indicadores, u.cotas));
    for (const g of u.grupos ?? []) {
      linhas.push(linha(u.nome, g.nome, "", g.indicadores, g.cotas));
      for (const e of g.especialidades ?? []) {
        linhas.push(linha(u.nome, g.nome, e.nome, e.indicadores, e.cotas));
      }
    }
  }
  return linhas;
}

/**
 * Celula de CSV segura: aspas escapadas e, para texto que comece com = + - @,
 * prefixo de apostrofo — o Excel interpretaria como formula (CSV injection).
 * Numeros passam direto: -1 e um numero, nao uma formula.
 */
export function celulaCsv(valor) {
  if (valor === null || valor === undefined) return "";
  if (typeof valor === "number") return String(valor);
  let texto = String(valor);
  if (/^[=+\-@\t\r]/.test(texto)) texto = `'${texto}`;
  return /[";\n\r]/.test(texto) ? `"${texto.replace(/"/g, '""')}"` : texto;
}

/** CSV com separador ';' (padrao do Excel em pt-BR) e BOM para acentuacao. */
export function gerarCsv(colunas, linhas) {
  const corpo = [colunas, ...linhas]
    .map((l) => l.map(celulaCsv).join(";"))
    .join("\r\n");
  return `﻿${corpo}`;
}
