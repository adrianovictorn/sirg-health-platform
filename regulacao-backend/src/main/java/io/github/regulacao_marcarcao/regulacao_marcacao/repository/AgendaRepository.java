package io.github.regulacao_marcarcao.regulacao_marcacao.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Agenda;

@Repository
public interface AgendaRepository extends JpaRepository<Agenda, Long> {

    List<Agenda> findByEstabelecimentoExecutanteId(Long estabelecimentoExecutanteId);

    List<Agenda> findByProfissionalId(Long profissionalId);

    List<Agenda> findAllByOrderByIdDesc();
}
