package io.github.regulacao_marcarcao.regulacao_marcacao.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.UnidadeExecutanteGrupo;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.UnidadeExecutanteResumoProjection;

public interface UnidadeExecutanteGrupoRepository extends JpaRepository<UnidadeExecutanteGrupo, Long> {

    @Query("""
            SELECT x FROM UnidadeExecutanteGrupo x
            JOIN FETCH x.grupoRelatorio
            WHERE x.unidade.id = :unidadeId
            ORDER BY x.grupoRelatorio.nome
            """)
    List<UnidadeExecutanteGrupo> findByUnidadeId(@Param("unidadeId") Long unidadeId);

    boolean existsByGrupoRelatorioId(Long grupoRelatorioId);

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM UnidadeExecutanteGrupo x WHERE x.unidade.id = :unidadeId")
    void deleteByUnidadeId(@Param("unidadeId") Long unidadeId);

    /**
     * Unidades ativas que podem ser executantes, com o tamanho de cada parte da
     * parametrizacao. {@code configurada} segue a mesma definicao de
     * {@link UsuarioUnidadeExecutanteRepository#findExecutantesConfiguradasDoUsuario}.
     */
    @Query(value = """
            SELECT
                u.id AS unidadeId,
                u.nome AS unidadeNome,
                u.tipo AS tipo,
                (SELECT COUNT(*) FROM unidade_executante_grupo g WHERE g.unidade_id = u.id) AS grupos,
                (SELECT COUNT(*) FROM unidade_executante_especialidade e WHERE e.unidade_id = u.id) AS especialidades,
                (SELECT COUNT(*) FROM local_agendamento l WHERE l.unidade_executante_id = u.id) AS locais,
                (SELECT COUNT(*) FROM usuario_unidade_executante o WHERE o.unidade_id = u.id) AS operadores
            FROM unidade u
            WHERE u.ativo = true AND u.tipo IN ('EXECUTANTE', 'AMBOS')
            ORDER BY u.nome
            """, nativeQuery = true)
    List<UnidadeExecutanteResumoProjection> resumirExecutantes();
}
