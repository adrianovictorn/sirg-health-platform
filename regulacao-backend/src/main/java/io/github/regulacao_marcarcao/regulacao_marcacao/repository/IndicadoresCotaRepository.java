package io.github.regulacao_marcarcao.regulacao_marcacao.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.CotaUnidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.CotaUtilizacaoProjection;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.OcupacaoCotaProjection;

/**
 * Agregacoes dos indicadores gerenciais de cota. Somente leitura: nao consome,
 * nao estorna e nao toca o motor de saldo ({@link CotaUnidadeRepository}).
 *
 * <p>Tres cuidados que definem o desenho:
 * <ul>
 *   <li><b>Nada de somar vagas de cotas diferentes.</b> Cotas de escopos e
 *       periodos diferentes incidem juntas sobre o mesmo agendamento (a mensal
 *       do grupo e a do dia da especialidade, por exemplo); somar os totais
 *       contaria a mesma vaga duas vezes. A utilizacao e a MEDIA da razao de
 *       cada cota, e MENSAL e DATA nunca se misturam.</li>
 *   <li><b>A ocupacao por profissional soma</b>, porque cota com profissional e
 *       um pool isolado (V100): cada agendamento consome uma so.</li>
 *   <li><b>O filtro de unidade inclui o pool do grupo dela</b>: a cota cujo
 *       titular e o grupo de unidades tambem limita aquela unidade.</li>
 * </ul>
 *
 * <p>Conta o que a cota registra em {@code quantidade_utilizada}. Agendamento
 * feito por ADMIN ou GESTOR nao consome cota e, portanto, nao aparece aqui.
 */
public interface IndicadoresCotaRepository extends Repository<CotaUnidade, Long> {

    String NO_PERIODO = """
            ((c.tipo_periodo = 'MENSAL' AND c.periodo BETWEEN :mesDe AND :mesAte)
              OR (c.tipo_periodo = 'DATA' AND c.data_especifica BETWEEN :dataDe AND :dataAte))
            """;

    String DA_UNIDADE = """
            (CAST(:unidadeId AS bigint) IS NULL
              OR c.unidade_id = :unidadeId
              OR c.grupo_unidades_id = (SELECT un.grupo_relatorio_id FROM unidade un WHERE un.id = :unidadeId))
            """;

    String FILTRO = " WHERE c.ativo = TRUE AND " + NO_PERIODO + " AND " + DA_UNIDADE;

    // Esgotada: sem vaga. Ociosa: nenhuma vaga usada e o periodo ja passou —
    // cota do mes corrente ou de data futura ainda pode ser usada.
    String AGREGADOS = """
             COUNT(*) AS cotas,
             COUNT(*) FILTER (WHERE c.quantidade_total > 0
                                AND c.quantidade_utilizada >= c.quantidade_total) AS esgotadas,
             COUNT(*) FILTER (WHERE c.quantidade_total > 0 AND c.quantidade_utilizada = 0
                                AND ((c.tipo_periodo = 'MENSAL' AND c.periodo < :mesAtual)
                                  OR (c.tipo_periodo = 'DATA' AND c.data_especifica < :hoje))) AS ociosas,
             CAST(AVG(CAST(c.quantidade_utilizada AS numeric) / c.quantidade_total)
                  FILTER (WHERE c.quantidade_total > 0) AS double precision) AS utilizacaoMedia
            """;

    /** Uma linha por tipo de periodo (MENSAL, DATA). */
    @Query(value = "SELECT c.tipo_periodo AS tipo, CAST(NULL AS bigint) AS unidadeId, CAST(NULL AS text) AS titular,"
            + AGREGADOS + " FROM cota_unidade c" + FILTRO + " GROUP BY c.tipo_periodo", nativeQuery = true)
    List<CotaUtilizacaoProjection> utilizacaoPorTipo(
            @Param("unidadeId") Long unidadeId,
            @Param("mesDe") String mesDe,
            @Param("mesAte") String mesAte,
            @Param("dataDe") LocalDate dataDe,
            @Param("dataAte") LocalDate dataAte,
            @Param("mesAtual") String mesAtual,
            @Param("hoje") LocalDate hoje);

    /**
     * Uma linha por tipo de periodo e titular. Titular e a unidade ou, quando
     * {@code unidadeId} vem nulo, o grupo de unidades (pool compartilhado).
     */
    @Query(value = """
            SELECT c.tipo_periodo AS tipo, c.unidade_id AS unidadeId,
                   COALESCE(MIN(u.nome), MIN(gu.nome)) AS titular,
            """ + AGREGADOS + """
            FROM cota_unidade c
            LEFT JOIN unidade u ON u.id = c.unidade_id
            LEFT JOIN grupo_relatorio gu ON gu.id = c.grupo_unidades_id
            """ + FILTRO + " GROUP BY c.tipo_periodo, c.unidade_id, c.grupo_unidades_id", nativeQuery = true)
    List<CotaUtilizacaoProjection> utilizacaoPorTitular(
            @Param("unidadeId") Long unidadeId,
            @Param("mesDe") String mesDe,
            @Param("mesAte") String mesAte,
            @Param("dataDe") LocalDate dataDe,
            @Param("dataAte") LocalDate dataAte,
            @Param("mesAtual") String mesAtual,
            @Param("hoje") LocalDate hoje);

    /** Vagas ofertadas x agendadas por profissional executante da cota. */
    @Query(value = """
            SELECT p.id AS id, p.nome AS nome, COUNT(*) AS cotas,
                   COALESCE(SUM(c.quantidade_total), 0) AS ofertadas,
                   COALESCE(SUM(c.quantidade_utilizada), 0) AS agendadas
            FROM cota_unidade c
            JOIN profissional p ON p.id = c.profissional_id
            """ + FILTRO + " GROUP BY p.id, p.nome", nativeQuery = true)
    List<OcupacaoCotaProjection> ocupacaoPorProfissional(
            @Param("unidadeId") Long unidadeId,
            @Param("mesDe") String mesDe,
            @Param("mesAte") String mesAte,
            @Param("dataDe") LocalDate dataDe,
            @Param("dataAte") LocalDate dataAte);

    /** O mesmo, por faixa de horario ({@code nome} = "HH:MM–HH:MM"), so das cotas com profissional e horario. */
    // LEFT(CAST(hora AS text), 5) em vez de to_char(..., 'HH24:MI'): os dois-pontos
    // do formato seriam lidos como parametro nomeado da consulta.
    @Query(value = """
            SELECT CAST(NULL AS bigint) AS id,
                   LEFT(CAST(c.hora_inicial AS text), 5) || '–' || LEFT(CAST(c.hora_final AS text), 5) AS nome,
                   COUNT(*) AS cotas,
                   COALESCE(SUM(c.quantidade_total), 0) AS ofertadas,
                   COALESCE(SUM(c.quantidade_utilizada), 0) AS agendadas
            FROM cota_unidade c
            """ + FILTRO + """
              AND c.profissional_id IS NOT NULL
              AND c.hora_inicial IS NOT NULL AND c.hora_final IS NOT NULL
            GROUP BY c.hora_inicial, c.hora_final
            ORDER BY c.hora_inicial, c.hora_final
            """, nativeQuery = true)
    List<OcupacaoCotaProjection> ocupacaoPorHorario(
            @Param("unidadeId") Long unidadeId,
            @Param("mesDe") String mesDe,
            @Param("mesAte") String mesAte,
            @Param("dataDe") LocalDate dataDe,
            @Param("dataAte") LocalDate dataAte);
}
