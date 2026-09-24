# Documentação Técnica — SIRG (Sistema de Regulação)

> **Versão documentada:** 1.6 | **Data:** 2026-09-24  
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
│       │   ├── controller/                          # REST Controllers (28)
│       │   ├── dto/                                 # DTOs organizados por domínio (21 subpacotes)
│       │   │   ├── agendamento/  agendamentoDTO/  cid/
│       │   │   ├── cota/                            # Cotas por unidade/grupo
│       │   │   ├── dashboard/  especialidade/  grupo_relatorio/  indicadores/
│       │   │   ├── motorista/  municipio/  notificacao/  paciente/  pacto/
│       │   │   ├── profissional/  regional/  relatorio/
│       │   │   ├── solicitacoesDTO/  solicitacaoEspecialidadeDTO/
│       │   │   ├── transporte/  unidade/  usuariosDTO/
│       │   ├── entity/                              # Entidades JPA (25)
│       │   │   └── enums/                           # Enumerações (14)
│       │   ├── exceptions/
│       │   │   └── GlobalExceptionHandler.java      # Mapeia regra de negócio → 409/400/404
│       │   ├── repository/                          # Repositórios Spring Data JPA (23)
│       │   │   └── projection/                      # Interfaces de projeção
│       │   ├── service/                             # Serviços de negócio (31)
│       │   └── validation/                          # Validações customizadas
│       │       ├── UniqueCPF.java
│       │       └── UniqueCPFValidator.java
│       └── resources/
│           ├── application.properties
│           ├── db/migration/                        # 84 migrações Flyway (V1–V84)
│           ├── db/preflight/                        # Verificação pré-deploy (fora do Flyway)
│           └── db/scripts/                          # Cópias arquivadas das migrations baselineadas
│
├── regulacao-frontend/                  # Módulo SvelteKit
│   ├── package.json
│   ├── vite.config.js
│   ├── svelte.config.js
│   └── src/
│       ├── app.css                                  # Tailwind base
│       ├── routes/                                  # 55 páginas (+page.svelte)
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
│           ├── menuConfig.js                        # Árvore de navegação única (seções/grupos/links + roles)
│           ├── RoleBasedMenu.svelte                 # Único componente de menu, filtra menuConfig.js pela role
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
| `nomePai` | `String` | length=150 — obrigatório no cadastro (validação no DTO) |
| `nomeMae` | `String` | length=150 — obrigatório no cadastro (validação no DTO) |
| `endereco` | `String` | length=300 — obrigatório no cadastro (validação no DTO) |
| `dataNascimento` | `LocalDate` | |
| `observacoes` | `String` | max 500 chars |
| `dataMalote` | `LocalDate` | Data de envio via malote físico |
| `cids` | `List<CID>` | @ManyToMany |
| `especialidades` | `List<SolicitacaoEspecialidade>` | @OneToMany |
| `agendamentos` | `List<AgendamentoSolicitacao>` | @OneToMany |
| `origemMunicipioId` | `UUID` | null = local; não-null = federada |
| `origemMunicipioNome` | `String` | Nome do município de origem federada |

> **Obrigatoriedade dos dados cadastrais (V80):** `nomePai`, `nomeMae` e `endereco` são
> colunas *nullable* no banco e obrigatórias **apenas no cadastro novo**
> (`@NotBlank` em `SolicitacaoCreateDTO`, junto com `cns`, `nomePaciente` e `cpfPaciente`).
>
> `SolicitacaoUpdateDTO` **não** os exige, de propósito: exigi-los na edição faria com
> que alterar qualquer registro anterior à V80 — corrigir um telefone, por exemplo —
> passasse a demandar o preenchimento dos três campos novos, travando a operação
> diária em produção. Assim, todo registro **novo** nasce completo e a base se saneia
> naturalmente, sem impacto sobre o histórico.
>
> Quando os registros históricos estiverem completos, o `@NotBlank` pode ser promovido
> para o Update DTO e, na sequência, o `NOT NULL` aplicado no banco.

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
| `profissionalSolicitante` | `Profissional` | @ManyToOne, nullable |
| `dataColeta` | `LocalDate` | Data da coleta do material — **opcional** (V81) |
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
| `unidade` | `Unidade` | @ManyToOne nullable — titular quando a cota é da unidade |
| `grupoUnidades` | `GrupoRelatorio` | @ManyToOne nullable — **titular**, quando a cota é de um grupo de unidades |
| `especialidade` | `Especialidade` | @ManyToOne nullable — **escopo**, uma especialidade |
| `grupoEspecialidades` | `GrupoRelatorio` | @ManyToOne nullable — **escopo**, todas as especialidades do grupo (V84) |
| `tipoPeriodo` | `TipoPeriodoCota` | `MENSAL` ou `DATA` |
| `periodo` | `String` | Formato "YYYY-MM" (usado quando tipoPeriodo = MENSAL), max 7 |
| `dataEspecifica` | `LocalDate` | Usado quando tipoPeriodo = DATA |
| `quantidadeTotal` | `Integer` | Vagas definidas, default=0 |
| `quantidadeUtilizada` | `Integer` | Vagas consumidas, default=0 |
| `ativo` | `boolean` | default=true |
| `criadoEm` | `LocalDateTime` | @CreationTimestamp |
| `version` | `Long` | @Version — optimistic locking |

**Duas dimensões independentes:**

| Dimensão | Colunas | Regra |
|---|---|---|
| **Titular** — de quem é a cota | `unidade_id`, `grupo_unidades_id` | exatamente um (CHECK `ck_cota_titular_exclusivo`) |
| **Escopo** — o que a cota limita | `especialidade_id`, `grupo_especialidades_id` | no máximo um; ambos nulos = cota geral (CHECK `ck_cota_escopo_exclusivo`) |

Ambos os grupos apontam para `grupo_relatorio`, em papéis distintos: um agrupa
**unidades**, o outro agrupa **especialidades**.

**Exibição do escopo (v1.6):** `CotaUnidadeViewDTO.from()` já projeta os quatro nomes
(`unidadeNome`, `grupoUnidadesNome`, `especialidadeNome`, `grupoEspecialidadesNome`), mas
cada tela decide como resolver o rótulo — não há um "nome do escopo" único no DTO. O card
"Cotas do Mês" de `/dashboard/unidade` (alimentado por
`GET /cotas/unidade/{unidadeId}/periodo/{periodo}`) checava só `especialidadeNome`, então
uma cota de escopo **grupo** (`grupoEspecialidadesNome` preenchido, `especialidadeNome`
nulo) caía no rótulo de cota **sem** escopo ("Cota geral (todas)") — mesmo texto para dois
casos diferentes. A tela `/unidade/cotas` já resolvia certo (`grupoEspecialidadesNome` →
`especialidadeNome` → "Cota geral (todas)"); o card do dashboard passou a seguir a mesma
ordem. Nenhum campo novo foi necessário — só a leitura no frontend.

**Unicidade (V84):** 2 titulares × 3 escopos × 2 tipos de período dariam 12 índices
parciais. Em vez disso há **um índice por tipo de período** sobre a chave inteira, usando
`COALESCE(coluna, -1)` para tratar os nulos (ids são BIGSERIAL, sempre positivos):
`uk_cota_mensal` e `uk_cota_data`.

### 3.15.1 Regra de consumo de cota

A cota limita **o agendamento** (não o cadastro da solicitação): o consumo acontece em
`AgendamentoService.criarAgendamentoParaMultiplosExames`, uma vaga por especialidade
agendada, na unidade da solicitação e na data do agendamento.

**Quais cotas incidem.** Todas as combinações de titular × escopo que se aplicam ao caso
e estão ativas, em ambos os tipos de período:

| Titular | Escopo |
|---|---|
| Unidade da solicitação | especialidade agendada |
| Grupo de unidades a que ela pertence | grupo de especialidades que contém a especialidade agendada |
| | cota geral (sem escopo) |

**Todas precisam ter saldo.** Isso permite configurar "100 exames de laboratório no mês,
sendo no máximo 10 de Hemograma": a cota do grupo e a da especialidade convivem, e a mais
restritiva é que bloqueia. Se qualquer uma estiver esgotada, a transação é abortada e
nenhum consumo persiste — inclusive os já feitos no mesmo laço (verificado em
`CotaRollbackIT`).

**Cota por grupo de especialidades = saldo único compartilhado.** "Unidade A /
Laboratório / 10" significa 10 exames de laboratório no mês somando todos os tipos do
grupo — e não 10 de cada. Existe para evitar cadastrar cota uma especialidade por vez: o
grupo "Laboratorio" sozinho tem 172.

**Resolução em uma consulta.** `CotaUnidadeRepository#buscarCotasAplicaveis` resolve as
duas dimensões de uma vez (antes eram até 8 consultas). Usa `LEFT JOIN` obrigatoriamente:
navegar direto (`c.unidade.id`) geraria INNER JOIN e descartaria justamente as linhas de
FK nula, que aqui são os casos válidos.

**Cota ausente = sem restrição.** Nenhuma cota configurada significa agendamento livre,
preservando o comportamento das unidades que nunca tiveram cota.

**ADMIN global não está sujeito a cota** (`UnidadeAcessoService.isAcessoGlobal`).

**Cota de grupo = pool compartilhado.** O total do grupo é um saldo único consumido por
ordem de chegada pelas unidades membros — não é dividido por unidade.

**Concorrência.** O consumo é um UPDATE condicional atômico
(`CotaUnidadeRepository#consumirVaga`), com `quantidade_utilizada < quantidade_total` no
próprio WHERE. Duas operações simultâneas não conseguem ler o mesmo saldo e ultrapassar o
limite: a segunda não afeta linha nenhuma e o serviço reporta cota esgotada.

**Mensagem de bloqueio.** A cota esgotada vira `IllegalStateException`, mapeada pelo
`GlobalExceptionHandler` para **409 Conflict** com `{ "message": ... }` — o campo que as
telas leem. A cota é recarregada (`findById`) antes de montar a mensagem porque
`consumirVaga` usa `@Modifying(clearAutomatically = true)`: sem isso a entidade fica
desanexada e ler `unidade`/`grupoRelatorio`/`especialidade` (todos LAZY) estoura
`LazyInitializationException`.

**Estorno.** Cancelar/excluir um agendamento devolve as vagas
(`CotaUnidadeService.estornarUtilizacao`, chamado por `AgendamentoService.deleteAgendamento`
**antes** da desvinculação das especialidades — depois dela não haveria mais como saber
quais especialidades pertenciam ao agendamento). Remanejar = excluir + recriar, portanto
estorna e reconsome, inclusive quando a nova data cai em outro período.

**Redução de cota.** `atualizar` recusa uma quantidade menor que a já utilizada, que
deixaria o saldo negativo.

---

### 3.15b Agrupamento de Unidades (reaproveita `GrupoRelatorio`)

Não existe tabela própria de "grupo de unidades". O agrupamento reaproveita
`grupo_relatorio` (§3.6), que já tem o desenho `codigo`/`nome`/`ativo`:

- `especialidade.grupo_relatorio_id` — uma Especialidade pertence a um grupo *(V58)*.
  Usado tanto para relatório quanto como **escopo de cota** (V84).
- `unidade.grupo_relatorio_id` — uma Unidade pertence a um grupo *(V82)*.
  Usado como **titular de cota** coletiva entre unidades.

Ou seja, o mesmo registro de grupo pode reunir especialidades (para relatório) e
unidades (para cota coletiva). O vínculo da unidade é manual, em `/admin/unidades`;
nenhuma unidade é agrupada automaticamente pela migração.

> **Consequência a conhecer:** os grupos cadastrados hoje foram criados para
> relatório (ex.: "Laboratório", "Cardiologia") e aparecem no seletor de grupo da
> cota. Um grupo só passa a ter efeito sobre cotas quando alguma unidade é
> explicitamente vinculada a ele.

> **Exclusão de grupo:** como o mesmo registro passou a carregar dois papéis,
> `DELETE /api/grupo-relatorio/deletar/{id}` agora recusa a exclusão quando há cotas
> ou unidades vinculadas (409), e a FK de `cota_unidade` é `ON DELETE RESTRICT`.
> Sem isso, apagar um grupo de relatório destruiria silenciosamente a configuração
> de cotas atrelada a ele.

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
| `Roles` | `ADMIN, ADMIN_UNIDADE, USER, PACIENTE, ENFERMEIRO, MEDICO, RECEPCAO, COORD_TRANSPORTE` |
| `StatusDaMarcacao` | `AGUARDANDO, AGENDADO, FALTOU, CANCELADO, REALIZADO, RETORNO, RETORNO_POLICLINICA, GEL` |
| `StatusAgendamento` | `AGENDADO, CANCELADO, PENDENTE, CONFIRMADO, REALIZADO, GEL` |
| `ItemCategoria` | `ESPECIALIDADE_MEDICA, EXAME_OU_PROCEDIMENTO` |
| `PrioridadeDaMarcacaoEnum` | `NORMAL, URGENTE, ...` |
| `TurnoEnum` | `MANHA, TARDE` |
| `TipoPeriodoCota` | `MENSAL, DATA` |
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
| `SolicitacaoCreateDTO` | Cadastro — exige nome, CPF, CNS, nome do pai, nome da mãe e endereço |
| `SolicitacaoViewDTO` | Retorno completo (inclui especialidades e agendamentos) |
| `SolicitacaoUpdateDTO` | Edição — **não** exige os dados cadastrais, de propósito (§3.2) |
| `SolicitacaoPublicViewDTO` | Acesso público via `/transparencia` |
| `SolicitacaoSimpleViewDTO` | Lista paginada otimizada |
| `SolicitacaoResumoDTO` | Contagem por status para dashboard |
| `SolicitacaoListFiltersDTO` | Filtros para buscas avançadas com Specification |

### 4.2b Cotas (`dto/cota`)

| DTO | Uso |
|---|---|
| `CotaUnidadeCreateDTO` | Cadastro — titular (`unidadeId` **ou** `grupoUnidadesId`) + escopo (`especialidadeId` **ou** `grupoEspecialidadesId` **ou** nenhum) |
| `CotaUnidadeUpdateDTO` | Ajuste de `quantidadeTotal` e `ativo` |
| `CotaUnidadeViewDTO` | Retorno com titular, escopo, período, consumo e saldo |
| `CotaUnidadeSaldoDTO` | Saldo disponível; quantidades **nulas** = sem cota configurada (sem restrição), não zero vagas |

### 4.2c Unidades e Profissionais

| DTO | Uso |
|---|---|
| `UnidadeCreateDTO` / `UnidadeUpdateDTO` | Cadastro/edição, incluindo o vínculo ao grupo de cota (`grupoRelatorioId`) |
| `UnidadeViewDTO` | Retorno completo com o grupo vinculado |
| `UnidadeSimpleViewDTO` | Listas (id, nome, codigo) |
| `ProfissionalCreateDTO` / `ProfissionalViewDTO` | Profissionais solicitantes |

### 4.3 Especialidades

| DTO | Uso |
|---|---|
| `EspecialidadeCreateDTO` | Cadastro |
| `EspecialidadeViewDTO` | Retorno completo com vagas e grupo |
| `EspecialidadeSimpleViewDTO` | Listas (id, nome, codigo, categoria) |
| `SolicitacaoEspecialidadeViewDTO` | Especialidade dentro de uma solicitação (expõe `dataColeta`) |

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
Usuário (RECEPCAO/ENFERMEIRO/MEDICO/ADMIN/ADMIN_UNIDADE)
  → POST /api/solicitacoes { dados }
  → SolicitacaoController.criarSolicitacao()      [@Valid]
    → Bean Validation no SolicitacaoCreateDTO:
        nomePaciente, cpfPaciente (@CPF, @UniqueCPF), cns,
        nomePai, nomeMae, endereco  ......... todos @NotBlank
        (falha → 400 com a lista de campos faltantes)
  → SolicitacaoService.createSolicitacao(dto, callerCpf)
    → unidadeAcessoService.resolverUnidadeAlvo(callerCpf, dto.unidadeId)
        · ADMIN               → usa a unidade enviada no DTO
        · usuário restrito    → FORÇA a unidade de lotação dele
          (impede cadastrar em nome de outra unidade trocando o campo)
    → Salva Solicitacao
    → Para cada especialidade no DTO:
        → Cria SolicitacaoEspecialidade (status do DTO, dataColeta opcional)
  → return 200 { SolicitacaoViewDTO }
```

> A obrigatoriedade dos dados cadastrais vale **apenas no cadastro**.
> `SolicitacaoUpdateDTO` não exige `nomePai`/`nomeMae`/`endereco`/`cns`, para que os
> registros anteriores à V80 continuem editáveis — ver §3.2.

### 5.3 Fluxo de Agendamento (com consumo de cota)

```
Usuário → POST /api/agendamentos/{solicitacaoId} { examesSelecionados, data, turno, local }
  → AgendamentoController.criarAgendamento()
  → AgendamentoService.criarAgendamentoParaMultiplosExames()   [@Transactional]
    → Valida capacidade global da especialidade (Especialidade.vagas) na data
    → Cria AgendamentoSolicitacao
    → adminGlobal = unidadeAcessoService.isAcessoGlobal(callerCpf)
    → Para cada exame selecionado:
        → Localiza a SolicitacaoEspecialidade pendente
        → Se NÃO for admin global e a solicitação tiver unidade:
            → cotaUnidadeService.incrementarUtilizacao(unidadeId, especialidadeId, data)
                → busca TODAS as cotas incidentes (titular × escopo × período)
                → para cada uma: UPDATE atômico condicional
                    · 1 linha afetada → vaga consumida
                    · 0 linhas        → esgotada ⇒ IllegalStateException
                                        ⇒ rollback desfaz os consumos já feitos
        → Atualiza status → AGENDADO e vincula ao agendamento
  → return 201 { AgendamentoSolicitacaoSimpleViewDTO }

  Cota esgotada ⇒ GlobalExceptionHandler ⇒ 409 { message: "Cota esgotada para ..." }
```

### 5.3b Fluxo de Cancelamento (estorno de cota)

```
Usuário → DELETE /api/agendamentos/{id}
  → AgendamentoService.deleteAgendamento(id, callerCpf)        [@Transactional]
    → exigirAcessoAoAgendamento()  (bloqueia agendamento de outra unidade)
    → estornarCotasDoAgendamento()
        → Para cada SolicitacaoEspecialidade do agendamento:
            → cotaUnidadeService.estornarUtilizacao(unidadeId, especialidadeId, dataAgendada)
                → devolve 1 vaga em cada cota incidente (guard: utilizada > 0)
    → desvincularAgendamento()   ← só DEPOIS do estorno: daqui em diante não há
                                   mais como saber quais especialidades eram do agendamento
    → delete AgendamentoSolicitacao
  → return 204
```

> **Remanejar = excluir + recriar**, portanto estorna e reconsome — inclusive quando a
> nova data cai em outro período.

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
| `grupo_relatorio` | V43 | Grupos — agrupa **especialidades** (relatório e escopo de cota) e **unidades** (titular de cota) |
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
- `usuarios.unidade_id` (V69) — FK para `unidade`, nullable — unidade de lotação do usuário
- `solicitacao.unidade_id` (V72) — FK para `unidade`, nullable — unidade de origem da solicitação
- `solicitacao.nome_pai` / `nome_mae` / `endereco` (V80) — nullable no banco, obrigatórios no cadastro
- `solicitacao_especialidade.data_coleta` (V81) — nullable, opcional
- `unidade.grupo_relatorio_id` (V82) — FK, nullable — grupo de unidades para cota coletiva
- `cota_unidade.grupo_unidades_id` (V82/V84) — FK, nullable — **titular** grupo de unidades
- `cota_unidade.grupo_especialidades_id` (V84) — FK, nullable — **escopo** grupo de especialidades

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
| V73 | Backfill `solicitacao.unidade_id` a partir de `usf_origem` (rodou com `unidade` vazia) |
| V74 | `usf_origem` passa a aceitar NULL |
| V75 | Limpeza de `data_nascimento` órfã |
| V76 | Re-executa o backfill da V73, agora com as unidades cadastradas |
| V77 | Semeia USF01–USF06 + HMCA e vincula as solicitações legadas restantes |
| V78 | `tipo_periodo` + `data_especifica` em `cota_unidade` (cota por data) |
| V79 | `profissional_id` em `solicitacao_especialidade` |
| V80 | `nome_pai`, `nome_mae`, `endereco` em `solicitacao` |
| V81 | `data_coleta` em `solicitacao_especialidade` |
| V82 | `unidade.grupo_relatorio_id`; cota por grupo em `cota_unidade` (titular exclusivo) |
| V83 | `ADMIN_UNIDADE` na constraint `usuarios_cargo_check` |
| V84 | `cota_unidade.grupo_especialidades_id` (escopo por grupo); rename `grupo_relatorio_id` → `grupo_unidades_id`; CHECK de escopo; índices únicos consolidados |

> **Limite do backfill (V73/V76/V77):** só é possível vincular a unidade quando
> `usf_origem` está preenchido **e** casa com alguma unidade cadastrada. O que sobra é
> uma solicitação *órfã* (`unidade_id IS NULL`), que nenhuma migração consegue atribuir.
> É por isso que o controle de acesso por unidade não bloqueia esses registros — ver §10.0.

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
Unidade N──1 GrupoRelatorio (opcional — habilita cota coletiva entre unidades)
Especialidade N──1 GrupoRelatorio (agrupamento para relatório E para cota)
CotaUnidade: titular = Unidade XOR GrupoRelatorio(unidades)
CotaUnidade: escopo  = Especialidade XOR GrupoRelatorio(especialidades) XOR nada

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
| GET | `/cotas/unidade/{unidadeId}` | Autenticado¹ | Cotas da unidade |
| GET | `/cotas/grupo-unidades/{grupoUnidadesId}` | ADMIN, ADMIN_UNIDADE | Cotas cujo titular é o grupo de unidades |
| GET | `/cotas/unidade/{id}/periodo/{periodo}` | Autenticado¹ | Cotas da unidade no período |
| GET | `/cotas/saldo?unidadeId=&especialidadeId=&periodo=` | Autenticado¹ | Saldo disponível (menor entre as cotas incidentes) |
| POST | `/cotas` | ADMIN | Cria cota (titular = `unidadeId` **ou** `grupoUnidadesId`; escopo = `especialidadeId` **ou** `grupoEspecialidadesId` **ou** nenhum) |
| PUT | `/cotas/{id}` | ADMIN | Atualiza quantidade/status da cota |

¹ Os endpoints que recebem `unidadeId` passam por `UnidadeAcessoService.exigirAcessoA`:
trocar o id na chamada direta não dá acesso a outra unidade.

> `GET /cotas/saldo` devolve `quantidadeTotal`/`saldoDisponivel` **nulos** com
> `disponivel = true` quando não há cota configurada — isso significa "sem restrição",
> e não "zero vagas".

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

### 8.4 Menu único orientado por configuração (`RoleBasedMenu` + `menuConfig.js`)

Até a v1.4 existiam 4 componentes de menu distintos (`Menu.svelte`, `Menu2.svelte`,
`Menu3.svelte`, `Menu4.svelte`), cada um com sua própria árvore de itens hardcoded, e
`RoleBasedMenu.svelte` apenas escolhia qual deles renderizar via `if/else` no `$user.role`.
Isso obrigava a editar HTML duplicado em até 4 lugares para qualquer mudança de navegação,
e já havia gerado inconsistências reais entre os componentes (ex.: link "Agendamento"
presente na versão mobile do menu clínico mas ausente na versão desktop).

A partir da v1.5, existe **um único** componente de menu (`lib/RoleBasedMenu.svelte`), cuja
árvore de navegação vem inteiramente de `lib/menuConfig.js`. Para dar ou tirar acesso de uma
tela, basta editar a lista `roles` do item correspondente nesse arquivo — não é necessário
tocar em nenhum `.svelte`.

**Estrutura do `menuConfig.js`:**

```js
export const MENU_SECTIONS = [
  {
    label: 'Principal',                 // cabeçalho de seção, sempre visível se tiver ao menos 1 item visível
    items: [
      {
        type: 'link',
        label: 'Dashboard',
        icon: [...],                     // array de `d` de <path>, suporta ícones multi-path
        href: '/dashboard/procedimentos', // destino padrão
        hrefByRole: {                     // override por role, quando o mesmo item aponta para páginas diferentes
          ADMIN: '/dashboard',
          RECEPCAO: '/dashboard/unidade',
          // ...
        },
        roles: ['ADMIN', 'RECEPCAO', 'ENFERMEIRO', 'MEDICO', 'COORD_TRANSPORTE', 'USER', 'PACIENTE']
      },
      // grupo colapsável — cada link interno declara suas próprias `roles`
      {
        type: 'group',
        key: 'gestao',                   // chave única, usada para abrir/fechar o acordeão
        label: 'Painel Gerencial',
        icon: [...],
        items: [
          { label: 'Cadastrar CID', href: '/cadastrar/cid', roles: [...] },
          // ...
        ]
      }
    ]
  }
  // ...demais seções: Solicitação, Agendas, Transporte, Gestão
];
```

**Regras de visibilidade:**
- Apenas **links-folha** declaram `roles`. Grupos e seções não declaram roles — ficam
  visíveis automaticamente se tiverem ao menos um link visível para a role atual
  (`buildMenuForRole()` filtra a árvore recursivamente e remove grupos/seções vazios).
  Isso evita duas fontes de verdade: dar acesso a um item = adicionar a role na lista
  `roles` desse item, nada mais.
- `resolveHref(item, role)` resolve `hrefByRole[role] ?? href` — usado tanto para o
  destino do link quanto para comparar com `activePage` (highlight do item ativo).
- O grupo colapsável que contém a página atual abre automaticamente ao montar o
  componente, calculado a partir da própria árvore filtrada (sem lista de rotas
  hardcoded por componente, ao contrário da versão anterior).

**Grupos dinâmicos (v1.6):**

Um grupo pode declarar `dynamic: '<chave>'` em vez de `items` fixos. Nesse caso os
itens vêm do backend, e as `roles` ficam **no próprio grupo** (os itens carregados não
têm como declarar roles individualmente).

```js
{
  type: 'group', key: 'agendas', label: 'Agendas',
  dynamic: 'agendasHospital',          // quem preenche: RoleBasedMenu
  roles: ['USER', 'PACIENTE'],          // quem vê
  items: []
}
```

`buildMenuForRole(role, dinamicos)` recebe um mapa `{ chave: [{ label, href }] }` e
encaixa os itens. Um grupo dinâmico ainda não carregado — ou sem resultado — não
aparece, seguindo a mesma regra dos grupos vazios.

Hoje existe uma fonte dinâmica:

| Chave | Origem | Regra |
|---|---|---|
| `agendasHospital` | `GET /api/grupo-relatorio/listar` | Grupos com `direcionadoHospital && ativo`, ordenados por nome; href = `/agendas/{codigo}` |

> **Por que deixou de ser fixo:** a lista anterior (Cardiologista, Doppler, …, USG)
> estava escrita no `menuConfig.js` e não tinha relação com o cadastro. Criar, renomear
> ou desativar um Grupo de Relatório não refletia no menu. Agora, marcar o grupo como
> *direcionado ao hospital* em `/cadastrar/grupo-relatorio` é o que o coloca no menu.
>
> **Consequência operacional:** o menu passa a mostrar exatamente os grupos marcados.
> Grupos que apareciam na lista fixa mas não estão marcados deixam de aparecer — é
> preciso marcá-los antes do deploy para preservar a navegação atual.

**Uso em uma página:** idêntico a antes — `<RoleBasedMenu activePage="/sua/rota" />`.
Nenhuma página deve importar um menu específico diretamente.

### 8.4b Novas Rotas Admin

| Rota | Descrição | Role |
|---|---|---|
| `/admin/unidades` | Gestão de Unidades de Saúde | ADMIN |
| `/admin/profissionais` | Gestão de Profissionais Solicitantes | ADMIN |
| `/admin/cotas` | Gestão de Cotas (por unidade ou por grupo) | ADMIN |
| `/admin/unidades` | Unidades + vínculo ao grupo de cota | ADMIN |
| `/unidade/cotas` | Consulta somente-leitura das cotas da própria unidade | ADMIN_UNIDADE |

### 8.5 Componente UserMenu

Exibido no header de todas as páginas autenticadas. Mostra foto de perfil ou inicial do nome. Contém dropdown com link para `/perfil` e botão de logout.

### 8.6 Rotas Principais

| Rota | Descrição | Roles |
|---|---|---|
| `/login` | Login com CPF + senha | Público |
| `/home` | Página inicial pós-login | Todos |
| `/perfil` | Perfil do usuário (foto, nome) | Todos |
| `/dashboard/unidade` | Dashboard da unidade | Todos |
| `/dashboard/procedimentos/data` | Agenda do dia | Todos |
| `/paciente` | Lista de pacientes/solicitações | Clínicos, Admin |
| `/paciente/[id]` | Detalhe da solicitação — alerta de cadastro incompleto | Clínicos, Admin, ADMIN_UNIDADE |
| `/cadastrar` | Cadastro de consulta | Clínicos, ADMIN_UNIDADE |
| `/exames` | Cadastro de exame/procedimento (com Data da Coleta) | Clínicos, ADMIN_UNIDADE |
| `/unidade/cotas` | Consulta das cotas da própria unidade (somente leitura) | ADMIN_UNIDADE |
| `/agendas/[grupo]` | Agenda do dia do grupo no hospital; `[grupo]` = `grupo_relatorio.codigo`. Coluna Data da Coleta aparece quando algum paciente da lista a tem preenchida (na prática, laboratório) | USER, PACIENTE |
| `/agendar/transporte` | Agendamento de transporte | COORD_TRANSPORTE, Admin |
| `/admin/listar-usuarios` | Gestão de usuários | ADMIN |
| `/admin/especialidades` | Gestão de especialidades | ADMIN |
| `/admin/unidades` | Unidades + vínculo ao grupo de cota | ADMIN |
| `/admin/cotas` | Cotas (titular: unidade/grupo; escopo: especialidade/grupo/geral) | ADMIN |
| `/admin/pactos` | Gestão de pactos federados | ADMIN |
| `/relatorio` | Relatório de produção | ADMIN |
| `/transparencia` | Dados públicos | Público |
| `/federation/convite/[token]` | Aceitar convite de pacto | Público |

### 8.7 Exportação de Relatórios no Frontend

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

### 9.4b Tratamento de Erros (GlobalExceptionHandler)

Todas as respostas de erro trazem `{ "message": ... }`, que é o campo lido pelas telas.

| Exceção | Status | Uso típico |
|---|---|---|
| `MethodArgumentNotValidException` (CPF duplicado) | 409 | CPF já cadastrado |
| `MethodArgumentNotValidException` (demais) | 400 | Mensagens dos campos inválidos |
| `IllegalStateException` | 409 | **Cota esgotada**; grupo com cotas/unidades vinculadas |
| `IllegalArgumentException` | 400 | Dados inválidos (período fora do formato, titular de cota ausente…) |
| `EntityNotFoundException` | 404 | Recurso inexistente |

> Antes da v1.6 nenhuma dessas três últimas era tratada: viravam 500, e o corpo padrão do
> Spring não inclui `message` (`server.error.include-message=never`). A cota bloqueava o
> agendamento corretamente, mas a tela exibia apenas "Verifique os dados e tente
> novamente", sem dizer que o limite da unidade havia sido atingido.

---

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
| `ADMIN_UNIDADE` | **Sempre** restrito à unidade de lotação; sem lotação ⇒ acesso negado |
| Demais roles | Restritos à unidade de lotação quando houver; sem lotação mantêm o comportamento histórico de acesso global |

**Autoridade central: `UnidadeAcessoService`**

A decisão fica num único serviço (antes estava duplicada em `SolicitacaoService` e
`AgendamentoService`, cada um com seu próprio `role.equals("ADMIN")`):

| Método | Uso |
|---|---|
| `contextoDe(cpf)` | Contexto do chamador — `id == null` significa global |
| `isAcessoGlobal(cpf)` | Se o chamador escapa de filtro e de cota |
| `exigirAcessoA(cpf, unidadeId)` | Valida um `unidadeId` vindo do request (lança `AccessDenied`) |
| `resolverUnidadeAlvo(cpf, unidadeSolicitada)` | Força a unidade do próprio usuário na escrita |

> **Por que `ADMIN_UNIDADE` sem lotação é negado em vez de global:** o ramo histórico
> "sem unidade ⇒ sem filtro" é adequado para os perfis antigos, mas aplicado a este perfil
> transformaria um administrador de unidade em administrador global de fato. O cadastro de
> usuário também exige a unidade para esse cargo (`UserService`), então o caso não deveria
> ocorrer — a negação é a segunda barreira.

**Registros legados sem unidade (`unidade_id IS NULL`):** não são bloqueados.
As migrações de backfill (V73/V76/V77) só vinculam a unidade quando `usf_origem` está
preenchido e casa com alguma unidade cadastrada; o que sobra é uma solicitação *órfã*,
que não pertence a unidade nenhuma — portanto não é "dado de outra unidade". Bloqueá-la
seria uma regressão sem ganho de segurança: um usuário restrito nunca cria solicitação
órfã, porque `resolverUnidadeAlvo` força a unidade de lotação dele no cadastro.

**Onde o filtro é aplicado:**
- *Listagens* — via **JPA Specification** (`SolicitacaoSpecification.filtrarPorUnidade`),
  garantindo o filtro na query SQL.
- *Acessos por id* — `SolicitacaoService.exigirAcessoASolicitacao` e
  `AgendamentoService.exigirAcessoAoAgendamento` cobrem `GET/PUT /solicitacoes/{id}`,
  `POST /solicitacoes/{id}/especialidades`, `DELETE /solicitacoes/especialidades/{id}` e
  `DELETE /agendamentos/{id}`. Sem isso o filtro das listagens seria contornável buscando
  o registro diretamente pela chave.
- *Escrita* — `resolverUnidadeAlvo` ignora o `unidadeId` do corpo quando o usuário é
  restrito, impedindo cadastrar/mover solicitação em nome de outra unidade.

**Escopo do perfil `ADMIN_UNIDADE`:**

| Acesso | Recurso |
|---|---|
| ✅ | Solicitações, agendamentos, pacientes, CIDs, profissionais — **da própria unidade** |
| ✅ | Consulta das cotas da própria unidade e do seu grupo (`/unidade/cotas`) |
| ❌ | Painel Administrativo (usuários, unidades, pactos, municípios, notificações) |
| ❌ | Indicadores (`/api/fechamento/**` — negado explicitamente na classe) |
| ❌ | Solicitações por Profissional (`hasRole('ADMIN')`) |
| ❌ | Criação/alteração de cotas e de grupos de unidades |

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

### 12.3b Executando os Testes

```bash
cd regulacao-backend

# Só os testes unitários (não precisam de banco)
./mvnw test

# Unitários + integração (exigem PostgreSQL no ar)
./mvnw test -Dtest='*Test,*IT'

# Uma suíte específica
./mvnw test -Dtest=CotaUnidadeSimulacaoTest
```

| Suíte | Precisa de banco? | Cobre |
|---|---|---|
| `CotaUnidadeValidacaoTest` | não | Validações de cadastro de cota |
| `CotaUnidadeSimulacaoTest` | não | Sequências de agendamento e **concorrência** |
| `UnidadeAcessoServiceTest` | não | Segregação por unidade |
| `SolicitacaoAcessoLegadoTest` | não | Compatibilidade com registros antigos |
| `CotaUnidadeIntegracaoIT` | **sim** | JPQL de consumo/estorno, CHECKs, saldo |
| `AgendamentoCotaFluxoIT` | **sim** | Fluxo de `/agendar` estourando a cota |
| `CotaRollbackIT` | **sim** | Rollback do consumo parcial |

> Os `*IT` são `@Transactional` (rollback ao fim), exceto `CotaRollbackIT` — que
> precisa rodar **fora** da transação do teste para observar o rollback real, e por
> isso limpa os dados que cria no `@AfterEach`.
>
> `RegulacaoMarcacaoApplicationTests.contextLoads` também exige banco: ele valida o
> mapeamento entidade↔schema (`ddl-auto=validate`) depois de o Flyway migrar.

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
