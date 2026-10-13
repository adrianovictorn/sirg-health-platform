import { browser } from "$app/environment";

// Se o código estiver rodando no NAVEGADOR (browser === true), a base da URL é vazia.
// Assim, a chamada será para "/api/...", e o Nginx cuidará do resto.
//
// Se o código estiver rodando no SERVIDOR (browser === false), usamos o endereço interno
// para a comunicação entre serviços.
export const API_BASE_URL = browser ? "" : "http://localhost:8080";
