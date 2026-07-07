<script>
    import { Toaster } from 'svelte-sonner';
	import '../app.css';
	import { page } from '$app/state';
	import { goto } from '$app/navigation';
	import { user } from '$lib/stores/auth.js';

	let { children } = $props();

	// Rotas acessíveis sem login. Qualquer outra rota exige um usuário autenticado.
	const PUBLIC_PATHS = ['/', '/login', '/integracao', '/paciente/consultar', '/federation/convite'];

	function isPublicPath(pathname) {
		return PUBLIC_PATHS.some((p) => pathname === p || pathname.startsWith(p + '/'));
	}

	$effect(() => {
		if (!isPublicPath(page.url.pathname) && !$user) {
			goto('/login', { replaceState: true });
		}
	});
</script>

{@render children()}

<Toaster  position="top-center" duration={4000} closeButton ></Toaster>