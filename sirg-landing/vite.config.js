import tailwindcss from '@tailwindcss/vite';
import { sveltekit } from '@sveltejs/kit/vite';
import { defineConfig } from 'vite';

// Sem proxy de /api: a landing não chama API pelo navegador. Os indicadores são
// lidos no servidor (src/lib/server/indicadores.js).
export default defineConfig({
	plugins: [tailwindcss(), sveltekit()],
	server: { port: 5174 }
});
