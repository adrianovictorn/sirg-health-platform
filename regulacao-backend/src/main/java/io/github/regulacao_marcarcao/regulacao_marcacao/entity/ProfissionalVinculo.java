package io.github.regulacao_marcarcao.regulacao_marcacao.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.OrigemVinculoEnum;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Onde um profissional atua, e em que ocupacao (V88).
 *
 * <p>Substitui o {@code profissional.unidade} unico: o mesmo CPF atende na
 * policlinica, no hospital e numa USF, com CBO possivelmente diferente em cada
 * lugar. E este vinculo que a abertura de agenda consulta para filtrar o combo
 * de profissionais do estabelecimento executante escolhido.
 *
 * <p>Unicidade da tripla (profissional, unidade, cbo) garantida por indice no
 * banco, com COALESCE no cbo_id — ver V88.
 */
@Entity
@Table(name = "profissional_vinculo")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProfissionalVinculo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profissional_id", nullable = false)
    private Profissional profissional;

    /** Estabelecimento onde atua. Unidade com tipo EXECUTANTE ou AMBOS (V86). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "unidade_id", nullable = false)
    private Unidade unidade;

    /** Ocupacao no vinculo. Nulo nos vinculos herdados, onde o dado nunca existiu. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cbo_id")
    private Cbo cbo;

    @Column(name = "ativo", nullable = false)
    private boolean ativo = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "origem", nullable = false, length = 20)
    private OrigemVinculoEnum origem = OrigemVinculoEnum.MANUAL;

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;
}
