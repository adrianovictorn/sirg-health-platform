// Conteúdo das telas ilustrativas da seção "O sistema por dentro".
//
// Tudo aqui é inventado: nenhum valor vem de produção nem do banco local. As unidades são
// as mesmas fictícias de PEDIDOS_ILUSTRATIVOS (sistema.js) e os pacientes não têm nome.
// Não acrescente CPF, CNS, telefone, endereço, CID, data de nascimento nem valores em reais,
// nem mascarados.

/** Espelha a lista de /paciente/fila: posição, prioridade, tempo de espera e pedidos. */
export const FILA_ILUSTRATIVA = {
	total: 312,
	pacientes: [
		{
			posicao: 1,
			nome: 'Paciente A',
			prioridade: 'emergencia',
			espera: 'há 96 dias',
			unidade: 'USF Rural',
			pedidos: [
				{
					especialidade: 'Neurologia',
					situacao: 'Pendente',
					prioridade: 'emergencia',
					espera: 'há 96 dias'
				},
				{
					especialidade: 'Tomografia',
					situacao: 'Pendente',
					prioridade: 'normal',
					espera: 'há 41 dias'
				}
			]
		},
		{
			posicao: 2,
			nome: 'Paciente B',
			prioridade: 'urgente',
			espera: 'há 74 dias',
			unidade: 'USF Centro',
			pedidos: [
				{
					especialidade: 'Cardiologia',
					situacao: 'Pendente',
					prioridade: 'urgente',
					espera: 'há 74 dias'
				}
			]
		},
		{
			posicao: 3,
			nome: 'Paciente C',
			prioridade: 'normal',
			espera: 'há 68 dias',
			unidade: 'USF Bairro Novo',
			pedidos: [
				{
					especialidade: 'Ortopedia',
					situacao: 'Retorno',
					prioridade: 'normal',
					espera: 'há 68 dias'
				},
				{
					especialidade: 'Ultrassonografia',
					situacao: 'Pendente',
					prioridade: 'normal',
					espera: 'há 12 dias'
				}
			]
		}
	]
};

/** Espelha a tabela de /unidade/cotas. O saldo e a barra saem de utilizadas e total. */
export const COTAS_ILUSTRATIVAS = {
	unidade: 'USF Centro',
	linhas: [
		{ especialidade: 'Cardiologia', periodo: '10/2026', utilizadas: 18, total: 40 },
		{ especialidade: 'Ultrassonografia', periodo: '10/2026', utilizadas: 34, total: 40 },
		{ especialidade: 'Ortopedia', periodo: '10/2026', utilizadas: 20, total: 20 },
		{ especialidade: 'Oftalmologia', periodo: '10/2026', utilizadas: 9, total: 30 },
		{ especialidade: 'Mamografia', periodo: '10/2026', utilizadas: 12, total: 25 }
	]
};

/**
 * Espelha /dashboard. Os números fecham entre si e com a fila acima: pendentes por unidade
 * somam os pacientes pendentes, e pendentes + agendadas + concluídas somam o total.
 */
export const PAINEL_ILUSTRATIVO = {
	visaoGeral: [
		{ cabecalho: '', titulo: 'Total de Solicitações', valor: 1284 },
		{ cabecalho: 'Pacientes', titulo: 'Pendentes', valor: 312 },
		{ cabecalho: 'Solicitações', titulo: 'Agendadas', valor: 187 },
		{ cabecalho: 'Solicitações', titulo: 'Concluídas', valor: 785 }
	],
	alerta: { cabecalho: 'Alertas', titulo: 'Urgência / Emergência', valor: 14 },
	pendentesPorUnidade: [
		{ unidade: 'USF Centro', valor: 128 },
		{ unidade: 'USF Rural', valor: 97 },
		{ unidade: 'USF Bairro Novo', valor: 87 }
	]
};
