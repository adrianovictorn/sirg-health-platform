import { token } from '$lib/stores/auth.js';
import { get } from 'svelte/store';


function resolveApiPrefix() {
  if (typeof window === 'undefined') return '/api';
  try {
    const params = new URLSearchParams(window.location.search);
    const apiParam = params.get('api');
    if (apiParam && /^\d{4,5}$/.test(apiParam)) {
      const p = `/api-${apiParam}`;
      window.localStorage.setItem('API_PREFIX', p);
      return p;
    }
    return window.localStorage.getItem('API_PREFIX') || '/api';
  } catch {
    return '/api';
  }
}

const API_PREFIX = resolveApiPrefix();

// Define a URL base para todas as chamadas Ã  sua API backend.
const BASE_URL = '/api';
/**
 * Uma funÃ§Ã£o central para enviar todas as requisiÃ§Ãµes para a API.
 * @param {object} param0 - Objeto com mÃ©todo, caminho e dados.
 * @returns {Promise<Response>} A resposta do fetch.
 */
async function send({ method, path, data }) {
  // Pega o valor atual do token da nossa store de autenticaÃ§Ã£o.
  const currentToken = get(token); 
  
  const opts = { 
    method,
    headers: {}
  };

  // Se a requisiÃ§Ã£o tiver um corpo (body), define o cabeÃ§alho correto.
  if (data) {
    opts.headers['Content-Type'] = 'application/json';
    opts.body = JSON.stringify(data);
  }

  // Se existir um token, adiciona-o ao cabeÃ§alho de AutorizaÃ§Ã£o.
  if (currentToken) {
    opts.headers['Authorization'] = `Bearer ${currentToken}`;
  }

  const base = API_PREFIX.replace(/\/$/, '');
  const url = `${base}/${path}`;
  const response = await fetch(url, opts);
  
  // Se o token estiver inválido/expirado (401), desloga o usuÃ¡rio.
  if (response.status === 401) {
    token.set(null);
  }

  return response;
}

// Função auxiliares para conveniÃªncia nas chamadas da API.
export function getApi(path) {
  return send({ method: 'GET', path });
}

export function postApi(path, data) {
  return send({ method: 'POST', path, data });
}

export function patchApi(path){
  return send({method: 'PATCH', path});
}

export function patchApiData(path, data){
  return send({method: 'PATCH', path, data});
}

export function putApi(path, data) {
  return send({ method: 'PUT', path, data });
}

export function deleteApi(path, data) {
  return send({ method: 'DELETE', path, data });
}


export function deleteByIdApi(path) {
  return send({ method: 'DELETE', path});
}

export async function postApiFile(path, formData) {
  const currentToken = get(token);
  const base = API_PREFIX.replace(/\/$/, '');
  const url = `${base}/${path}`;
  const opts = {
    method: 'POST',
    headers: {},
    body: formData
  };
  if (currentToken) {
    opts.headers['Authorization'] = `Bearer ${currentToken}`;
  }
  const response = await fetch(url, opts);
  if (response.status === 401) token.set(null);
  return response;
}

