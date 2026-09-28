/**
 * Extrai {ano, mes, dia} de `dataEspecifica`, aceitando tanto a string ISO
 * "YYYY-MM-DD" quanto um array `[ano, mes, dia, ...]` — o backend serializa
 * `LocalDate` assim quando a configuração de data do Jackson não é aplicada
 * (bug de configuração, registrado à parte; corrigir aqui é o jeito de não
 * depender de um deploy de backend pra parar de mostrar "2026,9,26"). Retorna
 * `null` se não reconhecer nenhum dos dois formatos.
 */
function parseDataEspecifica(v) {
  if (Array.isArray(v)) {
    const [ano, mes, dia] = v;
    if (
      Number.isInteger(ano) &&
      Number.isInteger(mes) &&
      Number.isInteger(dia)
    ) {
      return { ano: String(ano), mes: String(mes), dia: String(dia) };
    }
    return null;
  }
  const m = /^(\d{4})-(\d{1,2})-(\d{1,2})/.exec(v ?? "");
  return m ? { ano: m[1], mes: m[2], dia: m[3] } : null;
}

/**
 * Formata período de cota (mensal "YYYY-MM" ou data específica "YYYY-MM-DD"
 * ou `[ano, mes, dia]`) sem nunca passar por `new Date(...)` — evita qualquer
 * risco de "Invalid Date" por formato inesperado, timezone ou dado ausente.
 *
 * O parsing é tolerante (mês/dia com 1 ou 2 dígitos, sufixo de hora ignorado)
 * porque cotas antigas podem ter sido gravadas antes da validação atual do
 * backend. Só cai no "—" quando o campo está mesmo vazio; se tiver algo só que
 * não bate com nenhum formato esperado, mostra o valor bruto em vez de
 * esconder o dado.
 */
export function formatarPeriodoCota(c) {
  if (c.tipoPeriodo === "DATA") {
    const d = parseDataEspecifica(c.dataEspecifica);
    if (d)
      return `${d.dia.padStart(2, "0")}/${d.mes.padStart(2, "0")}/${d.ano}`;
    return c.dataEspecifica ? String(c.dataEspecifica) : "—";
  }
  const m = /^(\d{4})-(\d{1,2})/.exec(c.periodo ?? "");
  if (m) return `${m[2].padStart(2, "0")}/${m[1]}`;
  return c.periodo ? String(c.periodo) : "—";
}

/**
 * Chave comparável (string "YYYY-MM-DD", sempre com zero à esquerda) para
 * ordenar cotas por proximidade, misturando tipoPeriodo MENSAL e DATA na
 * mesma lista. Usa o mesmo parsing tolerante de `formatarPeriodoCota` — uma
 * cota sem data reconhecível vai pro fim ("9999-99-99"), nunca quebra o sort.
 */
export function chaveOrdenacaoCota(c) {
  if (c.tipoPeriodo === "DATA") {
    const d = parseDataEspecifica(c.dataEspecifica);
    return d
      ? `${d.ano}-${d.mes.padStart(2, "0")}-${d.dia.padStart(2, "0")}`
      : "9999-99-99";
  }
  const m = /^(\d{4})-(\d{1,2})/.exec(c.periodo ?? "");
  return m ? `${m[1]}-${m[2].padStart(2, "0")}-01` : "9999-99-99";
}

/**
 * Distância absoluta (em dias) entre a data da cota e `agora` — usada para
 * ordenar por "mais próxima de now()", incluindo cotas já passadas (uma cota
 * do mês passado ainda deve poder aparecer se não houver 5 cotas futuras).
 * `new Date` aqui é só para aritmética, nunca para exibir texto — não reintroduz
 * o risco de "Invalid Date" que `formatarPeriodoCota` evita.
 */
export function distanciaDeHoje(c, agora = new Date()) {
  const chave = chaveOrdenacaoCota(c);
  if (chave === "9999-99-99") return Infinity;
  const data = new Date(`${chave}T00:00:00`);
  const diffMs = data.getTime() - agora.getTime();
  return Number.isFinite(diffMs) ? Math.abs(diffMs) : Infinity;
}

/**
 * Separa uma lista de CotaUnidadeViewDTO em cotas manuais (inalteradas) e
 * cotas de agenda agrupadas por `agendaId` — usada em /admin/cotas para
 * mostrar N ocorrências x M unidades de uma mesma agenda como um grupo
 * recolhível em vez de N x M linhas soltas.
 *
 * A ordem original da lista é preservada dentro de cada grupo e na ordem em
 * que os agendaId aparecem pela primeira vez (Map mantém ordem de inserção).
 * Cota de origem AGENDA sem `agendaId` (não deveria acontecer, mas o dado vem
 * de fora) cai em `manuais` em vez de sumir silenciosamente.
 */
export function agruparCotasPorAgenda(cotas) {
  const manuais = [];
  const porAgenda = new Map();
  for (const c of cotas) {
    if (c.origem === "AGENDA" && c.agendaId != null) {
      if (!porAgenda.has(c.agendaId)) porAgenda.set(c.agendaId, []);
      porAgenda.get(c.agendaId).push(c);
    } else {
      manuais.push(c);
    }
  }
  return { manuais, porAgenda };
}

/**
 * Período coberto por um grupo de cotas de agenda, como texto: uma data única
 * ("05/10/2026") quando todas as ocorrências caem no mesmo dia, ou um
 * intervalo ("05/10/2026 a 26/10/2026") quando há mais de uma data. Reusa
 * `chaveOrdenacaoCota` para não duplicar o parsing tolerante de data.
 */
export function periodoCobertoPorGrupo(cotasDoGrupo) {
  const chaves = cotasDoGrupo
    .map((c) => chaveOrdenacaoCota(c))
    .filter((k) => k !== "9999-99-99");
  if (chaves.length === 0) return "—";
  chaves.sort();
  const formatar = (chave) => {
    const [ano, mes, dia] = chave.split("-");
    return `${dia}/${mes}/${ano}`;
  };
  const primeira = formatar(chaves[0]);
  const ultima = formatar(chaves[chaves.length - 1]);
  return primeira === ultima ? primeira : `${primeira} a ${ultima}`;
}
