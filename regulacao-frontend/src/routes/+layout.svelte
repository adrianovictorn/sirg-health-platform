<script>
    import { Toaster } from 'svelte-sonner';
	import '../app.css';

	let { children } = $props();
</script>

{@render children()}

<Toaster  position="top-center" duration={4000} closeButton ></Toaster>