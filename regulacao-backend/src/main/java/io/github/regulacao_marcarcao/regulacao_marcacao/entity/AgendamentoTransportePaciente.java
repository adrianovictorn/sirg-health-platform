package io.github.regulacao_marcarcao.regulacao_marcacao.entity;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.TurnoEnum;

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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "agendamento_transporte_paciente")
@Getter
@Setter
@NoArgsConstructor
public class AgendamentoTransportePaciente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agendamento_transporte_id", nullable = false)
    private AgendamentoTransporte agendamento;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "solicitacao_id", nullable = false)
    private Solicitacao solicitacao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "local_agendamento_id")
    private LocalAgendamento localAgendamento;

    @Enumerated(EnumType.STRING)
    @Column(name = "turno", nullable = false)
    private TurnoEnum turno = TurnoEnum.NAO_INFORMADO;

    @Column(name = "retorna_mesmo_dia")
    private Boolean retornaMesmoDia;
}
