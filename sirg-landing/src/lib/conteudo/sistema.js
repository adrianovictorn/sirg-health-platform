// Conteúdo estático da landing: módulos, etapas do fluxo, decisões de engenharia e autoria.

/** Etapas do fluxo de regulação, na ordem em que o pedido do paciente anda. */
export const FLUXO = [
	{
		titulo: 'Antes: a planilha',
		texto:
			'Pedidos anotados em arquivos diferentes, sem fila única, sem saber quantas vagas restam nem quem já foi avisado.'
	},
	{
		titulo: 'A regulação entra',
		texto:
			'Cada pedido ganha prioridade, unidade de origem e situação. A cota de cada unidade é descontada na hora do agendamento.'
	},
	{
		titulo: 'Agora: uma fila que anda',
		texto:
			'Solicitação, agendamento e transporte no mesmo registro. A gestão enxerga a fila; o paciente recebe a confirmação.'
	}
];

/** Linhas da planilha ilustrativa. Dados fictícios: nenhum vem de produção. */
export const PEDIDOS_ILUSTRATIVOS = [
	{
		pedido: '0142',
		especialidade: 'Cardiologia',
		unidade: 'USF Centro',
		situacao: 'Agendado',
		tom: 'folha'
	},
	{
		pedido: '0143',
		especialidade: 'Ultrassonografia',
		unidade: 'USF Rural',
		situacao: 'Na fila',
		tom: 'ceu'
	},
	{
		pedido: '0144',
		especialidade: 'Ortopedia',
		unidade: 'USF Centro',
		situacao: 'Transporte confirmado',
		tom: 'folha'
	},
	{
		pedido: '0145',
		especialidade: 'Oftalmologia',
		unidade: 'USF Bairro Novo',
		situacao: 'Agendado',
		tom: 'folha'
	},
	{
		pedido: '0146',
		especialidade: 'Neurologia',
		unidade: 'USF Rural',
		situacao: 'Urgente · na fila',
		tom: 'alerta'
	},
	{
		pedido: '0147',
		especialidade: 'Mamografia',
		unidade: 'USF Bairro Novo',
		situacao: 'Realizado',
		tom: 'neutro'
	}
];

/** Anotações soltas que somem quando a planilha vira fila. */
export const ANOTACOES = ['ligar depois??', 'já marcou?', 'ver vaga c/ secretaria', 'duplicado'];

/**
 * Módulos agrupados pela etapa do caminho do pedido, na ordem em que ele anda:
 * do registro na unidade até o que a gestão acompanha depois.
 */
export const MODULOS = [
	{
		etapa: 'O pedido entra',
		modulos: [
			{
				id: 'solicitacao',
				titulo: 'Solicitação',
				texto:
					'A unidade de saúde registra o pedido do paciente uma vez, com especialidade, prioridade e CID.'
			},
			{
				id: 'fila',
				titulo: 'Fila de espera',
				texto:
					'Quem espera, há quanto tempo e com qual prioridade, em uma lista única por município ou unidade.'
			}
		]
	},
	{
		etapa: 'A vaga é marcada',
		modulos: [
			{
				id: 'cota',
				titulo: 'Cota por unidade',
				texto:
					'Cada unidade tem suas vagas por especialidade e período. O saldo é conferido no momento de agendar.'
			},
			{
				id: 'agendamento',
				titulo: 'Agendamento',
				texto:
					'Marcação por especialidade, profissional e local, individual ou em lote, com comprovante em PDF.'
			}
		]
	},
	{
		etapa: 'O paciente chega',
		modulos: [
			{
				id: 'whatsapp',
				titulo: 'Avisos por WhatsApp',
				texto:
					'Confirmação, lembrete e cancelamento do agendamento enviados ao paciente, com opção de não receber.'
			},
			{
				id: 'transporte',
				titulo: 'Transporte sanitário',
				texto: 'Veículo, motorista e passageiros organizados a partir dos agendamentos do dia.'
			}
		]
	},
	{
		etapa: 'O município acompanha',
		modulos: [
			{
				id: 'relatorios',
				titulo: 'Relatórios e indicadores',
				texto:
					'Produção por especialidade, por profissional e por unidade, com exportação para planilha e PDF.'
			},
			{
				id: 'federacao',
				titulo: 'Federação entre municípios',
				texto:
					'Cada município roda em servidor e banco próprios. Quando há pacto, os pedidos trafegam por fila de mensagens.'
			}
		]
	}
];

export const DECISOES = [
	{
		titulo: 'Um servidor por município',
		texto:
			'Os dados de saúde de um município não dividem banco com os de outro. A troca entre eles é explícita, por fila.'
	},
	{
		titulo: 'Vaga não é vendida duas vezes',
		texto:
			'Cota e transporte usam controle de concorrência otimista: dois operadores não consomem a mesma vaga.'
	},
	{
		titulo: 'Banco que só cresce',
		texto:
			'Mais de cem migrations versionadas, sempre aditivas. Nenhuma atualização apaga dado de paciente.'
	},
	{
		titulo: 'Acesso decidido em um lugar',
		texto:
			'O que cada perfil vê de cada unidade é resolvido por um único serviço, coberto por testes de segurança.'
	}
];

export const AUTORES = [
	{
		nome: 'Adriano Victor N. Ribeiro',
		papel: 'Desenvolvimento',
		links: [
			{ rotulo: 'GitHub', href: 'https://github.com/adrianovictorn' },
			{ rotulo: 'LinkedIn', href: 'https://linkedin.com/in/adrianovictornribeiro' },
			{ rotulo: 'Instagram', href: 'https://www.instagram.com/adrianovictor.dev' }
		]
	}
];
