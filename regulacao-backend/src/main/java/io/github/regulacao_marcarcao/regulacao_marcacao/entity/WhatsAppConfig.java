package io.github.regulacao_marcarcao.regulacao_marcacao.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Chave que liga/desliga o envio pelo WhatsApp (V101). Linha unica, id = 1. */
@Entity
@Table(name = "whatsapp_config")
@Getter
@Setter
@NoArgsConstructor
public class WhatsAppConfig {

    public static final Long ID_UNICO = 1L;

    @Id
    private Long id;

    @Column(name = "envio_ligado", nullable = false)
    private boolean envioLigado;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "alterado_por_id")
    private User alteradoPor;

    @Column(name = "alterado_em")
    private Instant alteradoEm;
}
