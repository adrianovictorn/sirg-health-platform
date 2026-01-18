package io.github.regulacao_marcarcao.regulacao_marcacao.repository;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.FechamentoIndicadoresDia;

public interface FechamentoIndicadoresDiaRepository extends JpaRepository<FechamentoIndicadoresDia, Long> {
    
    Optional<FechamentoIndicadoresDia> findByDataReferenciaAndLocalAgendamentoIdAndGrupoRelatorioId(LocalDate data, Long localAgendamentoId,Long grupoId);
    
}
