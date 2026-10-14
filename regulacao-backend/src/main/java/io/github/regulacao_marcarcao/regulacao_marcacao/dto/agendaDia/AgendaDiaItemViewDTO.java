package io.github.regulacao_marcarcao.regulacao_marcacao.dto.agendaDia;

import java.time.LocalDate;

import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.AgendaDiaItemProjection;

/** Linha nominal da agenda do dia consolidada. CPF e CNS saem completos, como estao gravados (ADR 0010). */
public record AgendaDiaItemViewDTO(
        Long id,
        Long solicitacaoId,
        String nomePaciente,
        String cpf,
        String cns,
        LocalDate dataNascimento,
        String unidadeNome,
        String usfOrigem,
        String especialidadeNome,
        String status,
        String turno,
        String horaAgendada) {

    public static AgendaDiaItemViewDTO from(AgendaDiaItemProjection p) {
        return new AgendaDiaItemViewDTO(
                p.getSolicitacaoEspecialidadeId(),
                p.getSolicitacaoId(),
                p.getNomePaciente(),
                p.getCpfPaciente(),
                p.getCns(),
                p.getDataNascimento(),
                p.getUnidadeNome(),
                p.getUsfOrigem(),
                p.getEspecialidadeNome(),
                p.getStatus(),
                p.getTurno(),
                p.getHoraAgendada());
    }
}
