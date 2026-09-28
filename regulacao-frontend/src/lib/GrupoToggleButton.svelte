<script>
  // Só o botão de seta que expande/recolhe um grupo de especialidades. Fica
  // sempre ao lado do nome do grupo (na célula da tabela, ou na linha do
  // card do Dashboard). O painel em si é `GrupoEspecialidadesPainel.svelte`
  // — separado porque, pra ocupar a largura total da tabela/card (e não só a
  // largura da célula), o painel precisa ser renderizado em outro ponto do
  // DOM (uma linha de tabela à parte, com colspan), não logo ao lado do
  // botão. Por isso o estado "aberto" mora em quem usa os dois, não aqui.
  export let grupo = null; // { id, nome, especialidades: [...] } — só usado pra decidir se o botão aparece
  export let aberto = false;
  export let onToggle = () => {};
  export let escuro = false;
</script>

{#if grupo}
  <button
    type="button"
    on:click={onToggle}
    aria-expanded={aberto}
    aria-label={aberto ? 'Ocultar especialidades do grupo' : 'Ver especialidades do grupo'}
    class="inline-flex items-center gap-0.5 align-middle ml-1 transition-colors {escuro ? 'text-slate-500 hover:text-emerald-400' : 'text-gray-400 hover:text-emerald-700'}"
    title={aberto ? 'Ocultar especialidades do grupo' : 'Ver especialidades do grupo'}
  >
    <svg
      class="w-3.5 h-3.5 transition-transform duration-150 {aberto ? 'rotate-180' : ''}"
      fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2.5"
    >
      <path stroke-linecap="round" stroke-linejoin="round" d="M19 9l-7 7-7-7" />
    </svg>
  </button>
{/if}
