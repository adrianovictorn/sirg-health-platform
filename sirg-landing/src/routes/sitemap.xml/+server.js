import { urlDoSite } from '$lib/site.js';

// A landing é uma página só. Rota nova pública entra aqui.
const CAMINHOS = ['/'];

export function GET() {
	const urls = CAMINHOS.map((caminho) => `\t<url><loc>${urlDoSite()}${caminho}</loc></url>`).join(
		'\n'
	);
	const corpo = `<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n${urls}\n</urlset>\n`;

	return new Response(corpo, {
		headers: {
			'content-type': 'application/xml; charset=utf-8',
			'cache-control': 'public, max-age=3600'
		}
	});
}
