package io.github.regulacao_marcarcao.regulacao_marcacao.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.CotaUnidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.ConsumidorCotaProjection;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.ItemMigracaoProjection;

/**
 * Quem consome uma cota — itens agendados e seus agendamentos. Somente leitura:
 * nao altera nada nem toca o motor de saldo ({@link CotaUnidadeRepository}).
 *
 * <p>Cota COM profissional tem rastro exato: a FK {@code solicitacao_especialidade.cota_unidade_id}
 * (V93/V100). Cota SEM profissional nao grava FK no item (consome sem registrar
 * qual), entao os consumidores sao reconstruidos por incidencia — mesma unidade
 * (ou grupo), mesma especialidade (ou grupo, ou qualquer uma se a cota e geral) e
 * data do agendamento no periodo da cota. Isso tambem alcanca agendamentos feitos
 * por ADMIN/GESTOR, que nao consomem cota; por isso a tela avisa quando a contagem
 * diverge de {@code quantidade_utilizada}.
 */
public interface CotaConsumidoresRepository extends Repository<CotaUnidade, Long> {

    /**
     * Itens agendados que apontam para a cota E cujo agendamento cai no periodo
     * atual dela ({@code dataDe..dataAte}). O filtro de data e necessario: o
     * desvincular do agendamento nao limpa a FK da cota e o reagendamento por
     * ADMIN/GESTOR nao a regrava, entao um item pode citar a cota estando agendado
     * em OUTRA data.
     */
    @Query(value = """
            SELECT se.id AS itemId, se.agendamento_id AS agendamentoId, ag.data_agendada AS dataAgendada
            FROM solicitacao_especialidade se
            JOIN agendamento_solicitacao ag ON ag.id = se.agendamento_id
            WHERE se.cota_unidade_id = :cotaId
              AND ag.data_agendada BETWEEN :dataDe AND :dataAte
            ORDER BY se.id
            """, nativeQuery = true)
    List<ConsumidorCotaProjection> consumidoresPorCotaFixada(@Param("cotaId") Long cotaId,
            @Param("dataDe") LocalDate dataDe, @Param("dataAte") LocalDate dataAte);

    /**
     * Itens agendados sem cota fixada que incidem sobre uma cota sem profissional.
     * Titular: {@code unidadeId} OU {@code grupoUnidadesId} (um deles nulo).
     * Escopo: {@code especialidadeId} OU {@code grupoEspecialidadesId}; ambos nulos
     * = cota geral. Periodo: {@code dataDe..dataAte} (a mesma data para cota DATA,
     * o mes inteiro para MENSAL).
     */
    @Query(value = """
            SELECT se.id AS itemId, se.agendamento_id AS agendamentoId, ag.data_agendada AS dataAgendada
            FROM solicitacao_especialidade se
            JOIN agendamento_solicitacao ag ON ag.id = se.agendamento_id
            JOIN solicitacao s ON s.id = se.solicitacao_id
            LEFT JOIN especialidade e ON e.id = se.especialidade_id
            WHERE se.agendamento_id IS NOT NULL
              AND se.cota_unidade_id IS NULL
              AND ag.data_agendada BETWEEN :dataDe AND :dataAte
              AND (s.unidade_id = :unidadeId
                   OR s.unidade_id IN (SELECT un.id FROM unidade un WHERE un.grupo_relatorio_id = :grupoUnidadesId))
              AND ((CAST(:especialidadeId AS bigint) IS NULL AND CAST(:grupoEspecialidadesId AS bigint) IS NULL)
                   OR e.id = :especialidadeId
                   OR e.grupo_relatorio_id = :grupoEspecialidadesId)
            ORDER BY se.id
            """, nativeQuery = true)
    List<ConsumidorCotaProjection> consumidoresPorIncidencia(
            @Param("unidadeId") Long unidadeId,
            @Param("grupoUnidadesId") Long grupoUnidadesId,
            @Param("especialidadeId") Long especialidadeId,
            @Param("grupoEspecialidadesId") Long grupoEspecialidadesId,
            @Param("dataDe") LocalDate dataDe,
            @Param("dataAte") LocalDate dataAte);

    /** TODOS os itens dos agendamentos — o agendamento migra inteiro, com todos os exames. */
    @Query(value = """
            SELECT se.id                    AS seId,
                   se.agendamento_id        AS agendamentoId,
                   se.solicitacao_id        AS solicitacaoId,
                   s.unidade_id             AS unidadeId,
                   se.especialidade_id      AS especialidadeId,
                   e.codigo                 AS especialidadeCodigo,
                   se.cota_unidade_id       AS cotaUnidadeId,
                   se.status                AS status,
                   ag.data_agendada         AS dataAgendada,
                   s.origem_municipio_id    AS origemMunicipioId,
                   s.origem_municipio_nome  AS origemMunicipioNome,
                   s.nome_paciente          AS nomePaciente,
                   s.cpf_paciente           AS cpfPaciente
            FROM solicitacao_especialidade se
            JOIN agendamento_solicitacao ag ON ag.id = se.agendamento_id
            JOIN solicitacao s ON s.id = se.solicitacao_id
            LEFT JOIN especialidade e ON e.id = se.especialidade_id
            WHERE se.agendamento_id IN (:agendamentoIds)
            ORDER BY se.id
            """, nativeQuery = true)
    List<ItemMigracaoProjection> itensDosAgendamentos(@Param("agendamentoIds") Collection<Long> agendamentoIds);
}
