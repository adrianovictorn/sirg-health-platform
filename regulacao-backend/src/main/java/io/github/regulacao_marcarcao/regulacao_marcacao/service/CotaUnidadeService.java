package io.github.regulacao_marcarcao.regulacao_marcacao.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.cota.CotaUnidadeCreateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.cota.CotaUnidadeSaldoDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.cota.CotaUnidadeUpdateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.cota.CotaUnidadeViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.CotaUnidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Especialidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.GrupoRelatorio;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Unidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.TipoPeriodoCota;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.CotaUnidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.EspecialidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.GrupoRelatorioRepository;
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

        exigirCotaInexistente(cota, tipo, dto.periodo(), dto.dataEspecifica());

        cota.setTipoPeriodo(tipo);
        cota.setPeriodo(tipo == TipoPeriodoCota.MENSAL ? dto.periodo() : null);
        cota.setDataEspecifica(tipo == TipoPeriodoCota.DATA ? dto.dataEspecifica() : null);
        cota.setQuantidadeTotal(dto.quantidadeTotal());
        cota.setQuantidadeUtilizada(0);
        cota.setAtivo(true);
        return CotaUnidadeViewDTO.from(cotaRepository.save(cota));
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
            String periodo, LocalDate data) {
        List<CotaUnidade> doTitular = nova.getUnidade() != null
                ? cotaRepository.findByUnidadeId(nova.getUnidade().getId())
                : cotaRepository.findByGrupoUnidadesId(nova.getGrupoUnidades().getId());

        boolean jaExiste = doTitular.stream().anyMatch(c ->
                c.getTipoPeriodo() == tipo
                && idOuMenosUm(c.getEspecialidade()) == idOuMenosUm(nova.getEspecialidade())
                && idOuMenosUm(c.getGrupoEspecialidades()) == idOuMenosUm(nova.getGrupoEspecialidades())
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

    @Transactional
    public CotaUnidadeViewDTO atualizar(Long id, CotaUnidadeUpdateDTO dto) {
        CotaUnidade cota = cotaRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Cota nao encontrada."));
        if (dto.quantidadeTotal() < 0) {
            throw new IllegalArgumentException("A quantidade total deve ser maior ou igual a zero.");
        }
        if (dto.quantidadeTotal() < cota.getQuantidadeUtilizada()) {
            throw new IllegalArgumentException(
                    "A nova quantidade (" + dto.quantidadeTotal() + ") e menor que o total ja utilizado ("
                            + cota.getQuantidadeUtilizada() + "). Cancele agendamentos antes de reduzir a cota.");
        }
        cota.setQuantidadeTotal(dto.quantidadeTotal());
        cota.setAtivo(dto.ativo());
        return CotaUnidadeViewDTO.from(cotaRepository.save(cota));
    }

    // ------------------------------------------------------------------
    // Consumo / estorno
    // ------------------------------------------------------------------

    /**
     * Valida e consome todas as cotas aplicaveis ao atendimento.
     *
     * Se qualquer uma estiver esgotada, a transacao e abortada e nenhum consumo
     * persiste — inclusive os ja feitos neste laco.
     */
    @Transactional
    public void incrementarUtilizacao(Long unidadeId, Long especialidadeId, LocalDate dataAtual) {
        if (unidadeId == null || dataAtual == null) {
            return;
        }
        for (CotaUnidade cota : cotasAplicaveis(unidadeId, especialidadeId, dataAtual)) {
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
     * remanejado. Sem isto a cota "vaza": o saldo nunca volta e a unidade fica
     * travada por vagas que nao correspondem a atendimento nenhum.
     */
    @Transactional
    public void estornarUtilizacao(Long unidadeId, Long especialidadeId, LocalDate dataOriginal) {
        if (unidadeId == null || dataOriginal == null) {
            return;
        }
        for (CotaUnidade cota : cotasAplicaveis(unidadeId, especialidadeId, dataOriginal)) {
            cotaRepository.devolverVaga(cota.getId());
        }
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

        return cotaRepository.buscarCotasAplicaveis(
                unidadeId,
                grupoUnidadesId,
                especialidadeId,
                grupoEspecialidadesId,
                data.format(PERIODO_MENSAL),
                data);
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
    public List<CotaUnidadeViewDTO> listarTodas() {
        return cotaRepository.findAll().stream().map(CotaUnidadeViewDTO::from).toList();
    }

    /**
     * Saldo efetivamente disponivel para a unidade na especialidade/periodo.
     *
     * Considera todas as cotas incidentes: o saldo real e o <b>menor</b> entre
     * elas, porque basta uma estar esgotada para bloquear o agendamento. Sem cota
     * configurada, devolve saldo ilimitado (quantidades nulas + disponivel = true),
     * que e o comportamento historico.
     */
    @Transactional(readOnly = true)
    public CotaUnidadeSaldoDTO consultarSaldo(Long unidadeId, Long especialidadeId, String periodo) {
        LocalDate referencia = LocalDate.parse(periodo + "-01");
        List<CotaUnidade> cotas = cotasAplicaveis(unidadeId, especialidadeId, referencia);

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
}
