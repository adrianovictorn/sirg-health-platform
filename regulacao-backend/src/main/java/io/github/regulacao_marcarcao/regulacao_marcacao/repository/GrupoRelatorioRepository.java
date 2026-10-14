package io.github.regulacao_marcarcao.regulacao_marcacao.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.GrupoRelatorio;

public interface GrupoRelatorioRepository extends JpaRepository<GrupoRelatorio, Long> {

    /** Agendas da tela de Atendimentos na visao municipal (mesmo criterio que o menu aplicava no cliente). */
    List<GrupoRelatorio> findByAtivoTrueAndDirecionadoHospitalTrueOrderByNomeAsc();

    /**
     * Agendas do operador restrito a executantes (V110): grupos ativos ligados a
     * elas inteiros ou por alguma especialidade avulsa. A flag direcionadoHospital
     * nao vale aqui — o que a executante realiza e definido pela parametrizacao.
     * {@code unidadeIds} nunca pode ir vazio.
     */
    @Query(value = """
            SELECT gr.*
            FROM grupo_relatorio gr
            WHERE gr.ativo = true
              AND (gr.id IN (SELECT xg.grupo_relatorio_id FROM unidade_executante_grupo xg
                             WHERE xg.unidade_id IN (:unidadeIds))
                OR gr.id IN (SELECT e.grupo_relatorio_id FROM unidade_executante_especialidade xe
                             JOIN especialidade e ON e.id = xe.especialidade_id
                             WHERE xe.unidade_id IN (:unidadeIds)))
            ORDER BY gr.nome
            """, nativeQuery = true)
    List<GrupoRelatorio> findAgendasDasExecutantes(@Param("unidadeIds") Collection<Long> unidadeIds);
}
