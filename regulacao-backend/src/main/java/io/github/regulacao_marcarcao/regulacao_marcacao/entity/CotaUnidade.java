package io.github.regulacao_marcarcao.regulacao_marcacao.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.TipoPeriodoCota;
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
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "cota_unidade")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CotaUnidade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unidade_id", nullable = false)
    private Unidade unidade;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "especialidade_id", nullable = true)
    private Especialidade especialidade;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_periodo", nullable = false, length = 10)
    private TipoPeriodoCota tipoPeriodo = TipoPeriodoCota.MENSAL;

    // Usado quando tipoPeriodo = MENSAL (formato YYYY-MM)
    @Column(name = "periodo", length = 7)
    private String periodo;

    // Usado quando tipoPeriodo = DATA
    @Column(name = "data_especifica")
    private LocalDate dataEspecifica;

    @Column(name = "quantidade_total", nullable = false)
    private Integer quantidadeTotal = 0;

    @Column(name = "quantidade_utilizada", nullable = false)
    private Integer quantidadeUtilizada = 0;

    @Column(name = "ativo", nullable = false)
    private boolean ativo = true;

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @Version
    private Long version;
}
