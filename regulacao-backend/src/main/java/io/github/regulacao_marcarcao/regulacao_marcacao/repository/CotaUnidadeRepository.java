package io.github.regulacao_marcarcao.regulacao_marcacao.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.CotaUnidade;

@Repository
public interface CotaUnidadeRepository extends JpaRepository<CotaUnidade, Long>, JpaSpecificationExecutor<CotaUnidade> {

    List<CotaUnidade> findByUnidadeIdAndPeriodo(Long unidadeId, String periodo);

    List<CotaUnidade> findByUnidadeId(Long unidadeId);

    List<CotaUnidade> findByGrupoUnidadesId(Long grupoUnidadesId);

    List<CotaUnidade> findByGrupoEspecialidadesId(Long grupoEspecialidadesId);

    /** R10: cotas geradas por uma ocorrencia, para cancelar/estornar. */
    List<CotaUnidade> findByAgendaOcorrenciaId(Long agendaOcorrenciaId);

    /** R1: a cota de uma unidade especifica dentro de uma ocorrencia, para remanejar vagas. */
    Optional<CotaUnidade> findByAgendaOcorrenciaIdAndUnidadeId(Long agendaOcorrenciaId, Long unidadeId);

    // -----------------------------------------------------------------------
    // Cotas aplicaveis a um atendimento
    // -----------------------------------------------------------------------

    /**
     * Todas as cotas ativas que incidem sobre um atendimento.
     *
     * A cota tem duas dimensoes independentes, e esta consulta resolve as duas de
     * uma vez — antes eram ate 8 consultas separadas, uma por combinacao:
     *
     * <ul>
     *   <li><b>Titular</b>: a propria unidade, ou o grupo de unidades a que ela
     *       pertence (pool compartilhado).</li>
     *   <li><b>Escopo</b>: a especialidade exata, o grupo de especialidades que a
     *       contem, ou a cota geral (sem escopo).</li>
     * </ul>
     *
     * Todas as que casarem sao devolvidas, porque <b>todas incidem juntas</b>:
     * havendo cota de Hemograma e cota do grupo Laboratorio, as duas precisam ter
     * saldo. O mesmo ja valia entre cota MENSAL e cota por DATA.
     *
     * Os LEFT JOIN sao obrigatorios: navegar direto (ex.: {@code c.unidade.id})
     * geraria INNER JOIN e descartaria justamente as linhas cujo FK e nulo — que
     * aqui sao os casos validos (cota de grupo tem unidade_id nulo, cota geral tem
     * especialidade_id nulo).
     *
     * Parametros nulos simplesmente nao casam, sem precisar de consulta separada:
     * unidade sem grupo passa {@code grupoUnidadesId = null}, e a comparacao e falsa.
     *
     * {@code ORDER BY c.id}: sem ordenacao explicita a ordem de consumo entre cotas
     * concorrentes (ex.: cota de Hemograma + cota do grupo Laboratorio) dependia do
     * plano de execucao do banco — instavel entre versoes de indice (exposto pela
     * V94, que recriou {@code uk_cota_data}). Em producao isso nao muda o resultado
     * final (a transacao inteira desfaz em caso de erro), mas precisa ser
     * deterministico para o comportamento ser previsivel e testavel.
     */
    @Query("""
        SELECT c FROM CotaUnidade c
        LEFT JOIN c.unidade u
        LEFT JOIN c.grupoUnidades gu
        LEFT JOIN c.especialidade e
        LEFT JOIN c.grupoEspecialidades ge
        WHERE c.ativo = true
          AND (u.id = :unidadeId OR gu.id = :grupoUnidadesId)
          AND (
                e.id = :especialidadeId
             OR ge.id = :grupoEspecialidadesId
             OR (e.id IS NULL AND ge.id IS NULL)
          )
          AND (
                (c.tipoPeriodo = io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.TipoPeriodoCota.MENSAL
                 AND c.periodo = :periodo)
             OR (c.tipoPeriodo = io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.TipoPeriodoCota.DATA
                 AND c.dataEspecifica = :data)
          )
        ORDER BY c.id ASC
        """)
    List<CotaUnidade> buscarCotasAplicaveis(
            @Param("unidadeId") Long unidadeId,
            @Param("grupoUnidadesId") Long grupoUnidadesId,
            @Param("especialidadeId") Long especialidadeId,
            @Param("grupoEspecialidadesId") Long grupoEspecialidadesId,
            @Param("periodo") String periodo,
            @Param("data") LocalDate data);

    // -----------------------------------------------------------------------
    // Consumo atomico da cota
    // -----------------------------------------------------------------------

    /**
     * Consome uma vaga da cota de forma atomica.
     *
     * O incremento acontece direto no banco com a condicao de saldo embutida no
     * WHERE, entao duas requisicoes simultaneas nao conseguem enxergar o mesmo
     * saldo e ultrapassar o limite: a segunda simplesmente nao afeta linha
     * nenhuma. Retorna o numero de linhas atualizadas — 0 significa cota esgotada
     * (ou inativa), 1 significa vaga consumida.
     *
     * A coluna @Version nao e tocada de proposito: um bulk update JPQL ja ignora o
     * bloqueio otimista, e a condicao no WHERE e o que garante a corretude aqui.
     * O @Version continua valendo para as escritas via entidade (criar/atualizar cota).
     *
     * ATENCAO: {@code clearAutomatically} limpa o contexto de persistencia, entao
     * qualquer CotaUnidade carregada antes desta chamada fica DESANEXADA. Para ler
     * campos LAZY dela depois (unidade, grupos, especialidade) e preciso recarregar
     * — ver CotaUnidadeService#incrementarUtilizacao.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE CotaUnidade c SET c.quantidadeUtilizada = c.quantidadeUtilizada + 1 "
            + "WHERE c.id = :id AND c.ativo = true AND c.quantidadeUtilizada < c.quantidadeTotal")
    int consumirVaga(@Param("id") Long id);

    /**
     * Devolve uma vaga a cota (cancelamento/remanejamento de agendamento).
     * O guard {@code quantidadeUtilizada > 0} impede saldo negativo caso a mesma
     * operacao seja estornada duas vezes.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE CotaUnidade c SET c.quantidadeUtilizada = c.quantidadeUtilizada - 1 "
            + "WHERE c.id = :id AND c.quantidadeUtilizada > 0")
    int devolverVaga(@Param("id") Long id);

    // -----------------------------------------------------------------------
    // Migracao de data (CotaMigracaoDataService)
    // -----------------------------------------------------------------------

    /**
     * Carrega a cota com bloqueio pessimista de escrita: serializa a migracao com
     * outra migracao/edicao da mesma cota. O consumo atomico ({@link #consumirVaga})
     * nao passa por aqui e segue valendo.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM CotaUnidade c WHERE c.id = :id")
    Optional<CotaUnidade> findByIdParaMigracao(@Param("id") Long id);

    /**
     * Soma o total e o utilizado de uma cota que esta sendo unificada a esta.
     * Update direto, como o consumo: a corretude vem do WHERE/aritmetica no banco.
     * Limpa o contexto de persistencia — ver a ressalva de {@link #consumirVaga}.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE CotaUnidade c SET c.quantidadeTotal = c.quantidadeTotal + :total, "
            + "c.quantidadeUtilizada = c.quantidadeUtilizada + :utilizada WHERE c.id = :id")
    int absorver(@Param("id") Long id, @Param("total") int total, @Param("utilizada") int utilizada);

    // -----------------------------------------------------------------------
    // Listagem paginada de /admin/cotas (CotaListagemService)
    // -----------------------------------------------------------------------

    /**
     * Cotas de uma página já com tudo que {@code CotaUnidadeViewDTO.from} toca,
     * para não gerar N+1. A ordem NÃO é a da consulta: quem monta a página
     * reordena pelos ids escolhidos.
     */
    @Query("SELECT DISTINCT c FROM CotaUnidade c "
            + "LEFT JOIN FETCH c.unidade LEFT JOIN FETCH c.grupoUnidades "
            + "LEFT JOIN FETCH c.especialidade LEFT JOIN FETCH c.grupoEspecialidades "
            + "LEFT JOIN FETCH c.profissionalExecutante LEFT JOIN FETCH c.localAgendamento "
            + "LEFT JOIN FETCH c.agendaOcorrencia ao LEFT JOIN FETCH ao.agenda "
            + "WHERE c.id IN :ids")
    List<CotaUnidade> buscarComRelacionamentos(@Param("ids") Collection<Long> ids);

    /** Especialidades que alguma cota cobre, direta ou pelo grupo de especialidades. */
    @Query("SELECT e.id, e.nome FROM Especialidade e WHERE e.id IN "
            + "(SELECT c.especialidade.id FROM CotaUnidade c WHERE c.especialidade IS NOT NULL) "
            + "OR e.grupoRelatorio.id IN "
            + "(SELECT c.grupoEspecialidades.id FROM CotaUnidade c WHERE c.grupoEspecialidades IS NOT NULL)")
    List<Object[]> opcoesEspecialidades();

    /** Profissionais presentes em alguma cota (espelho de atendimento) ou na agenda da cota. */
    @Query("SELECT p.id, p.nome FROM Profissional p WHERE p.id IN "
            + "(SELECT c.profissionalExecutante.id FROM CotaUnidade c WHERE c.profissionalExecutante IS NOT NULL) "
            + "OR p.id IN (SELECT ag.profissional.id FROM CotaUnidade c JOIN c.agendaOcorrencia ao "
            + "JOIN ao.agenda ag)")
    List<Object[]> opcoesProfissionais();

    /** Executantes das agendas que geraram cotas. */
    @Query("SELECT u.id, u.nome FROM Unidade u WHERE u.id IN "
            + "(SELECT ag.estabelecimentoExecutante.id FROM CotaUnidade c JOIN c.agendaOcorrencia ao "
            + "JOIN ao.agenda ag)")
    List<Object[]> opcoesExecutantes();
}
