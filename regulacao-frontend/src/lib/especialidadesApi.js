import { getApi } from '$lib/api.js';

// Lista especialidades do catálogo (entidade), autenticado
export async function listarEspecialidadesCatalogo() {
  const res = await getApi('catalog/especialidades/listar');
  if (!res.ok) throw new Error('Falha ao listar especialidades do catálogo');
  return await res.json();
}

export async function listarEspecialidadesMedicas() {
  const res = await getApi('catalog/especialidades/listar/especialidades-medicas')
  if(!res.ok) throw new Error('Falha ao listar exames do catálogo');
  return await res.json();
}


export async function listarGrupoRelatorio(){
  const res = await getApi('grupo-relatorio/listar')
  if(!res.ok) throw new Error('Falha ao listar grupos de relatório');
  return await res.json();
}

// Cria uma especialidade no catálogo (admin)
export async function criarEspecialidadeCatalogo({ codigo, nome, categoria, grupoRelatorioId, vagas = 0, ativo = true }) {
  const res = await (await import('$lib/api.js')).postApi('catalog/especialidades', {
    codigo,
    nome,
    categoria,
    grupoRelatorioId,
    vagas,
    ativo
  });
  return res;
}
