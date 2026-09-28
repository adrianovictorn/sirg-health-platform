package io.github.regulacao_marcarcao.regulacao_marcacao.entity;

import java.time.LocalDate;
import java.time.LocalTime;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.StatusOcorrenciaEnum;
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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Data concreta gerada pela expansao de vigencia x dias da semana de uma
 * {@link Agenda} (R2, V89). E o que vira {@link CotaUnidade} — uma por par
 * (ocorrencia x unidade solicitante).
 *
 * <p>Editar a agenda depois de materializada nao altera ocorrencia que ja tem
 * marcacao (R2); cancelar uma ocorrencia desativa as cotas geradas por ela e
 * estorna o saldo nao consumido (R10).
 */
@Entity
@Table(name = "agenda_ocorrencia")
@Getter
@Setter
@NoArgsConstructor
public class AgendaOcorrencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agenda_id", nullable = false)
    private Agenda agenda;

    @Column(name = "data", nullable = false)
    private LocalDate data;

    @Column(name = "hora_inicial", nullable = false)
    private LocalTime horaInicial;

    @Column(name = "hora_final", nullable = false)
    private LocalTime horaFinal;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StatusOcorrenciaEnum status = StatusOcorrenciaEnum.ABERTA;

    @Version
    private Long version;
}
