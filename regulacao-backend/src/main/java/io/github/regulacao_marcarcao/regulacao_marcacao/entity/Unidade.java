package io.github.regulacao_marcarcao.regulacao_marcacao.entity;

import java.time.LocalDateTime;

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
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "unidade")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Unidade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nome", nullable = false, length = 200)
    private String nome;

    @Column(name = "codigo", length = 50)
    private String codigo;

    @Column(name = "cnes", length = 20, unique = true)
    private String cnes;

    @Column(name = "telefone", length = 20)
    private String telefone;

    @Column(name = "endereco", length = 300)
    private String endereco;

    @Column(name = "ativo", nullable = false)
    private boolean ativo = true;

    // Grupo ao qual a unidade pertence (opcional). Habilita cotas coletivas (V82).
    // Reaproveita GrupoRelatorio: assim como uma Especialidade pertence a um grupo,
    // uma Unidade pertence a um grupo.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grupo_relatorio_id", nullable = true)
    private GrupoRelatorio grupoRelatorio;

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;
}
