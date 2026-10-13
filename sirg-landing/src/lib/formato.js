// As instâncias respondem em UTC; a página fala a hora do município.
const FUSO = 'America/Bahia';

const numero = new Intl.NumberFormat('pt-BR');
const data = new Intl.DateTimeFormat('pt-BR', { timeZone: FUSO, dateStyle: 'long' });
const dataHora = new Intl.DateTimeFormat('pt-BR', {
	timeZone: FUSO,
	day: '2-digit',
	month: '2-digit',
	year: 'numeric',
	hour: '2-digit',
	minute: '2-digit'
});

export function formatarNumero(valor) {
	return numero.format(valor);
}

export function formatarData(iso) {
	return iso ? data.format(new Date(iso)) : null;
}

export function formatarDataHora(iso) {
	return iso ? dataHora.format(new Date(iso)) : null;
}
