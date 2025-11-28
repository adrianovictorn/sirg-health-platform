package io.github.regulacao_marcarcao.regulacao_marcacao.dto.agendamento.transporte;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.agendamento.transporte.summary.CidadeSummaryDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.agendamento.transporte.summary.LocalAgendamentoSummaryDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.agendamento.transporte.summary.MotoristaSummaryDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.agendamento.transporte.summary.SolicitacaoSummaryDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.agendamento.transporte.summary.TransporteSummaryDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.AgendamentoTransporte;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.AgendamentoTransportePaciente;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.StatusAgendamento;

public record AgendamentoTransporteListDTO(
    Long id,
    List<LocalAgendamentoSummaryDTO> localAgendamento,
    CidadeSummaryDTO cidade,
    TransporteSummaryDTO transporte,
    MotoristaSummaryDTO motorista,
    StatusAgendamento status,
    List<AgendamentoTransporteSolicitacaoDTO> pacientes,
    LocalDate data,
    LocalTime horaSaida
) {
    public static AgendamentoTransporteListDTO fromEntity(AgendamentoTransporte agendamentoTransporte){

        var pacientes = agendamentoTransporte.getPacientes().stream()
            .map(AgendamentoTransporteListDTO::mapPaciente)
            .toList();

        return new AgendamentoTransporteListDTO(
            agendamentoTransporte.getId(),
            agendamentoTransporte.getLocaisAgendamento().stream().map(LocalAgendamentoSummaryDTO::fromEntity).toList(),
            CidadeSummaryDTO.fromEntity(agendamentoTransporte.getCidade()),
            TransporteSummaryDTO.fromEntity(agendamentoTransporte.getTransporte()),
            MotoristaSummaryDTO.fromEntity(agendamentoTransporte.getMotorista()),
            agendamentoTransporte.getStatus(),
            pacientes,
            agendamentoTransporte.getData(),
            agendamentoTransporte.getHoraSaida());
    }

    private static AgendamentoTransporteSolicitacaoDTO mapPaciente(AgendamentoTransportePaciente association) {
        var resumo = SolicitacaoSummaryDTO.fromEntity(association.getSolicitacao());
        var localResumo = association.getLocalAgendamento() != null
            ? LocalAgendamentoSummaryDTO.fromEntity(association.getLocalAgendamento())
            : null;
        return AgendamentoTransporteSolicitacaoDTO.fromSummary(resumo, association.getTurno(), association.getRetornaMesmoDia(), localResumo);
    }
}
