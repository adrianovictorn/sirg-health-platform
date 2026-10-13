package io.github.regulacao_marcarcao.regulacao_marcacao.dto.notificacao;

import java.time.LocalDate;

/** Mensagem da federacao: o agendamento de um paciente do municipio destino mudou de data. */
public record AgendamentoRemarcadoMensagemDTO(
        Long solicitacaoIdDestino,
        String cpfMascarado,
        String nomePaciente,
        String municipioExecutor,
        LocalDate dataAnterior,
        LocalDate dataNova
) {}
