import { getApi, postApi, putApi } from "$lib/api.js";

// Painel de admin do WhatsApp (/admin/whatsapp). Todas as rotas exigem ADMIN.

/** Devolve o JSON ou lança Error com a mensagem do servidor ({ message }). */
async function lerResposta(res, mensagemPadrao) {
  const corpo = await res.json().catch(() => null);
  if (!res.ok) throw new Error(corpo?.message ?? mensagemPadrao);
  return corpo;
}

function comParametros(caminho, parametros) {
  const params = new URLSearchParams();
  for (const [chave, valor] of Object.entries(parametros)) {
    if (valor !== null && valor !== undefined && valor !== "")
      params.set(chave, String(valor));
  }
  const query = params.toString();
  return query ? `${caminho}?${query}` : caminho;
}

export async function buscarEstadoWhatsApp() {
  return lerResposta(
    await getApi("whatsapp/config"),
    "Não foi possível carregar o estado do WhatsApp.",
  );
}

export async function alterarEnvioWhatsApp(ligado) {
  return lerResposta(
    await putApi("whatsapp/config/envio", { ligado }),
    "Não foi possível alterar o envio de mensagens.",
  );
}

export async function listarMensagensWhatsApp({
  de,
  ate,
  tipo,
  resultado,
  page = 0,
  size = 20,
}) {
  return lerResposta(
    await getApi(
      comParametros("whatsapp/mensagens", {
        de,
        ate,
        tipo,
        resultado,
        page,
        size,
      }),
    ),
    "Não foi possível carregar as mensagens.",
  );
}

export async function buscarIndicadoresWhatsApp({ de, ate }) {
  return lerResposta(
    await getApi(comParametros("whatsapp/indicadores", { de, ate })),
    "Não foi possível carregar os indicadores.",
  );
}

/** tipo: 'CONFIRMACAO' ou 'LEMBRETE'. */
export async function reenviarMensagemWhatsApp(agendamentoId, tipo) {
  return lerResposta(
    await postApi("whatsapp/mensagens/reenviar", { agendamentoId, tipo }),
    "Não foi possível reenviar a mensagem.",
  );
}

export async function executarLembretesWhatsApp() {
  return lerResposta(
    await postApi("whatsapp/lembretes/executar", {}),
    "Não foi possível rodar o lote de lembretes.",
  );
}
