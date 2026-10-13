package io.github.regulacao_marcarcao.regulacao_marcacao.entity;

import java.time.Instant;
import java.time.LocalDate;

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
 * Auditoria de uma migracao de agendamentos por mudanca de data de cota (V108):
 * uma linha por operacao. Os ids de cota sao ids soltos, sem associacao JPA — na
 * unificacao a cota de origem e excluida e esta linha precisa sobreviver.
 */
@Entity
@Table(name = "cota_migracao_data")
@Getter
@Setter
@NoArgsConstructor
public class CotaMigracaoData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cota_origem_id", nullable = false)
    private Long cotaOrigemId;

    /** A propria cota de origem, ou a de destino quando houve unificacao. */
    @Column(name = "cota_resultante_id", nullable = false)
    private Long cotaResultanteId;

    @Column(name = "data_de", nullable = false)
    private LocalDate dataDe;

    @Column(name = "data_para", nullable = false)
    private LocalDate dataPara;

    @Column(name = "unificada", nullable = false)
    private boolean unificada;

    @Column(name = "agendamentos", nullable = false)
    private int agendamentos;

    @Column(name = "itens", nullable = false)
    private int itens;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "realizado_por_id")
    private User realizadoPor;

    @Column(name = "realizado_em", nullable = false)
    private Instant realizadoEm;
}
