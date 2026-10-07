package io.github.regulacao_marcarcao.regulacao_marcacao.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.SolicitacaoEspecialidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.CustoIndicadorProjections.CoberturaCatalogo;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.CustoIndicadorProjections.EvolucaoMes;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.CustoIndicadorProjections.Faltas;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.CustoIndicadorProjections.Teto;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.CustoIndicadorProjections.TetoMes;

/**
 * Agregacoes dos indicadores de custo. Somente leitura e somente numeros:
 * nenhuma destas consultas devolve dado de paciente.
 *
 * <p>A evolucao mensal usa os mesmos criterios de {@link CustoPainelRepository}
 * (status AGENDADO / REALIZADO, valor gravado no agendamento, mes da data
 * agendada), para o mes X da serie ser o mesmo numero que o painel de custos
 * mostra para o mes X. Regra nova la precisa entrar aqui tambem
 * ({@code CustoIndicadoresIT} compara os dois).
 *
 * <p><b>Faltas e cancelamentos</b>: o sistema grava "faltou" como CANCELADO, e o
 * item mantem o agendamento e o valor da epoca. O que se mede e
 * {@code status IN ('CANCELADO', 'FALTOU')} com agendamento — nao da para
 * separar falta de cancelamento. Item que voltou para a fila (agendamento
 * excluido ou remarcado) perde o vinculo e nao entra.
 */
public interface CustoIndicadoresRepository extends Repository<SolicitacaoEspecialidade, Long> {

    String FALTA_OU_CANCELAMENTO = "se.status IN ('CANCELADO', 'FALTOU')";

    // JOIN (e nao LEFT JOIN) com o agendamento: tudo aqui e posicionado pela data agendada.
    String ORIGEM = """
            FROM solicitacao_especialidade se
            JOIN agendamento_solicitacao ag ON ag.id = se.agendamento_id
            JOIN solicitacao s ON s.id = se.solicitacao_id
            LEFT JOIN especialidade e ON e.id = se.especialidade_id
            LEFT JOIN unidade u ON u.id = s.unidade_id
            """;

    String NO_PERIODO_E_UNIDADE = """
            WHERE ag.data_agendada BETWEEN :dataDe AND :dataAte
              AND (CAST(:unidadeId AS bigint) IS NULL OR s.unidade_id = :unidadeId)
            """;

    String AGREGADOS_DE_FALTA = " COALESCE(SUM(se.valor_unitario_agendado), 0) AS valor,"
            + " COUNT(*) FILTER (WHERE se.valor_unitario_agendado IS NOT NULL) AS itensComValor,"
            + " COUNT(*) FILTER (WHERE se.valor_unitario_agendado IS NULL) AS itensSemValor ";

    /** Uma linha por mes da data agendada. Mes sem movimento nao vem. */
    @Query(value = """
            SELECT to_char(ag.data_agendada, 'YYYY-MM') AS mes,
                   COALESCE(SUM(se.valor_unitario_agendado) FILTER (WHERE se.status = 'AGENDADO'), 0) AS agendado,
                   COALESCE(SUM(se.valor_unitario_agendado) FILTER (WHERE se.status = 'REALIZADO'), 0) AS concluido,
                   COALESCE(SUM(se.valor_unitario_agendado) FILTER (WHERE se.status IN ('CANCELADO', 'FALTOU')), 0)
                       AS faltas,
                   COUNT(DISTINCT s.id) FILTER (WHERE se.status = 'REALIZADO'
                                                  AND se.valor_unitario_agendado IS NOT NULL) AS pacientesAtendidos
            """ + ORIGEM + NO_PERIODO_E_UNIDADE + " GROUP BY 1", nativeQuery = true)
    List<EvolucaoMes> evolucaoPorMes(
            @Param("unidadeId") Long unidadeId,
            @Param("dataDe") LocalDate dataDe,
            @Param("dataAte") LocalDate dataAte);

    @Query(value = "SELECT CAST(NULL AS bigint) AS id, CAST(NULL AS text) AS nome," + AGREGADOS_DE_FALTA
            + ORIGEM + NO_PERIODO_E_UNIDADE + " AND " + FALTA_OU_CANCELAMENTO, nativeQuery = true)
    Faltas faltasTotal(
            @Param("unidadeId") Long unidadeId,
            @Param("dataDe") LocalDate dataDe,
            @Param("dataAte") LocalDate dataAte);

    /** Item legado sem especialidade fica fora desta quebra (continua no total). */
    @Query(value = "SELECT e.id AS id, e.nome AS nome," + AGREGADOS_DE_FALTA
            + ORIGEM + NO_PERIODO_E_UNIDADE + " AND " + FALTA_OU_CANCELAMENTO
            + " AND e.id IS NOT NULL GROUP BY e.id, e.nome", nativeQuery = true)
    List<Faltas> faltasPorEspecialidade(
            @Param("unidadeId") Long unidadeId,
            @Param("dataDe") LocalDate dataDe,
            @Param("dataAte") LocalDate dataAte);

    /** Solicitacao sem unidade vinculada vem numa linha propria, com id e nome nulos. */
    @Query(value = "SELECT s.unidade_id AS id, MIN(u.nome) AS nome," + AGREGADOS_DE_FALTA
            + ORIGEM + NO_PERIODO_E_UNIDADE + " AND " + FALTA_OU_CANCELAMENTO
            + " GROUP BY s.unidade_id", nativeQuery = true)
    List<Faltas> faltasPorUnidade(
            @Param("unidadeId") Long unidadeId,
            @Param("dataDe") LocalDate dataDe,
            @Param("dataAte") LocalDate dataAte);

    @Query(value = """
            SELECT COUNT(*) AS ativas,
                   COUNT(*) FILTER (WHERE e.valor_unitario IS NULL) AS semPreco
            FROM especialidade e
            WHERE e.ativo = TRUE
            """, nativeQuery = true)
    CoberturaCatalogo coberturaDoCatalogo();

    /** Tetos ATIVOS dos meses pedidos — o mesmo recorte do cartao de teto do painel de custos. */
    @Query(value = """
            SELECT u.id AS unidadeId, u.nome AS unidadeNome, g.nome AS grupoNome, t.periodo AS periodo,
                   t.valor_total AS valorTotal, t.valor_utilizado AS valorUtilizado
            FROM teto_financeiro t
            JOIN unidade u ON u.id = t.unidade_id
            JOIN grupo_relatorio g ON g.id = t.grupo_especialidades_id
            WHERE t.ativo = TRUE
              AND t.periodo BETWEEN :mesDe AND :mesAte
              AND (CAST(:unidadeId AS bigint) IS NULL OR t.unidade_id = :unidadeId)
            ORDER BY t.periodo DESC, u.nome, g.nome
            """, nativeQuery = true)
    List<Teto> tetos(
            @Param("unidadeId") Long unidadeId,
            @Param("mesDe") String mesDe,
            @Param("mesAte") String mesAte);

    @Query(value = """
            SELECT t.periodo AS mes,
                   COALESCE(SUM(t.valor_total), 0) AS liberado,
                   COALESCE(SUM(t.valor_utilizado), 0) AS utilizado,
                   COUNT(*) AS tetos
            FROM teto_financeiro t
            WHERE t.ativo = TRUE
              AND t.periodo BETWEEN :mesDe AND :mesAte
              AND (CAST(:unidadeId AS bigint) IS NULL OR t.unidade_id = :unidadeId)
            GROUP BY t.periodo
            """, nativeQuery = true)
    List<TetoMes> tetosPorMes(
            @Param("unidadeId") Long unidadeId,
            @Param("mesDe") String mesDe,
            @Param("mesAte") String mesAte);
}
