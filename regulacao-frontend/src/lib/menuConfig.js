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
  dashboard: [
    "M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-6 0a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1m-6 0h6",
  ],
  chart: [
    "M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z",
  ],
  calendar: [
    "M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z",
  ],
  agendaDia: [
    "M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2",
  ],
  pacientes: [
    "M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0z",
  ],
  relatorio: [
    "M9 17v-2m3 2v-4m3 4v-6m2 10H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z",
  ],
  solicitacao: [
    "M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z",
  ],
  gestao: [
    "M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z",
    "M15 12a3 3 0 11-6 0 3 3 0 016 0z",
  ],
  admin: [
    "M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z",
  ],
  filas: ["M4 6h16M4 10h16M4 14h16M4 18h16"],
  transporte: ["M8 7h12m0 0l-4-4m4 4l-4 4m0 6H4m0 0l4 4m-4-4l4-4"],
};

/**
 * Nome de exibição de cada perfil. Fonte única: o menu do usuário, o cadastro e
 * a listagem de usuários liam três listas separadas, que saíam de sincronia a
 * cada perfil novo (ADMIN_UNIDADE já faltava em uma delas).
 */
export const ROTULO_PERFIL = {
  ADMIN: "Administrador",
  ADMIN_UNIDADE: "Administrador da Unidade",
  GESTOR: "Gestor",
  USER: "Usuário Padrão",
  RECEPCAO: "Recepcionista",
  ENFERMEIRO: "Enfermeiro",
  MEDICO: "Médico",
  PACIENTE: "Paciente",
  COORD_TRANSPORTE: "Coordenador(a) de Transporte",
};

// Destino do "Dashboard" por perfil — fonte única também usada para decidir
// para onde ir logo depois de uma troca de perfil (ver TrocarPerfilModal.svelte).
const DASHBOARD_HREF_PADRAO = "/dashboard/procedimentos";
const DASHBOARD_HREF_POR_ROLE = {
  ADMIN: "/dashboard",
  COORD_TRANSPORTE: "/dashboard",
  ADMIN_UNIDADE: "/dashboard/unidade",
  GESTOR: "/dashboard",
  RECEPCAO: "/dashboard/unidade",
  ENFERMEIRO: "/dashboard/unidade",
  MEDICO: "/dashboard/unidade",
};

/** Para onde mandar o usuário logo após assumir `role` (troca de perfil, pós-login, etc.). */
export function hrefDashboardPorRole(role) {
  return DASHBOARD_HREF_POR_ROLE[role] ?? DASHBOARD_HREF_PADRAO;
}

const CLINICOS = ["ADMIN", "RECEPCAO", "ENFERMEIRO", "MEDICO"];

// ADMIN_UNIDADE: administrador restrito à própria unidade de lotação (postos de saúde).
// Vê o operacional da sua unidade, mas NÃO vê Painel Admin, Indicadores,
// Solicitações por Profissional nem qualquer tela de alcance global.
// O backend aplica a mesma restrição (UnidadeAcessoService), então ocultar aqui é
// conveniência de navegação, não a barreira de segurança.
const CLINICOS_E_UNIDADE = [...CLINICOS, "ADMIN_UNIDADE"];

export const MENU_SECTIONS = [
  {
    label: "Principal",
    items: [
      {
        type: "link",
        label: "Dashboard",
        icon: ICONS.dashboard,
        href: DASHBOARD_HREF_PADRAO,
        hrefByRole: DASHBOARD_HREF_POR_ROLE,
        roles: [
          "ADMIN",
          "ADMIN_UNIDADE",
          "GESTOR",
          "COORD_TRANSPORTE",
          "RECEPCAO",
          "ENFERMEIRO",
          "MEDICO",
          "USER",
          "PACIENTE",
        ],
      },
      {
        type: "link",
        label: "Indicadores",
        icon: ICONS.chart,
        href: "/indicadores",
        roles: ["ADMIN", "GESTOR"],
      },
      {
        type: "link",
        label: "Cotas da Unidade",
        icon: ICONS.chart,
        href: "/unidade/cotas",
        roles: ["ADMIN_UNIDADE"],
      },
      {
        type: "link",
        label: "Agendamento",
        icon: ICONS.calendar,
        href: "/agendar",
        roles: CLINICOS_E_UNIDADE,
      },
      {
        type: "link",
        label: "Agenda do Dia",
        icon: ICONS.agendaDia,
        href: "/dashboard/procedimentos/data",
        roles: CLINICOS_E_UNIDADE,
      },
      {
        type: "group",
        key: "pacientes",
        label: "Pacientes",
        icon: ICONS.pacientes,
        items: [
          {
            label: "Pacientes",
            href: "/paciente",
            roles: CLINICOS_E_UNIDADE,
          },
          {
            // GESTOR acompanha a fila (leitura), mas nao a lista de pacientes:
            // para ele o grupo aparece so com este item.
            label: "Fila de Espera",
            href: "/paciente/fila",
            roles: [...CLINICOS_E_UNIDADE, "GESTOR"],
          },
        ],
      },
      {
        type: "link",
        label: "Relatórios",
        icon: ICONS.relatorio,
        href: "/relatorio",
        roles: ["ADMIN", "GESTOR"],
      },
      {
        type: "link",
        label: "Relatório",
        icon: ICONS.relatorio,
        href: "/relatorio/hospital",
        roles: ["USER", "PACIENTE"],
      },
      {
        type: "link",
        label: "Solicitações por Profissional",
        icon: ICONS.chart,
        href: "/relatorio/profissional",
        roles: ["ADMIN", "GESTOR"],
      },
    ],
  },
  {
    label: "Solicitação",
    items: [
      {
        type: "group",
        key: "solicitacao",
        label: "Solicitação",
        icon: ICONS.solicitacao,
        items: [
          {
            label: "Cadastro de Consulta",
            href: "/cadastrar",
            roles: CLINICOS_E_UNIDADE,
          },
          {
            label: "Exame / Procedimento",
            href: "/exames",
            roles: CLINICOS_E_UNIDADE,
          },
        ],
      },
    ],
  },
  {
    label: "Agendas",
    items: [
      {
        type: "group",
        key: "agendas",
        label: "Agendas",
        icon: ICONS.calendar,
        // Grupo DINÂMICO: os itens não são fixos aqui — vêm dos Grupos de Relatório
        // marcados como "direcionado ao hospital" (grupo_relatorio.direcionado_hospital).
        // Antes esta lista era fixa (Cardiologista, Doppler, ..., USG) e ficava fora de
        // sincronia com o cadastro: bastava criar/renomear um grupo para o menu mentir.
        // Agora, marcar o grupo em /cadastrar/grupo-relatorio é o que o coloca no menu.
        //
        // `dynamic` diz QUAL fonte preenche os itens; `roles` diz quem vê o grupo.
        //
        // PACIENTE fora da lista (v1.6): a agenda lista nome/CPF/CNS de TODOS os
        // pacientes do dia por unidade — dado operacional, não o do próprio
        // paciente. O backend (`/especialidades/listar|contar/pacientes/por/grupo`)
        // já nega esse acesso; aqui é só para não oferecer um link que dá 403.
        dynamic: "agendasHospital",
        roles: ["USER"],
        items: [],
      },
    ],
  },
  {
    label: "Transporte",
    items: [
      {
        type: "group",
        key: "transporte",
        label: "Gestão de Transporte",
        icon: ICONS.transporte,
        items: [
          {
            label: "Agendar Transporte",
            href: "/agendar/transporte",
            roles: ["COORD_TRANSPORTE"],
          },
          {
            label: "Consultar Transporte",
            href: "/consultar/transporte",
            roles: ["COORD_TRANSPORTE"],
          },
        ],
      },
    ],
  },
  // Liberação de Agenda: oculta por agora (decisão do usuário, 2026-09-27) — a
  // rota /liberacao-agenda e o backend (AgendaController) continuam intactos,
  // só a entrada de navegação some. Remova o comentário abaixo para reativar.
  // {
  //   label: 'Liberação de Agenda',
  //   items: [
  //     // Fora do Painel Admin (decisao explicita, ver docs/decisoes/
  //     // 0001-agenda-alimenta-cota-acesso-admin-tela-propria.md): acesso restrito
  //     // a ADMIN, divergindo da regra R5 da especificacao original (que previa
  //     // tambem GESTOR e ADMIN_UNIDADE restrito a propria unidade).
  //     { type: 'link', label: 'Liberação de Agenda', icon: ICONS.agendaDia, href: '/liberacao-agenda', roles: ['ADMIN'] }
  //   ]
  // },
  {
    label: "Gerenciamento de Unidades",
    items: [
      {
        type: "group",
        key: "gerenciamento-unidades",
        label: "Gerenciamento de Unidades",
        icon: ICONS.gestao,
        items: [
          {
            label: "Cotas por Unidade",
            href: "/admin/cotas",
            roles: ["ADMIN"],
          },
          { label: "Unidades", href: "/admin/unidades", roles: ["ADMIN"] },
          {
            label: "Local de Agendamento",
            href: "/cadastrar/cidade/local-agendamento",
            roles: ["ADMIN"],
          },
          {
            label: "Cadastrar Cidade",
            href: "/cadastrar/cidade",
            roles: ["ADMIN"],
          },
          {
            label: "Profissionais",
            href: "/admin/profissionais",
            roles: ["ADMIN"],
          },
          // Custos: so ADMIN e GESTOR veem valores (o backend barra os demais em
          // /api/custos/**). GESTOR acompanha; so ADMIN altera preco, importa
          // planilha e libera teto — por isso "Importar Preços" e so dele.
          // Para o GESTOR este grupo aparece so com os tres links de custo: ele
          // nao tem acesso a nenhuma tela de gerenciamento de unidades acima.
          {
            label: "Painel de Custos",
            href: "/custos",
            roles: ["ADMIN", "GESTOR"],
          },
          {
            label: "Tetos Financeiros",
            href: "/custos/tetos",
            roles: ["ADMIN", "GESTOR"],
          },
          {
            label: "Preços das Especialidades",
            href: "/custos/especialidades",
            roles: ["ADMIN", "GESTOR"],
          },
          {
            label: "Importar Preços",
            href: "/admin/custos/importar",
            roles: ["ADMIN"],
          },
        ],
      },
    ],
  },
  {
    label: "Gestão",
    items: [
      {
        type: "group",
        key: "gestao",
        label: "Painel Gerencial",
        icon: ICONS.gestao,
        items: [
          {
            label: "Cadastrar CID",
            href: "/cadastrar/cid",
            roles: CLINICOS_E_UNIDADE,
          },
          {
            label: "Listar CID",
            href: "/listar/cid",
            roles: CLINICOS_E_UNIDADE,
          },
          {
            label: "Especialidade",
            href: "/cadastrar/especialidade",
            roles: ["ADMIN"],
          },
          {
            label: "Grupo Relatório",
            href: "/cadastrar/grupo-relatorio",
            roles: ["ADMIN"],
          },
          {
            label: "Cadastrar Cidade",
            href: "/cadastrar/cidade",
            roles: ["COORD_TRANSPORTE"],
          },
          {
            label: "Cadastrar Transporte",
            href: "/cadastrar/transporte",
            roles: ["COORD_TRANSPORTE"],
          },
          {
            label: "Cadastrar Motorista",
            href: "/cadastrar/motorista",
            roles: ["COORD_TRANSPORTE"],
          },
          {
            label: "Cadastrar Paciente",
            href: "/cadastrar/paciente",
            roles: ["COORD_TRANSPORTE"],
          },
          {
            label: "Ponto de Parada",
            href: "/cadastrar/cidade/local-agendamento",
            roles: ["COORD_TRANSPORTE"],
          },
        ],
      },
      {
        type: "group",
        key: "admin",
        label: "Painel Admin",
        icon: ICONS.admin,
        items: [
          {
            label: "Cadastrar Usuário",
            href: "/admin/cadastrar-usuario",
            roles: ["ADMIN"],
          },
          {
            label: "Listar Usuários",
            href: "/admin/listar-usuarios",
            roles: ["ADMIN"],
          },
          { label: "Pactos", href: "/admin/pactos", roles: ["ADMIN"] },
          {
            label: "Registrar Município",
            href: "/admin/municipios",
            roles: ["ADMIN"],
          },
          {
            label: "Notificações",
            href: "/admin/notificacoes",
            roles: ["ADMIN"],
          },
          { label: "WhatsApp", href: "/admin/whatsapp", roles: ["ADMIN"] },
        ],
      },
      {
        type: "group",
        key: "filas",
        label: "Filas Compartilhadas",
        icon: ICONS.filas,
        items: [
          {
            label: "Solicitações Compartilhadas",
            href: "/filas/compartilhadas",
            roles: ["ADMIN"],
          },
        ],
      },
    ],
  },
];

export const CHEVRON_DOWN = "M19 9l-7 7-7-7";

export function resolveHref(item, role) {
  return (item.hrefByRole && item.hrefByRole[role]) || item.href;
}

function isLinkVisible(link, role) {
  return !!role && Array.isArray(link.roles) && link.roles.includes(role);
}

/**
 * Filtra a árvore de menu para a role atual, removendo grupos/seções sem nenhum
 * link visível.
 *
 * @param role     role do usuário logado
 * @param dinamicos mapa `{ [chave]: [{ label, href }] }` com os itens dos grupos
 *                  que declaram `dynamic`. Quem carrega esses dados é o
 *                  RoleBasedMenu; aqui só são encaixados na árvore.
 *
 * Um grupo dinâmico ainda não carregado (ou sem resultado) simplesmente não
 * aparece — mesma regra dos grupos vazios, sem tratamento especial.
 */
export function buildMenuForRole(role, dinamicos = {}) {
  return MENU_SECTIONS.map((section) => {
    const items = section.items
      .map((item) => {
        if (item.type === "group") {
          // Grupo dinâmico: as roles ficam no próprio grupo (os itens vêm do
          // backend e não têm como declarar roles individualmente).
          if (item.dynamic) {
            if (
              !Array.isArray(item.roles) ||
              !role ||
              !item.roles.includes(role)
            )
              return null;
            const carregados = dinamicos[item.dynamic] ?? [];
            if (carregados.length === 0) return null;
            return { ...item, items: carregados };
          }
          const visibleLinks = item.items.filter((link) =>
            isLinkVisible(link, role),
          );
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

/** Monta o href da agenda de um grupo. O backend casa por `grupo_relatorio.codigo`. */
export function hrefDaAgendaDoGrupo(codigo) {
  return `/agendas/${encodeURIComponent(codigo)}`;
}
