package io.github.regulacao_marcarcao.regulacao_marcacao.dto.atendimento;

/** Cancelamento pelo executante. {@code motivo} e obrigatorio (validado no service). */
public record AtendimentoCancelamentoDTO(String motivo) {
}
