package io.github.regulacao_marcarcao.regulacao_marcacao.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.ItemCategoria;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.OrigemValorEspecialidade;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "especialidade", uniqueConstraints = {
        @UniqueConstraint(name = "uk_especialidade_codigo", columnNames = {"codigo"}),
        @UniqueConstraint(name = "uk_especialidade_nome", columnNames = {"nome"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Especialidade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Código estável (migração do enum name)
    @Column(nullable = false, length = 150)
    private String codigo;

    // Nome de exibição (migração do getDescricao)
    @Column(nullable = false, length = 255)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ItemCategoria categoria;

    @Column(nullable = false)
    private Boolean ativo = true;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grupo_relatorio_id", nullable = true)
    private GrupoRelatorio grupoRelatorio;

    @Column(name = "vagas", nullable = false)
    private Integer vagas = 0;

    /**
     * Especialidade cujo nome pode revelar condicao de saude (V102). Quando
     * {@code true}, as mensagens de WhatsApp nao citam o nome nem o local.
     */
    @Column(name = "sensivel", nullable = false)
    private Boolean sensivel = false;

    // ------------------------------------------------------------------
    // CUSTO (V105) — so sai pelos endpoints de /api/custos (ADMIN e GESTOR).
    // Nenhum destes campos pode entrar em EspecialidadeViewDTO,
    // EspecialidadeSimpleViewDTO ou em qualquer DTO lido por perfil de unidade.
    // ------------------------------------------------------------------

    /** Preco de uma unidade do procedimento. Nulo = sem preco (nao e R$ 0,00). */
    @Column(name = "valor_unitario", precision = 12, scale = 2)
    private BigDecimal valorUnitario;

    /**
     * Codigo SIGTAP, 10 digitos com zero a esquerda. Nao e unico: o catalogo
     * tem conceitos duplicados que apontam para o mesmo procedimento.
     */
    @Column(name = "codigo_sus", length = 10)
    private String codigoSus;

    /** De onde veio o preco atual; a importacao avisa antes de sobrescrever MANUAL. */
    @Enumerated(EnumType.STRING)
    @Column(name = "valor_origem", length = 20)
    private OrigemValorEspecialidade valorOrigem;

    @Column(name = "valor_atualizado_em")
    private LocalDateTime valorAtualizadoEm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "valor_atualizado_por_id", nullable = true)
    private User valorAtualizadoPor;
}

