// RASCUNHO da linha do tempo, derivado do histórico do repositório (git log) e do
// CHANGELOG.md. As datas são as dos commits, não as de entrada em produção.
//
// Cada item com `rascunho: true` aparece na página com o selo "rascunho". Depois de
// revisar o texto e a data de um item, apague o `rascunho: true` dele. Só ligue
// PUBLIC_INDEXAVEL=true quando nenhum item estiver mais em rascunho.
//
// Faltam e não estão no repositório: a data em que cada município entrou em
// produção e os marcos anteriores ao primeiro commit.

export const HISTORICO = [
	{
		quando: 'Junho de 2025',
		titulo: 'O primeiro commit',
		texto:
			'O SIRG nasce para tirar a regulação de consultas e exames das planilhas: cadastro da solicitação do paciente e marcação por especialidade em um só lugar.',
		rascunho: true
	},
	{
		quando: 'Setembro de 2025',
		titulo: 'Municípios conversam entre si',
		texto:
			'Chegam a federação entre municípios por fila de mensagens, os pactos entre secretarias e a base de interoperabilidade em FHIR.',
		rascunho: true
	},
	{
		quando: 'Outubro de 2025',
		titulo: 'Transporte sanitário',
		texto:
			'O agendamento do transporte passa a acompanhar o agendamento da consulta: veículo, motorista e passageiros no mesmo fluxo.',
		rascunho: true
	},
	{
		quando: 'Fevereiro de 2026',
		titulo: 'Transparência pública',
		texto:
			'O sistema ganha uma página aberta com os totais de atendimento e as especialidades ofertadas pelo município.',
		rascunho: true
	},
	{
		quando: 'Maio de 2026',
		titulo: 'Unidades de saúde e cotas',
		texto:
			'Cada unidade passa a ter seus usuários, seus pacientes e sua cota de vagas por especialidade. A regulação deixa de ser uma fila única da secretaria.',
		rascunho: true
	},
	{
		quando: 'Junho de 2026',
		titulo: 'São Felipe',
		texto:
			'O segundo município passa a usar o SIRG, em servidor próprio e com base de dados separada.',
		rascunho: true
	},
	{
		quando: 'Julho de 2026',
		titulo: 'Relatórios para a gestão',
		texto:
			'Relatório de solicitações por profissional e indicador de tempo de espera por especialidade.',
		rascunho: true
	},
	{
		quando: 'Setembro de 2026',
		titulo: 'CNES, perfil de gestor e múltiplos perfis',
		texto:
			'Estabelecimentos e profissionais passam a vir do CNES. Surge o perfil de gestor, e um mesmo usuário pode alternar entre perfis.',
		rascunho: true
	},
	{
		quando: 'Outubro de 2026',
		titulo: 'Fila de espera, custos e WhatsApp',
		texto:
			'A fila de espera vira tela própria, a gestão acompanha custos e teto financeiro, e o paciente recebe a confirmação do agendamento pelo WhatsApp.',
		rascunho: true
	}
];
