const doisDigitos = (n) => String(n).padStart(2, "0");

/**
 * Converte uma `LocalDate` vinda do backend para a string "AAAA-MM-DD" que o
 * `<input type="date">` e os endpoints esperam. Aceita o array
 * `[ano, mes, dia, ...]` — é assim que o backend serializa hoje (mesmo bug de
 * configuração do Jackson documentado em `datas.js`) — e a string ISO, com mês
 * e dia de 1 ou 2 dígitos e sufixo de hora ignorado. Não passa por
 * `new Date(...)`, então não há deslocamento de um dia por fuso horário.
 * Devolve "" quando o campo está vazio ou não bate com nenhum dos formatos.
 */
export function paraDataISO(v) {
  if (Array.isArray(v)) {
    const [ano, mes, dia] = v;
    if (![ano, mes, dia].every(Number.isInteger)) return "";
    return `${ano}-${doisDigitos(mes)}-${doisDigitos(dia)}`;
  }
  const m = /^(\d{4})-(\d{1,2})-(\d{1,2})/.exec(typeof v === "string" ? v : "");
  return m ? `${m[1]}-${doisDigitos(m[2])}-${doisDigitos(m[3])}` : "";
}
