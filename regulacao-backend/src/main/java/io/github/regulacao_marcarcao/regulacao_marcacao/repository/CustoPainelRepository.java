package io.github.regulacao_marcarcao.regulacao_marcacao.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.SolicitacaoEspecialidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.CustoPainelLinhaProjection;

/**
 * Agregacoes do painel de custos. Somente leitura e somente numeros: nenhuma
 * destas consultas devolve dado de paciente.
 *
 * <p>AGREGADOS, ORIGEM e FILTRO sao compartilhados pelas tres consultas de
 * proposito (mesmo motivo de FILA_FROM/FILA_FILTRO na fila de espera): o total,
 * a quebra por unidade e a quebra por especialidade precisam aplicar EXATAMENTE
 * o mesmo predicado, senao a soma das linhas deixa de bater com o total.
 *
 * <p>As tres visoes:
 * <ul>
 *   <li><b>estimado</b> — pedidos na fila (AGUARDANDO, RETORNO,
 *       RETORNO_POLICLINICA; GEL fica fora, como na fila de espera) x o preco
 *       ATUAL da especialidade. E a fila de hoje: o periodo nao se aplica.</li>
 *   <li><b>agendado</b> — itens AGENDADO com data agendada no periodo x o valor
 *       gravado no agendamento (V106).</li>
 *   <li><b>concluido</b> — itens REALIZADO com data agendada no periodo x o
 *       valor gravado no agendamento. Nao existe data de conclusao no sistema;
 *       a data agendada e a referencia.</li>
 * </ul>
 *
 * <p>Item sem preco (ou agendado antes da V106) nao soma zero: e contado a
 * parte em {@code ...SemPreco} / {@code ...SemValor}, para a tela dizer quantos
 * ficaram de fora.
 */
public interface CustoPainelRepository extends Repository<SolicitacaoEspecialidade, Long> {

    String NA_FILA = "se.status IN ('AGUARDANDO', 'RETORNO', 'RETORNO_POLICLINICA')";
    String NO_PERIODO = "ag.data_agendada BETWEEN :dataDe AND :dataAte";
    String AGENDADO = "se.status = 'AGENDADO' AND " + NO_PERIODO;
    String CONCLUIDO = "se.status = 'REALIZADO' AND " + NO_PERIODO;

    String AGREGADOS = " COALESCE(SUM(e.valor_unitario) FILTER (WHERE " + NA_FILA + "), 0) AS estimado,"
            + " COUNT(*) FILTER (WHERE " + NA_FILA + " AND e.valor_unitario IS NOT NULL) AS estimadoItens,"
            + " COUNT(*) FILTER (WHERE " + NA_FILA + " AND e.valor_unitario IS NULL) AS estimadoSemPreco,"
            + " COALESCE(SUM(se.valor_unitario_agendado) FILTER (WHERE " + AGENDADO + "), 0) AS agendado,"
            + " COUNT(*) FILTER (WHERE " + AGENDADO + " AND se.valor_unitario_agendado IS NOT NULL) AS agendadoItens,"
            + " COUNT(*) FILTER (WHERE " + AGENDADO + " AND se.valor_unitario_agendado IS NULL) AS agendadoSemValor,"
            + " COALESCE(SUM(se.valor_unitario_agendado) FILTER (WHERE " + CONCLUIDO + "), 0) AS concluido,"
            + " COUNT(*) FILTER (WHERE " + CONCLUIDO + " AND se.valor_unitario_agendado IS NOT NULL) AS concluidoItens,"
            + " COUNT(*) FILTER (WHERE " + CONCLUIDO + " AND se.valor_unitario_agendado IS NULL) AS concluidoSemValor ";

    String ORIGEM = """
            FROM solicitacao_especialidade se
            JOIN solicitacao s ON s.id = se.solicitacao_id
            LEFT JOIN especialidade e ON e.id = se.especialidade_id
            LEFT JOIN agendamento_solicitacao ag ON ag.id = se.agendamento_id
            LEFT JOIN unidade u ON u.id = s.unidade_id
            """;

    // CAST(:p AS tipo) IS NULL OR ...: sem o CAST o PostgreSQL nao consegue
    // inferir o tipo de um parametro nulo e a consulta falha em tempo de execucao.
    String FILTRO = """
            WHERE se.status IN ('AGUARDANDO', 'RETORNO', 'RETORNO_POLICLINICA', 'AGENDADO', 'REALIZADO')
              AND (CAST(:unidadeId AS bigint) IS NULL OR s.unidade_id = :unidadeId)
              AND (CAST(:grupoId AS bigint) IS NULL OR e.grupo_relatorio_id = :grupoId)
              AND (CAST(:categoria AS text) IS NULL OR e.categoria = :categoria)
            """;

    @Query(value = "SELECT CAST(NULL AS bigint) AS id, CAST(NULL AS text) AS nome, CAST(NULL AS text) AS codigoSus,"
            + AGREGADOS + ORIGEM + FILTRO, nativeQuery = true)
    CustoPainelLinhaProjection total(
            @Param("unidadeId") Long unidadeId,
            @Param("grupoId") Long grupoId,
            @Param("categoria") String categoria,
            @Param("dataDe") LocalDate dataDe,
            @Param("dataAte") LocalDate dataAte);

    /** Solicitacao sem unidade vinculada vem numa linha propria, com id e nome nulos. */
    @Query(value = "SELECT s.unidade_id AS id, MIN(u.nome) AS nome, CAST(NULL AS text) AS codigoSus,"
            + AGREGADOS + ORIGEM + FILTRO + " GROUP BY s.unidade_id", nativeQuery = true)
    List<CustoPainelLinhaProjection> porUnidade(
            @Param("unidadeId") Long unidadeId,
            @Param("grupoId") Long grupoId,
            @Param("categoria") String categoria,
            @Param("dataDe") LocalDate dataDe,
            @Param("dataAte") LocalDate dataAte);

    @Query(value = "SELECT e.id AS id, e.nome AS nome, e.codigo_sus AS codigoSus,"
            + AGREGADOS + ORIGEM + FILTRO + " AND e.id IS NOT NULL GROUP BY e.id, e.nome, e.codigo_sus",
            nativeQuery = true)
    List<CustoPainelLinhaProjection> porEspecialidade(
            @Param("unidadeId") Long unidadeId,
            @Param("grupoId") Long grupoId,
            @Param("categoria") String categoria,
            @Param("dataDe") LocalDate dataDe,
            @Param("dataAte") LocalDate dataAte);
}
