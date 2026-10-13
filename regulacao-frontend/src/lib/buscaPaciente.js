// Busca de paciente — parte pura usada por routes/agendar/+page.svelte.
//
// O operador digita o nome, o CPF ou os dois juntos ("Adriano Victor - 076.123"),
// que é o formato em que a própria tela mostra o paciente. Aqui se separa o texto
// em nome e documento e se decide se uma solicitação casa com o que foi digitado.
//
// O servidor continua recebendo um único `termo` (nome OU CPF); o filtro daqui só
// restringe o que ele devolveu, nunca amplia.

const ROTULO_SEM_CPF = /[\s-]*cpf n[aã]o informado\s*$/i;
// Separadores aceitos entre o nome e o documento: espaço, hífen, travessão,
// vírgula, dois-pontos, parêntese e o rótulo "CPF".
const SEPARADOR = String.raw`[\s,;:(–—-]`;
const DOCUMENTO_NO_FIM = new RegExp(
  String.raw`^(.*?)${SEPARADOR}*(?:cpf${SEPARADOR}*)?(\d[\d./\s-]*)\)?$`,
  "i",
);
const SEPARADOR_NO_FIM = new RegExp(`${SEPARADOR}+$`);

export function soDigitos(texto) {
  return String(texto ?? "").replace(/\D/g, "");
}

/** Minúsculas, sem acento e com espaços colapsados. */
export function normalizarNome(texto) {
  return String(texto ?? "")
    .normalize("NFD")
    .replace(/\p{M}/gu, "")
    .toLowerCase()
    .replace(/\s+/g, " ")
    .trim();
}

/**
 * Separa o texto digitado em nome e documento.
 *
 * Documento é só o bloco de dígitos (com ponto, barra, hífen ou espaço) no FIM
 * do texto. Dígito no meio fica no nome: recém-nascidos são cadastrados como
 * "RN 1 de Maria".
 */
export function separarConsulta(texto) {
  const limpo = String(texto ?? "")
    .replace(ROTULO_SEM_CPF, "")
    .trim();
  const partes = limpo.match(DOCUMENTO_NO_FIM);
  const nome = (partes ? partes[1] : limpo).replace(SEPARADOR_NO_FIM, "").trim();
  const documento = partes ? partes[2].replace(/\D+$/, "") : "";
  return { nome, documento, texto: limpo };
}

/** O `termo` enviado ao servidor: o nome, se houver; senão o documento como digitado. */
export function termoParaServidor(consulta) {
  return consulta.nome || consulta.documento;
}

/**
 * Termo da segunda tentativa, para quando a primeira (pelo nome) vem cheia e
 * sem ninguém que case. Vazio quando não há o que tentar.
 *
 * Um ou dois dígitos no fim costumam ser parte do nome ("RN de Maria 2"), e
 * sozinhos casariam com qualquer CPF: nesse caso vai o texto inteiro.
 */
export function termoDaSegundaBusca(consulta) {
  if (!consulta.nome || !consulta.documento) return "";
  return soDigitos(consulta.documento).length < 3 ? consulta.texto : consulta.documento;
}

/**
 * Diz se a solicitação casa com a consulta.
 *
 * - nome: sem diferenciar acento nem caixa;
 * - documento: só os dígitos do CPF, com ou sem máscara, aceitando parcial;
 * - nome e documento juntos: exige os dois.
 *
 * Um paciente cujo nome contém o texto inteiro digitado sempre casa — é o que
 * mantém visível o "RN de Maria 2" quando o "2" seria lido como documento.
 */
export function casaComConsulta(solicitacao, consulta) {
  const nomeDoPaciente = normalizarNome(solicitacao?.nomePaciente);
  if (nomeDoPaciente.includes(normalizarNome(consulta.texto))) return true;

  // Sem documento vale o nome já sem o separador: "Adriano Victor - " ainda
  // mostra o Adriano enquanto o operador digita o CPF.
  const nome = normalizarNome(consulta.nome);
  if (!nomeDoPaciente.includes(nome)) return false;

  const documento = soDigitos(consulta.documento);
  return !documento || soDigitos(solicitacao?.cpfPaciente).includes(documento);
}
