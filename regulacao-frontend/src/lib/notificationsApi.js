import { getApi, postApi } from "$lib/api.js";

export async function listarNaoLidas() {
  const res = await getApi("notifications/unread");
  if (!res.ok) throw new Error("Falha ao listar notificações");
  return await res.json();
}

export async function marcarComoLida(id) {
  const res = await postApi(`notifications/${id}/read`);
  if (!res.ok) throw new Error("Falha ao marcar como lida");
}

export async function marcarTodasComoLidas() {
  const res = await postApi("notifications/read-all");
  if (!res.ok) throw new Error("Falha ao marcar todas como lidas");
}

export async function listarLidas() {
  const res = await getApi("notifications/read");
  if (!res.ok) throw new Error("Falha ao listar notificações lidas");
  return await res.json();
}

export async function criarNotificacaoLocal({
  tipo = "LOCAL",
  resumo,
  linkPath,
  payload,
} = {}) {
  if (!resumo || !resumo.trim()) throw new Error("Resumo é obrigatório");
  const body = {
    tipo,
    resumo: resumo.trim(),
    linkPath: linkPath || null,
    payload: payload || null,
  };
  const res = await postApi("notifications", body);
  if (!res.ok) throw new Error("Falha ao criar notificação");
}
