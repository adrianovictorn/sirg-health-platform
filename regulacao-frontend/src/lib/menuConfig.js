// Configuração única de navegação do SIRG.
//
// Cada link declara `roles`: a lista de Roles (do backend, enum `Roles`) que podem vê-lo.
// Grupos (colapsáveis) e seções não declaram roles — ficam visíveis automaticamente
// se tiverem ao menos um link visível para o usuário atual. Isso evita duas fontes de
// verdade: para dar/tirar acesso de uma tela, basta editar `roles` no link correspondente.
//
// `href` é o destino padrão do link. Quando o mesmo item aponta para páginas diferentes
// dependendo da role (ex.: "Dashboard"), use `hrefByRole` para os casos especiais — o
// restante das roles cai no `href` padrão.

const ICONS = {
  dashboard: ['M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-6 0a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1m-6 0h6'],
  chart: ['M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z'],
  calendar: ['M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z'],
  agendaDia: ['M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2'],
  pacientes: ['M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0z'],
  relatorio: ['M9 17v-2m3 2v-4m3 4v-6m2 10H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z'],
  solicitacao: ['M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z'],
  gestao: [
    'M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z',
    'M15 12a3 3 0 11-6 0 3 3 0 016 0z'
  ],
  admin: ['M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z'],
  filas: ['M4 6h16M4 10h16M4 14h16M4 18h16'],
  transporte: ['M8 7h12m0 0l-4-4m4 4l-4 4m0 6H4m0 0l4 4m-4-4l4-4']
};

const CLINICOS = ['ADMIN', 'RECEPCAO', 'ENFERMEIRO', 'MEDICO'];

// ADMIN_UNIDADE: administrador restrito à própria unidade de lotação (postos de saúde).
// Vê o operacional da sua unidade, mas NÃO vê Painel Admin, Indicadores,
// Solicitações por Profissional nem qualquer tela de alcance global.
// O backend aplica a mesma restrição (UnidadeAcessoService), então ocultar aqui é
// conveniência de navegação, não a barreira de segurança.
const CLINICOS_E_UNIDADE = [...CLINICOS, 'ADMIN_UNIDADE'];

export const MENU_SECTIONS = [
  {
    label: 'Principal',
    items: [
      {
        type: 'link',
        label: 'Dashboard',
        icon: ICONS.dashboard,
        href: '/dashboard/procedimentos',
        hrefByRole: {
          ADMIN: '/dashboard',
          COORD_TRANSPORTE: '/dashboard',
          ADMIN_UNIDADE: '/dashboard/unidade',
          RECEPCAO: '/dashboard/unidade',
          ENFERMEIRO: '/dashboard/unidade',
          MEDICO: '/dashboard/unidade'
        },
        roles: ['ADMIN', 'ADMIN_UNIDADE', 'COORD_TRANSPORTE', 'RECEPCAO', 'ENFERMEIRO', 'MEDICO', 'USER', 'PACIENTE']
      },
      { type: 'link', label: 'Indicadores', icon: ICONS.chart, href: '/indicadores', roles: ['ADMIN'] },
      { type: 'link', label: 'Agendamento', icon: ICONS.calendar, href: '/agendar', roles: CLINICOS_E_UNIDADE },
      { type: 'link', label: 'Agenda do Dia', icon: ICONS.agendaDia, href: '/dashboard/procedimentos/data', roles: CLINICOS_E_UNIDADE },
      { type: 'link', label: 'Pacientes', icon: ICONS.pacientes, href: '/paciente', roles: CLINICOS_E_UNIDADE },
      { type: 'link', label: 'Relatórios', icon: ICONS.relatorio, href: '/relatorio', roles: ['ADMIN'] },
      { type: 'link', label: 'Relatório', icon: ICONS.relatorio, href: '/relatorio/hospital', roles: ['USER', 'PACIENTE'] },
      { type: 'link', label: 'Solicitações por Profissional', icon: ICONS.chart, href: '/relatorio/profissional', roles: ['ADMIN'] }
    ]
  },
  {
    label: 'Solicitação',
    items: [
      {
        type: 'group',
        key: 'solicitacao',
        label: 'Solicitação',
        icon: ICONS.solicitacao,
        items: [
          { label: 'Cadastro de Consulta', href: '/cadastrar', roles: CLINICOS_E_UNIDADE },
          { label: 'Exame / Procedimento', href: '/exames', roles: CLINICOS_E_UNIDADE }
        ]
      }
    ]
  },
  {
    label: 'Agendas',
    items: [
      {
        type: 'group',
        key: 'agendas',
        label: 'Agendas',
        icon: ICONS.calendar,
        items: [
          { label: 'Cardiologista', href: '/agendas/cardiologista', roles: ['USER', 'PACIENTE'] },
          { label: 'Doppler', href: '/agendas/doppler', roles: ['USER', 'PACIENTE'] },
          { label: 'Eletrocardiograma', href: '/agendas/eletrocardiograma', roles: ['USER', 'PACIENTE'] },
          { label: 'Laboratório', href: '/agendas/laboratorio', roles: ['USER', 'PACIENTE'] },
          { label: 'Ortopedista', href: '/agendas/ortopedista', roles: ['USER', 'PACIENTE'] },
          { label: 'Pediatria', href: '/agendas/pediatra', roles: ['USER', 'PACIENTE'] },
          { label: 'Raio X', href: '/agendas/raio-x', roles: ['USER', 'PACIENTE'] },
          { label: 'USG', href: '/agendas/ultrasom', roles: ['USER', 'PACIENTE'] }
        ]
      }
    ]
  },
  {
    label: 'Transporte',
    items: [
      {
        type: 'group',
        key: 'transporte',
        label: 'Gestão de Transporte',
        icon: ICONS.transporte,
        items: [
          { label: 'Agendar Transporte', href: '/agendar/transporte', roles: ['COORD_TRANSPORTE'] },
          { label: 'Consultar Transporte', href: '/consultar/transporte', roles: ['COORD_TRANSPORTE'] }
        ]
      }
    ]
  },
  {
    label: 'Gestão',
    items: [
      {
        type: 'group',
        key: 'gestao',
        label: 'Painel Gerencial',
        icon: ICONS.gestao,
        items: [
          { label: 'Cadastrar CID', href: '/cadastrar/cid', roles: CLINICOS_E_UNIDADE },
          { label: 'Listar CID', href: '/listar/cid', roles: CLINICOS_E_UNIDADE },
          // Consulta somente-leitura das cotas da própria unidade. A definição das
          // cotas continua em /admin/cotas, exclusiva do ADMIN global.
          { label: 'Cotas da Unidade', href: '/unidade/cotas', roles: ['ADMIN_UNIDADE'] },
          { label: 'Especialidade', href: '/cadastrar/especialidade', roles: ['ADMIN'] },
          { label: 'Grupo Relatório', href: '/cadastrar/grupo-relatorio', roles: ['ADMIN'] },
          { label: 'Cadastrar Cidade', href: '/cadastrar/cidade', roles: ['ADMIN', 'COORD_TRANSPORTE'] },
          { label: 'Local de Agendamento', href: '/cadastrar/cidade/local-agendamento', roles: ['ADMIN'] },
          { label: 'Cadastrar Transporte', href: '/cadastrar/transporte', roles: ['COORD_TRANSPORTE'] },
          { label: 'Cadastrar Motorista', href: '/cadastrar/motorista', roles: ['COORD_TRANSPORTE'] },
          { label: 'Cadastrar Paciente', href: '/cadastrar/paciente', roles: ['COORD_TRANSPORTE'] },
          { label: 'Ponto de Parada', href: '/cadastrar/cidade/local-agendamento', roles: ['COORD_TRANSPORTE'] }
        ]
      },
      {
        type: 'group',
        key: 'admin',
        label: 'Painel Admin',
        icon: ICONS.admin,
        items: [
          { label: 'Cadastrar Usuário', href: '/admin/cadastrar-usuario', roles: ['ADMIN'] },
          { label: 'Listar Usuários', href: '/admin/listar-usuarios', roles: ['ADMIN'] },
          { label: 'Pactos', href: '/admin/pactos', roles: ['ADMIN'] },
          { label: 'Registrar Município', href: '/admin/municipios', roles: ['ADMIN'] },
          { label: 'Notificações', href: '/admin/notificacoes', roles: ['ADMIN'] },
          { label: 'Unidades', href: '/admin/unidades', roles: ['ADMIN'] },
          { label: 'Profissionais', href: '/admin/profissionais', roles: ['ADMIN'] },
          { label: 'Cotas por Unidade', href: '/admin/cotas', roles: ['ADMIN'] }
        ]
      },
      {
        type: 'group',
        key: 'filas',
        label: 'Filas Compartilhadas',
        icon: ICONS.filas,
        items: [{ label: 'Solicitações Compartilhadas', href: '/filas/compartilhadas', roles: ['ADMIN'] }]
      }
    ]
  }
];

export const CHEVRON_DOWN = 'M19 9l-7 7-7-7';

export function resolveHref(item, role) {
  return (item.hrefByRole && item.hrefByRole[role]) || item.href;
}

function isLinkVisible(link, role) {
  return !!role && Array.isArray(link.roles) && link.roles.includes(role);
}

// Filtra a árvore de menu para a role atual, removendo grupos/seções sem nenhum link visível.
export function buildMenuForRole(role) {
  return MENU_SECTIONS.map((section) => {
    const items = section.items
      .map((item) => {
        if (item.type === 'group') {
          const visibleLinks = item.items.filter((link) => isLinkVisible(link, role));
          if (visibleLinks.length === 0) return null;
          return { ...item, items: visibleLinks };
        }
        return isLinkVisible(item, role) ? item : null;
      })
      .filter(Boolean);

    if (items.length === 0) return null;
    return { ...section, items };
  }).filter(Boolean);
}
