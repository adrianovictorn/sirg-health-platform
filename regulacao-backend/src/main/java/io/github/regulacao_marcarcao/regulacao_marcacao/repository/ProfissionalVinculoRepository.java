package io.github.regulacao_marcarcao.regulacao_marcacao.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Profissional;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.ProfissionalVinculo;

@Repository
public interface ProfissionalVinculoRepository extends JpaRepository<ProfissionalVinculo, Long> {

    List<ProfissionalVinculo> findByProfissionalIdOrderByIdAsc(Long profissionalId);

    /**
     * Quem atende neste estabelecimento executante.
     *
     * <p>E a consulta que a abertura de agenda faz para montar o combo de
     * profissionais depois que o executante e escolhido. Filtra tambem pelo
     * profissional ativo: vinculo ativo de profissional desativado nao deve
     * aparecer.
     */
    @Query("""
            SELECT DISTINCT v.profissional FROM ProfissionalVinculo v
            WHERE v.unidade.id = :unidadeId
              AND v.ativo = TRUE
              AND v.profissional.ativo = TRUE
            ORDER BY v.profissional.nome ASC
            """)
    List<Profissional> findProfissionaisAtivosPorUnidade(@Param("unidadeId") Long unidadeId);

    /**
     * Vinculo existente para a tripla exata.
     *
     * <p>Dois metodos porque no JPQL/Spring Data {@code cboId = null} nao casa com
     * {@code cbo_id IS NULL} — igualdade com NULL e sempre falso em SQL. Sem a
     * variante {@code CboIsNull} a importacao criaria vinculo duplicado sem CBO em
     * toda reimportacao, e o indice unico da V88 (com COALESCE) rejeitaria a
     * gravacao com erro de constraint em vez de simplesmente reconhecer o vinculo.
     */
    Optional<ProfissionalVinculo> findByProfissionalIdAndUnidadeIdAndCboId(
            Long profissionalId, Long unidadeId, Long cboId);

    Optional<ProfissionalVinculo> findByProfissionalIdAndUnidadeIdAndCboIsNull(
            Long profissionalId, Long unidadeId);

    boolean existsByUnidadeId(Long unidadeId);

    /** Usado pela abertura de agenda: o profissional escolhido atende mesmo neste executante? */
    boolean existsByProfissionalIdAndUnidadeIdAndAtivoTrue(Long profissionalId, Long unidadeId);

    boolean existsByCboId(Long cboId);
}
