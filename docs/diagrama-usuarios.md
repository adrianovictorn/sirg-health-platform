# Diagrama — Módulo de Usuários e Autenticação

Cobre a entidade `User`, suas roles, o ciclo de vida de autenticação JWT e os controles de segurança.

```mermaid
classDiagram

    class User {
        <<Entity — tabela: usuarios>>
        <<implements UserDetails>>
        +Long id
        +String cpf        [UNIQUE, NOT NULL, max 15]
        +String nome       [NOT NULL]
        +String password   [BCrypt, coluna: senha]
        +Roles role        [coluna: cargo]
        +String fotoPerfil [URL relativa, nullable]
        +boolean ativo     [default: true]
        ──────────────────────────────
        +getUsername() String       → retorna cpf
        +getPassword() String       → retorna password
        +isEnabled() boolean        → retorna ativo
        +isAccountNonLocked() bool  → retorna ativo
        +isAccountNonExpired() bool → true
        +isCredentialsNonExpired()  → true
        +getAuthorities()           → [ROLE_{role.name()}]
    }

    class Roles {
        <<enum>>
        ADMIN
        USER
        PACIENTE
        ENFERMEIRO
        MEDICO
        RECEPCAO
        COORD_TRANSPORTE
    }

    class TokenService {
        <<Service>>
        -String secret
        ──────────────────────────────
        +generateToken(User) String
        +validateToken(String) String
        -genExpirationDate() Instant
    }

    class JwtAuthenticationFilter {
        <<OncePerRequestFilter>>
        ──────────────────────────────
        +doFilterInternal(req, res, chain)
        -extractToken(request) String
    }

    class SecurityConfiguration {
        <<Configuration>>
        ──────────────────────────────
        +securityFilterChain(http) Bean
        +authenticationManager() Bean
        +passwordEncoder() Bean
        +corsConfigurer() Bean
    }

    class AuthController {
        <<RestController — /api/auth>>
        ──────────────────────────────
        +login(LoginRequestDTO) ResponseEntity
    }

    class UserController {
        <<RestController — /api/users>>
        ──────────────────────────────
        +getMe() UserViewDTO
        +listar() List~UserViewDTO~
        +criar(UserCreateDTO) UserViewDTO
        +atualizar(id, UserUpdateDTO) UserViewDTO
        +toggleStatus(id) UserViewDTO
        +uploadFoto(id, MultipartFile) UserViewDTO
        +removerFoto(id) UserViewDTO
    }

    class UserViewDTO {
        <<Record>>
        +Long id
        +String cpf
        +String nome
        +Roles role
        +String fotoUrl
        +boolean ativo
        ──────────────────────────────
        +from(User) UserViewDTO$
    }

    class LoginRequestDTO {
        <<Record>>
        +String cpf
        +String password
    }

    class LoginResponseDTO {
        <<Record>>
        +String token
    }

    %% Dependências e relacionamentos

    User          "0..*" ..> "1" Roles : role
    TokenService               ..>      User  : usa campos para gerar token
    JwtAuthenticationFilter    ..>      TokenService : validateToken()
    JwtAuthenticationFilter    ..>      User         : isEnabled() check
    AuthController             ..>      TokenService : generateToken()
    UserController             ..>      UserViewDTO  : retorna
    UserViewDTO                ..>      User         : from(User)
    SecurityConfiguration      ..>      JwtAuthenticationFilter : registra filtro
```

## JWT — estrutura do token

```
Header:  { "alg": "HS256", "typ": "JWT" }

Payload: {
  "iss": "regulacao-api",
  "sub": "<cpf do usuário>",
  "role": "<ADMIN | USER | MEDICO | ...>",
  "nome": "<nome completo>",
  "exp": <now + 2h, offset GMT-3>
}

Assinatura: HMAC256(secret do application.properties)
```

## Fluxo de autenticação

```
POST /api/auth/login
{ cpf, password }
        │
        ▼
AuthController.login()
        │
        ▼
AuthenticationManager.authenticate()
   └─ UserDetailsService.loadUserByUsername(cpf)
        └─ UserRepository.findByCpf(cpf) → User
   └─ BCryptPasswordEncoder.matches(raw, hash)
        │
        ├─ DisabledException (ativo=false) → 403 { "message": "Conta desativada..." }
        ├─ BadCredentialsException          → 401
        │
        ▼ (sucesso)
TokenService.generateToken(user)
        │
        ▼
200 OK { "token": "eyJ..." }
```

## Fluxo de autorização por requisição

```
GET/POST/PUT... /api/**
        │
        ▼
JwtAuthenticationFilter.doFilterInternal()
   1. Extrai "Bearer <token>" do header Authorization
   2. TokenService.validateToken(token) → CPF ou ""
   3. UserRepository.findByCpf(cpf) → User
   4. user.isEnabled() == true?
        ├─ NÃO → não autentica (401 retornado pelo Spring Security)
        └─ SIM → SecurityContextHolder.setAuthentication(
                    UsernamePasswordAuthenticationToken(user, null, authorities)
                 )
        │
        ▼
SecurityConfiguration.securityFilterChain()
   └─ Verifica se a rota requer autenticação
   └─ Verifica role do usuário se necessário (@PreAuthorize)
```

## Menus por role (frontend)

```
Roles          → Componente de menu
─────────────────────────────────────
ADMIN          → Menu.svelte        (acesso total)
RECEPCAO       → Menu3.svelte       (clínico)
ENFERMEIRO     → Menu3.svelte       (clínico)
MEDICO         → Menu3.svelte       (clínico)
COORD_TRANSPORTE → Menu4.svelte     (transporte)
USER / PACIENTE  → Menu2.svelte     (básico)
```

## Regras de negócio desta camada

| Regra | Onde é implementada |
|---|---|
| CPF é o identificador de login (`username` no Spring Security) | `User.getUsername()` retorna `cpf` |
| Senha nunca é retornada pela API | `UserViewDTO` não contém campo `password` |
| Usuário desativado (`ativo=false`) não consegue autenticar, mesmo com token válido | `JwtAuthenticationFilter` verifica `isEnabled()` em **toda** requisição |
| Não é possível desativar o último administrador ativo | `UserService.toggleStatus()` conta `countByRoleAndAtivoTrue(ADMIN)` antes de desativar |
| Login de conta desativada retorna 403 (não 401) com mensagem explicativa | `AuthController` captura `DisabledException` |
| Foto de perfil é armazenada no filesystem, a URL é salva em `User.fotoPerfil` | `FileStorageService` + `WebConfiguration` resource handler |
| Token expira em 2 horas (GMT-3) | `TokenService.genExpirationDate()` |
| CPF deve ser único no sistema | `@Column(unique=true)` + `@UniqueCPF` (bean validation custom) |
