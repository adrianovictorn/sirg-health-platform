# CLAUDE.md

Guia para trabalhar neste repositório com Claude Code. Este arquivo é intencionalmente
enxuto — a documentação completa do sistema já existe e é mantida em outro lugar. Não
duplique aqui o que já está lá; aponte para ela.

## O sistema

**SIRG (Sistema Integrado de Regulação e Gestão)** — regulação e marcação de consultas e
exames especializados em municípios do interior da Bahia, em **produção** (duas VPS reais,
uma por município). Substitui planilhas manuais: solicitação de paciente, agendamento por
especialidade, cota por unidade, transporte sanitário, relatórios e federação entre
municípios via fila.

Por ser um sistema de saúde em produção, mudanças de código podem afetar dados reais de
pacientes e o atendimento de uma unidade de saúde agora. Trate isso como um sistema crítico,
não como um projeto de estudo.

## Onde está a documentação

**Comece sempre por [docs/Início.md](docs/Início.md)** — é o índice do vault (Obsidian,
Markdown puro, também legível fora do Obsidian).

Antes de tocar em qualquer área do código, leia
**[docs/mapas/O que consultar.md](docs/mapas/O que consultar.md)** — responde "vou mexer em
X, o que preciso ler antes?" por tarefa (cota/agendamento, acesso/permissão, schema,
endpoint, frontend, transporte, federação) e lista as armadilhas conhecidas do sistema.

Estrutura do vault (`docs/`):

| Pasta | Conteúdo | Como editar |
|---|---|---|
| `docs/mapas/` | Mapas de arquitetura, domínio, segurança, operação | **Manual** — edite direto |
| `docs/especificacoes/` | O que foi pedido e ainda não está no código | **Manual** — edite direto |
| `docs/decisoes/` | Registros de decisão (ADRs) | **Manual** — edite direto, ver `docs/decisoes/README.md` |
| `docs/diagrama-*.md` | Diagramas Mermaid | **Manual** — edite direto |
| `docs/tecnica/` | Entidades, DTOs, fluxos, banco, endpoints, segurança | **Gerado** — não edite. Fonte: `DOCUMENTACAO_TECNICA.md` |
| `docs/referencia/` | Changelog, infraestrutura, visão institucional | **Gerado** — não edite. Fontes: `CHANGELOG.md`, `INFRA.md`, `README.md` |

Se você alterar `DOCUMENTACAO_TECNICA.md`, `CHANGELOG.md`, `INFRA.md` ou `README.md`, rode
`python docs/_build/gerar_vault.py` para regenerar o vault. Nunca edite arquivos dentro de
`docs/tecnica/` ou `docs/referencia/` diretamente — são sobrescritos pelo script.

**Regra do próprio vault, e que vale aqui também:** a fonte da verdade é o código. Quando
documentação e código divergirem, o código está certo.

## Stack

| Camada | Tecnologia |
|---|---|
| Backend | Spring Boot 3.4.3 · Java 21 · Maven |
| Frontend | SvelteKit 2.16 · Svelte 5 (runes) · Tailwind 4 · Vite 6 |
| Banco | PostgreSQL · Flyway |
| Auth | JWT (Auth0 java-jwt), stateless |
| Mensageria | RabbitMQ (federação entre municípios) |
| Interoperabilidade | HAPI FHIR R4 |

Detalhes de versões e dependências: `docs/tecnica/11 - Dependências e Configurações.md`.

## Estrutura de pastas

```
Regula-o/
├── regulacao-backend/    # Spring Boot — Controller → Service → Repository → Entity
├── regulacao-frontend/   # SvelteKit — src/routes (páginas) + src/lib (api.js, stores, componentes)
├── nginx/                # Proxy reverso (produção)
├── docs/                 # Vault de documentação (ver acima)
└── uploads/              # Fotos de perfil (runtime)
```

Árvore completa: `docs/tecnica/02 - Estrutura do Projeto.md`.

## Convenções encontradas no código

- **Camadas (backend):** Controller não acessa repositório nem conhece entidade; Service
  concentra a regra de negócio; Repository é Spring Data JPA puro.
- **DTOs:** sempre `record` Java, um por operação (`*CreateDTO`, `*UpdateDTO`, `*ViewDTO`),
  com factory estático `from(Entity)`.
- **Erros de negócio:** `IllegalStateException` → 409, `IllegalArgumentException` → 400,
  `EntityNotFoundException` → 404, todos mapeados em `GlobalExceptionHandler` com corpo
  `{ "message": ... }`. Nunca deixe uma regra de negócio virar 500 silencioso.
- **Filtros dinâmicos:** JPA Specification (`SolicitacaoSpecification` é o exemplo).
- **Concorrência:** optimistic locking (`@Version`) em `CotaUnidade` e
  `AgendamentoTransporte` — não remova nem contorne.
- **Acesso por unidade:** decisão centralizada em `UnidadeAcessoService`
  (`contextoDe`, `isAcessoGlobal`, `exigirAcessoA`, `resolverUnidadeAlvo`). Já esteve
  duplicada em dois services — não duplique de novo.
- **Migrations:** Flyway, `V__` sequencial, nunca editar uma migration já aplicada;
  operações aditivas, `IF NOT EXISTS`, backfill idempotente. Ver uma migration recente
  (V84+) como modelo antes de escrever uma nova.
- **Frontend:** menu é configuração (`src/lib/menuConfig.js`), nunca componente por role;
  toda chamada HTTP passa por `src/lib/api.js`; páginas novas usam Svelte 5 runes
  (`$state`, `$derived`, `$effect`), não `export let`/stores reativos do Svelte 4.

Isso é um resumo. Os detalhes e o "porquê" de cada decisão estão nos mapas e na seção
técnica linkados acima — leia-os antes de mexer, não só este resumo.

## Comandos

**Backend** (`regulacao-backend/`):
```bash
./mvnw spring-boot:run              # rodar local — http://localhost:8080
./mvnw test                         # testes unitários (não precisam de banco)
./mvnw test -Dtest='*Test,*IT'      # unitários + integração (exigem PostgreSQL)
mvn clean package -DskipTests       # build de produção
```

**Frontend** (`regulacao-frontend/`):
```bash
npm install
npm run dev      # http://localhost:5173, proxy /api/** → localhost:8080
npm run build    # build de produção
npm run lint      # prettier --check + eslint
npm run format   # prettier --write
```

Pré-requisitos e configuração do banco: `docs/tecnica/12 - Guia de Execução.md`.

## Fluxo de trabalho obrigatório

Toda mudança de código neste repositório passa por revisão em etapas, com pausas para
aprovação explícita do usuário. Não pule etapas nem implemente antes da aprovação.

**Qual comando usar:**

- **`/feature <descrição>`** — para algo **novo**: uma tela, um endpoint, uma entidade,
  uma regra que ainda não existe.
- **`/ajuste <descrição>`** — para **alterar um comportamento que já existe**: corrigir um
  bug, mudar uma regra, adicionar um campo a um fluxo já em produção. Este fluxo exige
  primeiro descrever o comportamento atual (baseado no código, não em suposição) e produzir
  o menor diff possível, porque o alvo já tem dados reais e usuários dependendo dele.

Ambos orquestram os mesmos três subagentes, na ordem:

1. **prompt-reviewer** (`Read`) — reescreve o pedido de forma objetiva, lista critérios de
   aceite verificáveis, ambiguidades/perguntas em aberto e riscos (dados de pacientes,
   regras de regulação, integrações, impacto em produção). Não implementa.
   → **Pausa** para você responder as ambiguidades.
2. **architecture-explorer** (`Read`, `Grep`, `Glob`, somente leitura) — lê `CLAUDE.md` e
   `docs/`, localiza os arquivos envolvidos e devolve um plano. Em modo `/ajuste`, primeiro
   descreve o comportamento atual e seus dependentes antes de propor qualquer mudança.
   → **Pausa** para você escrever "aprovado" (ou pedir ajustes ao plano).
3. Implementação seguindo exatamente o plano aprovado.
4. **code-reviewer** (`Read`, `Grep`, `Glob`, `Bash`) — revisa o `git diff` contra o plano
   aprovado e as convenções deste arquivo. Aponta bugs, desvios de padrão, falta de testes,
   problemas de segurança e de privacidade de dados de saúde. Pode rodar testes; não edita
   arquivos.

Definições completas dos subagentes: `.claude/agents/`. Comandos: `.claude/commands/`.
