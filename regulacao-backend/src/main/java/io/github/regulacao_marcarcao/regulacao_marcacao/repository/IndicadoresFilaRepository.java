package io.github.regulacao_marcarcao.regulacao_marcacao.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.SolicitacaoEspecialidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.AntecedenciaProjection;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.BalancoFilaMesProjection;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.FaixasEsperaProjection;

/**
 * Agregacoes dos indicadores gerenciais de fila e operacao. Somente leitura e
 * somente numeros: nenhuma destas consultas devolve dado de paciente.
 *
 * <p>O envelhecimento usa {@code FILA_FROM}/{@code FILA_FILTRO} da fila de
 * espera de proposito: a soma das faixas precisa bater com o total que a tela
 * da fila mostra, entao o predicado tem de ser o mesmo, nao uma copia.
 *
 * <p>Fuso: {@code data_cadastro} e {@code data_criacao} sao gravados pela JVM,
 * que roda em UTC. Para achar o dia ou o mes do municipio a coluna e convertida
 * ({@code AT TIME ZONE 'UTC' AT TIME ZONE 'America/Bahia'}). As faixas de espera
 * nao convertem nada: comparam com cortes calculados no servico a partir do
 * mesmo relogio, em blocos de 24h, como a fila de espera faz.
 */
public interface IndicadoresFilaRepository extends Repository<SolicitacaoEspecialidade, Long> {

    String FILA_FROM = SolicitacaoEspecialidadeRepository.FILA_FROM;
    String FILA_FILTRO = SolicitacaoEspecialidadeRepository.FILA_FILTRO;

    // Dias inteiros de 24h, como FilaEsperaService.diasDesde: ate 30 dias e
    // "entrou depois de agora - 31 dias". Sem data conta como 0 dia, tambem igual.
    String FAIXAS_DO_PACIENTE = """
             COUNT(*) FILTER (WHERE t.entrada IS NULL OR t.entrada > :corte31) AS ate30,
             COUNT(*) FILTER (WHERE t.entrada <= :corte31 AND t.entrada > :corte61) AS de31a60,
             COUNT(*) FILTER (WHERE t.entrada <= :corte61 AND t.entrada > :corte91) AS de61a90,
             COUNT(*) FILTER (WHERE t.entrada <= :corte91) AS mais90
            """;

    String FAIXAS_DO_PEDIDO = """
             COUNT(*) FILTER (WHERE se.data_cadastro IS NULL OR se.data_cadastro > :corte31) AS ate30,
             COUNT(*) FILTER (WHERE se.data_cadastro <= :corte31 AND se.data_cadastro > :corte61) AS de31a60,
             COUNT(*) FILTER (WHERE se.data_cadastro <= :corte61 AND se.data_cadastro > :corte91) AS de61a90,
             COUNT(*) FILTER (WHERE se.data_cadastro <= :corte91) AS mais90
            """;

    String DIA_LOCAL_DO_CADASTRO =
            "CAST((se.data_cadastro AT TIME ZONE 'UTC') AT TIME ZONE 'America/Bahia' AS date)";

    /**
     * PACIENTES por faixa, uma linha por unidade — a faixa e a do pedido mais
     * antigo do paciente, como a coluna de espera da fila. Solicitacao sem
     * unidade vem numa linha propria, com id e nome nulos.
     */
    @Query(value = "SELECT t.unidade_id AS id, MIN(t.unidade_nome) AS nome," + FAIXAS_DO_PACIENTE + """
            FROM (
                SELECT s.id AS solicitacao_id, s.unidade_id AS unidade_id, u.nome AS unidade_nome,
                       MIN(se.data_cadastro) AS entrada
            """ + FILA_FROM + " WHERE " + FILA_FILTRO + """
                GROUP BY s.id, s.unidade_id, u.nome
            ) t
            GROUP BY t.unidade_id
            """, nativeQuery = true)
    List<FaixasEsperaProjection> pacientesPorFaixaEUnidade(
            @Param("status")            List<String> status,
            @Param("especialidadeId")   Long especialidadeId,
            @Param("categoria")         String categoria,
            @Param("filtrarPrioridade") boolean filtrarPrioridade,
            @Param("prioridades")       List<String> prioridades,
            @Param("unidadeId")         Long unidadeId,
            @Param("dataDe")            LocalDate dataDe,
            @Param("dataAte")           LocalDate dataAte,
            @Param("cadastradoAte")     LocalDateTime cadastradoAte,
            @Param("termo")             String termo,
            @Param("termoDigitos")      String termoDigitos,
            @Param("corte31")           LocalDateTime corte31,
            @Param("corte61")           LocalDateTime corte61,
            @Param("corte91")           LocalDateTime corte91);

    /** PEDIDOS por faixa, uma linha por especialidade. Pedido legado sem especialidade fica fora desta quebra. */
    @Query(value = "SELECT e.id AS id, e.nome AS nome," + FAIXAS_DO_PEDIDO + FILA_FROM
            + " WHERE " + FILA_FILTRO + " AND e.id IS NOT NULL GROUP BY e.id, e.nome", nativeQuery = true)
    List<FaixasEsperaProjection> pedidosPorFaixaEEspecialidade(
            @Param("status")            List<String> status,
            @Param("especialidadeId")   Long especialidadeId,
            @Param("categoria")         String categoria,
            @Param("filtrarPrioridade") boolean filtrarPrioridade,
            @Param("prioridades")       List<String> prioridades,
            @Param("unidadeId")         Long unidadeId,
            @Param("dataDe")            LocalDate dataDe,
            @Param("dataAte")           LocalDate dataAte,
            @Param("cadastradoAte")     LocalDateTime cadastradoAte,
            @Param("termo")             String termo,
            @Param("termoDigitos")      String termoDigitos,
            @Param("corte31")           LocalDateTime corte31,
            @Param("corte61")           LocalDateTime corte61,
            @Param("corte91")           LocalDateTime corte91);

    /** PEDIDOS por faixa, uma linha por prioridade ({@code nome} = prioridade; nulo quando o pedido nao tem). */
    @Query(value = "SELECT CAST(NULL AS bigint) AS id, se.prioridade AS nome," + FAIXAS_DO_PEDIDO + FILA_FROM
            + " WHERE " + FILA_FILTRO + " GROUP BY se.prioridade", nativeQuery = true)
    List<FaixasEsperaProjection> pedidosPorFaixaEPrioridade(
            @Param("status")            List<String> status,
            @Param("especialidadeId")   Long especialidadeId,
            @Param("categoria")         String categoria,
            @Param("filtrarPrioridade") boolean filtrarPrioridade,
            @Param("prioridades")       List<String> prioridades,
            @Param("unidadeId")         Long unidadeId,
            @Param("dataDe")            LocalDate dataDe,
            @Param("dataAte")           LocalDate dataAte,
            @Param("cadastradoAte")     LocalDateTime cadastradoAte,
            @Param("termo")             String termo,
            @Param("termoDigitos")      String termoDigitos,
            @Param("corte31")           LocalDateTime corte31,
            @Param("corte61")           LocalDateTime corte61,
            @Param("corte91")           LocalDateTime corte91);

    /**
     * Antecedencia dos agendamentos com data marcada no periodo: data marcada
     * menos o dia (do municipio) em que o agendamento foi criado.
     *
     * <p>Sem {@code data_criacao} (anteriores a V55) nao ha o que medir: sao
     * contados a parte. Negativo e registro retroativo, tambem a parte e fora
     * da media, da mediana e das faixas.
     */
    @Query(value = """
            SELECT
                COUNT(*)                                             AS total,
                COUNT(*) FILTER (WHERE d.dias IS NULL)               AS semDataCriacao,
                COUNT(*) FILTER (WHERE d.dias < 0)                   AS retroativos,
                CAST(AVG(d.dias) FILTER (WHERE d.dias >= 0) AS double precision) AS media,
                CAST(PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY d.dias)
                     FILTER (WHERE d.dias >= 0) AS double precision) AS mediana,
                COUNT(*) FILTER (WHERE d.dias BETWEEN 0 AND 1)       AS ate1,
                COUNT(*) FILTER (WHERE d.dias BETWEEN 2 AND 7)       AS de2a7,
                COUNT(*) FILTER (WHERE d.dias BETWEEN 8 AND 15)      AS de8a15,
                COUNT(*) FILTER (WHERE d.dias BETWEEN 16 AND 30)     AS de16a30,
                COUNT(*) FILTER (WHERE d.dias > 30)                  AS mais30
            FROM (
                SELECT ag.data_agendada
                       - CAST((ag.data_criacao AT TIME ZONE 'UTC') AT TIME ZONE 'America/Bahia' AS date) AS dias
                FROM agendamento_solicitacao ag
                JOIN solicitacao s ON s.id = ag.solicitacao_id
                WHERE ag.data_agendada BETWEEN :dataDe AND :dataAte
                  AND (CAST(:unidadeId AS bigint) IS NULL OR s.unidade_id = :unidadeId)
            ) d
            """, nativeQuery = true)
    AntecedenciaProjection antecedencia(
            @Param("unidadeId") Long unidadeId,
            @Param("dataDe") LocalDate dataDe,
            @Param("dataAte") LocalDate dataAte);

    /** Pedidos cadastrados, por mes (do municipio) do cadastro. So {@code novos} vem preenchido. */
    @Query(value = "SELECT to_char(" + DIA_LOCAL_DO_CADASTRO + ", 'YYYY-MM') AS mes,"
            + " COUNT(*) AS novos, CAST(0 AS bigint) AS agendados, CAST(0 AS bigint) AS concluidos"
            + " FROM solicitacao_especialidade se"
            + " JOIN solicitacao s ON s.id = se.solicitacao_id"
            + " WHERE " + DIA_LOCAL_DO_CADASTRO + " BETWEEN :dataDe AND :dataAte"
            + " AND (CAST(:unidadeId AS bigint) IS NULL OR s.unidade_id = :unidadeId)"
            + " GROUP BY 1", nativeQuery = true)
    List<BalancoFilaMesProjection> pedidosNovosPorMes(
            @Param("unidadeId") Long unidadeId,
            @Param("dataDe") LocalDate dataDe,
            @Param("dataAte") LocalDate dataAte);

    /**
     * Pedidos com agendamento, por mes da data marcada: todos em {@code agendados}
     * e, deles, os REALIZADO em {@code concluidos}. So esses dois vem preenchidos.
     */
    @Query(value = """
            SELECT to_char(ag.data_agendada, 'YYYY-MM') AS mes,
                   CAST(0 AS bigint) AS novos,
                   COUNT(*) AS agendados,
                   COUNT(*) FILTER (WHERE se.status = 'REALIZADO') AS concluidos
            FROM solicitacao_especialidade se
            JOIN agendamento_solicitacao ag ON ag.id = se.agendamento_id
            JOIN solicitacao s ON s.id = se.solicitacao_id
            WHERE ag.data_agendada BETWEEN :dataDe AND :dataAte
              AND (CAST(:unidadeId AS bigint) IS NULL OR s.unidade_id = :unidadeId)
            GROUP BY 1
            """, nativeQuery = true)
    List<BalancoFilaMesProjection> pedidosAgendadosPorMes(
            @Param("unidadeId") Long unidadeId,
            @Param("dataDe") LocalDate dataDe,
            @Param("dataAte") LocalDate dataAte);
}
