# Changelog — SIRG (Sistema de Regulação)

## [Não publicado]

### Novidades

- **Custos: preco por especialidade, painel e teto financeiro (`/custos`, `/api/custos/**`).**
  Cada especialidade pode ter um **valor unitario** e um **codigo SUS** (SIGTAP). Com isso o
  sistema mostra quanto custa a fila, o que foi agendado e o que foi realizado, e permite
  limitar em reais o que cada unidade agenda de laboratorio no mes.

  **So ADMIN e GESTOR veem valores.** Nenhum perfil de unidade ve preco, custo, saldo ou
  limite — nem na tela, nem na resposta da API. GESTOR acompanha; so ADMIN altera preco,
  importa planilha e libera teto.

  **Nada muda ate alguem cadastrar preco e teto.** Subir esta versao nao cria preco nem teto
  nenhum. Especialidade sem preco continua funcionando como antes e so fica fora dos totais
  (aparece como "sem preco", nunca como R$ 0,00). Unidade sem teto nao tem limite de valor.

  **Precos (`Gerenciamento de Unidades > Precos das Especialidades`).** Digitados na tela ou importados de
  planilha (`Gerenciamento de Unidades > Importar Precos`, .xlsx ou .csv). A importacao primeiro mostra o que
  casou com o cadastro e o que nao casou, e so grava o que for marcado. Nao casa por nome
  parecido, nao substitui preco ja gravado sem confirmacao e nao cria especialidade. A tabela
  de exames laboratoriais enviada pela cliente esta transcrita em
  `docs/especificacoes/anexos/precos-exames-laboratoriais.csv`.

  **Valor da epoca.** O agendamento guarda o preco do dia em que foi marcado. Reajustar um
  preco vale para os proximos agendamentos e nao altera meses passados. Agendamentos feitos
  antes desta versao nao tem valor gravado e ficam fora dos totais.

  **Painel (`Gerenciamento de Unidades > Painel de Custos`).** Tres numeros, no total, por unidade e por
  especialidade: custo estimado da fila (pelo preco atual), custo agendado e custo concluido
  (pelo valor da epoca, no mes da data agendada). So numeros agregados, sem dado de paciente.

  **Teto financeiro (`Gerenciamento de Unidades > Tetos Financeiros`).** Um valor em reais por unidade, por mes
  e por grupo de especialidades (na pratica, Laboratorio). Cada exame agendado debita o seu
  preco; cancelar devolve. Sem saldo, a unidade recebe "Teto financeiro ... esgotado", **sem
  nenhum valor na mensagem**. Agendamento feito por ADMIN ou GESTOR debita, mas nao e
  bloqueado. Falta do paciente nao devolve (igual a cota). O teto convive com a cota em
  quantidade: as duas regras valem juntas.

  **Codigo SUS e campo novo.** O codigo que a especialidade ja tinha continua igual.

- **Agendar so os itens possiveis de um lote (`/agendar`).** Ao marcar varios exames,
  procedimentos ou consultas da mesma ficha, a tela agora confere as vagas antes de gravar. Se
  algum item nao puder ser agendado (cota esgotada, sem cota liberada, capacidade do dia, item que
  ja nao esta pendente), abre um aviso com **o que sera agendado, o que fica de fora e o motivo de
  cada um**. O operador escolhe entre voltar ou agendar somente os demais; os itens de fora
  continuam na fila.

  **Quando todos os itens cabem, nada muda:** o agendamento e gravado direto, sem etapa a mais.

  **O comprovante e a mensagem ao paciente trazem so o que foi agendado.**

  **Item que depende de correcao nao e deixado de fora.** Profissional nao escolhido ou hora fora
  do periodo da cota aparecem como "precisa de correcao": o operador ajusta e envia de novo.

  **O teto financeiro continua valendo para o agendamento inteiro.** Se o teto nao comportar os
  itens possiveis, nada e agendado e a tela avisa — sem citar valores.

  A conferencia e so uma consulta (`POST /api/agendamentos/{solicitacaoId}/verificar`): nao
  reserva vaga. Se outra unidade usar a vaga nesse intervalo, o agendamento e recusado como antes
  e basta enviar de novo.

- **Mensagens de WhatsApp para o paciente (`/admin/whatsapp`, `/api/whatsapp/**`).**
  O sistema passa a avisar o paciente pelo WhatsApp do numero do municipio:
  **confirmacao** quando e agendado para consulta ou exame, **remarcacao**, **cancelamento** e
  **lembrete** 3 dias antes. Transporte sanitario fica para a proxima entrega.

  **Nasce desligado.** Subir esta versao nao envia nada: precisa das credenciais no `.env`
  (`WHATSAPP_ACCESS_TOKEN`, `WHATSAPP_PHONE_NUMBER_ID`), dos tres templates aprovados pela Meta
  e de um ADMIN ligar a chave em **Painel Admin > WhatsApp**. Checklist em `INFRA.md`. Instancia
  sem credenciais opera exatamente como antes.

  **O agendamento nao depende do WhatsApp.** A mensagem so entra numa fila depois que o
  agendamento esta gravado, e quem fala com a Meta e uma tarefa separada. Meta lenta ou fora
  do ar nao atrasa nem desfaz agendamento, e nao mexe em cota.

  **O que a mensagem diz:** primeiro nome, atendimento, local, data, dia da semana, horario,
  turno e profissional. **Nunca** CPF, CNS, quem agendou ou a observacao do operador.

  **Especialidade sensivel.** O cadastro de especialidade ganhou a marcacao "Sensivel": as
  marcadas saem como "atendimento especializado", sem o local. **Nenhuma vem marcada — revise o
  catalogo antes de ligar o envio.**

  **Paciente pode sair.** A ficha do paciente ganhou "Nao enviar mensagens por WhatsApp", que
  vale para todas as fichas do mesmo CPF.

  **Remarcar** (excluir e agendar de novo em ate 10 minutos) gera uma unica mensagem de
  remarcacao. Por isso todo aviso de cancelamento sai com cerca de 10 minutos de atraso.

  **Lembrete:** lote as 8h para quem tem atendimento daqui a 3 dias. Se o lote da vespera nao
  rodou, inclui os de daqui a 2 dias. Nunca dois lembretes para o mesmo agendamento.

  **Painel:** chave de ligar/desligar o envio (nao afeta o recebimento do webhook), volume por
  periodo (enviadas, entregues, lidas, falhas, nao enviadas por motivo, cobraveis, recebidas),
  lista de mensagens, reenvio da confirmacao ou do lembrete de um agendamento e "rodar
  lembretes agora". A lista nao mostra nome nem telefone inteiro. So ADMIN.

  **Travas:** limite diario (padrao 200) e lista de numeros de teste — enquanto preenchida, so
  eles recebem.

  **Telefone:** aceita mascara, 55 e celular antigo sem o nono digito. Fixo e numero sem DDD
  nao recebem e aparecem no painel como "Telefone invalido ou sem DDD".

  **O webhook passa a gravar** o status de entrega de cada mensagem e a contagem diaria de
  mensagens recebidas. O texto das respostas dos pacientes continua sem ser guardado: **o
  numero ainda nao e canal de atendimento.**

  Migrations V101 a V104, todas aditivas.

- **Webhook do WhatsApp (`GET` e `POST /api/webhooks/whatsapp`).**
  Primeira fatia da integracao com a WhatsApp Business Cloud API (Meta): a rota que o painel da
  Meta pede em "URL de callback". Publica, sem JWT — autenticada pelo verify token (GET) e pela
  assinatura HMAC-SHA256 do corpo com o App Secret (POST).

  **So valida e registra em log.** Nada e gravado, nenhuma mensagem e enviada ou respondida.
  O log leva apenas tipo do evento, id da conta e quantidades — nunca telefone, nome ou texto.
  Lembretes, aviso de agendamento e atendimento de duvidas ficam para as proximas fatias.

  **Desligado por padrao.** Liga por instancia com `WHATSAPP_VERIFY_TOKEN` e
  `WHATSAPP_APP_SECRET` no `.env` da VPS; sem os dois a rota responde 404 e o resto do sistema
  nao muda. Passo a passo em `INFRA.md`. Sem migration.

- **Fila de Espera (`/paciente/fila`, `GET /api/fila-espera`).**
  Lista os pacientes com pedidos aguardando marcacao, **uma linha por paciente**, com os pedidos
  agrupados na linha. Filtros combinaveis: tipo (Consulta / Especialidade ou Exame ou
  Procedimento), especialidade, status (Pendente, Retorno, Retorno Policlinica), prioridade,
  tempo de espera (mais de 30, 60 ou 90 dias), periodo de cadastro e ordem (mais antigos ou mais
  recentes primeiro). Somente leitura: nao agenda, nao consome cota, nao muda status.

  **O filtro e por pedido, nao por paciente.** O paciente entra se tiver ao menos um pedido que
  bate; a linha mostra so esses pedidos e a espera exibida e a do mais antigo entre eles.

  **O tipo tem 2 opcoes, nao 4.** `ItemCategoria` so distingue `ESPECIALIDADE_MEDICA` de
  `EXAME_OU_PROCEDIMENTO`. Separar exame de procedimento exige migration e reclassificacao
  manual do catalogo — ficou fora desta entrega.

  **A espera vem de `solicitacao_especialidade.data_cadastro`, com duas limitacoes avisadas na
  tela:** pedidos anteriores a V62 tem a data da migracao (a coluna nasceu com `DEFAULT now()`),
  entao aparecem com espera menor que a real; e pedido em RETORNO conta desde o pedido original,
  porque nao ha registro de quando o status mudou.

  **GEL fica fora da fila.** Tem card e lista proprios; `status=GEL` na fila devolve 400.

  **Busca livre por nome, CPF ou CNS (`termo`).** Campo unico; nome por "contem", CPF e CNS so
  pelos digitos e parcial. CPF e CNS so sao comparados quando o termo e feito de digitos e
  pontuacao — "Maria 2" procura esse texto no nome, em vez de trazer todo CPF que contenha 2.
  `%` e `_` sao texto comum (`strpos`, nao `LIKE`), diferente das listas antigas. **O termo nao
  vai para a URL da tela**, ao contrario dos demais filtros: nome e CPF de paciente nao devem
  ficar em historico do navegador nem em link compartilhado. Os cards do dashboard nunca enviam
  termo, entao o numero do card continua batendo com a lista.

- **Menu: "Pacientes" virou grupo** com "Pacientes" e "Fila de Espera". O GESTOR ve o grupo so
  com a fila.

- **GESTOR abre a ficha do paciente em modo consulta.** `GET /api/solicitacoes/{id}` passou a
  aceitar GESTOR; a tela esconde as acoes de edicao para ele.

### Seguranca

- **Listas nominais de pacientes deixaram de ser publicas.** `GET /api/solicitacoes/buscar/**`
  e `GET /api/agendamentos/pendentes/**` estavam como `permitAll` e devolviam nome, CPF e CNS
  sem login. Os matchers sairam do `SecurityConfiguration` e os endpoints ganharam
  `@PreAuthorize`. A consulta publica do paciente continua so em `/api/solicitacoes/public/**`.
  `SegurancaEndpointsIT` (MockMvc) quebra o build se alguem reabrir.

- **`DELETE /api/agendamentos/{id}` e `PUT /api/especialidades/{id}` ganharam `@PreAuthorize`.**
  Nao tinham nenhum; o GESTOR, que so consulta, conseguia chama-los direto pela API.

### Correcoes

- **As listas que os cards do dashboard abrem nao tinham escopo de unidade.** O numero do card ja
  era o da unidade, mas a lista aberta (pendentes, agendados, concluidos, urgentes, GEL) trazia o
  municipio inteiro, e `/unidade/{id}` aceitava o id de outra unidade. Agora todas passam por
  `UnidadeAcessoService.escopoDeListagem`; pedir outra unidade devolve 403.

- **Quem nao tem unidade de lotacao deixou de ver o municipio inteiro nessas telas.** Para
  listagem e contagem nominal, perfil que nao e ADMIN/GESTOR e nao tem lotacao recebe lista vazia
  e cards zerados, com um aviso para procurar o administrador. Excecao: COORD_TRANSPORTE sem
  lotacao mantem a visao global. `contextoDe` (escrita e acesso por id) nao mudou.

- **O numero do card passou a bater com a lista que ele abre.** "Pendentes" e "Urgencia /
  Emergencia" contam **pacientes** (mesma query da fila), nao pedidos — o valor exibido cai.
  O resumo ganhou os campos `pacientesPendentes`, `pacientesUrgentes` e
  `pacientesPendentesPorUnidade`; os campos antigos continuam no contrato.

- **Agendar varios itens da mesma ficha de uma vez falhava para a unidade com cota.** Ao marcar
  dois ou mais exames, procedimentos ou consultas no mesmo agendamento, a tela mostrava um erro
  sem explicacao sempre que a ficha tinha outro item fora do lote. Era
  `LazyInitializationException`: o consumo da cota do 1o item limpa o contexto de persistencia e o
  2o item era procurado numa lista ja desanexada. ADMIN e GESTOR nao eram afetados, nem o
  agendamento de um item so. Nada muda em cota, teto ou contrato; continua tudo-ou-nada.

---

## [1.7] — 2026-09-25

### Novidades

- **Cadastro de estabelecimento executante, com dados vindos do CNES.**
  `unidade` ganhou `tipo` (`SOLICITANTE` / `EXECUTANTE` / `AMBOS`) e os campos cadastrais do
  CNES (CNPJ, razao social, nome fantasia, bairro, CEP, numero, e-mail). O backend consulta
  `apidadosabertos.saude.gov.br/cnes/estabelecimentos/{cnes}` e devolve a ficha pronta; o
  operador confere e salva.

  **Estendeu `unidade` em vez de criar entidade de prestador.** Cota, fila, dashboard, relatorio
  e escopo de acesso por unidade ja filtram por `unidade_id` — uma tabela paralela obrigaria a
  duplicar essa logica inteira. Toda unidade existente virou `AMBOS` na V86, entao nada mudou de
  comportamento; restringir depois, unidade a unidade, e seguro.

  **404 nao e 503.** CNES inexistente responde 404 no DATASUS e virou 404 na tela ("confira o
  numero"), separado de instabilidade do servico, que vira 503 com o cadastro manual seguindo
  disponivel. Sem a distincao, quem digitava o numero errado lia "servico indisponivel" e ficava
  tentando de novo.

  **O codigo IBGE do municipio tem 6 digitos** na API, sem o verificador: Sao Felipe e `292910`,
  nao `2929107`. Passar 7 devolve lista vazia com HTTP 200 — falha silenciosa, por isso a
  normalizacao mora no `CnesService` e nao na tela. O CNPJ tambem vem em dois campos
  (`numero_cnpj` no privado, `numero_cnpj_entidade` na unidade publica); ler so um devolve nulo
  em metade dos casos.

- **Profissional com CPF e multiplos vinculos (executante x CBO).**
  O campo unico `profissional.unidade` nao descrevia a realidade: o mesmo medico atende na
  policlinica, no hospital e numa USF, com ocupacao possivelmente diferente em cada lugar. Agora
  existe `profissional_vinculo` (profissional x unidade x CBO), e
  `GET /api/profissionais/executante/{id}/ativos` responde "quem atende aqui" — a consulta que a
  abertura de agenda vai usar.

  `profissional.unidade` **continua populado** e marcado como `@Deprecated`: a V88 copiou cada
  valor para um vinculo (CBO nulo, origem `MANUAL`) e as telas atuais seguem lendo do campo
  antigo. Remover junto com a criacao do vinculo quebraria producao.

  A tabela `cbo` nasceu **vazia, sem seed**. Digitar codigos a mao entrega dado que ninguem
  confere: CBO errado rotula o profissional com a ocupacao de outra pessoa e nada acusa o erro.
  A carga vem do proprio arquivo do DATASUS (upsert por codigo) ou de cadastro avulso.

- **Importacao de profissionais do CNES por arquivo, em duas etapas.**
  `POST /api/profissionais/importar-cnes` le o CSV da *Extracao de dados de profissional* do
  portal do CNES e **devolve o que encontrou sem gravar nada**, com a situacao de cada linha
  (`NOVO_PROFISSIONAL`, `NOVO_VINCULO`, `JA_EXISTE`, `SEM_UNIDADE`, `INVALIDA`).
  `POST /api/profissionais/importar-cnes/confirmar` grava so as linhas marcadas, reconferindo
  tudo — a previa informa a tela, nao autoriza a gravacao.

  **Por arquivo porque nao existe API.** Todas as rotas de profissional do DATASUS respondem 404
  (`/cnes/profissionais`, `/cnes/vinculos`, `/cnes/equipes`, `/cnes/ocupacoes` e o sub-recurso do
  estabelecimento); os servicos internos de `cnes.datasus.gov.br` responderam 503. O SOAP exige
  credencial que a SMS precisa solicitar ao DATASUS.

  **A importacao e idempotente:** subir o mesmo arquivo duas vezes nao duplica profissional
  (dedupe por CPF) nem vinculo (dedupe pela tripla). Isso importa porque a coordenacao reimporta
  o arquivo inteiro quando muda uma linha.

  **Linha ruim nao derruba o lote:** ela e pulada com aviso nominal ("Linha 37 ignorada: CPF com
  3 digitos"). Abortar as 400 linhas por causa de uma obrigaria a coordenacao a editar o CSV para
  conseguir importar as outras 399.

  O leitor e deliberadamente tolerante, porque o arquivo nao tem contrato: descobre o separador,
  casa as colunas por lista de apelidos (`CO_CPF`, `CPF`, `NU_CPF`...), decodifica ISO-8859-1 ou
  UTF-8 conforme os bytes, pula o preambulo do portal e **repoe zeros a esquerda** de CPF, CNES e
  CBO — quem abre o CSV no Excel para conferir perde esses zeros, e sem repor o CPF `01234567890`
  viraria um profissional novo a cada importacao.

  **A reposicao para em 3 zeros.** Ela conserta o dano do Excel; alem disso, fabricaria dado:
  um CPF truncado em `123` viraria `00000000123`, passaria na validacao de formato e criaria
  profissional com documento inventado. Acima do limite o valor segue cru e a previa recusa a
  linha mostrando o que estava no arquivo.

- **Novo perfil GESTOR.**
  Acompanha indicadores, relatorios e desempenho das unidades. **Nao monta cota nem
  configura agenda** — leitura, e so de agregados. Ve: Indicadores, Dashboard global,
  Relatorios, Solicitacoes por Profissional, saldo de cota da unidade e contagem de
  atendimentos por grupo/data.

  **Fora de proposito, e por isso negado:** a lista de pacientes da agenda
  (`/especialidades/listar/pacientes/por/grupo`) expoe nome, CPF e CNS de todos os
  pacientes do dia. Metrica de desempenho nao precisa de PII, entao o GESTOR recebeu a
  contagem (agregada) e nao a listagem.

  GESTOR e **global** mesmo tendo unidade de lotacao — o papel e comparar unidades, e
  restringi-lo a propria lotacao esvaziaria isso. E a unica excecao, junto com ADMIN, a
  regra de "tem unidade ⇒ restrito".

- **Multiplos perfis por usuario, com alternancia.**
  Um usuario pode ter varios perfis liberados e trocar entre eles pelo menu do usuario.
  A alternancia **nao soma acessos**: vale um perfil de cada vez, e o perfil em uso
  define tanto as permissoes quanto o escopo de unidade.

  | Conceito | Onde vive | Papel |
  |---|---|---|
  | Principal | `usuarios.cargo` | O do login; fallback de token sem claim |
  | Concedidos | `usuario_perfis` (nova) | O que a pessoa *pode* assumir |
  | Ativo | claim `perfilAtivo` do JWT | O que vale **nesta** requisicao |

  `POST /api/users/me/perfil` confere se o perfil esta concedido e devolve um token novo.
  Nada e gravado: sair e entrar volta ao principal, e a troca vale por sessao — nao
  muda o perfil nos outros dispositivos da pessoa.

  **A conferencia acontece na requisicao, nao na emissao:** o `JwtAuthenticationFilter`
  revalida o `perfilAtivo` contra `usuario_perfis` a cada request. Retirar um perfil de
  alguem tem efeito **imediato** — o token que ja esta na mao da pessoa para de valer
  para aquele perfil e cai no principal, sem esperar as 2h de expiracao.

  **O escopo de unidade passou a seguir o perfil ativo.** `UnidadeAcessoService` lia
  `usuarios.cargo`; agora resolve o perfil efetivo pelas authorities da requisicao
  (reconferidas contra os perfis concedidos). Sem isso, quem tem ADMIN e ADMIN_UNIDADE
  continuaria global ao alternar para ADMIN_UNIDADE e a alternancia seria decorativa.

- **Rotulos de perfil em um lugar so** (`ROTULO_PERFIL`, no `menuConfig.js`).
  O cadastro de usuario, o modal de edicao e a listagem repetiam a mesma lista de cargos
  em tres lugares, e ja estavam fora de sincronia: `ADMIN_UNIDADE` faltava no modal de
  edicao, entao editar um administrador de unidade pelo modal trocava o cargo dele sem
  querer. Agora os tres leem a mesma fonte.

### Correções

- **Upload acima do limite virava erro generico.** `MaxUploadSizeExceededException` nao tinha
  handler: o corpo padrao do Spring nao traz `message` (`server.error.include-message=never`),
  entao quem subia um CSV grande lia uma falha sem causa. Agora e 400 dizendo o limite (5MB) e o
  que fazer (exportar um recorte por estabelecimento).

- **Listas por cargo ignoravam perfil secundario.** `listarRoleMedico`,
  `listarRoleEnfermeiro` e afins comparavam so `usuarios.cargo`. Com multiplos perfis,
  quem tem MEDICO como perfil secundario nao apareceria na lista de medicos; passaram a
  considerar os perfis concedidos.

### Banco de dados

- **V86** — `unidade.tipo` (default `AMBOS`, com CHECK) + dados cadastrais do CNES; indice
  `ix_unidade_tipo_ativo` para o combo de executantes.
- **V87** — tabela `cbo` (codigo unico de 6 digitos), sem seed.
- **V88** — `profissional.cpf` com UNIQUE **parcial** (`WHERE cpf IS NOT NULL`, para os cadastros
  antigos sem CPF conviverem) + tabela `profissional_vinculo`, com unicidade da tripla via
  `COALESCE(cbo_id, -1)`: no Postgres dois `NULL` sao distintos, e sem o COALESCE o indice
  deixaria passar N vinculos duplicados sem CBO. O backfill copia `profissional.unidade_id` para
  vinculo (CBO nulo, origem `MANUAL`) e usa `NOT EXISTS`, entao e reaplicavel.

  Consequencia no codigo: conferir se o vinculo existe exige **duas** consultas (`...AndCboId` e
  `...AndCboIsNull`), porque `cbo_id = NULL` nunca casa em SQL.

**Validacao (V1 -> V88 em banco limpo, Postgres 17.2):** as 88 migracoes aplicam do zero, o
contexto Spring sobe com `ddl-auto=validate` contra o schema resultante, e os comportamentos
foram conferidos em SQL — backfill idempotente, vinculo duplicado sem CBO recusado pelo indice
COALESCE, CPF repetido recusado, dois profissionais sem CPF aceitos, RESTRICT barrando exclusao
de unidade e de CBO em uso, CASCADE levando os vinculos do profissional excluido.

| Migration | Descrição |
|---|---|
| V85 | `GESTOR` na constraint `usuarios_cargo_check`; tabela `usuario_perfis` + backfill |

**Seguranca em producao:** V85 e **aditiva**. `usuarios.cargo` nao e removida nem
alterada; a constraint so **amplia** a lista de valores aceitos (nenhuma linha existente
pode violar). O backfill insere em `usuario_perfis` exatamente o cargo que cada usuario
ja tem, com `ON CONFLICT DO NOTHING` — rodar de novo nao duplica nada. Sem o backfill um
usuario ficaria sem perfil concedido; ainda assim `getPerfisConcedidos()` garante o
principal no conjunto, entao mesmo uma base nao migrada mantem o acesso.

### Testes

**82 testes** no total (eram 72), todos passando contra PostgreSQL real.

| Suite | Qtd | O que cobre |
|---|---|---|
| `PerfilMultiploIT` | 10 | **PostgreSQL real**: escopo global do GESTOR, troca para perfil concedido/negado, perfil revogado perdendo efeito no token ja emitido, token antigo sem claim, usuario legado sem `usuario_perfis`, escopo seguindo o perfil ativo, exigencia de lotacao para ADMIN_UNIDADE secundario |

`UnidadeAcessoServiceTest` (9) passa sem alteracao — e a suite que cobre a resolucao de
escopo por unidade, justamente o ponto mexido para o perfil ativo valer. Nenhuma das
outras 8 suites precisou de ajuste.

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

- **Agenda do Dia passa a ser por Unidade, nao mais uma unica agenda fixa do "hospital".**
  `especialidades/listar|contar/pacientes/por/grupo` filtravam por
  `agendamento_solicitacao.local_agendamento_id = 3` — uma linha fixa de
  `local_agendamento` (tabela de **destinos externos** de referencia: Hospital Roberto
  Santos, Policlinica Reconvale...), sem relacao nenhuma com `Unidade`. Todo usuario
  autenticado via a mesma lista, de qualquer unidade, com **zero controle de acesso** (os
  dois endpoints nao tinham `@PreAuthorize`). Agora o filtro e `solicitacao.unidade_id`: a
  mesma `Unidade` ja usada em cotas e no perfil `ADMIN_UNIDADE`. Usuario Padrao e demais
  perfis restritos veem so a propria unidade de lotacao, automaticamente — sem escolha, e
  sem como ver ou alterar a de outra unidade trocando o parametro (`UnidadeAcessoService`
  recusa com 403). Só o ADMIN global tem um seletor de unidade nas telas
  `/agendas/[grupo]` e `/dashboard/procedimentos*`, para ver ou modificar a agenda de
  qualquer unidade; sem escolher uma, a consulta e recusada com 400 em vez de devolver
  tudo misturado.

  `PACIENTE` saiu do menu "Agendas" e do `@PreAuthorize` dos dois endpoints: essa agenda
  lista nome/CPF/CNS de **todos** os pacientes do dia da unidade — dado operacional, nao
  o do proprio paciente logado. Antes, qualquer conta com perfil Paciente enxergava essa
  lista inteira.

  > **Atencao no deploy:** contas com perfil Usuario Padrao **sem** Unidade de Saude
  > vinculada (campo ja existente em `/admin/cadastrar-usuario`, so nao vinha sendo usado
  > por esse perfil) passam a ver uma mensagem pedindo para vincular a unidade, em vez da
  > agenda. Verifique/atribua a unidade de cada Usuario Padrao em `/admin/listar-usuarios`
  > antes de subir, ou o operador fica temporariamente sem a tela ate o vinculo ser feito.

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

**72 testes**, em quatro niveis:

| Suite | Qtd | O que cobre |
|---|---|---|
| `CotaUnidadeValidacaoTest` | 14 | Validacoes de cadastro: titular/escopo exclusivos, grupo vazio, duplicidade, periodo |
| `CotaUnidadeSimulacaoTest` | 16 | Sequencias (5 vagas -> 6o bloqueado), saldo compartilhado do grupo de especialidades, cancelamento, concorrencia (20 threads / 5 vagas -> exatamente 5 passam), legado |
| `UnidadeAcessoServiceTest` | 9 | Segregacao por unidade, `ADMIN_UNIDADE` sem lotacao, acesso cruzado |
| `SolicitacaoAcessoLegadoTest` | 6 | Registros antigos: orfaos acessiveis/editaveis, edicao sem os campos novos |
| `CotaUnidadeIntegracaoIT` | 15 | **PostgreSQL real**: JPQL de consumo/estorno, cota por grupo de especialidades, CHECKs de titular e escopo, saldo |
| `AgendamentoCotaFluxoIT` | 6 | **PostgreSQL real**, fluxo de /agendar: unidade estoura a cota no 6o agendamento, cancelamento devolve a vaga (inclusive com 2 especialidades no mesmo agendamento — diagnostico do 409 relatado em producao, nao reproduziu), ADMIN global isento, legado sem unidade |
| `AgendaPorUnidadeIT` | 5 | **PostgreSQL real**: agenda do dia filtrada por `solicitacao.unidade_id` — cada unidade ve so os proprios pacientes, operador restrito nao ve outra unidade nem trocando o parametro, ADMIN escolhe a unidade e e recusado sem escolher, paciente so aparece com agendamento (nao so AGUARDANDO) |
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
