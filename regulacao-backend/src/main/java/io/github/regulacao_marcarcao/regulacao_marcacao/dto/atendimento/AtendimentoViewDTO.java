package io.github.regulacao_marcarcao.regulacao_marcacao.dto.atendimento;

import java.time.Instant;
import java.time.LocalDate;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.SolicitacaoEspecialidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.StatusDaMarcacao;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.AtendimentoProjection;

/**
 * Um item agendado para o dia, dentro do bloco do paciente
 * ({@link AtendimentoPacienteViewDTO}). Nao leva nome nem documento: e tambem a
 * resposta de cada acao de desfecho, que assim nao trafega CPF e CNS a toa.
 *
 * {@code observacao}, {@code registradoPorNome} e {@code registradoEm} descrevem o
 * desfecho lancado nesta tela (V109) e so saem enquanto o item esta em FALTOU,
 * CANCELADO ou REALIZADO. {@code podeDesfazer} e a regra do servidor: desfecho
 * lancado aqui e ainda nao devolvido a fila pela unidade.
 */
public record AtendimentoViewDTO(
        Long id,
        Long solicitacaoId,
        String especialidadeNome,
        String categoria,
        String status,
        String turno,
        String horaAgendada,
        LocalDate dataColeta,
        String observacao,
        String registradoPorNome,
        Instant registradoEm,
        boolean podeDesfazer) {

    public static AtendimentoViewDTO from(AtendimentoProjection p) {
        boolean comDesfecho = temDesfecho(p.getStatus());
        return new AtendimentoViewDTO(
                p.getSolicitacaoEspecialidadeId(),
                p.getSolicitacaoId(),
                p.getEspecialidadeNome(),
                p.getCategoria(),
                p.getStatus(),
                p.getTurno(),
                p.getHoraAgendada(),
                p.getDataColeta(),
                comDesfecho ? p.getObservacao() : null,
                comDesfecho ? p.getRegistradoPorNome() : null,
                comDesfecho ? p.getRegistradoEm() : null,
                podeDesfazer(p.getStatus(), p.getRegistradoEm(), p.getDevolvidoComoItemId() != null));
    }

    /** Mesmo conteudo de {@link #from(AtendimentoProjection)}, a partir do item que uma acao acabou de alterar. */
    public static AtendimentoViewDTO from(SolicitacaoEspecialidade se) {
        var agendamento = se.getAgendamentoSolicitacao();
        var especialidade = se.getEspecialidadeSolicitada();
        String status = se.getStatus() != null ? se.getStatus().name() : null;
        boolean comDesfecho = temDesfecho(status);
        return new AtendimentoViewDTO(
                se.getId(),
                se.getSolicitacao().getId(),
                especialidade != null ? especialidade.getNome() : se.getEspecialidadeCodigoLegacy(),
                especialidade != null && especialidade.getCategoria() != null
                        ? especialidade.getCategoria().name() : null,
                status,
                agendamento != null && agendamento.getTurno() != null ? agendamento.getTurno().name() : null,
                se.getHoraAgendada() != null ? se.getHoraAgendada().toString() : null,
                se.getDataColeta(),
                comDesfecho ? se.getAtendimentoObservacao() : null,
                comDesfecho && se.getAtendimentoRegistradoPor() != null
                        ? se.getAtendimentoRegistradoPor().getNome() : null,
                comDesfecho ? se.getAtendimentoRegistradoEm() : null,
                podeDesfazer(status, se.getAtendimentoRegistradoEm(), se.getDevolvidoComo() != null));
    }

    private static boolean temDesfecho(String status) {
        return StatusDaMarcacao.REALIZADO.name().equals(status)
                || StatusDaMarcacao.FALTOU.name().equals(status)
                || StatusDaMarcacao.CANCELADO.name().equals(status);
    }

    private static boolean podeDesfazer(String status, Instant registradoEm, boolean devolvido) {
        return temDesfecho(status) && registradoEm != null && !devolvido;
    }
}
