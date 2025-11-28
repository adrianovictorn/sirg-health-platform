package io.github.regulacao_marcarcao.regulacao_marcacao.dto.agendamento.transporte;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.agendamento.transporte.summary.CidadeSummaryDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.agendamento.transporte.summary.LocalAgendamentoSummaryDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.agendamento.transporte.summary.MotoristaSummaryDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.agendamento.transporte.summary.SolicitacaoSummaryDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.agendamento.transporte.summary.TransporteSummaryDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.AgendamentoTransporte;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.AgendamentoTransportePaciente;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.StatusAgendamento;

public record AgendamentoTransporteViewDTO (
    Long id,
    Set<AgendamentoTransporteSolicitacaoDTO> pacientes,
    List<LocalAgendamentoSummaryDTO> local,
    CidadeSummaryDTO cidade,
    TransporteSummaryDTO transporte,
    LocalDate data,
    LocalTime horaSaida,
    MotoristaSummaryDTO motorista,
    StatusAgendamento status
) {

    public static AgendamentoTransporteViewDTO fromDTO(AgendamentoTransporte agendamentoTransporte){
        if (agendamentoTransporte == null) {
            return null;
        }

        var pacientes = agendamentoTransporte.getPacientes() == null
            ? Collections.<AgendamentoTransporteSolicitacaoDTO>emptySet()
            : agendamentoTransporte.getPacientes().stream()
                .map(AgendamentoTransporteViewDTO::mapPaciente)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        var locais = agendamentoTransporte.getLocaisAgendamento() == null
            ? Collections.<LocalAgendamentoSummaryDTO>emptyList()
            : agendamentoTransporte.getLocaisAgendamento().stream()
                .map(LocalAgendamentoSummaryDTO::fromEntity)
                .toList();

        var cidade = agendamentoTransporte.getCidade() == null ? null : CidadeSummaryDTO.fromEntity(agendamentoTransporte.getCidade());
        var transporte = agendamentoTransporte.getTransporte() == null ? null : TransporteSummaryDTO.fromEntity(agendamentoTransporte.getTransporte());
        var motorista = MotoristaSummaryDTO.fromEntity(agendamentoTransporte.getMotorista());

        return new AgendamentoTransporteViewDTO(
            agendamentoTransporte.getId(),
            pacientes,
            locais,
            cidade,
            transporte,
            agendamentoTransporte.getData(),
            agendamentoTransporte.getHoraSaida(),
            motorista,
            agendamentoTransporte.getStatus());
    }

    private static AgendamentoTransporteSolicitacaoDTO mapPaciente(AgendamentoTransportePaciente association) {
        var resumo = SolicitacaoSummaryDTO.fromEntity(association.getSolicitacao());
        var localResumo = association.getLocalAgendamento() != null
            ? LocalAgendamentoSummaryDTO.fromEntity(association.getLocalAgendamento())
            : null;
        return AgendamentoTransporteSolicitacaoDTO.fromSummary(resumo, association.getTurno(), association.getRetornaMesmoDia(), localResumo);
    }
}
