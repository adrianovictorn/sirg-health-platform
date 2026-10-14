<script>
  import { user } from '$lib/stores/auth.js';
  import { carregarAgendasAtendimento } from '$lib/atendimentosApi.js';
  import { buildMenuForRole, fontesDinamicasDaRole, organizarItensDoGrupo, resolveHref, hrefDaAgendaDoGrupo, CHEVRON_DOWN } from '$lib/menuConfig.js';

  export let activePage = '';

  let abertoMobile = false;
  let open = '';
  // Subgrupo aberto (terceiro nível). Mesmo mecanismo do grupo: um por vez.
  let openSub = '';

  // Itens dos grupos de menu declarados como `dynamic` no menuConfig.
  let dinamicos = {};

  const toggle = (key) => { open = open === key ? '' : key; };
  const toggleSub = (key) => { openSub = openSub === key ? '' : key; };

  $: role = $user?.role ?? null;
  // `entradas` é a lista do grupo já organizada em subgrupos, só para exibição;
  // `items` continua sendo a lista plana filtrada por role.
  $: sections = buildMenuForRole(role, dinamicos).map((section) => ({
    ...section,
    items: section.items.map((item) =>
      item.type === 'group' ? { ...item, entradas: organizarItensDoGrupo(item) } : item
    )
  }));

  /**
   * Carrega as agendas do menu a partir do backend (/api/atendimentos/agendas), que
   * já devolve o escopo de quem está logado: grupos ativos direcionados ao hospital
   * ou, para o operador vinculado a unidades executantes (V110), os grupos delas.
   * Antes esta lista era fixa no menuConfig e não refletia o cadastro.
   *
   * Só busca quando a role exibe o grupo dinâmico, e de novo quando a role muda
   * (alternância de perfil). Se a chamada falhar, o grupo "Agendas" apenas não
   * aparece — os demais itens continuam vindo da configuração estática.
   */
  let agendasCarregadasPara = null;

  async function carregarAgendasDoHospital(paraRole) {
    agendasCarregadasPara = paraRole;
    if (!fontesDinamicasDaRole(paraRole).includes('agendasHospital')) {
      dinamicos = { ...dinamicos, agendasHospital: [] };
      return;
    }
    try {
      const grupos = await carregarAgendasAtendimento();
      if (agendasCarregadasPara !== paraRole) return;
      dinamicos = {
        ...dinamicos,
        agendasHospital: grupos.map((g) => ({ label: g.nome, href: hrefDaAgendaDoGrupo(g.codigo) }))
      };
    } catch {
      // Menu é navegação: falha aqui não pode derrubar a página.
    }
  }

  // Foco visível por teclado (WCAG 2.4.7), igual para link, sublink e grupo.
  const FOCO = 'focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-emerald-400';

  // Link e botão de grupo partem da mesma base (mesma borda esquerda de 2px e o
  // mesmo recuo), para os ícones caírem na mesma coluna em qualquer estado.
  const ITEM = `flex items-center gap-3 pl-[10px] pr-3 py-2 rounded-lg text-sm font-medium leading-5 border-l-2 transition-colors ${FOCO}`;

  const SUBITEM = `flex items-center gap-2 px-3 py-1.5 rounded-md text-sm leading-5 transition-colors ${FOCO}`;

  // Título de seção: slate-400 sobre slate-900 (o slate-500 anterior ficava abaixo de 4.5:1).
  const TITULO_SECAO = 'px-3 pb-1 text-[11px] font-semibold uppercase tracking-wider text-slate-400';

  const link = (href) =>
    activePage === href
      ? `${ITEM} bg-emerald-500/15 text-emerald-400 border-emerald-400`
      : `${ITEM} text-slate-300 hover:bg-slate-800 hover:text-white border-transparent`;

  const sublink = (href) =>
    activePage === href
      ? `${SUBITEM} text-emerald-400 bg-emerald-500/10 font-medium`
      : `${SUBITEM} text-slate-300 hover:text-white hover:bg-slate-700/50`;

  // Terceiro nível: o subgrupo ocupa uma linha da lista do grupo (mesma base do
  // sublink, com o marcador na mesma coluna) e repete o chevron do grupo; os itens
  // dele ficam atrás de uma segunda guia vertical, sem marcador nem ícone.
  const NETO = `flex items-center px-2 py-1.5 rounded-md text-sm leading-5 transition-colors ${FOCO}`;
  const LISTA_NETOS = 'ml-[13px] pl-2 border-l border-slate-700 space-y-0.5';

  const netolink = (href) =>
    activePage === href
      ? `${NETO} text-emerald-400 bg-emerald-500/10 font-medium`
      : `${NETO} text-slate-300 hover:text-white hover:bg-slate-700/50`;

  // Fechado com a página atual dentro: o rótulo fica em emerald, para o usuário
  // não perder onde está (aberto, quem marca a posição é o próprio item).
  const subgroupBtn = (sub) =>
    `w-full ${SUBITEM} font-medium ${
      openSub === sub.key
        ? 'text-white'
        : sub.items.some((l) => resolveHref(l, role) === activePage)
          ? 'text-emerald-400 hover:bg-slate-700/50'
          : 'text-slate-200 hover:text-white hover:bg-slate-700/50'
    }`;

  const groupBtn = (key) =>
    `w-full ${ITEM} border-transparent ${
      open === key ? 'text-white bg-slate-800' : 'text-slate-300 hover:bg-slate-800 hover:text-white'
    }`;

  $: if (role !== agendasCarregadasPara) carregarAgendasDoHospital(role);

  // Abre automaticamente o grupo que contém a página atual, sem listas de rotas hardcoded.
  $: {
    const grupoAtivo = sections
      .flatMap((s) => s.items)
      .find((item) => item.type === 'group' && item.items.some((l) => resolveHref(l, role) === activePage));
    if (grupoAtivo) {
      open = grupoAtivo.key;
      // O subgrupo que contém a página atual abre junto com o grupo.
      const subAtivo = grupoAtivo.entradas.find(
        (e) => e.type === 'subgroup' && e.items.some((l) => resolveHref(l, role) === activePage)
      );
      if (subAtivo) openSub = subAtivo.key;
    }
  }
</script>

{#if role}
  <!-- ── DESKTOP SIDEBAR ─────────────────────────────────────────────── -->
  <aside class="hidden md:flex w-64 min-h-screen bg-slate-900 flex-col shadow-xl border-r border-slate-800">
    <div class="flex flex-col items-center py-6 px-4 border-b border-slate-800">
      <img src="/images/logo7.png" alt="SIRG" class="h-16 w-auto mb-3 drop-shadow" />
      <span class="text-xs font-semibold tracking-widest text-slate-400 uppercase">Sistema de Regulação</span>
    </div>

    <nav aria-label="Menu principal" class="flex-1 overflow-y-auto px-3 py-4 space-y-5">
      {#each sections as section}
        <div class="space-y-0.5">
          <p class={TITULO_SECAO}>{section.label}</p>
          {#each section.items as item}
            {#if item.type === 'group'}
              <button type="button" on:click={() => toggle(item.key)} aria-expanded={open === item.key} class={groupBtn(item.key)}>
                <svg class="w-4 h-4 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2" aria-hidden="true">
                  {#each item.icon as d}<path stroke-linecap="round" stroke-linejoin="round" {d} />{/each}
                </svg>
                <span class="flex-1 text-left">{item.label}</span>
                <svg class="w-4 h-4 shrink-0 text-slate-400 transition-transform duration-200 {open === item.key ? 'rotate-180' : ''}" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2" aria-hidden="true">
                  <path stroke-linecap="round" stroke-linejoin="round" d={CHEVRON_DOWN} />
                </svg>
              </button>
              {#if open === item.key}
                <div class="ml-5 pl-3 border-l border-slate-700 space-y-0.5">
                  {#each item.entradas as sub (sub.key ?? sub.href)}
                    {#if sub.type === 'subgroup'}
                      <button type="button" on:click={() => toggleSub(sub.key)} aria-expanded={openSub === sub.key} class={subgroupBtn(sub)}>
                        <span class="w-1 h-1 rounded-full bg-current opacity-60 shrink-0" aria-hidden="true"></span>
                        <span class="flex-1 text-left">{sub.label}</span>
                        <svg class="w-3.5 h-3.5 shrink-0 text-slate-400 transition-transform duration-200 {openSub === sub.key ? 'rotate-180' : ''}" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2" aria-hidden="true">
                          <path stroke-linecap="round" stroke-linejoin="round" d={CHEVRON_DOWN} />
                        </svg>
                      </button>
                      {#if openSub === sub.key}
                        <div role="group" aria-label={sub.label} class={LISTA_NETOS}>
                          {#each sub.items as neto (neto.href)}
                            <a href={resolveHref(neto, role)} class={netolink(resolveHref(neto, role))} aria-current={activePage === resolveHref(neto, role) ? 'page' : undefined}>
                              {neto.label}
                            </a>
                          {/each}
                        </div>
                      {/if}
                    {:else}
                      <a href={resolveHref(sub, role)} class={sublink(resolveHref(sub, role))} aria-current={activePage === resolveHref(sub, role) ? 'page' : undefined}>
                        <span class="w-1 h-1 rounded-full bg-current opacity-60 shrink-0" aria-hidden="true"></span>
                        {sub.label}
                      </a>
                    {/if}
                  {/each}
                </div>
              {/if}
            {:else}
              <a href={resolveHref(item, role)} class={link(resolveHref(item, role))} aria-current={activePage === resolveHref(item, role) ? 'page' : undefined}>
                <svg class="w-4 h-4 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2" aria-hidden="true">
                  {#each item.icon as d}<path stroke-linecap="round" stroke-linejoin="round" {d} />{/each}
                </svg>
                {item.label}
              </a>
            {/if}
          {/each}
        </div>
      {/each}
    </nav>

    <div class="px-4 py-4 border-t border-slate-800 flex items-center gap-2">
      <div class="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-pulse"></div>
      <span class="text-xs text-slate-400">Adriano Victor © 2025</span>
    </div>
  </aside>

  <!-- ── MOBILE SIDEBAR ──────────────────────────────────────────────── -->
  <!-- O trilho abaixo é `fixed` e não ocupa espaço no fluxo: este espaçador reserva
       a largura dele para o conteúdo da página não ficar por baixo. -->
  <div class="md:hidden w-10 shrink-0" aria-hidden="true"></div>
  <div class="md:hidden fixed top-0 left-0 z-20 h-screen flex shadow-2xl transition-all duration-300 {abertoMobile ? 'w-74' : 'w-10'}">
    <!-- `invisible` com o painel fechado: tira os links da tabulação e do leitor de tela. -->
    <div class="flex flex-col h-full bg-slate-900 text-white overflow-hidden transition-all duration-300 {abertoMobile ? 'w-64 shrink-0 opacity-100' : 'w-0 opacity-0 invisible'}">
      <div class="flex flex-col items-center py-5 border-b border-slate-800">
        <img src="/images/logo7.png" alt="SIRG" class="h-12 w-auto mb-2" />
        <span class="text-xs font-semibold tracking-widest text-slate-400 uppercase">SIRG</span>
      </div>

      <nav aria-label="Menu principal" class="flex-1 overflow-y-auto px-3 py-3 space-y-5">
        {#each sections as section}
          <div class="space-y-0.5">
            <p class={TITULO_SECAO}>{section.label}</p>
            {#each section.items as item}
              {#if item.type === 'group'}
                <button type="button" on:click={() => toggle(item.key)} aria-expanded={open === item.key} class={groupBtn(item.key)}>
                  <svg class="w-4 h-4 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2" aria-hidden="true">
                    {#each item.icon as d}<path stroke-linecap="round" stroke-linejoin="round" {d} />{/each}
                  </svg>
                  <span class="flex-1 text-left">{item.label}</span>
                  <svg class="w-4 h-4 shrink-0 text-slate-400 transition-transform duration-200 {open === item.key ? 'rotate-180' : ''}" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2" aria-hidden="true">
                    <path stroke-linecap="round" stroke-linejoin="round" d={CHEVRON_DOWN} />
                  </svg>
                </button>
                {#if open === item.key}
                  <div class="ml-5 pl-3 border-l border-slate-700 space-y-0.5">
                    {#each item.entradas as sub (sub.key ?? sub.href)}
                      {#if sub.type === 'subgroup'}
                        <button type="button" on:click={() => toggleSub(sub.key)} aria-expanded={openSub === sub.key} class={subgroupBtn(sub)}>
                          <span class="w-1 h-1 rounded-full bg-current opacity-60 shrink-0" aria-hidden="true"></span>
                          <span class="flex-1 text-left">{sub.label}</span>
                          <svg class="w-3.5 h-3.5 shrink-0 text-slate-400 transition-transform duration-200 {openSub === sub.key ? 'rotate-180' : ''}" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2" aria-hidden="true">
                            <path stroke-linecap="round" stroke-linejoin="round" d={CHEVRON_DOWN} />
                          </svg>
                        </button>
                        {#if openSub === sub.key}
                          <div role="group" aria-label={sub.label} class={LISTA_NETOS}>
                            {#each sub.items as neto (neto.href)}
                              <a href={resolveHref(neto, role)} class={netolink(resolveHref(neto, role))} aria-current={activePage === resolveHref(neto, role) ? 'page' : undefined}>
                                {neto.label}
                              </a>
                            {/each}
                          </div>
                        {/if}
                      {:else}
                        <a href={resolveHref(sub, role)} class={sublink(resolveHref(sub, role))} aria-current={activePage === resolveHref(sub, role) ? 'page' : undefined}>
                          <span class="w-1 h-1 rounded-full bg-current opacity-60 shrink-0" aria-hidden="true"></span>
                          {sub.label}
                        </a>
                      {/if}
                    {/each}
                  </div>
                {/if}
              {:else}
                <a href={resolveHref(item, role)} class={link(resolveHref(item, role))} aria-current={activePage === resolveHref(item, role) ? 'page' : undefined}>
                  <svg class="w-4 h-4 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2" aria-hidden="true">
                    {#each item.icon as d}<path stroke-linecap="round" stroke-linejoin="round" {d} />{/each}
                  </svg>
                  {item.label}
                </a>
              {/if}
            {/each}
          </div>
        {/each}
      </nav>

      <div class="px-4 py-3 border-t border-slate-800">
        <span class="text-xs text-slate-400">© 2025</span>
      </div>
    </div>

    <button
      type="button"
      on:click={() => (abertoMobile = !abertoMobile)}
      aria-label={abertoMobile ? 'Fechar menu' : 'Abrir menu'}
      aria-expanded={abertoMobile}
      class="flex items-center justify-center w-10 h-full bg-slate-900 hover:bg-slate-800 border-r border-slate-800 transition-colors {FOCO}"
    >
      <svg class="w-4 h-4 text-slate-400 transition-transform duration-300 {abertoMobile ? 'rotate-180' : ''}" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
        <path stroke-linecap="round" stroke-linejoin="round" d="m9 5 7 7-7 7" />
      </svg>
    </button>
  </div>
{/if}
