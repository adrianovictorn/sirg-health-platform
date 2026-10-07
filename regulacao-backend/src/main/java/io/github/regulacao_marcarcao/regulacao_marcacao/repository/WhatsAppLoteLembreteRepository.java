package io.github.regulacao_marcarcao.regulacao_marcacao.repository;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.WhatsAppLoteLembrete;

public interface WhatsAppLoteLembreteRepository extends JpaRepository<WhatsAppLoteLembrete, Long> {

    Optional<WhatsAppLoteLembrete> findByDataExecucao(LocalDate dataExecucao);

    boolean existsByDataExecucao(LocalDate dataExecucao);
}
