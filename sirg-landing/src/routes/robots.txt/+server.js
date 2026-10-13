import { indexavel, urlDoSite } from '$lib/site.js';

export function GET() {
	const corpo = indexavel()
		? `User-agent: *\nAllow: /\n\nSitemap: ${urlDoSite()}/sitemap.xml\n`
		: 'User-agent: *\nDisallow: /\n';

	return new Response(corpo, {
		headers: {
			'content-type': 'text/plain; charset=utf-8',
			'cache-control': 'public, max-age=3600'
		}
	});
}
