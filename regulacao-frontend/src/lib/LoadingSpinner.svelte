<script>
  import { onMount, onDestroy } from 'svelte';
  import gsap from 'gsap';

  let { tamanho = 24, mensagem = '', inline = false } = $props();

  let spinnerEl;
  let tween;

  // Espessura do anel acompanha o tamanho, sem ficar fina demais nos usos pequenos (inline).
  let espessura = $derived(Math.max(2, Math.round(tamanho / 8)));

  onMount(() => {
    tween = gsap.to(spinnerEl, {
      rotation: 360,
      repeat: -1,
      ease: 'none',
      duration: 1,
      transformOrigin: '50% 50%'
    });
  });

  onDestroy(() => {
    tween?.kill();
  });
</script>

<div
  class={inline
    ? 'inline-flex items-center gap-2'
    : 'flex flex-col items-center justify-center gap-3 p-10'}
  role="status"
  aria-live="polite"
>
  <div
    bind:this={spinnerEl}
    style="width:{tamanho}px;height:{tamanho}px;border-width:{espessura}px;"
    class="rounded-full border-emerald-100 border-t-emerald-600 border-r-emerald-500/60 border-b-emerald-200"
  ></div>
  {#if mensagem}
    <span class="text-sm text-gray-600">{mensagem}</span>
  {:else}
    <span class="sr-only">Carregando...</span>
  {/if}
</div>
