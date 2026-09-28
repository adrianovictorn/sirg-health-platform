import { writable, derived } from 'svelte/store';
import { browser } from '$app/environment';
import { jwtDecode } from 'jwt-decode';

export const token = writable(browser ? localStorage.getItem('jwt_token') : null);

token.subscribe(value => {
  if (browser) {
    if (value) {
      localStorage.setItem('jwt_token', value);
    } else {
      localStorage.removeItem('jwt_token');
      localStorage.removeItem('profile_picture_url');
    }
  }
});

export const user = derived(token, ($token) => {
  if (!$token) return null;
  try {
    const decoded = jwtDecode($token);
    return {
      cpf: decoded.sub,
      nome: decoded.nome,
      // `role` é o perfil EM USO agora. Com a alternância de perfis (v1.7) ele
      // passa a vir de `perfilAtivo`; `decoded.role` é o fallback para tokens
      // emitidos antes disso, que só tinham esse campo. Tudo que já lia `role`
      // (menu, telas) continua funcionando sem saber da mudança.
      role: decoded.perfilAtivo ?? decoded.role
    };
  } catch (e) {
    console.error("Token inválido:", e);
    return null;
  }
});

/**
 * Perfis que o usuário logado pode assumir. Vem de `GET /users/me` (não do
 * token), porque quem concede perfil é o backend e a lista pode mudar sem a
 * pessoa sair e entrar de novo. Com mais de um, o menu do usuário mostra o
 * seletor de alternância.
 */
export const perfisDisponiveis = writable([]);

// Separate store for profile picture URL, persisted in localStorage
export const profilePicture = writable(browser ? localStorage.getItem('profile_picture_url') : null);

profilePicture.subscribe(value => {
  if (browser) {
    if (value) {
      localStorage.setItem('profile_picture_url', value);
    } else {
      localStorage.removeItem('profile_picture_url');
    }
  }
});

/**
 * Busca o usuário logado uma vez e atualiza foto e perfis disponíveis.
 *
 * Uma requisição só para as duas coisas — antes de existir a alternância de
 * perfis isso já era feito aqui para a foto, então aproveitamos a mesma ida ao
 * servidor em vez de somar outra a cada carregamento de página.
 */
export async function refreshUsuarioAtual() {
  if (!browser) return;
  try {
    const { getApi } = await import('$lib/api.js');
    const res = await getApi('users/me');
    if (res.ok) {
      const userData = await res.json();
      profilePicture.set(userData.fotoUrl || null);
      perfisDisponiveis.set(Array.isArray(userData.perfis) ? userData.perfis : []);
    }
  } catch (e) {
    console.error('Erro ao carregar usuário atual:', e);
  }
}

/** Mantido pelo nome antigo: as telas que só querem a foto continuam chamando isto. */
export const refreshProfilePicture = refreshUsuarioAtual;

/**
 * Troca o perfil ativo: o backend confere se o perfil está liberado e devolve
 * um token novo já emitido com ele. Trocar o token basta — o store `user`
 * deriva dele, então o menu e as telas se reorganizam sozinhos.
 */
export async function trocarPerfil(perfil) {
  const { postApi } = await import('$lib/api.js');
  const res = await postApi('users/me/perfil', { perfil });
  if (!res.ok) {
    const erro = await res.json().catch(() => ({}));
    throw new Error(erro.message || 'Não foi possível trocar de perfil.');
  }
  const { token: novoToken } = await res.json();
  token.set(novoToken);
  return novoToken;
}
