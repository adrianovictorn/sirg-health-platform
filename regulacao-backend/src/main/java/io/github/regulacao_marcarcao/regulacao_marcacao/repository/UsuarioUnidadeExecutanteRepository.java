package io.github.regulacao_marcarcao.regulacao_marcacao.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.UsuarioUnidadeExecutante;

public interface UsuarioUnidadeExecutanteRepository extends JpaRepository<UsuarioUnidadeExecutante, Long> {

    @Query("""
            SELECT x FROM UsuarioUnidadeExecutante x
            JOIN FETCH x.usuario u
            WHERE x.unidade.id = :unidadeId
            ORDER BY u.nome
            """)
    List<UsuarioUnidadeExecutante> findByUnidadeId(@Param("unidadeId") Long unidadeId);

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM UsuarioUnidadeExecutante x WHERE x.unidade.id = :unidadeId")
    void deleteByUnidadeId(@Param("unidadeId") Long unidadeId);

    /**
     * Executantes CONFIGURADAS do usuario — o que ele ve na tela de Atendimentos.
     * Uniao da unidade de lotacao ({@code usuarios.unidade_id}) com as executantes
     * em que ele esta na lista de operadores (ADR 0013); as duas passam pelo mesmo
     * filtro. Configurada = ativa, tipo EXECUTANTE ou AMBOS, com ao menos um local
     * ligado e ao menos um grupo ou especialidade. Lista vazia = USER sem executante,
     * que nao ve nada (falha fechada), nunca a visao municipal.
     */
    @Query(value = """
            SELECT u.id
            FROM unidade u
            WHERE (EXISTS (SELECT 1 FROM usuario_unidade_executante x
                           WHERE x.unidade_id = u.id AND x.usuario_id = :usuarioId)
                OR u.id = (SELECT us.unidade_id FROM usuarios us WHERE us.id = :usuarioId))
              AND u.ativo = true
              AND u.tipo IN ('EXECUTANTE', 'AMBOS')
              AND EXISTS (SELECT 1 FROM local_agendamento l WHERE l.unidade_executante_id = u.id)
              AND (EXISTS (SELECT 1 FROM unidade_executante_grupo g WHERE g.unidade_id = u.id)
                OR EXISTS (SELECT 1 FROM unidade_executante_especialidade e WHERE e.unidade_id = u.id))
            ORDER BY u.id
            """, nativeQuery = true)
    List<Long> findExecutantesConfiguradasDoUsuario(@Param("usuarioId") Long usuarioId);
}
