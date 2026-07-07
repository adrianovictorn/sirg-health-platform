# Changelog — SIRG (Sistema de Regulação)

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
