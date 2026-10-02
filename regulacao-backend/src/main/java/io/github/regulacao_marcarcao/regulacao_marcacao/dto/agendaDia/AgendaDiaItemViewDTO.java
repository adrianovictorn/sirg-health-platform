package io.github.regulacao_marcarcao.regulacao_marcacao.dto.agendaDia;

import java.time.LocalDate;

import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.AgendaDiaItemProjection;

/** Linha nominal da agenda do dia consolidada. CPF e CNS saem mascarados. */
public record AgendaDiaItemViewDTO(
        Long id,
        Long solicitacaoId,
        String nomePaciente,
        String cpfMascarado,
        String cnsMascarado,
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
                mascarar(p.getCpfPaciente(), 2),
                mascarar(p.getCns(), 4),
                p.getDataNascimento(),
                p.getUnidadeNome(),
                p.getUsfOrigem(),
                p.getEspecialidadeNome(),
                p.getStatus(),
                p.getTurno(),
                p.getHoraAgendada());
    }

    /** Mantem so os ultimos {@code visiveis} digitos; o restante vira '*'. */
    static String mascarar(String valor, int visiveis) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String digitos = valor.replaceAll("\\D", "");
        if (digitos.length() <= visiveis) {
            return "*".repeat(digitos.length());
        }
        return "*".repeat(digitos.length() - visiveis) + digitos.substring(digitos.length() - visiveis);
    }
}
