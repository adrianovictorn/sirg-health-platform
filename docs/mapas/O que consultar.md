---
titulo: O que consultar
tags:
  - sirg/mapa
gerado: manual
atualizado: 2026-09-25
---

# O que consultar antes de mexer

Esta nota não se lê do começo ao fim. Ela responde uma pergunta só: **vou mexer em X, o que preciso ler antes?** A ordem importa, porque quase todo erro caro neste sistema vem de mexer numa camada sem saber o que outra camada já garante.

Regra geral: **a fonte da verdade é o código**; os documentos existem para dizer *por que* o código está daquele jeito. Quando os dois divergirem, o código está certo e o documento está velho — corrija o documento na hora, é a única forma de ele continuar servindo.

---

## O mínimo, em qualquer tarefa

1. [[Início]] — o que o sistema faz e onde as coisas moram.
2. [[Mapa - Domínio]] — o vocabulário. Confundir *solicitação*, *especialidade solicitada* e *agendamento* é o erro que mais custa tempo aqui.
3. A seção técnica da área que você vai tocar ([[Índice Técnico]]).

Se a tarefa mexe em **schema, cota ou permissão**, some a isto as três armadilhas da última seção desta nota.

---

## Por tarefa

### Cota, agendamento, saldo

O motor de saldo é a regra mais sensível do sistema, e está em produção testado. **Não reescreva: alimente.**

| Leia | Por quê |
|---|---|
[[03 - Entidades e Classes de Domínio]] §3.15 e §3.15.1 | `CotaUnidade` tem **duas dimensões independentes**: titular (unidade *ou* grupo de unidades) e escopo (especialidade, grupo de especialidades, ou nenhum = geral). Cotas de escopos diferentes **incidem juntas** |
`service/CotaUnidadeService.java` | `incrementarUtilizacao(unidadeId, especialidadeId, data)` e `estornarUtilizacao(...)` são a porta de entrada. `consultarSaldo` é a leitura |
`service/AgendamentoService.java` | Onde o consumo acontece de verdade, e onde o estorno é chamado **antes** de desvincular as especialidades — depois dela não haveria mais como saber o que o agendamento consumia |
[[05 - Fluxos do Sistema]] §5.3 e §5.3b | O fluxo de consumo e o de estorno, em texto |
`entity/CotaUnidade.java` | `@Version` — *optimistic locking*. Duas marcações simultâneas na mesma cota não podem passar as duas |

Cota esgotada é `IllegalStateException` → **409** via `GlobalExceptionHandler`, e a tela lê o campo `message`. Devolver 500 aqui equivale a esconder o motivo do bloqueio do operador.

### Acesso, perfil, permissão

| Leia | Por quê |
|---|---|
[[Mapa - Segurança e Acesso]] | Panorama das duas dimensões: role e unidade |
[[10 - Padrões e Arquitetura]] §10.0 | A regra de escopo por unidade, com a tabela por role |
`service/UnidadeAcessoService.java` | **Autoridade central.** `contextoDe(cpf)`, `isAcessoGlobal(cpf)`, `exigirAcessoA(cpf, unidadeId)`. Essa decisão já esteve duplicada em dois services, cada um com seu `role.equals("ADMIN")` — não duplique de novo |
`config/JwtAuthenticationFilter.java` | O perfil ativo é reconferido **a cada requisição** contra os perfis concedidos. É o que faz retirar um perfil ter efeito imediato |
[[09 - Segurança]] | Claims, rotas públicas, proteções pontuais |

### Criar entidade ou mexer no schema

| Leia | Por quê |
|---|---|
[[06 - Banco de Dados]] §6.3 | Histórico de migrações — e a última versão aplicada, que define o número da sua |
Uma migração recente inteira (`V84`, `V86` ou `V88`) | O padrão da casa: cabeçalho explicando **a decisão**, operações aditivas, `IF NOT EXISTS`, backfill idempotente com `NOT EXISTS` |
[[Infraestrutura]] — "Antes de um deploy com migrations novas" e "Notas sobre migrations" | A ordem de deploy quando há migração nova, e as armadilhas já conhecidas |

Migração aplicada **nunca** é editada. `ddl-auto=validate`: se a entidade e o SQL divergirem, a aplicação não sobe — e é assim que se descobre o erro antes do deploy, não depois.

### Criar endpoint

| Leia | Por quê |
|---|---|
[[07 - APIs e Endpoints]] | O que já existe, para não criar rota duplicada com outro nome |
Um controller recente (`CboController`, `CnesController`) | O padrão: `@PreAuthorize` na rota, DTO como record, service com a regra, controller sem lógica |
[[04 - DTOs]] | DTO por operação (`Create`, `Update`, `View`), sempre record |
`exceptions/GlobalExceptionHandler.java` | Qual exceção vira qual status. Lançar a exceção certa é o que faz a tela mostrar a mensagem certa |

### Frontend

| Leia | Por quê |
|---|---|
[[08 - Frontend]] §8.4 | **Menu é configuração, não componente.** Dar ou tirar acesso a uma tela é editar `roles` em `lib/menuConfig.js`; nenhum `.svelte` é tocado. Os quatro `Menu*.svelte` antigos não existem mais |
`lib/api.js` | Toda chamada passa pelo `send()`, que injeta o Bearer e faz auto-logout em 401 |
`lib/stores/auth.js` | `token`, `user` (derivado do JWT), `profilePicture` |
[[08 - Frontend]] §8.6 | Tabela de rotas com as roles de cada uma |

Svelte 5 com runes (`$state`, `$derived`, `$effect`) — não `export let` nem stores reativos do Svelte 4. Ver [[10 - Padrões e Arquitetura]] §10.9.

### Transporte sanitário

[[diagrama-transporte]] e [[03 - Entidades e Classes de Domínio]] §3.9. Tem `@Version` porque várias pessoas disputam as mesmas vagas do veículo.

### Federação entre municípios

[[diagrama-federacao]], [[10 - Padrões e Arquitetura]] §10.5 e `config/InstanceContext.java` — `getMunicipioLocal()`, `getNomeIdentificador()`, `getQueueName()`. Feature exclusiva de um município é `if` sobre o nome do município local; ver também [[Infraestrutura]].

### Custo, preço e teto financeiro

[[03 - Entidades e Classes de Domínio]] §3.15d e [[07 - APIs e Endpoints]] §7.10i. O código está em `service/Custo*Service.java`, `service/TetoFinanceiroService.java` e no trecho `registrarCustoEDebitarTeto` de `service/AgendamentoService.java`. Antes de mexer:

- **Valor em reais só sai por `/api/custos/**`**, para ADMIN e GESTOR. Nunca acrescente preço, custo ou saldo a `EspecialidadeViewDTO`, `SolicitacaoEspecialidadeViewDTO`, `CotaUnidadeViewDTO` ou qualquer DTO que um perfil de unidade leia. A mensagem de teto esgotado também não cita valor.
- **O teto não é a cota.** Tabela, service e endpoints próprios; `CotaUnidadeService` não sabe que ele existe. Não junte os dois.
- **Sem preço não é zero.** `valor_unitario` nulo fica fora dos totais e é contado à parte.
- **Estorno usa o que está gravado no item** (`valor_unitario_agendado` e `teto_financeiro_id`), nunca recalcula.
- **Preço nunca entra por migration.** Os dois municípios têm cadastros diferentes; a carga é pela importação com conferência.

### WhatsApp e mensagens ao paciente

[[07 - APIs e Endpoints]] §7.10g (webhook) e §7.10h (envio), [[0002-webhook-whatsapp-por-instancia]] e [[0003-envio-whatsapp-fila-em-banco-e-registro-de-status]]. O código está em `service/whatsapp/`. Antes de mexer: a mensagem nunca leva CPF, CNS, quem agendou nem a observação do operador; a fila não guarda texto nem telefone; e nada disso pode rodar dentro da transação do agendamento. Ativação em produção: [[Infraestrutura]].

### Relatórios e exportação

[[08 - Frontend]] §8.7 — Excel e PDF são gerados **no browser** (ExcelJS, jsPDF). O backend tem POI para o que precisa sair pronto do servidor.

### Subir para produção

[[Infraestrutura]] inteiro, e [[12 - Guia de Execução]] para rodar local antes.

---

## Se a próxima tarefa for a fase 3 da Agenda

Ordem de leitura, com o motivo de cada parada:

1. [[Agenda e Oferta]] §3 e §4 — as decisões já tomadas valem como premissa: a agenda **gera** cota (não substitui o motor de saldo), horário é faixa com vagas, executante é `Unidade` com `tipo`.
2. [[Agenda e Oferta]] §3.4 — as regras R1 a R10. R2, R3 e R10 são as que mandam no código de edição e cancelamento.
3. `service/CotaUnidadeService.java` — a agenda vai **criar** `CotaUnidade` com `tipoPeriodo = DATA` e `dataEspecifica`, uma por par (ocorrência × unidade). Entender `criar` e o índice único por período evita colidir com cota que já existe.
4. [[03 - Entidades e Classes de Domínio]] §3.15 — as duas dimensões da cota, de novo: a cota gerada pela agenda conviverá com a cota mensal, e as duas incidem juntas.
5. `entity/Unidade.java` + `entity/ProfissionalVinculo.java` — o que a fase 2 já entregou para a agenda apontar: executante com `tipo`, e `GET /api/profissionais/executante/{id}/ativos` para o combo de profissionais.
6. `V88__Profissional_Cpf_E_Vinculos.sql` — o padrão de migração que a V89/V90 devem seguir, incluindo o índice único com `COALESCE`.
7. [[Agenda e Oferta]] §8 — a numeração planejada das migrações, e §10, o que está **fora** de escopo (slot por minuto, SOAP do CNES, tabela CBO completa).

---

## Cinco armadilhas que não se descobre lendo o código devagar

Estão documentadas num lugar só cada uma. Quem não leu, erra.

**1. `NULL` não é igual a `NULL` em índice único do Postgres.** Dois registros com a mesma chave e coluna nula *passam* por um `UNIQUE` comum. Por isso `cota_unidade` (V84) e `profissional_vinculo` (V88) usam `COALESCE(coluna, -1)` no índice. Consequência no Java: conferir existência exige **duas** consultas, `...AndCboId` e `...AndCboIsNull`, porque `cbo_id = NULL` nunca casa em SQL. Ver [[03 - Entidades e Classes de Domínio]] §3.16c.

**2. Solicitação órfã existe.** Registros antigos com `unidade_id IS NULL` que nenhuma migração conseguiu atribuir (o backfill só resolve quando `usf_origem` casa com unidade cadastrada). É por isso que o controle de acesso por unidade **não bloqueia** esses registros — ver [[06 - Banco de Dados]] §6.3 e [[10 - Padrões e Arquitetura]] §10.0. Filtro novo que ignore isso esconde pacientes reais da tela.

**3. O código IBGE do município na API do CNES tem 6 dígitos**, sem o verificador: São Felipe é `292910`, não `2929107`. Com 7 dígitos a API responde **200 com lista vazia** — falha silenciosa, sem erro nenhum para investigar. Ver [[07 - APIs e Endpoints]] §7.10f.

**4. A JVM do container roda em UTC.** `LocalDate.now()` devolve o dia seguinte entre 21h e 0h de Brasília. Regra que depende de "hoje" usa um fuso explícito — `WhatsAppEnvioProperties.FUSO` ou o `ZoneId` de `AgendaDiaConsolidadaService`. Ver [[07 - APIs e Endpoints]] §7.10h.

**5. Escrita no banco depois do commit some sem erro.** Um `@TransactionalEventListener(AFTER_COMMIT)` que grava precisa de `@Transactional(propagation = REQUIRES_NEW)` no método chamado. Os ITs `@Transactional` não detectam a falta: neles nunca há commit e o listener nem roda. Ver `WhatsAppEnvioFluxoIT`.

---

## Onde *não* procurar a verdade

| Não confie | Use |
|---|---|
`profissional.unidade` | `ProfissionalVinculo` — o campo está `@Deprecated` desde a V88 e sobrevive só para as telas que ainda não migraram |
`solicitacao.usf_origem` (`UsfEnum`) | `solicitacao.unidade_id` — a coluna antiga aceita NULL desde a V74 |
`LocalDeAgendamentoEnum` | `LocalAgendamento` (tabela) — o enum é legado |
Notas em `tecnica/` e `referencia/` como lugar de editar | São **geradas**. Edite `DOCUMENTACAO_TECNICA.md` e rode `python docs/_build/gerar_vault.py` |

E o teste que vale consultar como especificação executável: `CotaUnidadeSimulacaoTest` (sequências e concorrência de consumo), `CotaUnidadeValidacaoTest` (o que é recusado no cadastro), `ProfissionalImportacaoServiceTest` (o que cada linha do CNES vira). Quando a dúvida é "o que acontece se...", o teste responde mais rápido que o texto.

---

Volta para [[Início]] · vizinhos: [[Mapa - Domínio]], [[Mapa - Arquitetura]], [[Mapa - Segurança e Acesso]], [[Mapa - Operação]]
