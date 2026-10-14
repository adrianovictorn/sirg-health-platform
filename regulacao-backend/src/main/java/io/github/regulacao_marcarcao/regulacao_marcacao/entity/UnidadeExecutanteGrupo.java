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
 * Grupo de Relatorio inteiro realizado por uma unidade executante (V110).
 *
 * <p>Nao confundir com {@code unidade.grupo_relatorio_id} (V82), que e o titular
 * de cota coletiva da unidade SOLICITANTE. Um grupo pode estar em mais de uma
 * executante (V111); o indice unico e no par {@code (grupo_relatorio_id, unidade_id)}.
 */
@Entity
@Table(name = "unidade_executante_grupo")
@Getter
@Setter
@NoArgsConstructor
public class UnidadeExecutanteGrupo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "unidade_id", nullable = false)
    private Unidade unidade;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "grupo_relatorio_id", nullable = false)
    private GrupoRelatorio grupoRelatorio;

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    public UnidadeExecutanteGrupo(Unidade unidade, GrupoRelatorio grupoRelatorio) {
        this.unidade = unidade;
        this.grupoRelatorio = grupoRelatorio;
    }
}
