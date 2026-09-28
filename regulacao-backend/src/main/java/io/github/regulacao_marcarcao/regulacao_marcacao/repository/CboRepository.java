package io.github.regulacao_marcarcao.regulacao_marcacao.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Cbo;

@Repository
public interface CboRepository extends JpaRepository<Cbo, Long> {

    /** O codigo e a chave do upsert feito pela importacao do CNES. */
    Optional<Cbo> findByCodigo(String codigo);

    List<Cbo> findByAtivoTrueOrderByDescricaoAsc();

    List<Cbo> findAllByOrderByDescricaoAsc();
}
