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
      role: decoded.role
    };
  } catch (e) {
    console.error("Token inválido:", e);
    return null;
  }
});

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

export async function refreshProfilePicture() {
  if (!browser) return;
  try {
    const { getApi } = await import('$lib/api.js');
    const res = await getApi('users/me');
    if (res.ok) {
      const userData = await res.json();
      profilePicture.set(userData.fotoUrl || null);
    }
  } catch (e) {
    console.error('Erro ao atualizar foto de perfil:', e);
  }
}
