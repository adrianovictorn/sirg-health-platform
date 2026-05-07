<script lang="ts">
  export let header: string = '';
  export let title: string;
  export let value: string | number;
  export let href: string;
  export let color: 'danger' | 'warning' | 'default' = 'danger';

  const colorStyles: Record<string, { container: string; title: string; value: string }> = {
    danger: {
      container: 'bg-red-600',
      title: 'text-white',
      value: 'text-white'
    },
    warning: {
      container: 'bg-yellow-500',
      title: 'text-white',
      value: 'text-white'
    },
    default: {
      container: 'bg-gray-600',
      title: 'text-white',
      value: 'text-white'
    }
  };

  $: styles = colorStyles[color] ?? colorStyles.default;
</script>

<a href={href} class="block">
  <div class={`rounded-xl p-4 shadow-md transform hover:scale-102 hover:shadow-xl transition-all duration-300 cursor-pointer text-center flex flex-col items-center justify-center ${styles.container}`}>
    {#if header}
      <h1 class="text-base font-semibold p-0.5 text-white">{header}</h1>
    {/if}
    <h2 class={`text-base font-semibold ${styles.title}`}>{title}</h2>
    <h2 class={`text-lg font-bold ${styles.value}`}>{value}</h2>
  </div>
</a>
