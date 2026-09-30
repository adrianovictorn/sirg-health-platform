package io.github.regulacao_marcarcao.regulacao_marcacao.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.cota.CotaUnidadeCreateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.cota.CotaUnidadeSaldoDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.cota.CotaUnidadeUpdateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.cota.CotaUnidadeViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.AgendaOcorrencia;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.CotaUnidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Especialidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.GrupoRelatorio;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Profissional;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Unidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.OrigemCotaEnum;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.TipoPeriodoCota;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.CotaUnidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.EspecialidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.GrupoRelatorioRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.LocalAgendamentoRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.ProfissionalRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UnidadeRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

/**
 * Cotas de atendimento.
 *
 * <h3>Duas dimensoes independentes</h3>
 *
 * <b>Titular</b> — de quem e a cota: uma Unidade <b>ou</b> um grupo de unidades
 * (pool compartilhado entre as unidades membros). Exatamente um dos dois.
 *
 * <b>Escopo</b> — o que a cota limita: uma Especialidade, <b>ou</b> um grupo de
 * especialidades, <b>ou</b> nada (cota geral, vale para qualquer especialidade).
 * No maximo um.
 *
 * <h3>Cota por grupo de especialidades</h3>
 * Existe para evitar cadastrar cota uma especialidade por vez — o grupo
 * "Laboratorio" sozinho tem 172. A cota do grupo e um <b>saldo unico
 * compartilhado</b> por todas as especialidades dele: "Unidade A / Laboratorio /
 * 10" significa 10 exames de laboratorio no mes, somando todos os tipos.
 *
 * <h3>Todas as cotas aplicaveis incidem juntas</h3>
 * Se mais de uma cota cobre o caso, <b>todas</b> precisam ter saldo. Vale entre
 * periodos (MENSAL + DATA), entre titulares (unidade + grupo de unidades) e entre
 * escopos (especialidade + grupo de especialidades) — permitindo configurar
 * "100 exames de laboratorio no mes, sendo no maximo 10 de Hemograma".
 *
 * <h3>Cota ausente = sem restricao</h3>
 * Nenhuma cota cadastrada para o caso significa agendamento livre, preservando o
 * comportamento das unidades que nunca tiveram cota configurada.
 *
 * <h3>Concorrencia</h3>
 * O consumo usa um UPDATE condicional atomico
 * ({@link CotaUnidadeRepository#consumirVaga}), entao duas operacoes simultaneas
 * nao conseguem ambas ler o mesmo saldo e estourar o limite.
 */
@Service
@RequiredArgsConstructor
public class CotaUnidadeService {

    private static final DateTimeFormatter PERIODO_MENSAL = DateTimeFormatter.ofPattern("yyyy-MM");

    private final CotaUnidadeRepository cotaRepository;
    private final UnidadeRepository unidadeRepository;
    private final GrupoRelatorioRepository grupoRelatorioRepository;
    private final EspecialidadeRepository especialidadeRepository;
    private final ProfissionalRepository profissionalRepository;
    private final LocalAgendamentoRepository localAgendamentoRepository;

    // ------------------------------------------------------------------
    // CRUD
    // ------------------------------------------------------------------

    @Transactional
    public CotaUnidadeViewDTO criar(CotaUnidadeCreateDTO dto) {
        TipoPeriodoCota tipo = dto.tipoPeriodo() != null ? dto.tipoPeriodo() : TipoPeriodoCota.MENSAL;

        // --- titular: exatamente um ---
        boolean temUnidade = dto.unidadeId() != null;
        boolean temGrupoUnidades = dto.grupoUnidadesId() != null;
        if (temUnidade == temGrupoUnidades) {
            throw new IllegalArgumentException(
                    "Informe exatamente um titular para a cota: unidadeId OU grupoUnidadesId.");
        }

        // --- escopo: no maximo um ---
        if (dto.especialidadeId() != null && dto.grupoEspecialidadesId() != null) {
            throw new IllegalArgumentException(
                    "Informe apenas um escopo: especialidadeId OU grupoEspecialidadesId "
                            + "(deixe ambos vazios para cota geral).");
        }

        if (tipo == TipoPeriodoCota.MENSAL) {
            if (dto.periodo() == null || !dto.periodo().matches("\\d{4}-\\d{2}")) {
                throw new IllegalArgumentException("Periodo invalido. Use o formato YYYY-MM (ex: 2026-05).");
            }
        } else if (dto.dataEspecifica() == null) {
            throw new IllegalArgumentException("Data especifica e obrigatoria para cota por data.");
        }

        if (dto.quantidadeTotal() == null || dto.quantidadeTotal() < 0) {
            throw new IllegalArgumentException("A quantidade total deve ser maior ou igual a zero.");
        }

        CotaUnidade cota = new CotaUnidade();

        if (temUnidade) {
            cota.setUnidade(unidadeRepository.findById(dto.unidadeId())
                    .orElseThrow(() -> new EntityNotFoundException("Unidade nao encontrada.")));
        } else {
            cota.setGrupoUnidades(buscarGrupo(dto.grupoUnidadesId(), "Grupo de unidades"));
        }

        if (dto.especialidadeId() != null) {
            cota.setEspecialidade(especialidadeRepository.findById(dto.especialidadeId())
                    .orElseThrow(() -> new EntityNotFoundException("Especialidade nao encontrada.")));
        } else if (dto.grupoEspecialidadesId() != null) {
            GrupoRelatorio grupoEsp = buscarGrupo(dto.grupoEspecialidadesId(), "Grupo de especialidades");
            if (especialidadeRepository.countByGrupoRelatorioId(grupoEsp.getId()) == 0) {
                throw new IllegalArgumentException(
                        "O grupo '" + grupoEsp.getNome() + "' nao possui especialidades vinculadas, "
                                + "entao uma cota para ele nao limitaria nada.");
            }
            cota.setGrupoEspecialidades(grupoEsp);
        }

        aplicarEspelhoAtendimento(cota, tipo, dto.profissionalId(), dto.localAgendamentoId(),
                dto.horarioDinamico(), dto.tempoMedioAtendimentoMinutos(), dto.horaInicial(), dto.horaFinal(),
                dto.diasSemana());
        exigirCotaInexistente(cota, tipo, dto.periodo(), dto.dataEspecifica(), null);

        cota.setTipoPeriodo(tipo);
        cota.setPeriodo(tipo == TipoPeriodoCota.MENSAL ? dto.periodo() : null);
        cota.setDataEspecifica(tipo == TipoPeriodoCota.DATA ? dto.dataEspecifica() : null);
        cota.setQuantidadeTotal(dto.quantidadeTotal());
        cota.setQuantidadeUtilizada(0);
        cota.setAtivo(true);
        return CotaUnidadeViewDTO.from(cotaRepository.save(cota));
    }

    private static final Map<DayOfWeek, String> SIGLA_DIA = Map.of(
            DayOfWeek.MONDAY, "SEG",
            DayOfWeek.TUESDAY, "TER",
            DayOfWeek.WEDNESDAY, "QUA",
            DayOfWeek.THURSDAY, "QUI",
            DayOfWeek.FRIDAY, "SEX",
            DayOfWeek.SATURDAY, "SAB",
            DayOfWeek.SUNDAY, "DOM");

    /** Mesmo parser de {@code AgendaService#validarDiasSemana} — duplicado de proposito (ver V95, decisao de nao acoplar os dois services). */
    private Set<DayOfWeek> validarDiasSemana(List<String> diasSemana) {
        Set<DayOfWeek> resultado = new LinkedHashSet<>();
        for (String sigla : diasSemana) {
            DayOfWeek dia = SIGLA_DIA.entrySet().stream()
                    .filter(e -> e.getValue().equalsIgnoreCase(sigla))
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Dia da semana invalido: '" + sigla + "'. Use SEG,TER,QUA,QUI,SEX,SAB,DOM."));
            resultado.add(dia);
        }
        return resultado;
    }

    /**
     * Espelho de atendimento (V92 + V95): profissional executante, local e
     * horario, todos opcionais — cota geral ou de laboratorio continua sem
     * eles. Cota DATA usa a data unica como referencia de "quando"; cota
     * MENSAL usa {@code diasSemana} (V95) para o mesmo papel — por isso
     * profissional/local/horario numa cota MENSAL exigem diasSemana (sem
     * data unica, precisa de algo que diga quando o profissional atende).
     * diasSemana sozinho (sem profissional) tambem e aceito, so para indicar
     * os dias de atendimento gerais da cota.
     */
    private void aplicarEspelhoAtendimento(CotaUnidade cota, TipoPeriodoCota tipo,
            Long profissionalId, Long localAgendamentoId, boolean horarioDinamico,
            Integer tempoMedioAtendimentoMinutos, java.time.LocalTime horaInicial, java.time.LocalTime horaFinal,
            List<String> diasSemana) {

        boolean temEspelho = profissionalId != null || localAgendamentoId != null
                || horarioDinamico || horaInicial != null || horaFinal != null;
        boolean temDiasSemana = diasSemana != null && !diasSemana.isEmpty();

        if (temDiasSemana && tipo != TipoPeriodoCota.MENSAL) {
            throw new IllegalArgumentException(
                    "Dias da semana so se aplicam a cota do tipo MENSAL (cota DATA ja e um dia especifico).");
        }
        if (temEspelho && tipo == TipoPeriodoCota.MENSAL && !temDiasSemana) {
            throw new IllegalArgumentException(
                    "Profissional, local ou horario numa cota MENSAL exigem os dias da semana de atendimento.");
        }
        if (temEspelho && tipo != TipoPeriodoCota.DATA && tipo != TipoPeriodoCota.MENSAL) {
            throw new IllegalArgumentException(
                    "Profissional, local ou horario exigem cota do tipo DATA ou MENSAL com dias da semana.");
        }

        cota.setDiasSemana(temDiasSemana
                ? String.join(",", validarDiasSemana(diasSemana).stream().map(SIGLA_DIA::get).toList())
                : null);

        if (horarioDinamico) {
            if (horaInicial == null || horaFinal == null) {
                throw new IllegalArgumentException(
                        "Horario dinamico exige hora inicial e hora final, "
                                + "para dividir o periodo entre as vagas.");
            }
            if (!horaFinal.isAfter(horaInicial)) {
                throw new IllegalArgumentException("A hora final deve ser posterior a hora inicial.");
            }
        } else {
            if ((horaInicial == null) != (horaFinal == null)) {
                throw new IllegalArgumentException(
                        "Informe hora inicial e hora final juntas, ou nenhuma das duas.");
            }
            if (horaInicial != null && !horaFinal.isAfter(horaInicial)) {
                throw new IllegalArgumentException("A hora final deve ser posterior a hora inicial.");
            }
        }

        cota.setProfissionalExecutante(profissionalId != null
                ? profissionalRepository.findById(profissionalId)
                        .orElseThrow(() -> new EntityNotFoundException("Profissional nao encontrado."))
                : null);
        cota.setLocalAgendamento(localAgendamentoId != null
                ? localAgendamentoRepository.findById(localAgendamentoId)
                        .orElseThrow(() -> new EntityNotFoundException("Local de agendamento nao encontrado."))
                : null);
        cota.setHorarioDinamico(horarioDinamico);
        cota.setTempoMedioAtendimentoMinutos(horarioDinamico ? tempoMedioAtendimentoMinutos : null);
        cota.setHoraInicial(horaInicial);
        cota.setHoraFinal(horaFinal);
    }

    /**
     * Horario calculado para a vaga de numero {@code posicao} (1-based, a
     * quantidadeUtilizada da cota apos o consumo desta vaga) de uma cota com
     * horario dinamico — divide [horaInicial, horaFinal] em quantidadeTotal-1
     * partes iguais (quantidadeTotal=1 usa horaInicial direto). Ex.: periodo
     * 07h-11h com 5 vagas gera 07h, 08h, 09h, 10h, 11h.
     *
     * <p>Devolve {@code null} para cota sem horario dinamico (nada a calcular
     * — a tela usa o periodo so como referencia e o operador informa a hora a
     * mao, se quiser).
     */
    public java.time.LocalTime calcularHorarioSlot(CotaUnidade cota, int posicao) {
        if (!cota.isHorarioDinamico() || cota.getHoraInicial() == null || cota.getHoraFinal() == null) {
            return null;
        }
        int total = cota.getQuantidadeTotal() != null ? cota.getQuantidadeTotal() : 1;
        int indice = Math.max(0, Math.min(posicao, total) - 1);
        if (total <= 1) {
            return cota.getHoraInicial();
        }
        long duracaoMinutos = java.time.Duration.between(cota.getHoraInicial(), cota.getHoraFinal()).toMinutes();
        long passoMinutos = Math.round((double) duracaoMinutos / (total - 1));
        return cota.getHoraInicial().plusMinutes(passoMinutos * indice);
    }

    /**
     * Cota materializada por uma ocorrencia de agenda (V90) — nao passa pelas
     * validacoes de {@link #criar}, que pressupoe entrada direta do operador
     * (ex.: "titular exatamente um" ja vem resolvido de quem chama). Nao
     * duplica o motor de consumo/estorno: a cota gerada usa exatamente os
     * mesmos {@code consumirVaga}/{@code devolverVaga} de uma cota manual.
     */
    @Transactional
    public CotaUnidade criarParaAgenda(AgendaOcorrencia ocorrencia, Unidade unidadeSolicitante,
            Especialidade especialidade, GrupoRelatorio grupoEspecialidades, int quantidade) {
        CotaUnidade cota = new CotaUnidade();
        cota.setUnidade(unidadeSolicitante);
        cota.setEspecialidade(especialidade);
        cota.setGrupoEspecialidades(grupoEspecialidades);
        cota.setTipoPeriodo(TipoPeriodoCota.DATA);
        cota.setDataEspecifica(ocorrencia.getData());

        exigirCotaInexistente(cota, TipoPeriodoCota.DATA, null, ocorrencia.getData(), null);

        cota.setQuantidadeTotal(quantidade);
        cota.setQuantidadeUtilizada(0);
        cota.setAtivo(true);
        cota.setOrigem(OrigemCotaEnum.AGENDA);
        cota.setAgendaOcorrencia(ocorrencia);
        return cotaRepository.save(cota);
    }

    private GrupoRelatorio buscarGrupo(Long id, String rotulo) {
        return grupoRelatorioRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(rotulo + " nao encontrado."));
    }

    /**
     * Recusa duplicidade antes de o indice unico disparar um erro cru de banco.
     *
     * A verificacao roda em memoria sobre as cotas do titular (volume pequeno) em
     * vez de uma consulta com {@code :param IS NULL}. Esse padrao ja causou
     * incidente em producao: o PostgreSQL nao infere o tipo do parametro quando ele
     * so aparece dentro do IS NULL, e a consulta quebra assim que o driver a promove
     * a prepared statement (ver CHANGELOG 1.4).
     */
    private void exigirCotaInexistente(CotaUnidade nova, TipoPeriodoCota tipo,
            String periodo, LocalDate data, Long excluirId) {
        List<CotaUnidade> doTitular = nova.getUnidade() != null
                ? cotaRepository.findByUnidadeId(nova.getUnidade().getId())
                : cotaRepository.findByGrupoUnidadesId(nova.getGrupoUnidades().getId());

        boolean jaExiste = doTitular.stream()
                .filter(c -> excluirId == null || !c.getId().equals(excluirId))
                .anyMatch(c ->
                c.getTipoPeriodo() == tipo
                && idOuMenosUm(c.getEspecialidade()) == idOuMenosUm(nova.getEspecialidade())
                && idOuMenosUm(c.getGrupoEspecialidades()) == idOuMenosUm(nova.getGrupoEspecialidades())
                // V94: profissional executante entra na chave de duplicidade — permite
                // duas cotas da mesma especialidade/data para profissionais diferentes
                // (ex.: dois ginecologistas no mesmo dia), mantendo no maximo uma cota
                // SEM profissional por data (mesmo comportamento de antes da V92/V94).
                && idOuMenosUm(c.getProfissionalExecutante()) == idOuMenosUm(nova.getProfissionalExecutante())
                && (tipo == TipoPeriodoCota.MENSAL
                        ? periodo.equals(c.getPeriodo())
                        : data.equals(c.getDataEspecifica())));

        if (jaExiste) {
            throw new IllegalArgumentException(
                    "Ja existe cota cadastrada para esse titular, escopo e periodo.");
        }
    }

    private long idOuMenosUm(Especialidade e) {
        return e != null ? e.getId() : -1L;
    }

    private long idOuMenosUm(GrupoRelatorio g) {
        return g != null ? g.getId() : -1L;
    }

    private long idOuMenosUm(Profissional p) {
        return p != null ? p.getId() : -1L;
    }

    /**
     * Edicao completa de uma cota MANUAL (V90). Antes so {@code quantidadeTotal}
     * e {@code ativo} eram editaveis; agora titular, escopo e periodo tambem —
     * com a mesma validacao de {@link #criar}, porque o DTO e uma substituicao
     * total (a tela sempre reenvia o registro inteiro).
     *
     * <p>Cota gerada por agenda ({@code origem = AGENDA}) recusa este endpoint:
     * edita-se a agenda de origem, nunca a cota diretamente.
     */
    @Transactional
    public CotaUnidadeViewDTO atualizar(Long id, CotaUnidadeUpdateDTO dto) {
        CotaUnidade cota = cotaRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Cota nao encontrada."));

        if (cota.getOrigem() == OrigemCotaEnum.AGENDA) {
            throw new IllegalStateException(
                    "Esta cota foi gerada por uma agenda — edite a agenda de origem.");
        }

        TipoPeriodoCota tipo = dto.tipoPeriodo() != null ? dto.tipoPeriodo() : TipoPeriodoCota.MENSAL;

        boolean temUnidade = dto.unidadeId() != null;
        boolean temGrupoUnidades = dto.grupoUnidadesId() != null;
        if (temUnidade == temGrupoUnidades) {
            throw new IllegalArgumentException(
                    "Informe exatamente um titular para a cota: unidadeId OU grupoUnidadesId.");
        }

        if (dto.especialidadeId() != null && dto.grupoEspecialidadesId() != null) {
            throw new IllegalArgumentException(
                    "Informe apenas um escopo: especialidadeId OU grupoEspecialidadesId "
                            + "(deixe ambos vazios para cota geral).");
        }

        if (tipo == TipoPeriodoCota.MENSAL) {
            if (dto.periodo() == null || !dto.periodo().matches("\\d{4}-\\d{2}")) {
                throw new IllegalArgumentException("Periodo invalido. Use o formato YYYY-MM (ex: 2026-05).");
            }
        } else if (dto.dataEspecifica() == null) {
            throw new IllegalArgumentException("Data especifica e obrigatoria para cota por data.");
        }

        if (dto.quantidadeTotal() == null || dto.quantidadeTotal() < 0) {
            throw new IllegalArgumentException("A quantidade total deve ser maior ou igual a zero.");
        }
        if (dto.quantidadeTotal() < cota.getQuantidadeUtilizada()) {
            throw new IllegalArgumentException(
                    "A nova quantidade (" + dto.quantidadeTotal() + ") e menor que o total ja utilizado ("
                            + cota.getQuantidadeUtilizada() + "). Cancele agendamentos antes de reduzir a cota.");
        }

        if (temUnidade) {
            cota.setUnidade(unidadeRepository.findById(dto.unidadeId())
                    .orElseThrow(() -> new EntityNotFoundException("Unidade nao encontrada.")));
            cota.setGrupoUnidades(null);
        } else {
            cota.setGrupoUnidades(buscarGrupo(dto.grupoUnidadesId(), "Grupo de unidades"));
            cota.setUnidade(null);
        }

        if (dto.especialidadeId() != null) {
            cota.setEspecialidade(especialidadeRepository.findById(dto.especialidadeId())
                    .orElseThrow(() -> new EntityNotFoundException("Especialidade nao encontrada.")));
            cota.setGrupoEspecialidades(null);
        } else if (dto.grupoEspecialidadesId() != null) {
            GrupoRelatorio grupoEsp = buscarGrupo(dto.grupoEspecialidadesId(), "Grupo de especialidades");
            if (especialidadeRepository.countByGrupoRelatorioId(grupoEsp.getId()) == 0) {
                throw new IllegalArgumentException(
                        "O grupo '" + grupoEsp.getNome() + "' nao possui especialidades vinculadas, "
                                + "entao uma cota para ele nao limitaria nada.");
            }
            cota.setGrupoEspecialidades(grupoEsp);
            cota.setEspecialidade(null);
        } else {
            cota.setEspecialidade(null);
            cota.setGrupoEspecialidades(null);
        }

        aplicarEspelhoAtendimento(cota, tipo, dto.profissionalId(), dto.localAgendamentoId(),
                dto.horarioDinamico(), dto.tempoMedioAtendimentoMinutos(), dto.horaInicial(), dto.horaFinal(),
                dto.diasSemana());
        exigirCotaInexistente(cota, tipo, dto.periodo(), dto.dataEspecifica(), id);

        cota.setTipoPeriodo(tipo);
        cota.setPeriodo(tipo == TipoPeriodoCota.MENSAL ? dto.periodo() : null);
        cota.setDataEspecifica(tipo == TipoPeriodoCota.DATA ? dto.dataEspecifica() : null);
        cota.setQuantidadeTotal(dto.quantidadeTotal());
        cota.setAtivo(dto.ativo());
        return CotaUnidadeViewDTO.from(cotaRepository.save(cota));
    }

    // ------------------------------------------------------------------
    // Consumo / estorno
    // ------------------------------------------------------------------

    /**
     * Valida e consome as cotas aplicaveis ao atendimento (overload sem
     * profissional/cota especifica — ver {@link #incrementarUtilizacao(Long, Long, LocalDate, Long)}).
     */
    @Transactional
    public void incrementarUtilizacao(Long unidadeId, Long especialidadeId, LocalDate dataAtual) {
        incrementarUtilizacao(unidadeId, especialidadeId, dataAtual, null);
    }

    /**
     * Valida e consome a(s) cota(s) aplicaveis ao atendimento (V100: pools
     * isolados por profissional).
     *
     * <p>Com {@code cotaFixadaId} informado (profissional escolhido pela unidade),
     * consome SOMENTE aquela cota — nunca as de outros profissionais para a mesma
     * especialidade/data. Sem {@code cotaFixadaId}, consome somente as cotas SEM
     * profissional definido (cota "geral"), ignorando as de profissionais
     * especificos — evita consumir "de carona" numa cota que nao foi escolhida.
     * Entre cotas gerais concorrentes (ex.: MENSAL + DATA da mesma unidade), o
     * comportamento e o de sempre: todas incidem juntas.
     *
     * <p>Se qualquer cota a consumir estiver esgotada, a transacao e abortada e
     * nenhum consumo persiste — inclusive os ja feitos neste laco.
     */
    @Transactional
    public void incrementarUtilizacao(Long unidadeId, Long especialidadeId, LocalDate dataAtual, Long cotaFixadaId) {
        if (unidadeId == null || dataAtual == null) {
            return;
        }
        for (CotaUnidade cota : cotasParaConsumo(cotasAplicaveis(unidadeId, especialidadeId, dataAtual), cotaFixadaId)) {
            if (cotaRepository.consumirVaga(cota.getId()) == 0) {
                // Nenhuma linha atualizada: saldo esgotado.
                //
                // A cota e RECARREGADA antes de montar a mensagem: consumirVaga usa
                // @Modifying(clearAutomatically = true), que limpa o contexto de
                // persistencia e desanexa `cota`. Usar a instancia antiga aqui
                // estouraria LazyInitializationException ao ler unidade/grupos/
                // especialidade (todos @ManyToOne LAZY) — e o operador receberia um
                // 500 opaco em vez de saber que a cota acabou.
                CotaUnidade atual = cotaRepository.findById(cota.getId()).orElse(cota);
                throw new IllegalStateException(mensagemEsgotada(atual));
            }
        }
    }

    /**
     * Devolve as vagas consumidas por um agendamento cancelado, excluido ou
     * remanejado (overload sem profissional/cota especifica — ver
     * {@link #estornarUtilizacao(Long, Long, LocalDate, Long)}).
     */
    @Transactional
    public void estornarUtilizacao(Long unidadeId, Long especialidadeId, LocalDate dataOriginal) {
        estornarUtilizacao(unidadeId, especialidadeId, dataOriginal, null);
    }

    /**
     * Devolve as vagas consumidas por um agendamento cancelado, excluido ou
     * remanejado (V100: espelha exatamente o isolamento por profissional de
     * {@link #incrementarUtilizacao(Long, Long, LocalDate, Long)} — {@code
     * cotaFixadaId} deve ser o id gravado em {@code SolicitacaoEspecialidade.cotaUnidade}
     * no momento do consumo original). Sem isto a cota "vaza": o saldo nunca
     * volta e a unidade fica travada por vagas que nao correspondem a
     * atendimento nenhum.
     */
    @Transactional
    public void estornarUtilizacao(Long unidadeId, Long especialidadeId, LocalDate dataOriginal, Long cotaFixadaId) {
        if (unidadeId == null || dataOriginal == null) {
            return;
        }
        for (CotaUnidade cota : cotasParaConsumo(cotasAplicaveis(unidadeId, especialidadeId, dataOriginal), cotaFixadaId)) {
            cotaRepository.devolverVaga(cota.getId());
        }
    }

    /**
     * Filtra, entre as cotas aplicaveis, quais devem ser consumidas/estornadas
     * (V100). Ver {@link #incrementarUtilizacao(Long, Long, LocalDate, Long)}.
     */
    private List<CotaUnidade> cotasParaConsumo(List<CotaUnidade> aplicaveis, Long cotaFixadaId) {
        if (cotaFixadaId != null) {
            CotaUnidade fixada = aplicaveis.stream()
                    .filter(c -> c.getId().equals(cotaFixadaId))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "A cota escolhida (id=" + cotaFixadaId + ") nao e aplicavel a este agendamento/data."));
            return List.of(fixada);
        }
        return aplicaveis.stream().filter(c -> c.getProfissionalExecutante() == null).toList();
    }

    /**
     * Filtra, entre as cotas aplicaveis, quais contam para o saldo exibido
     * (V100) — mesma regra de isolamento de {@link #cotasParaConsumo}, mas por
     * profissional em vez de cota especifica (a tela sabe o profissional
     * escolhido antes de saber o id exato da cota).
     */
    private List<CotaUnidade> cotasParaSaldo(List<CotaUnidade> aplicaveis, Long profissionalId) {
        if (profissionalId != null) {
            return aplicaveis.stream()
                    .filter(c -> c.getProfissionalExecutante() != null
                            && c.getProfissionalExecutante().getId().equals(profissionalId))
                    .toList();
        }
        return aplicaveis.stream().filter(c -> c.getProfissionalExecutante() == null).toList();
    }

    /**
     * True se ha ao menos uma cota ATIVA aplicavel ao caso — usado para bloquear
     * ADMIN_UNIDADE sem cota nenhuma (ver {@code AgendamentoService}). Deliberadamente
     * isolado de {@link #incrementarUtilizacao}: nao mexe no motor de saldo, so
     * responde "existe cota", sem consumir nada. Uma cota existente mas esgotada
     * ainda conta como "existe" — quem bloqueia por saldo zero e o consumo, nao esta
     * checagem.
     */
    @Transactional(readOnly = true)
    public boolean existeCotaAtivaAplicavel(Long unidadeId, Long especialidadeId, LocalDate data) {
        if (unidadeId == null || data == null) {
            return false;
        }
        return !cotasAplicaveis(unidadeId, especialidadeId, data).isEmpty();
    }

    /**
     * Resolve qual cota sera efetivamente usada num agendamento — desde a V100,
     * chamada ANTES de {@link #incrementarUtilizacao}, cujo resultado (id da
     * cota, ou {@code null}) e passado adiante para decidir o que consumir
     * (ver {@link #cotasParaConsumo}): com profissional resolvido, so aquela
     * cota; sem profissional, so as cotas gerais. Tambem fica registrada em
     * {@code SolicitacaoEspecialidade.cotaUnidade} para auditoria (ex.: provar
     * qual dos dois profissionais atendeu o paciente). Nao consome nada por si
     * so — e {@code @Transactional(readOnly = true)}.
     *
     * <p>Sem cota com profissional aplicavel: devolve {@code null} (nada a rastrear).
     * Uma so: resolve sozinho. Mais de uma: exige {@code cotaEscolhidaId} explicito —
     * a unidade precisa escolher qual profissional atende, nao ha como adivinhar.
     */
    @Transactional(readOnly = true)
    public CotaUnidade resolverCotaParaAgendamento(Long unidadeId, Long especialidadeId, LocalDate data,
            Long cotaEscolhidaId) {
        List<CotaUnidade> aplicaveis = cotasAplicaveis(unidadeId, especialidadeId, data);

        if (cotaEscolhidaId != null) {
            return aplicaveis.stream()
                    .filter(c -> c.getId().equals(cotaEscolhidaId))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "A cota escolhida (id=" + cotaEscolhidaId + ") nao e aplicavel a este agendamento."));
        }

        List<CotaUnidade> comProfissional = aplicaveis.stream()
                .filter(c -> c.getProfissionalExecutante() != null)
                .toList();

        if (comProfissional.size() > 1) {
            throw new IllegalArgumentException(
                    "Ha mais de uma cota com profissional para esta especialidade e data — "
                            + "escolha uma informando cotaUnidadeId.");
        }
        return comProfissional.isEmpty() ? null : comProfissional.get(0);
    }

    /** Cotas aplicaveis a um atendimento, expostas para a unidade ver o "espelho" antes de agendar. */
    @Transactional(readOnly = true)
    public List<CotaUnidadeViewDTO> listarCotasAplicaveis(Long unidadeId, Long especialidadeId, LocalDate data) {
        return cotasAplicaveis(unidadeId, especialidadeId, data).stream()
                .map(CotaUnidadeViewDTO::from)
                .toList();
    }

    /**
     * Cotas ativas que incidem sobre um atendimento da unidade, na especialidade e
     * data informadas — as duas dimensoes resolvidas numa unica consulta.
     */
    private List<CotaUnidade> cotasAplicaveis(Long unidadeId, Long especialidadeId, LocalDate data) {
        Unidade unidade = unidadeRepository.findById(unidadeId).orElse(null);
        if (unidade == null) {
            return List.of();
        }

        Long grupoUnidadesId = unidade.getGrupoRelatorio() != null
                ? unidade.getGrupoRelatorio().getId()
                : null;

        // Grupo ao qual a especialidade pertence — e ele que a cota "de grupo" cobre.
        Long grupoEspecialidadesId = especialidadeId == null ? null
                : especialidadeRepository.findById(especialidadeId)
                        .map(e -> e.getGrupoRelatorio() != null ? e.getGrupoRelatorio().getId() : null)
                        .orElse(null);

        List<CotaUnidade> encontradas = cotaRepository.buscarCotasAplicaveis(
                unidadeId,
                grupoUnidadesId,
                especialidadeId,
                grupoEspecialidadesId,
                data.format(PERIODO_MENSAL),
                data);

        // V95: cota MENSAL com dias da semana marcados so incide nos dias
        // marcados — sem isso, ela apareceria "aplicavel" em qualquer dia do
        // mes, mesmo fora do dia de atendimento do profissional. Cota MENSAL
        // sem diasSemana (o "geral" de sempre) continua sem filtro nenhum.
        DayOfWeek diaDaSemana = data.getDayOfWeek();
        return encontradas.stream()
                .filter(c -> c.getDiasSemana() == null || c.getDiasSemana().isBlank()
                        || validarDiasSemana(List.of(c.getDiasSemana().split(","))).contains(diaDaSemana))
                .toList();
    }

    private String mensagemEsgotada(CotaUnidade cota) {
        String titular = cota.getUnidade() != null
                ? "a unidade " + cota.getUnidade().getNome()
                : "o grupo de unidades " + cota.getGrupoUnidades().getNome();

        String alvo;
        if (cota.getEspecialidade() != null) {
            alvo = " em " + cota.getEspecialidade().getNome();
        } else if (cota.getGrupoEspecialidades() != null) {
            alvo = " no grupo " + cota.getGrupoEspecialidades().getNome();
        } else {
            alvo = "";
        }

        String quando = cota.getTipoPeriodo() == TipoPeriodoCota.MENSAL
                ? "no periodo " + cota.getPeriodo()
                : "na data " + cota.getDataEspecifica();

        return "Cota esgotada para " + titular + alvo + " " + quando
                + " (" + cota.getQuantidadeUtilizada() + "/" + cota.getQuantidadeTotal() + ").";
    }

    // ------------------------------------------------------------------
    // Consultas
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<CotaUnidadeViewDTO> listarPorUnidade(Long unidadeId) {
        return cotaRepository.findByUnidadeId(unidadeId).stream()
                .map(CotaUnidadeViewDTO::from).toList();
    }

    @Transactional(readOnly = true)
    public List<CotaUnidadeViewDTO> listarPorGrupo(Long grupoUnidadesId) {
        return cotaRepository.findByGrupoUnidadesId(grupoUnidadesId).stream()
                .map(CotaUnidadeViewDTO::from).toList();
    }

    @Transactional(readOnly = true)
    public List<CotaUnidadeViewDTO> listarPorUnidadeEPeriodo(Long unidadeId, String periodo) {
        return cotaRepository.findByUnidadeIdAndPeriodo(unidadeId, periodo).stream()
                .map(CotaUnidadeViewDTO::from).toList();
    }

    @Transactional(readOnly = true)
    public CotaUnidadeViewDTO buscarPorId(Long id) {
        return cotaRepository.findById(id)
                .map(CotaUnidadeViewDTO::from)
                .orElseThrow(() -> new EntityNotFoundException("Cota nao encontrada."));
    }

    @Transactional(readOnly = true)
    public List<CotaUnidadeViewDTO> listarTodas() {
        return cotaRepository.findAll().stream().map(CotaUnidadeViewDTO::from).toList();
    }

    /**
     * Saldo efetivamente disponivel para a unidade na especialidade/periodo
     * (overload sem profissional — ver {@link #consultarSaldo(Long, Long, String, Long)}).
     */
    @Transactional(readOnly = true)
    public CotaUnidadeSaldoDTO consultarSaldo(Long unidadeId, Long especialidadeId, String periodo) {
        return consultarSaldo(unidadeId, especialidadeId, periodo, null);
    }

    /**
     * Saldo efetivamente disponivel para a unidade na especialidade/periodo
     * (V100: isolado por profissional quando informado — mesma regra de
     * {@link #cotasParaSaldo}).
     *
     * Considera todas as cotas incidentes (do profissional, ou as gerais, sem
     * misturar): o saldo real e o <b>menor</b> entre elas, porque basta uma
     * estar esgotada para bloquear o agendamento. Sem cota aplicavel, devolve
     * saldo ilimitado (quantidades nulas + disponivel = true), que e o
     * comportamento historico.
     */
    @Transactional(readOnly = true)
    public CotaUnidadeSaldoDTO consultarSaldo(Long unidadeId, Long especialidadeId, String periodo, Long profissionalId) {
        LocalDate referencia = LocalDate.parse(periodo + "-01");
        List<CotaUnidade> cotas = cotasParaSaldo(cotasAplicaveis(unidadeId, especialidadeId, referencia), profissionalId);

        if (cotas.isEmpty()) {
            Unidade unidade = unidadeRepository.findById(unidadeId)
                    .orElseThrow(() -> new EntityNotFoundException("Unidade nao encontrada."));
            Especialidade esp = especialidadeId != null
                    ? especialidadeRepository.findById(especialidadeId).orElse(null)
                    : null;
            return new CotaUnidadeSaldoDTO(
                    unidade.getId(), unidade.getNome(),
                    esp != null ? esp.getId() : null,
                    esp != null ? esp.getNome() : null,
                    periodo, null, null, null, true, false);
        }

        CotaUnidade maisRestritiva = cotas.stream()
                .min(Comparator.comparingInt(c -> c.getQuantidadeTotal() - c.getQuantidadeUtilizada()))
                .orElseThrow();

        int saldo = maisRestritiva.getQuantidadeTotal() - maisRestritiva.getQuantidadeUtilizada();
        boolean daUnidade = maisRestritiva.getUnidade() != null;

        Long idEscopo = null;
        String nomeEscopo = null;
        if (maisRestritiva.getEspecialidade() != null) {
            idEscopo = maisRestritiva.getEspecialidade().getId();
            nomeEscopo = maisRestritiva.getEspecialidade().getNome();
        } else if (maisRestritiva.getGrupoEspecialidades() != null) {
            nomeEscopo = "Grupo " + maisRestritiva.getGrupoEspecialidades().getNome();
        }

        return new CotaUnidadeSaldoDTO(
                daUnidade ? maisRestritiva.getUnidade().getId() : unidadeId,
                daUnidade ? maisRestritiva.getUnidade().getNome() : maisRestritiva.getGrupoUnidades().getNome(),
                idEscopo,
                nomeEscopo,
                periodo,
                maisRestritiva.getQuantidadeTotal(),
                maisRestritiva.getQuantidadeUtilizada(),
                saldo,
                saldo > 0,
                !daUnidade);
    }

    /**
     * Saldo para uma DATA especifica (overload sem profissional — ver
     * {@link #consultarSaldoPorData(Long, Long, LocalDate, Long)}).
     */
    @Transactional(readOnly = true)
    public CotaUnidadeSaldoDTO consultarSaldoPorData(Long unidadeId, Long especialidadeId, LocalDate data) {
        return consultarSaldoPorData(unidadeId, especialidadeId, data, null);
    }

    /**
     * Saldo para uma DATA especifica, em vez do periodo mensal inteiro (V100:
     * isolado por profissional quando informado — mesma regra de {@link
     * #cotasParaSaldo}).
     *
     * {@link #consultarSaldo} sempre usa o dia 1 do mes como referencia para
     * {@link #cotasAplicaveis}, entao uma cota do tipo DATA so "casaria" se fosse
     * cadastrada para o dia 1 — na pratica nunca, para um agendamento num dia
     * qualquer. Este metodo roda a mesma consulta com o dia real do agendamento,
     * sem alterar {@link #consultarSaldo} (usado hoje pela tela de agendamento
     * para o saldo do mes, que continua exibido).
     */
    @Transactional(readOnly = true)
    public CotaUnidadeSaldoDTO consultarSaldoPorData(Long unidadeId, Long especialidadeId, LocalDate data, Long profissionalId) {
        List<CotaUnidade> cotas = cotasParaSaldo(cotasAplicaveis(unidadeId, especialidadeId, data), profissionalId);
        String referencia = data.toString();

        if (cotas.isEmpty()) {
            Unidade unidade = unidadeRepository.findById(unidadeId)
                    .orElseThrow(() -> new EntityNotFoundException("Unidade nao encontrada."));
            Especialidade esp = especialidadeId != null
                    ? especialidadeRepository.findById(especialidadeId).orElse(null)
                    : null;
            return new CotaUnidadeSaldoDTO(
                    unidade.getId(), unidade.getNome(),
                    esp != null ? esp.getId() : null,
                    esp != null ? esp.getNome() : null,
                    referencia, null, null, null, true, false);
        }

        CotaUnidade maisRestritiva = cotas.stream()
                .min(Comparator.comparingInt(c -> c.getQuantidadeTotal() - c.getQuantidadeUtilizada()))
                .orElseThrow();

        int saldo = maisRestritiva.getQuantidadeTotal() - maisRestritiva.getQuantidadeUtilizada();
        boolean daUnidade = maisRestritiva.getUnidade() != null;

        Long idEscopo = null;
        String nomeEscopo = null;
        if (maisRestritiva.getEspecialidade() != null) {
            idEscopo = maisRestritiva.getEspecialidade().getId();
            nomeEscopo = maisRestritiva.getEspecialidade().getNome();
        } else if (maisRestritiva.getGrupoEspecialidades() != null) {
            nomeEscopo = "Grupo " + maisRestritiva.getGrupoEspecialidades().getNome();
        }

        return new CotaUnidadeSaldoDTO(
                daUnidade ? maisRestritiva.getUnidade().getId() : unidadeId,
                daUnidade ? maisRestritiva.getUnidade().getNome() : maisRestritiva.getGrupoUnidades().getNome(),
                idEscopo,
                nomeEscopo,
                referencia,
                maisRestritiva.getQuantidadeTotal(),
                maisRestritiva.getQuantidadeUtilizada(),
                saldo,
                saldo > 0,
                !daUnidade);
    }
}
