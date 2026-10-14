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
//
// Subgrupos (terceiro nível): um grupo longo que mistura dois assuntos pode declarar
// `subgroups: [{ key, label }]` e marcar cada link com `sub: <key>`. A lista `items`
// continua PLANA — a filtragem por role (`buildMenuForRole`) não muda —, e quem monta
// o nível extra para exibição é `organizarItensDoGrupo`. Subgrupo não declara roles:
// aparece se o usuário enxerga itens dele, como já acontece com grupo e seção.

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
  // Os quatro abaixo substituem ícones repetidos ou pouco representativos (o
  // gráfico de barras servia a três itens, a engrenagem a dois grupos, e
  // "transporte" era um par de setas). Mesma família dos demais: Heroicons v1 outline.
  cotas: [
    "M11 3.055A9.001 9.001 0 1020.945 13H11V3.055z",
    "M20.488 9H15V3.512A9.025 9.025 0 0120.488 9z",
  ],
  profissional: [
    "M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z",
  ],
  unidades: [
    "M19 21V5a2 2 0 00-2-2H7a2 2 0 00-2 2v16m14 0h2m-2 0h-5m-9 0H3m2 0h5M9 7h1m-1 4h1m4-4h1m-1 4h1m-5 10v-5a1 1 0 011-1h2a1 1 0 011 1v5m-4 0h4",
  ],
  filas: [
    "M8.684 13.342C8.886 12.938 9 12.482 9 12c0-.482-.114-.938-.316-1.342m0 2.684a3 3 0 110-2.684m0 2.684l6.632 3.316m-6.632-6l6.632-3.316m0 0a3 3 0 105.367-2.684 3 3 0 00-5.367 2.684zm0 9.316a3 3 0 105.368 2.684 3 3 0 00-5.368-2.684z",
  ],
  transporte: [
    "M9 17a2 2 0 11-4 0 2 2 0 014 0zM19 17a2 2 0 11-4 0 2 2 0 014 0z",
    "M13 16V6a1 1 0 00-1-1H4a1 1 0 00-1 1v10a1 1 0 001 1h1m8-1a1 1 0 01-1 1H9m4-1V8a1 1 0 011-1h2.586a1 1 0 01.707.293l3.414 3.414a1 1 0 01.293.707V16a1 1 0 01-1 1h-1m-6-1a1 1 0 001 1h1M5 17a2 2 0 104 0m-4 0a2 2 0 114 0m6 0a2 2 0 104 0m-4 0a2 2 0 114 0",
  ],
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
  // Usuário Padrão é o operador da unidade executante: o trabalho do dia dele
  // é a tela de Atendimentos (município inteiro), não a agenda por unidade.
  USER: "/atendimentos",
};

/** Para onde mandar o usuário logo após assumir `role` (troca de perfil, pós-login, etc.). */
export function hrefDashboardPorRole(role) {
  return DASHBOARD_HREF_POR_ROLE[role] ?? DASHBOARD_HREF_PADRAO;
}

const CLINICOS = ["ADMIN", "RECEPCAO", "ENFERMEIRO", "MEDICO"];

// ADMIN_UNIDADE: administrador restrito à própria unidade de lotação (postos de saúde).
// Vê o operacional da sua unidade, mas NÃO vê Administração, Indicadores,
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
        type: "group",
        key: "solicitacao",
        label: "Nova Solicitação",
        icon: ICONS.solicitacao,
        items: [
          {
            label: "Consulta",
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
        // Para o USER este é o próprio "Dashboard" (ver DASHBOARD_HREF_POR_ROLE),
        // então o link só é declarado para o ADMIN: evita dois itens no menu do
        // operador apontando para a mesma tela.
        type: "link",
        label: "Atendimentos",
        icon: ICONS.agendaDia,
        href: "/atendimentos",
        roles: ["ADMIN"],
      },
      {
        // Os números do dia de Atendimentos (totais e por unidade solicitante).
        // Para o USER cai logo abaixo de Dashboard; para o ADMIN, de Atendimentos.
        type: "link",
        label: "Quantitativos",
        icon: ICONS.chart,
        href: "/atendimentos/quantitativos",
        roles: ["USER", "ADMIN"],
      },
      {
        type: "link",
        label: "Cotas da Unidade",
        icon: ICONS.cotas,
        href: "/unidade/cotas",
        roles: ["ADMIN_UNIDADE"],
      },
    ],
  },
  {
    label: "Hospital",
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
        //
        // Cada agenda abre a tela de Atendimentos já filtrada pelo grupo (ver
        // `hrefDaAgendaDoGrupo`), com o município inteiro. As telas antigas
        // /agendas/{grupo} filtram pela unidade SOLICITANTE e ficavam vazias para
        // quem é lotado numa unidade executante; continuam existindo, sem link.
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
    label: "Relatórios e Indicadores",
    items: [
      {
        type: "link",
        label: "Indicadores",
        icon: ICONS.chart,
        href: "/indicadores",
        roles: ["ADMIN", "GESTOR"],
      },
      {
        type: "link",
        label: "Relatórios",
        icon: ICONS.relatorio,
        href: "/relatorio",
        roles: ["ADMIN", "ADMIN_UNIDADE", "GESTOR"],
      },
      {
        type: "link",
        label: "Relatórios",
        icon: ICONS.relatorio,
        href: "/relatorio/hospital",
        roles: ["USER", "PACIENTE"],
      },
      {
        type: "link",
        label: "Solicitações por Profissional",
        icon: ICONS.profissional,
        href: "/relatorio/profissional",
        roles: ["ADMIN", "GESTOR"],
      },
    ],
  },
  {
    label: "Gestão",
    items: [
      {
        type: "group",
        key: "gerenciamento-unidades",
        label: "Unidades e Custos",
        icon: ICONS.unidades,
        subgroups: [
          { key: "unidades", label: "Unidades" },
          { key: "custos", label: "Custos" },
        ],
        items: [
          {
            label: "Cotas por Unidade",
            href: "/admin/cotas",
            roles: ["ADMIN"],
            sub: "unidades",
          },
          {
            // Mesmo nome do título da tela; "Unidades" repetiria o rótulo do subgrupo.
            label: "Gestão de Unidades",
            href: "/admin/unidades",
            roles: ["ADMIN"],
            sub: "unidades",
          },
          {
            label: "Locais de Agendamento",
            href: "/cadastrar/cidade/local-agendamento",
            roles: ["ADMIN"],
            sub: "unidades",
          },
          {
            label: "Cadastrar Cidade",
            href: "/cadastrar/cidade",
            roles: ["ADMIN"],
            sub: "unidades",
          },
          {
            label: "Profissionais",
            href: "/admin/profissionais",
            roles: ["ADMIN"],
            sub: "unidades",
          },
          // Custos: so ADMIN e GESTOR veem valores (o backend barra os demais em
          // /api/custos/**). GESTOR acompanha; so ADMIN altera preco, importa
          // planilha e libera teto — por isso "Importar Preços" e so dele.
          // Para o GESTOR este grupo aparece so com os tres links de custo: ele
          // nao tem acesso a nenhuma tela de gerenciamento de unidades acima. Como
          // sobra um subgrupo so, os tres links aparecem direto no grupo.
          {
            label: "Painel de Custos",
            href: "/custos",
            roles: ["ADMIN", "GESTOR"],
            sub: "custos",
          },
          {
            label: "Tetos Financeiros",
            href: "/custos/tetos",
            roles: ["ADMIN", "GESTOR"],
            sub: "custos",
          },
          {
            label: "Preços das Especialidades",
            href: "/custos/especialidades",
            roles: ["ADMIN", "GESTOR"],
            sub: "custos",
          },
          {
            label: "Importar Preços",
            href: "/admin/custos/importar",
            roles: ["ADMIN"],
            sub: "custos",
          },
        ],
      },
      {
        type: "group",
        key: "gestao",
        label: "Cadastros",
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
            label: "Especialidades",
            href: "/cadastrar/especialidade",
            roles: ["ADMIN"],
          },
          {
            label: "Grupos de Relatório",
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
        label: "Administração",
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
        // Era um grupo com um único item ("Solicitações Compartilhadas"): um
        // clique a mais para chegar à mesma tela. Link direto, mesmo href e roles.
        type: "link",
        label: "Filas Compartilhadas",
        icon: ICONS.filas,
        href: "/filas/compartilhadas",
        roles: ["ADMIN"],
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

/**
 * Diz se a role enxerga a rota `href` no menu. Serve para uma tela decidir se
 * mostra um atalho para outra sem repetir a regra de roles: a fonte continua
 * sendo `MENU_SECTIONS`. Não é controle de acesso — quem autoriza é o backend.
 */
export function roleVeRota(role, href) {
  return buildMenuForRole(role).some((section) =>
    section.items.some((item) =>
      item.type === "group"
        ? item.items.some((link) => link.href === href)
        : item.href === href,
    ),
  );
}

/**
 * Organiza os itens JÁ FILTRADOS de um grupo para exibição, montando o terceiro
 * nível a partir de `group.subgroups` e do `sub` de cada link. Só apresentação:
 * não decide acesso (isso é de `buildMenuForRole`) e devolve cada link recebido
 * exatamente uma vez, na ordem original.
 *
 * Devolve uma lista em que cada entrada é um link solto ou
 * `{ type: "subgroup", key, label, items }`. `key` é única no menu inteiro
 * (`<grupo>/<subgrupo>`), para servir de estado aberto/fechado.
 *
 * Não cria nível à toa: subgrupo com um link só vira link solto, e se o grupo
 * inteiro couber num único subgrupo os links ficam direto no grupo (caso do
 * GESTOR em "Unidades e Custos", que só enxerga os custos).
 */
export function organizarItensDoGrupo(group) {
  const rotulos = new Map((group.subgroups ?? []).map((s) => [s.key, s.label]));
  if (rotulos.size === 0) return group.items;

  const entradas = [];
  const porChave = new Map();
  for (const link of group.items) {
    if (!rotulos.has(link.sub)) {
      entradas.push(link);
      continue;
    }
    let subgrupo = porChave.get(link.sub);
    if (!subgrupo) {
      subgrupo = {
        type: "subgroup",
        key: `${group.key}/${link.sub}`,
        label: rotulos.get(link.sub),
        items: [],
      };
      porChave.set(link.sub, subgrupo);
      entradas.push(subgrupo);
    }
    subgrupo.items.push(link);
  }

  const semSubgrupoDeUm = entradas.flatMap((e) =>
    e.type === "subgroup" && e.items.length === 1 ? e.items : [e],
  );
  if (semSubgrupoDeUm.length === 1 && semSubgrupoDeUm[0].type === "subgroup") {
    return semSubgrupoDeUm[0].items;
  }
  return semSubgrupoDeUm;
}

/**
 * Monta o href da agenda de um grupo: a tela de Atendimentos filtrada por ele.
 * O backend casa por `grupo_relatorio.codigo`.
 */
export function hrefDaAgendaDoGrupo(codigo) {
  return `/atendimentos?grupo=${encodeURIComponent(codigo)}`;
}
