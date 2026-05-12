package io.github.regulacao_marcarcao.regulacao_marcacao.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Profissional;

@Repository
public interface ProfissionalRepository extends JpaRepository<Profissional, Long> {

    List<Profissional> findByAtivoTrue();

    List<Profissional> findByUnidadeId(Long unidadeId);

    List<Profissional> findByUnidadeIdAndAtivoTrue(Long unidadeId);

    Page<Profissional> findByNomeContainingIgnoreCase(String nome, Pageable pageable);
}
