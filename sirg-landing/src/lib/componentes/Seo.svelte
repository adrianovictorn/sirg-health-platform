<script>
	import { AUTORES } from '$lib/conteudo/sistema.js';
	import { DESCRICAO, TITULO, indexavel, urlDoSite } from '$lib/site.js';

	const url = urlDoSite();
	const imagem = `${url}/logo-sirg.png`;

	const dadosEstruturados = {
		'@context': 'https://schema.org',
		'@type': 'SoftwareApplication',
		name: 'SIRG',
		alternateName: 'Sistema Integrado de Regulação e Gestão',
		description: DESCRICAO,
		url,
		image: imagem,
		applicationCategory: 'HealthApplication',
		operatingSystem: 'Web',
		inLanguage: 'pt-BR',
		author: AUTORES.map((autor) => ({
			'@type': 'Person',
			name: autor.nome,
			...(autor.links.length ? { sameAs: autor.links.map((link) => link.href) } : {})
		}))
	};

	// "<" escapado para o JSON não conseguir fechar a tag <script> onde é embutido.
	const jsonLd = JSON.stringify(dadosEstruturados).replace(/</g, '\\u003c');
</script>

<svelte:head>
	<title>{TITULO}</title>
	<meta name="description" content={DESCRICAO} />
	<link rel="canonical" href="{url}/" />
	{#if !indexavel()}
		<meta name="robots" content="noindex, nofollow" />
	{/if}

	<meta property="og:type" content="website" />
	<meta property="og:locale" content="pt_BR" />
	<meta property="og:site_name" content="SIRG" />
	<meta property="og:title" content={TITULO} />
	<meta property="og:description" content={DESCRICAO} />
	<meta property="og:url" content="{url}/" />
	<meta property="og:image" content={imagem} />

	<meta name="twitter:card" content="summary" />
	<meta name="twitter:title" content={TITULO} />
	<meta name="twitter:description" content={DESCRICAO} />
	<meta name="twitter:image" content={imagem} />

	<!-- eslint-disable-next-line svelte/no-at-html-tags -- conteúdo fixo, montado acima -->
	{@html `<script type="application/ld+json">${jsonLd}</` + `script>`}
</svelte:head>
