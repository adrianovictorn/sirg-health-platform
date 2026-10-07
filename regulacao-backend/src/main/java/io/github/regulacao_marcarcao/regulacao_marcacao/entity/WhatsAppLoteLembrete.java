package io.github.regulacao_marcarcao.regulacao_marcacao.entity;

import java.time.Instant;
import java.time.LocalDate;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.WhatsAppOrigem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Dia em que o lote de lembretes rodou com o envio ligado (V103). */
@Entity
@Table(name = "whatsapp_lote_lembrete")
@Getter
@Setter
@NoArgsConstructor
public class WhatsAppLoteLembrete {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "data_execucao", nullable = false)
    private LocalDate dataExecucao;

    @Column(name = "executado_em", nullable = false)
    private Instant executadoEm;

    @Enumerated(EnumType.STRING)
    @Column(name = "origem", nullable = false, length = 20)
    private WhatsAppOrigem origem;

    @Column(name = "enfileirados", nullable = false)
    private int enfileirados;
}
