# Documentação Técnica — SIRG (Sistema de Regulação)

> **Versão documentada:** 1.3 | **Data:** 2026-05-11  
> **Objetivo:** Permitir que qualquer desenvolvedor ou IA consiga compreender, reconstruir, manter e evoluir o sistema sem depender de explicações adicionais.

---

## Sumário

1. [Visão Geral do Projeto](#1-visão-geral-do-projeto)
2. [Estrutura do Projeto](#2-estrutura-do-projeto)
3. [Entidades e Classes de Domínio](#3-entidades-e-classes-de-domínio)
4. [DTOs (Data Transfer Objects)](#4-dtos)
5. [Fluxos do Sistema](#5-fluxos-do-sistema)
6. [Banco de Dados](#6-banco-de-dados)
7. [APIs e Endpoints](#7-apis-e-endpoints)
8. [Frontend](#8-frontend)
9. [Segurança](#9-segurança)
10. [Padrões e Arquitetura](#10-padrões-e-arquitetura)
11. [Dependências e Configurações](#11-dependências-e-configurações)
12. [Guia de Execução](#12-guia-de-execução)

---

## 1. Visão Geral do Projeto

O SIRG é um sistema web para **regulação e marcação de consultas e exames especializados** em municípios do interior da Bahia. Ele foi desenvolvido para substituir planilhas manuais, centralizando o fluxo de:

- Cadastro de solicitações de pacientes (consultas e exames/procedimentos)
- Agendamento de especialidades em locais específicos
- Controle de transporte sanitário
- Geração de relatórios de produção
- Gestão de usuários com múltiplos perfis de acesso
- Federação entre municípios (troca de filas via RabbitMQ)

### Tecnologias Centrais

| Camada | Tecnologia | Versão |
|---|---|---|
| Backend | Spring Boot | 3.4.3 |
| Linguagem | Java | 21 |
| Frontend | SvelteKit | 2.16.0 |
| UI | Svelte | 5.0.0 |
| CSS | Tailwind CSS | 4.0.0 |
| Banco de Dados | PostgreSQL | — |
| Migrações | Flyway | — |
| Autenticação | JWT (Auth0 java-jwt) | 4.4.0 |
| Mensageria | RabbitMQ (Spring AMQP) | — |
| Interoperabilidade | HAPI FHIR R4 | 6.8.0 |
| Build Backend | Maven | — |
| Build Frontend | Vite | 6.2.6 |

---

## 2. Estrutura do Projeto

```
Regula-o/
├── regulacao-backend/                   # Módulo Spring Boot
│   ├── pom.xml
│   └── src/main/
│       ├── java/io/github/regulacao_marcarcao/regulacao_marcacao/
│       │   ├── RegulacaoMarcacaoApplication.java   # Entry point
│       │   ├── adapter/                             # Adaptadores de interoperabilidade
│       │   │   └── FhirAdapterService.java
│       │   ├── config/                              # Configurações Spring
│       │   │   ├── CorsConfig.java
│       │   │   ├── DataInitializer.java
│       │   │   ├── EspecialidadeInitializer.java
│       │   │   ├── InstanceContext.java
│       │   │   ├── JacksonConfig.java
│       │   │   ├── RabbitMQConfig.java
│       │   │   ├── SecurityConfiguration.java
│       │   │   ├── TokenService.java (em service/)
│       │   │   └── WebConfiguration.java
│       │   ├── controller/                          # REST Controllers (24)
│       │   ├── dto/                                 # DTOs organizados por domínio (80+)
│       │   │   ├── agendamentoDTO/
│       │   │   ├── agendamento/transporte/
│       │   │   ├── cid/
│       │   │   ├── especialidade/
│       │   │   ├── municipio/
│       │   │   ├── notificacao/
│       │   │   ├── pacto/
│       │   │   ├── regional/
│       │   │   ├── relatorio/
│       │   │   ├── solicitacoesDTO/
│       │   │   ├── solicitacaoEspecialidadeDTO/
│       │   │   ├── transporte/
│       │   │   └── usuariosDTO/
│       │   ├── entity/                              # Entidades JPA (22)
│       │   │   └── enums/                           # Enumerações (13)
│       │   ├── exceptions/
│       │   │   └── GlobalExceptionHandler.java
│       │   ├── repository/                          # Repositórios Spring Data JPA (15+)
│       │   │   └── projection/                      # Interfaces de projeção
│       │   ├── service/                             # Serviços de negócio (15+)
│       │   └── validation/                          # Validações customizadas
│       │       ├── UniqueCPF.java
│       │       └── UniqueCPFValidator.java
│       └── resources/
│           ├── application.properties
│           └── db/migration/                        # 67 migrações Flyway (V1–V67)
│
├── regulacao-frontend/                  # Módulo SvelteKit
│   ├── package.json
│   ├── vite.config.js
│   ├── svelte.config.js
│   └── src/
│       ├── app.css                                  # Tailwind base
│       ├── routes/                                  # 51 páginas (+page.svelte)
│       │   ├── +layout.svelte                       # Layout raiz com Toaster
│       │   ├── login/
│       │   ├── home/
│       │   ├── perfil/
│       │   ├── dashboard/
│       │   ├── admin/
│       │   ├── paciente/
│       │   ├── cadastrar/
│       │   ├── agendar/
│       │   ├── agendas/
│       │   ├── consultar/
│       │   ├── relatorio/
│       │   ├── usf/
│       │   ├── federation/
│       │   └── ...
│       └── lib/
│           ├── api.js                               # Cliente HTTP centralizado
│           ├── stores/
│           │   └── auth.js                          # Stores de autenticação
│           ├── Menu.svelte                          # Menu ADMIN
│           ├── Menu2.svelte                         # Menu USER/padrão
│           ├── Menu3.svelte                         # Menu RECEPCAO/ENFERMEIRO/MEDICO
│           ├── Menu4.svelte                         # Menu COORD_TRANSPORTE
│           ├── RoleBasedMenu.svelte                 # Seletor de menu por role
│           ├── UserMenu.svelte                      # Dropdown de perfil no header
│           ├── ModalEditarUsuarios.svelte
│           ├── especialidadesApi.js
│           ├── municipiosApi.js
│           ├── pactosApi.js
│           ├── notificationsApi.js
│           └── registryApi.js
│
└── uploads/                             # Armazenamento de fotos de perfil (runtime)
    └── profile-pictures/
```

---

## 3. Entidades e Classes de Domínio

### 3.1 User

**Tabela:** `usuarios`  
**Implementa:** `UserDetails` (Spring Security)

| Campo | Tipo Java | Coluna SQL | Restrições |
|---|---|---|---|
| `id` | `Long` | `id` | PK, BIGSERIAL |
| `cpf` | `String` | `cpf` | UNIQUE, NOT NULL, length=15 |
| `nome` | `String` | `nome` | NOT NULL |
| `password` | `String` | `senha` | NOT NULL (BCrypt) |
| `role` | `Roles` (enum) | `cargo` | NOT NULL |
| `fotoPerfil` | `String` | `foto_perfil` | nullable, URL relativa |
| `ativo` | `boolean` | `ativo` | NOT NULL, default=true |

**Métodos UserDetails:**
- `getUsername()` → retorna `cpf`
- `isEnabled()` → retorna `ativo`
- `isAccountNonLocked()` → retorna `ativo`
- `isAccountNonExpired()`, `isCredentialsNonExpired()` → sempre `true`

---

### 3.2 Solicitacao

**Tabela:** `solicitacao`  
Entidade central do sistema. Representa a solicitação de um paciente por consultas ou exames.

| Campo | Tipo Java | Notas |
|---|---|---|
| `id` | `Long` | PK |
| `usfOrigem` | `UsfEnum` | Unidade de Saúde da Família de origem |
| `nomePaciente` | `String` | max 150 chars |
| `cpfPaciente` | `String` | UNIQUE, length=15 |
| `cns` | `String` | Cartão Nacional de Saúde, length=15 |
| `telefone` | `String` | length=15 |
| `dataNascimento` | `LocalDate` | |
| `observacoes` | `String` | max 500 chars |
| `dataMalote` | `LocalDate` | Data de envio via malote físico |
| `cids` | `List<CID>` | @ManyToMany |
| `especialidades` | `List<SolicitacaoEspecialidade>` | @OneToMany |
| `agendamentos` | `List<AgendamentoSolicitacao>` | @OneToMany |
| `origemMunicipioId` | `UUID` | null = local; não-null = federada |
| `origemMunicipioNome` | `String` | Nome do município de origem federada |

---

### 3.3 SolicitacaoEspecialidade

**Tabela:** `solicitacao_especialidade`  
Cada linha representa uma especialidade solicitada dentro de uma Solicitacao.

| Campo | Tipo Java | Notas |
|---|---|---|
| `id` | `Long` | PK |
| `solicitacao` | `Solicitacao` | @ManyToOne, NOT NULL |
| `agendamentoSolicitacao` | `AgendamentoSolicitacao` | @ManyToOne, nullable |
| `especialidadeSolicitada` | `Especialidade` | @ManyToOne |
| `especialidadeCodigoLegacy` | `String` | Para dados migrados de versões antigas |
| `status` | `StatusDaMarcacao` | enum |
| `prioridade` | `PrioridadeDaMarcacaoEnum` | enum |
| `dataDeCadastro` | `LocalDateTime` | @CreationTimestamp |

---

### 3.4 AgendamentoSolicitacao

**Tabela:** `agendamento_solicitacao`  
Agrupamento de especialidades para um mesmo dia e local.

| Campo | Tipo Java | Notas |
|---|---|---|
| `id` | `Long` | PK |
| `localAgendado` | `LocalDeAgendamentoEnum` | enum |
| `localAgendamento` | `LocalAgendamento` | @ManyToOne (FK para tabela) |
| `dataAgendada` | `LocalDate` | NOT NULL |
| `observacoes` | `String` | max 500 chars |
| `especialidades` | `List<SolicitacaoEspecialidade>` | @OneToMany |
| `solicitacao` | `Solicitacao` | @ManyToOne, NOT NULL |
| `turno` | `TurnoEnum` | enum (MANHA/TARDE) |
| `dataCriacao` | `LocalDateTime` | @CreationTimestamp |

---

### 3.5 Especialidade

**Tabela:** `especialidade`

| Campo | Tipo Java | Notas |
|---|---|---|
| `id` | `Long` | PK |
| `codigo` | `String` | UNIQUE, length=150 |
| `nome` | `String` | UNIQUE, length=255 |
| `categoria` | `ItemCategoria` | enum (CONSULTA/EXAME/PROCEDIMENTO) |
| `ativo` | `Boolean` | default=true |
| `grupoRelatorio` | `GrupoRelatorio` | @ManyToOne, LAZY |
| `vagas` | `Integer` | Vagas mensais disponíveis, default=0 |

---

### 3.6 GrupoRelatorio

**Tabela:** `grupo_relatorio`  
Agrupamento de especialidades para fins de relatório de produção.

| Campo | Tipo Java | Notas |
|---|---|---|
| `id` | `Long` | PK |
| `codigo` | `String` | length=155 |
| `nome` | `String` | length=155 |
| `ativo` | `Boolean` | |
| `direcionadoHospital` | `boolean` | default=false |
| `especialidades` | `List<Especialidade>` | @OneToMany, CascadeType.ALL |

---

### 3.7 Municipio

**Tabela:** `municipio`  
Representa um nó participante da rede federada.

| Campo | Tipo Java | Notas |
|---|---|---|
| `id` | `UUID` | PK, gerado automaticamente |
| `nome` | `String` | UNIQUE |
| `rabbitQueueName` | `String` | UNIQUE, nome da fila RabbitMQ |
| `cnes` | `String` | UNIQUE, código CNES |
| `baseUrl` | `String` | URL base da API do município |
| `publicKey` | `String` | Chave pública para verificação |
| `apiKey` | `String` | UNIQUE |
| `discoverable` | `Boolean` | Se aparece no registro público |

---

### 3.8 Pacto (PactoRegional)

**Tabela:** `pacto_regional` / `pactos`  
Acordo de cooperação entre municípios.

| Campo | Tipo Java | Notas |
|---|---|---|
| `id` | `Long` | PK |
| `nome` | `String` | |
| `descricao` | `String` | |
| `municipioCriador` | `Municipio` | @ManyToOne |
| `status` | `StatusPacto` | enum |
| `createdAt` | `LocalDateTime` | @PrePersist |
| `membros` | `Set<Municipio>` | @ManyToMany, tabela `pacto_membros` |

---

### 3.9 AgendamentoTransporte

**Tabela:** `agendamento_transporte`

| Campo | Tipo Java | Notas |
|---|---|---|
| `id` | `Long` | PK |
| `pacientes` | `Set<AgendamentoTransportePaciente>` | @OneToMany, CascadeType.ALL |
| `transporte` | `Transporte` | @ManyToOne, NOT NULL |
| `locaisAgendamento` | `List<LocalAgendamento>` | @ManyToMany |
| `cidade` | `Cidade` | @ManyToOne, NOT NULL |
| `motorista` | `Motorista` | @ManyToOne |
| `data` | `LocalDate` | |
| `horaSaida` | `LocalTime` | |
| `status` | `StatusAgendamento` | enum |
| `version` | `Long` | @Version — otimistic locking |

---

### 3.10 Cidade e LocalAgendamento

**Cidade** (`cidade`): Município físico com código IBGE, nome e CEP. Possui lista de `LocalAgendamento`.

**LocalAgendamento** (`local_agendamento`): Local físico de atendimento (clínica, hospital, etc.) vinculado a uma Cidade. Possui `enumValue` único para compatibilidade com `LocalDeAgendamentoEnum`.

---

### 3.11 Transporte e Motorista

**Transporte** (`transporte`): Veículo sanitário com nome, vagas, tipo (`TipoVeiculoEnum`) e modelo.  
**Motorista** (`motorista`): Nome, telefone e observações.

---

### 3.12 CID

**Tabela:** `cid`  
Classificação Internacional de Doenças. Vinculada a Solicitacoes via `@ManyToMany`.

---

### 3.13 Notificacao

**Tabela:** `notificacao`

| Campo | Tipo Java | Notas |
|---|---|---|
| `id` | `Long` | PK |
| `municipioDestinoId` | `UUID` | NOT NULL |
| `tipo` | `String` | max 50 chars |
| `resumo` | `String` | |
| `linkPath` | `String` | Rota de navegação |
| `payload` | `String` | JSONB com dados completos |
| `lida` | `boolean` | default=false |
| `createdAt` | `LocalDateTime` | @PrePersist |

---

---

### 3.14 Unidade

**Tabela:** `unidade`

| Campo | Tipo Java | Notas |
|---|---|---|
| `id` | `Long` | PK |
| `nome` | `String` | UNIQUE, NOT NULL, max 200 |
| `codigo` | `String` | Código interno, optional, max 50 |
| `cnes` | `String` | UNIQUE, CNES, optional, max 20 |
| `telefone` | `String` | optional, max 20 |
| `endereco` | `String` | optional, max 300 |
| `ativo` | `boolean` | default=true |
| `criadoEm` | `LocalDateTime` | @CreationTimestamp |

---

### 3.15 CotaUnidade

**Tabela:** `cota_unidade`  
Controla a quantidade de vagas/cotas disponíveis por unidade, podendo ser geral ou direcionada a uma especialidade específica.

| Campo | Tipo Java | Notas |
|---|---|---|
| `id` | `Long` | PK |
| `unidade` | `Unidade` | @ManyToOne NOT NULL |
| `especialidade` | `Especialidade` | @ManyToOne nullable — null = cota geral |
| `periodo` | `String` | Formato "YYYY-MM", NOT NULL, max 7 |
| `quantidadeTotal` | `Integer` | Vagas definidas, default=0 |
| `quantidadeUtilizada` | `Integer` | Vagas consumidas, default=0 |
| `ativo` | `boolean` | default=true |
| `criadoEm` | `LocalDateTime` | @CreationTimestamp |
| `version` | `Long` | @Version — optimistic locking |

**Restrições de unicidade (índices parciais PostgreSQL):**
- `uk_cota_unidade_especialidade` — (unidade_id, especialidade_id, periodo) WHERE especialidade_id IS NOT NULL
- `uk_cota_unidade_geral` — (unidade_id, periodo) WHERE especialidade_id IS NULL

---

### 3.16 Profissional

**Tabela:** `profissional`  
Profissional de saúde solicitante vinculado a uma unidade.

| Campo | Tipo Java | Notas |
|---|---|---|
| `id` | `Long` | PK |
| `nome` | `String` | NOT NULL, max 200 |
| `conselho` | `String` | CRM, COREN, CRO…, max 20 |
| `numeroRegistro` | `String` | Número no conselho, max 50 |
| `especialidadeAtuacao` | `String` | Texto livre, max 200 |
| `telefone` | `String` | max 20 |
| `unidade` | `Unidade` | @ManyToOne nullable |
| `ativo` | `boolean` | default=true |
| `criadoEm` | `LocalDateTime` | @CreationTimestamp |
| `atualizadoEm` | `LocalDateTime` | @UpdateTimestamp |

---

### 3.17 Enumerações

| Enum | Valores |
|---|---|
| `Roles` | `ADMIN, USER, PACIENTE, ENFERMEIRO, MEDICO, RECEPCAO, COORD_TRANSPORTE` |
| `StatusDaMarcacao` | `AGUARDANDO, AGENDADO, FALTOU, CANCELADO, REALIZADO, RETORNO, RETORNO_POLICLINICA, GEL` |
| `StatusAgendamento` | `AGENDADO, CANCELADO, PENDENTE, CONFIRMADO, REALIZADO, GEL` |
| `ItemCategoria` | `CONSULTA, EXAME, PROCEDIMENTO` |
| `PrioridadeDaMarcacaoEnum` | `NORMAL, URGENTE, ...` |
| `TurnoEnum` | `MANHA, TARDE` |
| `UsfEnum` | Siglas das USFs cadastradas |
| `TipoVeiculoEnum` | Tipos de veículos sanitários |
| `LocalDeAgendamentoEnum` | Locais de atendimento (enum legado) |
| `StatusPacto` | `ATIVO, ...` |
| `PactoEventoStatus` | Status de eventos de pacto |
| `PactoConviteStatus` | `PENDENTE, ACEITO, RECUSADO` |

---

## 4. DTOs

Os DTOs são registros imutáveis (`record` Java) organizados por subpacote de domínio.

### 4.1 Usuários

| DTO | Campos | Uso |
|---|---|---|
| `LoginRequestDTO` | `cpf, password` | Corpo do POST /auth/login |
| `LoginResponseDTO` | `token` | Resposta do login |
| `UserCreateDTO` | `nome, cpf, password, role` | Criação de usuário |
| `UserUpdateDTO` | `nome, cpf, password, role` | Atualização de usuário |
| `UserViewDTO` | `id, cpf, nome, role, fotoUrl, ativo` | Visualização (inclui status e foto) |

`UserViewDTO.from(User)` é um factory method estático que projeta a entidade para o DTO, mapeando `fotoPerfil → fotoUrl` e `ativo → ativo`.

### 4.2 Solicitações

| DTO | Uso |
|---|---|
| `SolicitacaoCreateDTO` | Cadastro de solicitação |
| `SolicitacaoViewDTO` | Retorno completo (inclui especialidades e agendamentos) |
| `SolicitacaoUpdateDTO` | Edição |
| `SolicitacaoPublicViewDTO` | Acesso público via `/transparencia` |
| `SolicitacaoSimpleViewDTO` | Lista paginada otimizada |
| `SolicitacaoResumoDTO` | Contagem por status para dashboard |
| `SolicitacaoListFiltersDTO` | Filtros para buscas avançadas com Specification |

### 4.3 Especialidades

| DTO | Uso |
|---|---|
| `EspecialidadeCreateDTO` | Cadastro |
| `EspecialidadeViewDTO` | Retorno completo com vagas e grupo |
| `EspecialidadeSimpleViewDTO` | Listas (id, nome, codigo, categoria) |
| `SolicitacaoEspecialidadeViewDTO` | Especialidade dentro de uma solicitação |

### 4.4 Agendamento de Transporte

Os DTOs de transporte seguem a convenção `*CreateDTO / *ViewDTO / *UpdateDTO / *ListDTO` mais DTOs de "summary" para aninhamento:

- `TransporteSummaryDTO`, `CidadeSummaryDTO`, `LocalAgendamentoSummaryDTO`, `MotoristaSummaryDTO`, `SolicitacaoSummaryDTO`
- `AgendamentoTransporteViewDTO` agrega todos os summaries acima.

### 4.5 Pactos Federados

- `PactoCreateDTO`, `PactoViewDTO`, `PactoMembrosDTO`
- `PactoEventoResumoDTO`, `PactoEventoEnviadaViewDTO`
- `PublicarSolicitacaoPactoDTO`, `ClaimResultDTO`
- Convites: `CriarConvitesDTO`, `ConviteViewDTO`, `ResponderConviteDTO`, mensagens RabbitMQ
- Join: `CriarJoinRequestDTO`, `JoinRequestViewDTO`, mensagens RabbitMQ

---

## 5. Fluxos do Sistema

### 5.1 Fluxo de Autenticação

```
Usuário → POST /api/auth/login { cpf, password }
  → AuthController.login()
  → AuthenticationManager.authenticate()          (Spring Security)
    → UserDetailsService.loadUserByUsername(cpf)  (busca no banco)
    → BCrypt.matches(password, hash)               (validação da senha)
  → Se DisabledException: return 403 { message }  (usuário ativo=false)
  → TokenService.generateToken(user)
    → JWT.create().withSubject(cpf)
              .withClaim("role", role.name())
              .withClaim("nome", nome)
              .withExpiresAt(now + 2h, GMT-3)
              .sign(HMAC256(secret))
  → return 200 { token }

Requisições subsequentes:
  → Header: Authorization: Bearer <token>
  → JwtAuthenticationFilter.doFilterInternal()
    → TokenService.validateToken(token) → retorna CPF
    → UserRepository.findByCpf(cpf)
    → if (user != null && user.isEnabled())
        SecurityContextHolder.setAuthentication(...)
    → continua filtro
```

### 5.2 Fluxo de Cadastro de Solicitação

```
Usuário (RECEPCAO/ENFERMEIRO/ADMIN) → POST /api/solicitacoes { dados }
  → SolicitacaoController.criar()
  → SolicitacaoService.criar(dto)
    → Valida CPF único
    → Salva Solicitacao
    → Para cada especialidade no DTO:
        → Cria SolicitacaoEspecialidade com status=AGUARDANDO
    → Publica no RabbitMQ se pacto ativo? (opcional)
  → return 201 { SolicitacaoViewDTO }
```

### 5.3 Fluxo de Agendamento

```
Usuário → POST /api/agendamentos { solicitacaoId, localId, data, turno, especialidadeIds }
  → AgendamentoController
  → AgendamentoService
    → Cria AgendamentoSolicitacao
    → Para cada SolicitacaoEspecialidade:
        → Atualiza status → AGENDADO
        → Vincula ao AgendamentoSolicitacao
  → return 201 { AgendamentoViewDto }
```

### 5.4 Fluxo de Upload de Foto de Perfil

```
Usuário → POST /api/users/{id}/foto [multipart/form-data, arquivo=file]
  → UserController.uploadFoto()
  → FileStorageService.salvarFoto(file, userId)
    → Valida tipo MIME (JPEG/PNG/GIF/WebP)
    → Valida tamanho (max 5MB)
    → Gera nome: {userId}_{UUID}.{extensão}
    → Salva em: uploads/profile-pictures/
    → Retorna URL: /api/uploads/profile-pictures/{filename}
  → UserService.atualizarFotoPerfil(userId, url)
    → user.setFotoPerfil(url)
    → save
  → return 200 { UserViewDTO }

Frontend:
  → profilePicture.set(userData.fotoUrl)  → localStorage['profile_picture_url']
  → UserMenu re-renderiza com <img src={$profilePicture}>
```

### 5.5 Fluxo Federado (Pactos entre Municípios)

```
Município A cria Pacto → convida Município B
  → PactoConvite criado → enviado via RabbitMQ para fila do Município B
  → Município B aceita → PactoConviteAceiteMensagem enviada de volta
  → Pacto ativado com membros

Compartilhamento de Solicitação:
  → ADMIN publica solicitacao no pacto
  → PactoEvento criado com payload FHIR R4
  → Mensagem enviada via RabbitMQ para todos os membros
  → Membro interessado faz "claim" → evento atualizado, solicitacao marcada

Notificação de Agendamento Externo:
  → Município B agenda uma solicitação publicada por A
  → AgendamentoExternoMensagemDTO → fila de A
  → Notificacao criada em A para o ADMIN
```

### 5.6 Fluxo de Desativação de Usuário

```
ADMIN → PATCH /api/users/{id}/status
  → UserController.toggleStatus()
  → UserService.toggleStatus(id)
    → Busca usuário
    → Se usuario.ativo && usuario.role == ADMIN:
        → count = userRepository.countByRoleAndAtivoTrue(ADMIN)
        → if count <= 1: throw IllegalStateException (409 Conflict)
    → usuario.setAtivo(!usuario.isAtivo())
    → save
  → return 200 { UserViewDTO }

Próxima requisição do usuário desativado:
  → JwtAuthenticationFilter: user.isEnabled() == false → não autentica
  → return 403 / redireciona para login
```

---

## 6. Banco de Dados

### 6.1 Configuração

- **SGBD:** PostgreSQL
- **Banco (dev):** `dev_marcacao_database`
- **Usuário (dev):** `dev_user`
- **Porta padrão:** 5432
- **Gerenciamento de schema:** Flyway (`spring.flyway.enabled=true`)
- **DDL Hibernate:** `validate` (nunca altera o schema)

### 6.2 Principais Tabelas

| Tabela | Criada em | Descrição |
|---|---|---|
| `solicitacao` | V1 | Solicitação de paciente |
| `agendamento_solicitacao` | V1 | Agrupamento de agendamentos |
| `solicitacao_especialidade` | V1 | Especialidade solicitada |
| `usuarios` | V3 | Usuários do sistema |
| `cid` | V5+ | Classificação Internacional de Doenças |
| `especialidade` | V40 | Especialidades/exames cadastrados |
| `grupo_relatorio` | V43 | Grupos de relatório |
| `municipio` | V30 | Municípios da rede federada |
| `pacto_regional` | V30 | Pactos entre municípios |
| `pacto_membros` | V30 | M:N entre pacto e municipio |
| `pacto_convite` | V35 | Convites para pactos |
| `pacto_join_request` | V36 | Requisições de adesão |
| `notificacao` | V37 | Notificações do sistema |
| `pacto_evento` | V38 | Eventos de fila federada |
| `transporte` | V46 | Veículos sanitários |
| `motorista` | V47 | Motoristas |
| `cidade` | V48 | Cidades |
| `local_agendamento` | V49 | Locais físicos de atendimento |
| `agendamento_transporte` | V50 | Agendamento de transporte |
| `agendamento_transporte_paciente` | V51 | Pacientes em transporte |
| `fechamento_indicadores_dia` | V6x | Indicadores diários fechados |
| `unidade` | V68 | Unidades de Saúde |
| `cota_unidade` | V70 | Cotas por unidade (geral ou por especialidade) |
| `profissional` | V71 | Profissionais solicitantes |

**Colunas adicionadas em tabelas existentes:**
- `usuarios.unidade_id` (V69) — FK para `unidade`, nullable — vínculo do usuário à unidade
- `solicitacao.unidade_id` (V72) — FK para `unidade`, nullable — unidade de origem da solicitação

### 6.3 Histórico de Migrações Notáveis

| Migration | Descrição |
|---|---|
| V1 | Schema inicial: solicitacao, agendamento, especialidade (legacy enum) |
| V3 | Tabela `usuarios` |
| V40 | Especialidade migra de enum para tabela relacional |
| V41 | Migração de dados: enum → tabela especialidade |
| V43 | Tabela `grupo_relatorio` |
| V46–V55 | Módulo de transporte completo |
| V62 | Coluna `vagas` em especialidade |
| V64 | Coluna `grupo_relatorio_id` em especialidade |
| V66 | Coluna `foto_perfil` em usuarios |
| V67 | Coluna `ativo` em usuarios |
| V68 | Tabela `unidade` |
| V69 | Coluna `unidade_id` em `usuarios` (FK) |
| V70 | Tabela `cota_unidade` com índices únicos parciais |
| V71 | Tabela `profissional` |
| V72 | Coluna `unidade_id` em `solicitacao` (FK) |

### 6.4 Relacionamentos Principais (ERD Simplificado)

```
Solicitacao 1──N SolicitacaoEspecialidade N──1 Especialidade N──1 GrupoRelatorio
Solicitacao 1──N AgendamentoSolicitacao
SolicitacaoEspecialidade N──1 AgendamentoSolicitacao
Solicitacao N──M CID
Solicitacao N──1 Unidade (opcional — para segregação de dados)

User N──1 Unidade (opcional — null para ADMIN)

Unidade 1──N CotaUnidade
Unidade 1──N Profissional
CotaUnidade N──1 Especialidade (null = cota geral)

AgendamentoTransporte N──1 Transporte
AgendamentoTransporte N──1 Cidade
AgendamentoTransporte N──1 Motorista
AgendamentoTransporte 1──N AgendamentoTransportePaciente
AgendamentoTransporte N──M LocalAgendamento
Cidade 1──N LocalAgendamento

Pacto N──M Municipio (via pacto_membros)
Pacto 1──N PactoEvento
Pacto 1──N PactoConvite
Pacto 1──N PactoJoinRequest
```

---

## 7. APIs e Endpoints

**Base URL:** `http://localhost:8080/api`  
**Documentação Swagger:** `http://localhost:8080/swagger-ui/index.html`

### 7.1 Autenticação

| Método | Endpoint | Auth | Corpo | Resposta |
|---|---|---|---|---|
| POST | `/auth/login` | ❌ | `{ cpf, password }` | `{ token }` |

**Erros de login:**
- `401` — credenciais inválidas
- `403` — conta desativada (`{ "message": "Conta desativada..." }`)

---

### 7.2 Usuários (`/users`)

| Método | Endpoint | Role Mínima | Descrição |
|---|---|---|---|
| GET | `/users/me` | Qualquer autenticado | Retorna dados do usuário atual |
| GET | `/users` | ADMIN | Lista todos os usuários |
| POST | `/users` | ADMIN | Cria usuário |
| PUT | `/users/{id}` | ADMIN | Atualiza usuário |
| PATCH | `/users/{id}/status` | ADMIN | Ativa/Desativa usuário (409 se último admin) |
| POST | `/users/{id}/foto` | Qualquer autenticado | Upload de foto de perfil (multipart) |
| DELETE | `/users/{id}/foto` | Qualquer autenticado | Remove foto de perfil |
| GET | `/users/medicos` | Autenticado | Lista usuários com role MEDICO |
| GET | `/users/enfermeiros` | Autenticado | Lista usuários com role ENFERMEIRO |
| GET | `/users/recepcionistas` | Autenticado | Lista usuários com role RECEPCAO |

---

### 7.3 Solicitações (`/solicitacoes`)

| Método | Endpoint | Descrição |
|---|---|---|
| POST | `/solicitacoes` | Cria solicitação |
| GET | `/solicitacoes` | Lista (paginada, com filtros via Specification) |
| GET | `/solicitacoes/{id}` | Busca por ID |
| PUT | `/solicitacoes/{id}` | Atualiza |
| DELETE | `/solicitacoes/{id}` | Remove |
| GET | `/solicitacoes/pacientes` | Lista pacientes com solicitações |
| GET | `/solicitacoes/resumo-dashboard` | Contagem por status |
| GET | `/solicitacoes/pacientes/gel` | Lista pacientes com status GEL |
| GET | `/solicitacoes/public/**` | Acesso público (sem auth) |

**Filtros disponíveis (query params):** `nome`, `cpf`, `status`, `especialidade`, `usfOrigem`, `dataInicio`, `dataFim`, `prioridade`

---

### 7.4 Especialidades (`/catalog/especialidades`)

| Método | Endpoint | Descrição |
|---|---|---|
| GET | `/catalog/especialidades/buscar` | Busca paginada |
| GET | `/catalog/especialidades/listar` | Lista todas |
| GET | `/catalog/especialidades/listar/especialidades-medicas` | Filtra categoria CONSULTA |
| GET | `/catalog/especialidades/listar/exames` | Filtra categoria EXAME/PROCEDIMENTO |
| POST | `/catalog/especialidades` | Cria (ADMIN) |
| PUT | `/catalog/especialidades/{id}` | Atualiza (ADMIN) |
| PATCH | `/catalog/especialidades/ativo/{id}` | Ativa/Desativa |

---

### 7.5 Agendamento de Consultas (`/agendamentos`)

| Método | Endpoint | Descrição |
|---|---|---|
| POST | `/agendamentos` | Cria agendamento |
| GET | `/agendamentos/pendentes/**` | Agendamentos pendentes (público) |
| GET | `/agendamentos` | Lista agendamentos |
| PUT | `/agendamentos/{id}` | Atualiza |
| DELETE | `/agendamentos/{id}` | Remove |
| GET | `/agendamentos/dia` | Agenda do dia |
| POST | `/agendamentos/multi` | Agendamento múltiplo |

---

### 7.6 Agendamento de Transporte (`/agendamentos-transporte`)

| Método | Endpoint | Descrição |
|---|---|---|
| POST | `/agendamentos-transporte` | Cria agendamento de transporte |
| GET | `/agendamentos-transporte` | Lista |
| GET | `/agendamentos-transporte/{id}` | Busca por ID |
| PUT | `/agendamentos-transporte/{id}` | Atualiza |
| DELETE | `/agendamentos-transporte/{id}` | Remove |
| PATCH | `/agendamentos-transporte/{id}/status` | Atualiza status |
| GET | `/agendamentos-transporte/dia` | Transportes do dia |

---

### 7.7 Cadastros Auxiliares

| Recurso | Base Endpoint | Operações |
|---|---|---|
| Cidades | `/cidades` | CRUD + listar locais |
| Locais de Agendamento | `/local-agendamento` | CRUD |
| Transportes | `/transportes` | CRUD |
| Motoristas | `/motoristas` | CRUD |
| CIDs | `/cids` | CRUD |
| Grupos de Relatório | `/grupo-relatorio` | CRUD |
| Municípios | `/municipios` | CRUD + registry |

---

### 7.10b Unidades (`/unidades`)

| Método | Endpoint | Role Mínima | Descrição |
|---|---|---|---|
| GET | `/unidades` | Autenticado | Lista todas as unidades |
| GET | `/unidades/ativas` | Autenticado | Lista unidades ativas (SimpleView) |
| GET | `/unidades/{id}` | Autenticado | Busca por ID |
| POST | `/unidades` | ADMIN | Cria unidade |
| PUT | `/unidades/{id}` | ADMIN | Atualiza unidade |
| PATCH | `/unidades/{id}/status` | ADMIN | Ativa/Desativa unidade |

### 7.10c Cotas por Unidade (`/cotas`)

| Método | Endpoint | Role Mínima | Descrição |
|---|---|---|---|
| GET | `/cotas` | ADMIN | Lista todas as cotas |
| GET | `/cotas/unidade/{unidadeId}` | Autenticado | Cotas da unidade |
| GET | `/cotas/unidade/{id}/periodo/{periodo}` | Autenticado | Cotas da unidade no período |
| GET | `/cotas/saldo?unidadeId=&especialidadeId=&periodo=` | Autenticado | Consulta saldo disponível |
| POST | `/cotas` | ADMIN | Cria cota |
| PUT | `/cotas/{id}` | ADMIN | Atualiza quantidade/status da cota |

### 7.10d Profissionais (`/profissionais`)

| Método | Endpoint | Role Mínima | Descrição |
|---|---|---|---|
| GET | `/profissionais/buscar` | Autenticado | Busca paginada (filtro por nome) |
| GET | `/profissionais/{id}` | Autenticado | Busca por ID |
| GET | `/profissionais/unidade/{id}/ativos` | Autenticado | Profissionais ativos da unidade |
| POST | `/profissionais` | RECEPCAO/ADMIN | Cria profissional |
| PUT | `/profissionais/{id}` | RECEPCAO/ADMIN | Atualiza profissional |
| PATCH | `/profissionais/{id}/status` | RECEPCAO/ADMIN | Ativa/Desativa |
| DELETE | `/profissionais/{id}` | ADMIN | Remove profissional |

---

### 7.8 Módulo Federado

| Recurso | Base Endpoint | Descrição |
|---|---|---|
| Pactos | `/pactos` | Criar, listar, buscar pactos |
| Convites | `/pactos/convites` | Criar, aceitar, recusar convites |
| Join Requests | `/pactos/join` | Requisitar e processar adesão |
| Notificações | `/notificacoes` | Listar, marcar como lida |
| Registry | `/registry` | Registro público de municípios (sem auth) |
| Interoperabilidade | `/interoperabilidade` | Endpoints FHIR |
| Transparência | `/transparencia` | Dados públicos (sem auth) |

---

### 7.9 Relatórios e Exportação

| Endpoint | Descrição |
|---|---|
| `/relatorios` | Relatório de produção por grupo |
| `/exportar/**` | Exportação Excel (Apache POI) |
| `/indicadores` | Indicadores por período |
| `/fechamento-indicadores` | Fechamento diário de indicadores |

---

### 7.10 Servindo Arquivos Estáticos

| Path | Descrição |
|---|---|
| `/api/uploads/profile-pictures/**` | Fotos de perfil (sem auth) |

Mapeado via `WebMvcConfigurer.addResourceHandlers()` para o filesystem em `{user.dir}/uploads/profile-pictures/`.

---

## 8. Frontend

### 8.1 Arquitetura Geral

O frontend é uma **SPA com roteamento file-based** (SvelteKit). Cada pasta em `src/routes/` com `+page.svelte` é uma página. O layout raiz (`+layout.svelte`) adiciona o `<Toaster>` global do svelte-sonner.

**Vite Proxy:** Todas as chamadas `/api/**` são proxeadas para `http://localhost:8080` em desenvolvimento, eliminando problemas de CORS no dev.

### 8.2 Gerenciamento de Estado (`src/lib/stores/auth.js`)

Três stores Svelte são exportadas:

**`token`** — `writable<string|null>`
- Persiste em `localStorage['jwt_token']`
- Ao setar `null`: limpa token e `profile_picture_url` do localStorage (logout)

**`user`** — `derived<{cpf, nome, role}|null>`
- Calculado a partir de `token` via `jwtDecode`
- Claims extraídos: `sub → cpf`, `nome → nome`, `role → role`

**`profilePicture`** — `writable<string|null>`
- Persiste em `localStorage['profile_picture_url']`
- Atualizado via `refreshProfilePicture()` que chama `GET /api/users/me`

### 8.3 Cliente HTTP (`src/lib/api.js`)

Todas as requisições passam pela função `send()` centralizada que:
1. Lê o token do store
2. Define `Content-Type: application/json` se houver body
3. Injeta `Authorization: Bearer <token>`
4. Chama `token.set(null)` em respostas 401 (auto-logout)
5. Suporta prefixo de API dinâmico via query param `?api=<porta>` (armazenado em localStorage)

**Funções exportadas:**

| Função | Método HTTP |
|---|---|
| `getApi(path)` | GET |
| `postApi(path, data)` | POST com JSON |
| `putApi(path, data)` | PUT com JSON |
| `patchApi(path)` | PATCH sem body |
| `patchApiData(path, data)` | PATCH com JSON |
| `deleteApi(path, data)` | DELETE com JSON |
| `deleteByIdApi(path)` | DELETE sem body |
| `postApiFile(path, formData)` | POST multipart (não define Content-Type — browser define o boundary) |

### 8.4 Menus por Role (`RoleBasedMenu`)

```svelte
{#if $user.role === 'ADMIN'}      → Menu.svelte    (acesso total)
{:else if $user.role === 'RECEPCAO'
       || $user.role === 'ENFERMEIRO'
       || $user.role === 'MEDICO'} → Menu3.svelte   (clínico)
{:else if $user.role === 'COORD_TRANSPORTE'} → Menu4.svelte   (transporte)
{:else}                            → Menu2.svelte   (usuário básico)
```

**Todas as páginas autenticadas devem usar `<RoleBasedMenu activePage="..." />`**, não menus específicos diretamente.

### 8.4b Novas Rotas Admin

| Rota | Descrição | Role |
|---|---|---|
| `/admin/unidades` | Gestão de Unidades de Saúde | ADMIN |
| `/admin/profissionais` | Gestão de Profissionais Solicitantes | ADMIN |
| `/admin/cotas` | Gestão de Cotas por Unidade | ADMIN |

---

### 8.5 Menu ADMIN (`Menu.svelte`)

Seções: Dashboard, Agenda do Dia, Pacientes, Solicitação (expandível: Consulta + Exame), Gestão (expandível: CID, Usuários, Especialidades, Cidades, Transportes, Motoristas, Municípios, Pactos, Grupos de Relatório).

### 8.6 Menu Clínico (`Menu3.svelte`)

Seções: Principal (Dashboard `/dashboard/unidade`, Agenda do Dia, Pacientes), Solicitação (Consulta, Exame/Procedimento), Gestão (CID).

### 8.7 Menu Transporte (`Menu4.svelte`)

Seções: Principal (Dashboard), Transporte (Agendar, Consultar), Gestão (Transporte, Cidade, Motorista, Paciente, Ponto de Parada).

### 8.8 Componente UserMenu

Exibido no header de todas as páginas autenticadas. Mostra foto de perfil ou inicial do nome. Contém dropdown com link para `/perfil` e botão de logout.

### 8.9 Rotas Principais

| Rota | Descrição | Roles |
|---|---|---|
| `/login` | Login com CPF + senha | Público |
| `/home` | Página inicial pós-login | Todos |
| `/perfil` | Perfil do usuário (foto, nome) | Todos |
| `/dashboard/unidade` | Dashboard da unidade | Todos |
| `/dashboard/procedimentos/data` | Agenda do dia | Todos |
| `/paciente` | Lista de pacientes/solicitações | Clínicos, Admin |
| `/paciente/[id]` | Detalhe da solicitação | Clínicos, Admin |
| `/cadastrar` | Cadastro de consulta | Clínicos |
| `/exames` | Cadastro de exame/procedimento | Clínicos |
| `/agendar/transporte` | Agendamento de transporte | COORD_TRANSPORTE, Admin |
| `/admin/listar-usuarios` | Gestão de usuários | ADMIN |
| `/admin/especialidades` | Gestão de especialidades | ADMIN |
| `/admin/pactos` | Gestão de pactos federados | ADMIN |
| `/relatorio` | Relatório de produção | ADMIN |
| `/transparencia` | Dados públicos | Público |
| `/federation/convite/[token]` | Aceitar convite de pacto | Público |

### 8.10 Exportação de Relatórios no Frontend

O frontend suporta geração de documentos sem servidor:
- **Excel:** via `ExcelJS` — gera `.xlsx` no browser
- **PDF:** via `jsPDF` + `jspdf-autotable` — gera `.pdf` no browser
- **Gráficos:** via `Chart.js` — dashboards visuais

---

## 9. Segurança

### 9.1 Autenticação JWT

**Biblioteca:** `com.auth0:java-jwt:4.4.0`  
**Algoritmo:** HMAC256  
**Segredo:** Configurado via `api.security.token.secret` (application.properties)  
**Expiração:** 2 horas (offset GMT-3)  
**Issuer:** `regulacao-api`

**Claims do token:**
- `sub` — CPF do usuário
- `role` — nome da role (`ADMIN`, `USER`, etc.)
- `nome` — nome completo

### 9.2 JwtAuthenticationFilter

- Extends `OncePerRequestFilter`
- Extrai token do header `Authorization: Bearer <token>`
- Chama `TokenService.validateToken()` → retorna CPF ou string vazia
- Busca usuário no banco pelo CPF
- **Verifica `user.isEnabled()`** antes de autenticar — usuários desativados com token válido são bloqueados
- Define `UsernamePasswordAuthenticationToken` no `SecurityContextHolder`

### 9.3 SecurityConfiguration

**Rotas públicas (sem autenticação):**
- `/api/auth/**`
- `/actuator/**`
- `/api/transparencia/**`
- `/api/solicitacoes/public/**`
- `/swagger-ui/**`, `/v3/api-docs/**`
- `/api/agendamentos/pendentes/**`
- `/api/registry/**`
- `/api/uploads/**`

**Todas as demais rotas:** requerem autenticação.

**Sessão:** `SessionCreationPolicy.STATELESS` — nenhuma sessão HTTP é criada.

**CORS:** Configurado em `CorsConfig.java`:
- `http://localhost:5173` (dev)
- `https://sirg.com.br` (produção)
- IPs locais para acesso em rede

### 9.4 Autorização por Role

Implementada via `@PreAuthorize` ou verificações no service:

- Criação/atualização/desativação de usuários: ADMIN apenas
- Criação de especialidades: ADMIN apenas
- Gestão de pactos: ADMIN apenas
- Operações clínicas (solicitação, agendamento): RECEPCAO, ENFERMEIRO, MEDICO, ADMIN
- Transporte: COORD_TRANSPORTE, ADMIN

### 9.5 Proteções Especiais

**Último admin:** `UserService.toggleStatus()` impede desativar o último ADMIN ativo do sistema. Retorna `409 Conflict` com mensagem explicativa.

**Senha:** Hasheada com `BCryptPasswordEncoder`. Nunca é retornada nas respostas da API.

**CPF único:** Validado por `@UniqueCPF` (custom constraint + `UniqueCPFValidator`).

**Upload de foto:** `FileStorageService` valida tipo MIME e tamanho (max 5MB) antes de salvar.

---

## 10. Padrões e Arquitetura

### 10.0 Controle de Acesso por Unidade

O sistema implementa **segregação de dados por unidade** na camada de serviço:

| Role | Comportamento |
|---|---|
| `ADMIN` | Acessa dados de todas as unidades (sem filtro) |
| Demais roles | Veem apenas dados da sua própria unidade |

**Implementação:**
- `SolicitacaoService.getUnidadeIdDoUsuario(cpf)` — retorna `null` para ADMIN ou usuário sem unidade, ou `unidade.id` para os demais
- Os métodos `todasSolicitacoes()` e `buscarPacientes()` aceitam o parâmetro `cpf` (vindo de `Authentication.getName()`) e aplicam `SolicitacaoSpecification.filtrarPorUnidade(unidadeId)` automaticamente
- O filtro é aplicado via **JPA Specification**, garantindo segurança na query SQL (não é possível bypas via manipulação de parâmetros de request)

**Entidade User:**
- Campo `unidade` (`@ManyToOne`, nullable) — ADMIN pode ter `unidade = null`
- Campo exposto no `UserViewDTO` como `unidadeId` e `unidadeNome`

**Entidade Solicitacao:**
- Campo `unidade` (`@ManyToOne`, nullable) — deve ser preenchido no cadastro para habilitar o filtro

---

### 10.1 Arquitetura em Camadas

```
Controller → Service → Repository → Entity (JPA) → PostgreSQL
   ↕DTO          ↕                       ↕
 Request/      Business               Database
 Response      Logic
```

Cada camada tem responsabilidade única. Controllers não acessam repositórios diretamente; Services não conhecem a camada HTTP.

### 10.2 Padrão de DTOs como Records Java

Todos os DTOs são `record` Java imutáveis com factory methods estáticos `from(Entity)` para conversão. Isso elimina a necessidade de `@JsonIgnoreProperties` e mantém a separação entre entidade e representação de API.

Exemplo:
```java
public record UserViewDTO(Long id, String cpf, String nome, Roles role, String fotoUrl, boolean ativo) {
    public static UserViewDTO from(User user) {
        return new UserViewDTO(user.getId(), user.getUsername(), user.getNome(),
                               user.getRole(), user.getFotoPerfil(), user.isAtivo());
    }
}
```

### 10.3 Specification Pattern

`SolicitacaoSpecification` implementa `Specification<Solicitacao>` para composição dinâmica de filtros em queries JPA. Permite que o frontend envie qualquer combinação de filtros sem necessidade de criar múltiplos métodos no repositório.

### 10.4 Otimistic Locking

`AgendamentoTransporte` possui campo `@Version Long version` para controle de concorrência otimista — evita condições de corrida em agendamentos simultâneos de transporte.

### 10.5 Federação via Mensageria

A comunicação entre municípios usa **RabbitMQ** como broker. Cada instância do sistema tem sua própria fila (configurada em `app.municipio.queue-name`). As mensagens são objetos Java serializados em JSON (Jackson) contendo DTOs específicos para cada tipo de evento:

- Convite de pacto → `PactoConviteMensagemDTO`
- Aceite de convite → `PactoConviteAceiteMensagemDTO`
- Publicação de solicitação → `PactoEventoEnviadaViewDTO` (com FHIR payload)
- Claim de solicitação → `PactoEventoClaimAceiteMensagemDTO`
- Agendamento externo → `AgendamentoExternoMensagemDTO`

### 10.6 Interoperabilidade FHIR

Solicitações compartilhadas via pacto são serializadas no formato **FHIR R4** (usando HAPI FHIR) para garantir compatibilidade com outros sistemas de saúde. `FhirAdapterService` é responsável pelas conversões.

### 10.7 Inicialização de Dados

`DataInitializer` e `EspecialidadeInitializer` são `@Component` com `@EventListener(ApplicationReadyEvent)` que inserem dados essenciais na primeira execução (usuário admin padrão, especialidades iniciais).

### 10.8 Configuração de Instância

`InstanceContext` é um bean `@Component` que lê `app.municipio.nome-identificador` e `app.municipio.queue-name` do `application.properties`, tornando o sistema parametrizável por instalação sem alteração de código.

### 10.9 Frontend: Svelte 5 Runes vs Svelte 4

O projeto usa uma mistura de sintaxes:
- Páginas mais recentes usam **Svelte 5 Runes** (`$state()`, `$props()`)
- Páginas mais antigas usam **Svelte 4** (`export let`, `$:`)
- Ambas são compatíveis na mesma aplicação SvelteKit 2

---

## 11. Dependências e Configurações

### 11.1 Backend (`pom.xml`)

| Dependência | Versão | Uso |
|---|---|---|
| `spring-boot-starter-web` | 3.4.3 | API REST, MVC |
| `spring-boot-starter-data-jpa` | 3.4.3 | ORM, repositórios |
| `spring-boot-starter-security` | 3.4.3 | Autenticação e autorização |
| `spring-boot-starter-validation` | 3.4.3 | Bean Validation |
| `spring-boot-starter-actuator` | 3.4.3 | Health checks |
| `spring-boot-starter-amqp` | 3.4.3 | RabbitMQ |
| `flyway-core` + `flyway-database-postgresql` | — | Migrações de banco |
| `postgresql` | runtime | Driver JDBC |
| `lombok` | — | Redução de boilerplate |
| `mapstruct` | 1.6.3 | Mapeamento de objetos |
| `springdoc-openapi-starter-webmvc-ui` | 2.8.14 | Swagger UI |
| `com.auth0:java-jwt` | 4.4.0 | Geração e validação de JWT |
| `io.jsonwebtoken:jjwt-*` | 0.12.5 | Suporte JWT adicional |
| `poi` + `poi-ooxml` | 5.2.5 | Exportação Excel |
| `hapi-fhir-structures-r4` | 6.8.0 | Interoperabilidade FHIR |

### 11.2 Frontend (`package.json`)

| Dependência | Versão | Uso |
|---|---|---|
| `@sveltejs/kit` | 2.16.0 | Framework web |
| `svelte` | 5.0.0 | UI reativa |
| `vite` | 6.2.6 | Build tool |
| `tailwindcss` | 4.0.0 | Utilitários CSS |
| `chart.js` | 4.5.1 | Gráficos |
| `exceljs` | 4.4.0 | Exportação Excel no browser |
| `jspdf` + `jspdf-autotable` | 5.0.2 | Exportação PDF |
| `jwt-decode` | 4.0.0 | Decodificação de JWT no frontend |
| `lucide-svelte` | 0.544.0 | Ícones SVG |
| `bits-ui` | 2.8.10 | Componentes UI acessíveis |
| `svelte-sonner` | 1.0.5 | Toast notifications |

### 11.3 `application.properties` (Desenvolvimento)

```properties
spring.application.name=regulacao-marcacao
spring.datasource.url=jdbc:postgresql://localhost:5432/dev_marcacao_database
spring.datasource.username=dev_user
spring.datasource.password=dev_password
spring.datasource.driver-class-name=org.postgresql.Driver
spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect
spring.flyway.enabled=true
spring.flyway.baseline-on-migrate=true
spring.flyway.locations=classpath:db/migration
spring.jpa.hibernate.ddl-auto=validate
api.security.token.secret=minha-chave-secreta-super-dificil-de-adivinhar-123456
server.port=8080
spring.servlet.multipart.max-file-size=5MB
spring.servlet.multipart.max-request-size=5MB
app.upload.dir=uploads/profile-pictures
spring.rabbitmq.host=localhost
spring.rabbitmq.port=5672
spring.rabbitmq.username=guest
spring.rabbitmq.password=guest
app.municipio.nome-identificador=CONCEICAO_DO_ALMEIDA
app.municipio.queue-name=fila_conceicao_do_almeida
app.notifications.ignore-self-executor=false
```

**Variáveis a alterar em produção:**
- `spring.datasource.*` — credenciais reais
- `api.security.token.secret` — chave forte (mínimo 32 caracteres aleatórios)
- `spring.rabbitmq.*` — broker de produção
- `app.municipio.*` — identificador único por instância

---

## 12. Guia de Execução

### 12.1 Pré-requisitos

- **Java 21** (`java -version` deve mostrar 21.x)
- **Maven 3.9+** (`mvn -version`)
- **Node.js 20+** e **npm 10+** (`node -v`, `npm -v`)
- **PostgreSQL 14+** rodando localmente
- **RabbitMQ 3.12+** rodando localmente (para funcionalidades federadas)

### 12.2 Configuração do Banco de Dados

```sql
-- Conecte ao PostgreSQL como superusuário
CREATE DATABASE dev_marcacao_database;
CREATE USER dev_user WITH ENCRYPTED PASSWORD 'dev_password';
GRANT ALL PRIVILEGES ON DATABASE dev_marcacao_database TO dev_user;
```

O Flyway criará todas as tabelas automaticamente na primeira execução do backend.

### 12.3 Executando o Backend

```bash
cd regulacao-backend

# Compilar e executar
mvn spring-boot:run

# Ou compilar o JAR e executar
mvn clean package -DskipTests
java -jar target/regulacao-marcacao-0.0.1-SNAPSHOT.jar
```

O backend estará disponível em `http://localhost:8080`.  
Swagger UI: `http://localhost:8080/swagger-ui/index.html`

### 12.4 Executando o Frontend

```bash
cd regulacao-frontend

# Instalar dependências
npm install

# Executar em modo desenvolvimento
npm run dev
```

O frontend estará disponível em `http://localhost:5173`.

O proxy Vite (`vite.config.js`) redireciona automaticamente `/api/**` para `http://localhost:8080/api/**`.

### 12.5 Usuário Inicial

O `DataInitializer` cria um usuário administrador padrão na primeira execução se não existir nenhum usuário no banco. Verifique o código de `DataInitializer.java` para as credenciais padrão de desenvolvimento.

### 12.6 Configuração RabbitMQ (opcional)

Se a funcionalidade de federação entre municípios não for necessária, o RabbitMQ pode ser ignorado. O sistema funciona para operações locais sem ele, desde que a conexão seja tolerada (configurar `spring.rabbitmq.addresses` com endereço válido ou desabilitar o módulo).

### 12.7 Build para Produção

**Backend:**
```bash
cd regulacao-backend
mvn clean package -DskipTests
# Resultado: target/regulacao-marcacao-0.0.1-SNAPSHOT.jar
```

**Frontend:**
```bash
cd regulacao-frontend
npm run build
# Resultado: build/ (arquivos estáticos)
```

O frontend buildado pode ser servido via Nginx ou configurado como adapter-node (`@sveltejs/adapter-node`) para execução Node.js standalone.

### 12.8 Armazenamento de Fotos

O diretório `uploads/profile-pictures/` é criado automaticamente pelo `FileStorageService` relativo ao diretório de trabalho (`System.getProperty("user.dir")`). Em produção, garanta que:
- O processo Java tenha permissão de escrita no diretório
- O diretório seja persistente (não efêmero, como em containers sem volume)
- O mesmo path seja acessível para leitura via o resource handler configurado

Para container Docker, monte um volume:
```dockerfile
VOLUME /app/uploads
```
E configure `app.upload.dir=/app/uploads/profile-pictures`.
