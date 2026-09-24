# Changelog — SIRG (Sistema de Regulação)

## [1.6] — 2026-09-24

### Novidades

- **Dados cadastrais obrigatórios do paciente na Solicitação.**
  `Solicitacao` passa a ter **Nome do Pai**, **Nome da Mãe** e **Endereço**, e o **CNS**
  passa a ser exigido (o CPF já era obrigatório via `@NotBlank/@CPF/@UniqueCPF`).
  As colunas foram criadas como *nullable* no banco (V80) e a obrigatoriedade vale
  **apenas no cadastro novo** (`SolicitacaoCreateDTO`). A edição não exige os campos,
  para que alterar um registro anterior à V80 — corrigir um telefone, por exemplo —
  não passe a demandar o preenchimento dos três campos novos. Assim todo registro novo
  nasce completo, o histórico segue legível/agendável/editável, e a base se saneia
  naturalmente. Depois disso, `@NotBlank` no Update e `NOT NULL` no banco.
  O endpoint `PUT /api/solicitacoes/{id}` passou a ter `@Valid` — antes ele não validava
  nada, apesar de existir o DTO.

- **Data da coleta em Exame/Procedimento.**
  Campo opcional `dataColeta` em `SolicitacaoEspecialidade` (V81). Fica no item solicitado,
  e não na Solicitação, porque a mesma solicitação pode conter exames com coletas em
  datas distintas. Não impede o cadastro quando não informado.

- **Cotas de Grupo, reaproveitando `GrupoRelatorio`.**
  Nenhuma tabela nova de grupo: a V82 apenas acrescenta `unidade.grupo_relatorio_id`,
  de modo que o mesmo registro de grupo passa a reunir Especialidades (para relatório,
  via V58) e Unidades (para cota coletiva). `cota_unidade` passou a aceitar um titular
  que é **uma unidade OU um grupo** (CHECK `ck_cota_titular_exclusivo`, V82), sendo a
  cota de grupo um **pool compartilhado** entre as unidades membros. O vínculo da
  unidade ao grupo é manual, em `/admin/unidades` — a migração não agrupa nada
  sozinha, então o comportamento de cota das unidades atuais fica inalterado.
  Quando a unidade pertence a um grupo, **as duas** cotas incidem e ambas precisam ter
  saldo — mesmo princípio já aplicado entre cota MENSAL e cota por DATA.

- **Cota por grupo de especialidades** (V84).
  Configurar cota uma especialidade por vez e inviavel: o grupo "Laboratorio" sozinho tem
  **172 especialidades**. Agora a cota pode ser lancada no grupo, cobrindo todas elas com
  um **saldo unico compartilhado** — "Unidade A / Laboratorio / 10" = 10 exames de
  laboratorio no mes somando todos os tipos.

  A cota passou a ter duas dimensoes independentes:

  | Dimensao | Opcoes |
  |---|---|
  | **Titular** (de quem e) | uma Unidade **ou** um grupo de unidades (pool) — exatamente um |
  | **Escopo** (o que limita) | uma Especialidade, **ou** um grupo de especialidades, **ou** nada (geral) — no maximo um |

  Cotas de escopos diferentes **incidem juntas**, permitindo configurar "100 exames de
  laboratorio no mes, sendo no maximo 10 de Hemograma": a mais restritiva bloqueia.
  Cadastrar cota num grupo sem especialidades e recusado — nao limitaria nada.

- **Perfil `ADMIN_UNIDADE`** (V83) para os usuários dos postos de saúde: poderes
  equivalentes aos de administrador, porém restritos à unidade de lotação.
  Não acessa Painel Administrativo, Indicadores nem Solicitações por Profissional.
  Nova tela `/unidade/cotas` (somente-leitura); `/admin/unidades` ganhou o seletor de
  grupo de cota.

### Correções

- **A cota nunca era devolvida ao cancelar/excluir um agendamento.**
  `CotaUnidadeService.incrementarUtilizacao` consumia a vaga no agendamento, mas
  `AgendamentoService.deleteAgendamento` apenas desvinculava as especialidades e apagava o
  agendamento. O resultado era um vazamento silencioso: `quantidade_utilizada` só subia,
  e a unidade travava por vagas que não correspondiam a atendimento nenhum — sem nenhuma
  forma de recuperar o saldo pela aplicação. Agora há `estornarUtilizacao`, chamado antes
  da desvinculação (depois dela não haveria mais como saber quais especialidades
  pertenciam ao agendamento), devolvendo uma vaga por especialidade agendada em todas as
  cotas incidentes (unidade e grupo, MENSAL e DATA).

- **Duas marcações simultâneas podiam ultrapassar a cota.**
  O consumo era `if (utilizada >= total) throw; utilizada++` — leitura e escrita separadas,
  entre as quais outra transação cabe. Substituído por um UPDATE condicional atômico
  (`CotaUnidadeRepository#consumirVaga`, com `quantidade_utilizada < quantidade_total` no
  próprio WHERE): a segunda operação simplesmente não afeta linha nenhuma e o serviço
  reporta cota esgotada.

- **Acesso a dados de outra unidade por chamada direta à API.**
  A segregação por unidade existia apenas nas *listagens* de solicitações (via
  Specification). Os acessos **por id** não verificavam nada: `GET /api/solicitacoes/{id}`,
  `PUT /api/solicitacoes/{id}`, `POST /api/solicitacoes/{id}/especialidades`,
  `DELETE /api/solicitacoes/especialidades/{id}`, `GET /api/agendamentos` e
  `DELETE /api/agendamentos/{id}` devolviam ou alteravam registros de qualquer unidade.
  Além disso, `createSolicitacao` confiava no `unidadeId` do corpo da requisição, o que
  permitia cadastrar em nome de outra unidade. Todos esses pontos passam pelo novo
  `UnidadeAcessoService`.

- **Usuário sem unidade caíndo em acesso global.**
  A regra anterior (`unidade == null` ⇒ global) é apropriada para os perfis históricos,
  mas transformaria um `ADMIN_UNIDADE` sem lotação em administrador global de fato.
  Para esse perfil o acesso agora é negado, e o cadastro/edição de usuário exige a unidade.

- **`FechamentoIndicadoresController` não tinha nenhuma `@PreAuthorize`**, ou seja,
  qualquer usuário autenticado alcançava os indicadores consolidados. Como são dados
  globais (por local/grupo, atravessando unidades), a classe passou a negar explicitamente
  o `ADMIN_UNIDADE`, sem alterar o acesso dos perfis existentes.

### Refatoração

- **`UnidadeAcessoService`** concentra a decisão de "de qual unidade este usuário pode ver
  dados", antes duplicada em `SolicitacaoService.getContextoUnidade` e
  `AgendamentoService.isAdminGlobal` (dois `role.equals("ADMIN")` soltos). Ambos passaram a
  delegar, então a regra tem um lugar só — que era pré-requisito para o novo perfil.

### Banco de dados

| Migration | Descrição |
|---|---|
| V80 | `nome_pai`, `nome_mae`, `endereco` em `solicitacao` |
| V81 | `data_coleta` em `solicitacao_especialidade` |
| V82 | `unidade.grupo_relatorio_id`; `cota_unidade.grupo_relatorio_id` + `unidade_id` nullable + CHECK de titular exclusivo + índices únicos parciais |
| V83 | `ADMIN_UNIDADE` na constraint `usuarios_cargo_check` |
| V84 | `cota_unidade.grupo_especialidades_id`; rename `grupo_relatorio_id` → `grupo_unidades_id`; CHECK de escopo; indices unicos consolidados (12 parciais → 2 com COALESCE) |

### Segurança das migrations em produção

Todas as operações de V80–V83 são **aditivas**: nenhuma coluna é removida, nenhum dado
existente é alterado.

- **V80/V81** — `ADD COLUMN` *nullable* sem default: operação de metadados no PostgreSQL
  11+, sem reescrita de tabela.
- **V82** — as linhas atuais de `cota_unidade` têm `unidade_id NOT NULL` (V70) e recebem
  `grupo_relatorio_id` nulo, portanto satisfazem o novo CHECK sem ajuste. Os índices
  únicos recriados cobrem um **subconjunto** das linhas cobertas pelos da V78 (ganham o
  filtro `unidade_id IS NOT NULL`), então não podem falhar por duplicidade nova.
- **V83** — apenas **amplia** a lista de `usuarios_cargo_check` criada na V50. Como essa
  constraint já vigora em produção, nenhuma linha existente pode violá-la.

Script de verificação pré-deploy (somente leitura) em
`regulacao-backend/src/main/resources/db/preflight/preflight_v80_v84.sql` — fora de
`db/migration`, portanto não é executado pelo Flyway.

- **Cota esgotada estourava `LazyInitializationException` em vez da mensagem.**
  Descoberto ao executar a regra contra o PostgreSQL real — mock nenhum reproduz isto.
  `CotaUnidadeRepository#consumirVaga` e `#devolverVaga` usam
  `@Modifying(clearAutomatically = true)`, que **limpa o contexto de persistencia** apos o
  UPDATE. A entidade `CotaUnidade` carregada antes ficava **desanexada**, e
  `mensagemEsgotada(cota)` — que le `unidade`, `grupoRelatorio` e `especialidade`, todos
  `@ManyToOne(LAZY)` — quebrava com *"Could not initialize proxy - no session"*.
  Resultado pratico: **toda** cota esgotada devolvia 500 opaco; o bloqueio funcionava, mas
  o operador nunca sabia que o limite da unidade havia sido atingido. Corrigido
  recarregando a cota (`findById`) antes de montar a mensagem — custo pago apenas no
  caminho de falha.

- **`GlobalExceptionHandler` nao tratava as excecoes de regra de negocio.**
  `IllegalStateException` (cota esgotada), `IllegalArgumentException` (dados invalidos) e
  `EntityNotFoundException` viravam 500, cujo corpo padrao do Spring nao traz o campo
  `message` (`server.error.include-message=never`) — justamente o campo que as telas leem.
  Agora mapeiam para 409 / 400 / 404 com `{ "message": ... }`. Sem isto a regra de cota
  ficaria invisivel na interface mesmo funcionando no backend.

- **Excluir um Grupo de Relatorio destruiria a configuracao de cotas.**
  Consequencia direta de o mesmo registro passar a agrupar Especialidades e Unidades:
  `DELETE /api/grupo-relatorio/deletar/{id}` nao tinha verificacao alguma. Agora a FK de
  `cota_unidade` e `ON DELETE RESTRICT` (nao CASCADE) e o servico recusa a exclusao com
  mensagem explicita quando ha cotas ou unidades vinculadas.

- **Registro legado sem unidade passaria a dar 403 para usuario restrito.**
  Os novos controles por id (`GET/PUT /solicitacoes/{id}`, `DELETE /agendamentos/{id}`...)
  tratavam `unidade == null` como "outra unidade". Mas as migracoes de backfill
  (V73/V76/V77) so vinculam a unidade quando `usf_origem` esta preenchido e casa com
  alguma unidade cadastrada — o que sobra e uma solicitacao **orfa**, que nao pertence a
  unidade nenhuma. Antes deste controle qualquer usuario abria esses registros. Agora
  registro sem unidade nao e bloqueado. Isso nao abre brecha: usuario restrito nunca cria
  solicitacao orfa, porque `resolverUnidadeAlvo` forca a unidade de lotacao dele.

### Novidades (interface)

- **Menu de Agendas do perfil Usuario Padrao passa a seguir o cadastro.**
  A lista era fixa no `menuConfig.js` (Cardiologista, Doppler, Eletrocardiograma,
  Laboratorio, Ortopedista, Pediatria, Raio X, USG) e nao tinha relacao nenhuma com os
  Grupos de Relatorio: criar, renomear ou desativar um grupo nao refletia no menu.
  Agora o menu monta a partir dos grupos marcados como **direcionado ao hospital**
  (`grupo_relatorio.direcionado_hospital`), ativos e ordenados por nome.

  O `menuConfig.js` ganhou suporte a **grupo dinamico** (`dynamic: '<chave>'`), mantendo
  a fonte unica de verdade: quem declara quem ve continua sendo o proprio arquivo; so a
  lista de itens vem do backend. Grupo dinamico sem resultado nao aparece, mesma regra
  dos grupos vazios.

  > **Atencao no deploy:** o menu passa a refletir exatamente o que esta marcado. Os
  > grupos da lista antiga que nao estiverem marcados deixam de aparecer — marque-os em
  > `/cadastrar/grupo-relatorio` antes de subir.

- **Data da Coleta na agenda do hospital.**
  A projecao `PainelEspecialidadeProjection` e a consulta de pacientes agendados por
  grupo passam a trazer a data de coleta, agregada por paciente (DISTINCT, no mesmo
  padrao ja usado para `especialidades`, porque um registro pode reunir varios exames).
  A coluna so aparece quando algum paciente da agenda tem a data preenchida — assim ela
  surge sozinha no laboratorio, sem fixar o codigo do grupo, que agora e cadastravel.

- **Dashboard "Cotas do Mes": cota por grupo mostra o nome do grupo.**
  Cota com escopo de grupo de especialidades aparecia como "Cota geral (todas)", que e o
  rotulo de cota **sem** escopo — duas coisas diferentes com o mesmo texto. O backend
  (`CotaUnidadeViewDTO`) ja devolvia `grupoEspecialidadesNome`; o card em
  `/dashboard/unidade` so lia `especialidadeNome`, que fica nulo quando o escopo e um
  grupo. Sem alteracao no backend — so o card passou a checar `grupoEspecialidadesNome`
  antes de cair no rotulo de "sem escopo".

- **Alerta de cadastro incompleto em `/paciente/{id}`.**
  Ao abrir um paciente cujo registro nao tem Nome do Pai, Nome da Mae, Endereco, CNS ou
  unidade de origem, um aviso ambar lista o que falta. E **somente visual e dispensavel**:
  nao bloqueia edicao, agendamento nem qualquer outra acao — apenas sinaliza o que vale a
  pena completar. Aparece so na abertura das informacoes do paciente.

### Testes

**58 testes**, em quatro niveis:

| Suite | Qtd | O que cobre |
|---|---|---|
| `CotaUnidadeValidacaoTest` | 14 | Validacoes de cadastro: titular/escopo exclusivos, grupo vazio, duplicidade, periodo |
| `CotaUnidadeSimulacaoTest` | 16 | Sequencias (5 vagas -> 6o bloqueado), saldo compartilhado do grupo de especialidades, cancelamento, concorrencia (20 threads / 5 vagas -> exatamente 5 passam), legado |
| `UnidadeAcessoServiceTest` | 9 | Segregacao por unidade, `ADMIN_UNIDADE` sem lotacao, acesso cruzado |
| `SolicitacaoAcessoLegadoTest` | 6 | Registros antigos: orfaos acessiveis/editaveis, edicao sem os campos novos |
| `CotaUnidadeIntegracaoIT` | 15 | **PostgreSQL real**: JPQL de consumo/estorno, cota por grupo de especialidades, CHECKs de titular e escopo, saldo |
| `AgendamentoCotaFluxoIT` | 5 | **PostgreSQL real**, fluxo de /agendar: unidade estoura a cota no 6o agendamento, cancelamento devolve a vaga, ADMIN global isento, legado sem unidade |
| `CotaRollbackIT` | 1 | **PostgreSQL real, fora da transacao do teste**: prova que o consumo parcial e desfeito quando outra cota incidente esta esgotada |

Os `*IT` exigem banco no ar e sao `@Transactional` (rollback ao fim, nao sujam a base).
Rode com `./mvnw test -Dtest='*Test,*IT'`.

**Validado contra banco real:** V80–V84 aplicadas com sucesso, `ddl-auto=validate` aprovou
o mapeamento entidade↔schema, e o script de preflight executou sem nenhum BLOQUEIO.


## [1.5] — 2026-07-07

### Refatoração

- **Menu único orientado por configuração, substituindo 4 componentes de menu separados.**
  Antes: `Menu.svelte` (ADMIN), `Menu2.svelte` (USER/PACIENTE), `Menu3.svelte`
  (RECEPCAO/ENFERMEIRO/MEDICO) e `Menu4.svelte` (COORD_TRANSPORTE) — cada um com sua
  própria árvore de itens em HTML duplicado, e `RoleBasedMenu.svelte` apenas escolhendo
  qual deles renderizar via `if/else` no `$user.role`. Qualquer mudança de navegação
  exigia editar até 4 arquivos, e isso já havia gerado inconsistências reais (ex.: o link
  "Agendamento" existia na versão mobile do menu clínico mas não na versão desktop; as
  páginas `/agendas/raio-x` e `/agendas/ultrasom` nunca passavam `activePage`, então o
  grupo "Agendas" não abria automaticamente nelas).

  Agora: um único `lib/RoleBasedMenu.svelte`, cuja árvore de navegação vem inteiramente de
  `lib/menuConfig.js`. Cada link-folha declara a lista `roles` que pode vê-lo; grupos e
  seções ficam visíveis automaticamente se tiverem ao menos um link visível (sem precisar
  declarar roles próprias, eliminando uma segunda fonte de verdade). Para dar/tirar acesso
  de uma tela a uma role, basta editar `roles` no item correspondente em `menuConfig.js` —
  nenhum `.svelte` precisa ser tocado.

  Todas as 51 páginas do frontend foram migradas para `<RoleBasedMenu activePage="..." />`
  (algumas ainda importavam `Menu`/`Menu2`/`Menu3` diretamente, ou tinham imports mortos
  do menu antigo). `Menu.svelte`, `Menu2.svelte`, `Menu3.svelte` e `Menu4.svelte` foram
  removidos.

  Ao consolidar, dois bugs de navegação pré-existentes foram corrigidos como efeito
  colateral: (1) `/agendar` agora aparece para RECEPCAO/ENFERMEIRO/MEDICO também no
  desktop (já existia no mobile, e o backend já autoriza agendamento para essas roles —
  ver seção 9.4 da documentação técnica); (2) as páginas de agenda por especialidade
  (`/agendas/raio-x`, `/agendas/ultrasom`, etc.) agora passam `activePage` corretamente,
  então o grupo "Agendas" abre e o item ativo é destacado.

## [1.4] — 2026-07-07

### Novidades

- **Relatório de Solicitações por Profissional — lista de solicitações inline.**
  Na tabela "Quantitativo por Profissional" (`/relatorio/profissional`), cada linha agora
  expande em acordeão ao ser clicada, mostrando as solicitações (paciente, especialidade,
  tipo, data) daquele profissional no período filtrado, sem precisar navegar até a seção
  "Detalhado" separada.

- **Indicador de Tempo de Espera por Especialidade.**
  Nova seção em `/indicadores` com filtros (período, unidade, especialidade) e um
  indicador de destaque (tempo médio/mínimo/máximo em dias, em número de solicitações já
  agendadas), além de gráfico e tabela por especialidade.
  Métrica: dias entre a data da solicitação (`data_cadastro`) e a data do atendimento
  agendado (`data_agendada`).
  Backend: `SolicitacaoEspecialidadeRepository#tempoEsperaPorEspecialidade` /
  `#tempoEsperaGeral`, expostos em `GET /api/fechamento/tempo-espera/por-especialidade`
  e `GET /api/fechamento/tempo-espera/geral`.

### Correções

- **Boletim/Comprovante de Agendamento não refletia a Unidade do paciente.**
  Desde a migração de `Solicitacao.usfOrigem` (enum legado) para `Solicitacao.unidade`
  (tabela `unidade`, migração V68–V72), o cadastro de novas solicitações passou a gravar
  apenas `unidadeId`, deixando `usfOrigem` sempre nulo. O comprovante de agendamento
  (gerado em `/agendar`) e a tela de agendamento ainda liam apenas `usfOrigem`, exibindo
  o campo em branco para toda solicitação criada após a migração.
  Corrigido: `SolicitacaoAgendamentoViewDTO` e `SolicitacaoAgendamentoSimpleViewDTO`
  passam a expor `unidadeId`/`unidadeNome`; a tela de agendar e o PDF do comprovante
  exibem a Unidade, com fallback para `usfOrigem` em registros antigos.

- **Relatório "Detalhado por Solicitação" retornava vazio mesmo havendo dados.**
  Causa raiz: várias queries nativas em `SolicitacaoEspecialidadeRepository` usavam o
  padrão `(:parametro IS NULL OR ...)` para filtros opcionais, com o parâmetro aparecendo
  *apenas* dentro do `IS NULL` em uma das ocorrências. O PostgreSQL não consegue inferir
  o tipo desse parâmetro nessas condições; isso não falha na primeira execução (protocolo
  simples), mas assim que o driver JDBC promove a consulta para *prepared statement* no
  servidor (o que acontece naturalmente após poucas execuções da mesma consulta em uma
  conexão do pool — exatamente o que ocorre ao clicar em "Filtrar" repetidas vezes),
  o Postgres passa a exigir resolução de tipo antecipada e retorna
  `ERROR: could not determine data type of parameter $1`. O controller respondia 500,
  e o frontend tratava qualquer resposta não-OK como "lista vazia", sem exibir erro.
  Reproduzido com teste de integração direto contra o banco (8 chamadas repetidas
  reproduziram a falha de forma consistente). Corrigido adicionando cast explícito
  (`CAST(:param AS date)` / `CAST(:param AS bigint)`) em todas as ocorrências afetadas
  — 6 consultas no total, incluindo as duas novas do indicador de tempo de espera.

- **Tela de Agendamento (`/agendar`) não permitia agendar solicitações com status GEL.**
  Um commit anterior já havia incluído `StatusDaMarcacao.GEL` na busca/autocomplete de
  solicitações pendentes (`AgendamentoService#buscarPendentesParaAutoComplete`), então o
  paciente aparecia na busca — mas dois outros filtros, mais adiante no fluxo, ainda só
  aceitavam `AGUARDANDO`/`RETORNO`/`RETORNO_POLICLINICA`: (1) `SolicitacaoAgendamentoViewDTO`,
  que monta a lista de exames pendentes exibida como checkboxes ao selecionar a
  solicitação — por isso a lista aparecia vazia para pacientes GEL; e (2)
  `AgendamentoService#criarAgendamentoParaMultiplosExames`, que faz o match do exame
  selecionado ao confirmar o agendamento — mesmo que a lista aparecesse, a submissão
  falharia com "Exame pendente não encontrado na solicitação". Ambos agora também aceitam
  `StatusDaMarcacao.GEL`, completando o fluxo de ponta a ponta.

- **Foto de perfil não aparecia após migração para Docker.**
  Causa raiz: `WebConfiguration.addResourceHandlers()` montava o caminho de leitura das
  fotos concatenando literalmente `System.getProperty("user.dir") + "/" + app.upload.dir`.
  Isso funciona apenas quando `app.upload.dir` é um caminho **relativo** (padrão local:
  `uploads/profile-pictures`). Em produção/Docker, `docker-compose.prod.yaml` define
  `APP_UPLOAD_DIR=/app/uploads/profile-pictures` (caminho **absoluto**), o que fazia o
  resource handler apontar para `/app//app/uploads/profile-pictures` — um diretório que
  não existe. O upload (`FileStorageService`) sempre salvou no lugar certo (ele já usava
  `Paths.get(dir).toAbsolutePath()`, que trata caminhos absolutos corretamente); só a
  *leitura* estava quebrada, por isso os arquivos existiam no volume mas nunca eram
  servidos (404 silencioso, ícone de imagem quebrada). Corrigido alinhando
  `WebConfiguration` para usar a mesma resolução de caminho (`Paths.get(uploadDir)
  .toAbsolutePath().normalize()`) que `FileStorageService` já usava.

### Arquivos alterados

**Backend**
- `config/WebConfiguration.java`
- `service/AgendamentoService.java`
- `controller/FechamentoIndicadoresController.java`
- `controller/RelatorioSolicitacaoProfissionalController.java` *(novo)*
- `service/FechamentoIndicadoresDiaService.java`
- `service/RelatorioSolicitacaoProfissionalService.java` *(novo)*
- `service/RelatorioProfissionalExcelService.java` *(novo)*
- `repository/SolicitacaoEspecialidadeRepository.java`
- `repository/projection/ProfissionalQuantitativoProjection.java` *(novo)*
- `repository/projection/ProfissionalSolicitacaoDetalheProjection.java` *(novo)*
- `repository/projection/TempoEsperaEspecialidadeProjection.java` *(novo)*
- `repository/projection/TempoEsperaGeralProjection.java` *(novo)*
- `dto/solicitacoesDTO/SolicitacaoAgendamentoViewDTO.java`
- `dto/solicitacoesDTO/SolicitacaoAgendamentoSimpleViewDTO.java`

**Frontend**
- `routes/indicadores/+page.svelte`
- `routes/relatorio/profissional/+page.svelte` *(novo)*
- `routes/agendar/+page.svelte`
- `lib/Menu.svelte`

### Observação

O indicador de tempo de espera pode mostrar médias negativas em bases com registros
migrados de versões antigas, onde `data_cadastro` foi gravada no momento da migração em
vez da data real da solicitação original. Não é um bug de código — é uma limitação dos
dados históricos migrados, que tende a se corrigir naturalmente conforme dados antigos
saem da janela de período filtrada.
