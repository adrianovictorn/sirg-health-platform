package io.github.regulacao_marcarcao.regulacao_marcacao.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.User;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.Roles;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByCpf(String cpf);

    long countByRoleAndAtivoTrue(Roles role);

    /** Usuarios ativos que podem assumir o perfil — como principal ou como perfil concedido. */
    @Query("""
            SELECT DISTINCT u FROM User u
            LEFT JOIN u.perfis p
            WHERE u.ativo = true AND (u.role = :perfil OR p = :perfil)
            ORDER BY u.nome
            """)
    List<User> findAtivosQuePodemAssumir(@Param("perfil") Roles perfil);
}
