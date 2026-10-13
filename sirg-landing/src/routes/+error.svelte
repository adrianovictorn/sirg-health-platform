<script>
	import { page } from '$app/state';
</script>

<svelte:head>
	<title>Página não encontrada — SIRG</title>
	<meta name="robots" content="noindex" />
</svelte:head>

<main class="bg-tinta text-papel flex min-h-svh items-center">
	<div class="conteiner py-16">
		<p class="algarismo text-folha text-lg">{page.status}</p>
		<h1
			class="font-display mt-4 text-4xl leading-tight font-bold tracking-tight text-balance sm:text-5xl"
		>
			{page.status === 404 ? 'Esta página não existe.' : 'Algo deu errado por aqui.'}
		</h1>
		<p class="text-nevoa mt-6 max-w-xl text-lg">
			O endereço pode ter mudado. A página inicial tem tudo sobre o SIRG.
		</p>
		<a
			href="/"
			class="bg-folha text-tinta hover:bg-papel active:bg-nevoa mt-10 inline-flex min-h-13 items-center rounded-full px-7 font-semibold transition-colors"
		>
			Ir para o início
		</a>
	</div>
</main>
