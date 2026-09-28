---
titulo: Mapa - Arquitetura
tags:
  - sirg/mapa
gerado: manual
---

# Mapa — Arquitetura

Um monorepo com três partes: `regulacao-backend/` (Spring Boot), `regulacao-frontend/` (SvelteKit) e `nginx/` + `docker-compose.prod.yaml` (produção). Árvore completa de pastas em [[02 - Estrutura do Projeto]].

## Backend — camadas

`Controller` → `Service` → `Repository` → `Entity`, com DTOs cruzando a fronteira do controller. O controller não conhece entidade, o service concentra regra de negócio, o repository é Spring Data JPA.

- [[07 - APIs e Endpoints]] — todos os endpoints, por módulo.
- [[04 - DTOs]] — DTOs são **records Java**, um por operação (`Create`, `Update`, `View`).
- [[10 - Padrões e Arquitetura]] — camadas, Specification Pattern para filtros dinâmicos, *optimistic locking* no transporte, `InstanceContext` para o município da instância.

## Frontend

SvelteKit 2 com Svelte 5 (runes: `$state`, `$derived`, `$effect` — não `export let`/stores reativos do Svelte 4).

| Peça | Arquivo |
|---|---|
| Sessão e usuário | `src/lib/stores/auth.js` |
| Cliente HTTP (injeta o Bearer) | `src/lib/api.js` |
| Menu único por configuração | `RoleBasedMenu` + `src/lib/menuConfig.js` |
| Header autenticado | `UserMenu` |

Nenhuma página importa um menu específico: todas usam `<RoleBasedMenu activePage="/sua/rota" />`. Relatórios são gerados **no browser** — ExcelJS para `.xlsx`, jsPDF para `.pdf`, Chart.js para gráficos. Detalhe e tabela de rotas em [[08 - Frontend]].

## Persistência

PostgreSQL com Flyway; toda mudança de schema é uma migração `V__`. Tabelas, ERD simplificado e histórico de migrações notáveis em [[06 - Banco de Dados]].

## Integrações

- **RabbitMQ** — federação entre municípios, uma queue por município ([[diagrama-federacao]]).
- **HAPI FHIR R4** — interoperabilidade, ver [[10 - Padrões e Arquitetura]].

Versões e dependências fixadas em [[11 - Dependências e Configurações]].

---

Volta para [[Início]] · vizinhos: [[Mapa - Domínio]], [[Mapa - Segurança e Acesso]], [[Mapa - Operação]]
