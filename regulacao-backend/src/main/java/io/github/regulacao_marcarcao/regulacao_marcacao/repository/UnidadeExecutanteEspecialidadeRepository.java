package io.github.regulacao_marcarcao.regulacao_marcacao.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.UnidadeExecutanteEspecialidade;

public interface UnidadeExecutanteEspecialidadeRepository extends JpaRepository<UnidadeExecutanteEspecialidade, Long> {

    @Query("""
            SELECT x FROM UnidadeExecutanteEspecialidade x
            JOIN FETCH x.especialidade e
            LEFT JOIN FETCH e.grupoRelatorio
            WHERE x.unidade.id = :unidadeId
            ORDER BY e.nome
            """)
    List<UnidadeExecutanteEspecialidade> findByUnidadeId(@Param("unidadeId") Long unidadeId);

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM UnidadeExecutanteEspecialidade x WHERE x.unidade.id = :unidadeId")
    void deleteByUnidadeId(@Param("unidadeId") Long unidadeId);
}
