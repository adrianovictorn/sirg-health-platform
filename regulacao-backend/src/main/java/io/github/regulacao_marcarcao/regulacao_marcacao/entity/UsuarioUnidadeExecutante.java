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
 * Operador (perfil USER) vinculado a uma unidade executante (V110). Um usuario
 * pode estar em varias executantes. E a parte separada da lotacao unica
 * ({@code usuarios.unidade_id}), que segue valendo como unidade solicitante.
 */
@Entity
@Table(name = "usuario_unidade_executante")
@Getter
@Setter
@NoArgsConstructor
public class UsuarioUnidadeExecutante {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private User usuario;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "unidade_id", nullable = false)
    private Unidade unidade;

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    public UsuarioUnidadeExecutante(User usuario, Unidade unidade) {
        this.usuario = usuario;
        this.unidade = unidade;
    }
}
