import { getApi } from "$lib/api.js";

export async function listarMunicipios() {
  const res = await getApi("municipios");
  if (!res.ok) throw new Error("Falha ao listar municípios");
  return await res.json();
}
