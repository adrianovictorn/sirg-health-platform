import { criarLeitorDeIndicadores } from '$lib/server/indicadores.js';
import { instancias } from '$lib/server/instancias.js';

// Um leitor por processo: o cache dele é o que impede cada visita de ir às instâncias.
const leitor = criarLeitorDeIndicadores();

// Versão e data de deploy da instância ficam no servidor: o que sai daqui vai inteiro para o navegador.
function paraPagina(resumo) {
	if (!resumo.disponivel) return { disponivel: false };
	return {
		disponivel: true,
		totais: resumo.totais,
		catalogo: resumo.catalogo,
		geradoEm: resumo.geradoEm
	};
}

export async function load() {
	const municipios = await Promise.all(
		instancias().map(async (instancia) => ({
			id: instancia.id,
			nome: instancia.nome,
			uf: instancia.uf,
			urlAcesso: instancia.urlAcesso,
			...paraPagina(await leitor.ler(instancia))
		}))
	);

	return { municipios };
}
