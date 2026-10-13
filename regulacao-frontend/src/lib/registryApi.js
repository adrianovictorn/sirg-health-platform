import { getApi, postApi } from "$lib/api.js";

export async function listarDisponiveis(search = "") {
  const q = search ? `?search=${encodeURIComponent(search)}` : "";
  const res = await getApi(`registry/municipios/disponiveis${q}`);
  if (!res.ok) throw new Error("Falha ao buscar municípios disponíveis");
  return await res.json();
}

export async function registrarPublico({
  nome,
  cnes,
  rabbitQueueName,
  baseUrl,
}) {
  const res = await postApi("registry/municipios/register-public", {
    nome,
    cnes,
    rabbitQueueName,
    baseUrl,
  });
  if (!res.ok) throw new Error("Falha ao registrar município");
  return await res.json();
}

export async function listarPactosPublicos() {
  const res = await getApi("registry/pactos");
  if (!res.ok) throw new Error("Falha ao listar pactos públicos");
  return await res.json();
}
