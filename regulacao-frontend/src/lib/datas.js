/**
 * Extrai {ano, mes, dia, hora, minuto} de uma data vinda do backend, aceitando
 * tanto a string ISO ("YYYY-MM-DD", com "THH:mm..." ou " HH:mm..." opcional)
 * quanto o array `[ano, mes, dia, hora, minuto, ...]` — é assim que o backend
 * serializa `LocalDate`/`LocalDateTime` hoje (mesmo bug de configuração do
 * Jackson documentado em `cotas.js`). Segundo e nanos são omitidos do array
 * quando zero, então ele chega com 3, 5, 6 ou 7 elementos. Retorna `null` se
 * não reconhecer nenhum dos dois formatos.
 */
function parseData(v) {
  if (Array.isArray(v)) {
    const [ano, mes, dia, hora = 0, minuto = 0] = v;
    if ([ano, mes, dia, hora, minuto].every(Number.isInteger)) {
      return { ano, mes, dia, hora, minuto };
    }
    return null;
  }
  const m = /^(\d{4})-(\d{1,2})-(\d{1,2})(?:[T ](\d{1,2}):(\d{2}))?/.exec(
    typeof v === "string" ? v : "",
  );
  if (!m) return null;
  return {
    ano: Number(m[1]),
    mes: Number(m[2]),
    dia: Number(m[3]),
    hora: Number(m[4] ?? 0),
    minuto: Number(m[5] ?? 0),
  };
}

const doisDigitos = (n) => String(n).padStart(2, "0");

/**
 * Formata uma data do backend como "dd/mm/aaaa" sem nunca passar por
 * `new Date(...)` — os componentes são impressos como vieram, então não há
 * "Invalid Date" nem deslocamento de um dia por fuso horário. Devolve "—"
 * quando o campo está vazio; se tiver algo que não bate com nenhum formato
 * esperado, mostra o valor bruto em vez de esconder o dado.
 */
export function formatarData(v) {
  if (v == null || v === "") return "—";
  const d = parseData(v);
  if (!d) return String(v) || "—";
  return `${doisDigitos(d.dia)}/${doisDigitos(d.mes)}/${d.ano}`;
}

/**
 * Igual a `formatarData`, mas com a hora: "dd/mm/aaaa HH:mm". A hora é a que
 * o servidor gravou, sem conversão para o fuso do navegador. Data sem hora
 * (`LocalDate`) sai com "00:00". Só serve para `LocalDate`/`LocalDateTime`:
 * um sufixo de fuso ("Z", "-03:00") é ignorado, não convertido.
 */
export function formatarDataHora(v) {
  if (v == null || v === "") return "—";
  const d = parseData(v);
  if (!d) return String(v) || "—";
  return `${doisDigitos(d.dia)}/${doisDigitos(d.mes)}/${d.ano} ${doisDigitos(d.hora)}:${doisDigitos(d.minuto)}`;
}
