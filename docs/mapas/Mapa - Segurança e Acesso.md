---
titulo: Mapa - Segurança e Acesso
tags:
  - sirg/mapa
  - sirg/seguranca
gerado: manual
---

# Mapa — Segurança e Acesso

Quem entra, o que vê, e o que o impede. Detalhe em [[09 - Segurança]]; o ciclo de autenticação desenhado em [[diagrama-usuarios]].

## Autenticação

Login é **CPF + senha** (o `username` do `UserDetails` é o CPF). Token JWT HMAC256, issuer `regulacao-api`, 2 horas de validade, claims `sub` (CPF), `role` e `nome`. Sessão `STATELESS` — nenhuma sessão HTTP.

`JwtAuthenticationFilter` valida o token **e checa `user.isEnabled()`**: usuário desativado com token ainda válido é bloqueado. Senha em BCrypt, nunca retornada pela API.

## Autorização — duas dimensões

**1. Por role** (`@PreAuthorize` ou checagem no service): ADMIN para cadastros e gestão; RECEPCAO/ENFERMEIRO/MEDICO para operação clínica; COORD_TRANSPORTE para transporte.

**2. Por unidade** — a segregação de dados, decidida num único lugar, `UnidadeAcessoService`:

| Role | Escopo |
|---|---|
| `ADMIN` | Todas as unidades, sem filtro e sem cota |
| `ADMIN_UNIDADE` | **Sempre** a unidade de lotação; sem lotação ⇒ acesso negado |
| Demais | Unidade de lotação quando houver; sem lotação, acesso global (histórico) |

Métodos: `contextoDe(cpf)`, `isAcessoGlobal(cpf)`, `exigirAcessoA(cpf, unidadeId)`. A regra completa está em [[10 - Padrões e Arquitetura]] (seção 10.0) — antes ela vivia duplicada em `SolicitacaoService` e `AgendamentoService`.

## Proteções pontuais

- Não se desativa o **último ADMIN ativo** (409).
- CPF único via `@UniqueCPF`.
- Upload de foto: MIME e tamanho (máx. 5 MB) validados em `FileStorageService`.

## Erros

`GlobalExceptionHandler` devolve sempre `{ "message": ... }` — é o campo que as telas leem. Cota esgotada é `409` via `IllegalStateException`; validação é `400`; CPF duplicado é `409`; inexistente é `404`.

## Rotas públicas

`/api/auth/**`, `/api/transparencia/**`, `/api/solicitacoes/public/**`, `/api/agendamentos/pendentes/**`, `/api/registry/**`, `/api/uploads/**`, Swagger e Actuator. Todo o resto exige autenticação. CORS listado em `CorsConfig.java`.

---

Volta para [[Início]] · vizinhos: [[Mapa - Arquitetura]], [[Mapa - Domínio]]
