package io.github.regulacao_marcarcao.regulacao_marcacao.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.WhatsAppConfig;

public interface WhatsAppConfigRepository extends JpaRepository<WhatsAppConfig, Long> {
}
