package io.github.regulacao_marcarcao.regulacao_marcacao.entity;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Especialidade avulsa realizada por uma unidade executante (V110) — o ajuste
 * fino ao lado dos grupos inteiros de {@link UnidadeExecutanteGrupo}. Uma
 * especialidade avulsa pode estar em mais de uma executante (V111); o indice
 * unico e no par {@code (especialidade_id, unidade_id)}.
 */
@Entity
@Table(name = "unidade_executante_especialidade")
@Getter
@Setter
@NoArgsConstructor
public class UnidadeExecutanteEspecialidade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "unidade_id", nullable = false)
    private Unidade unidade;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "especialidade_id", nullable = false)
    private Especialidade especialidade;

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    public UnidadeExecutanteEspecialidade(Unidade unidade, Especialidade especialidade) {
        this.unidade = unidade;
        this.especialidade = especialidade;
    }
}
