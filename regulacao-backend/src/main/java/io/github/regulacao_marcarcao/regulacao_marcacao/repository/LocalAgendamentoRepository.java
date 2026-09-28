package io.github.regulacao_marcarcao.regulacao_marcacao.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.LocalAgendamento;

public interface LocalAgendamentoRepository extends JpaRepository<LocalAgendamento, Long> {

    Optional<LocalAgendamento> findByEnumValue(String enumValue);

    List<LocalAgendamento> findByCidade_Id(Long cidadeId);

    /** Chave de dedupe da busca por CNES (V91) — indice unico parcial no banco. */
    Optional<LocalAgendamento> findByCnes(String cnes);
}
