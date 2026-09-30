package io.github.regulacao_marcarcao.regulacao_marcacao.dto.agendamentoDTO;

import java.time.LocalTime;
import java.util.List;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.AgendamentoSolicitacao;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.SolicitacaoEspecialidade;

public record AgendamentoSolicitacaoSimpleViewDTO(
    Long id,
    String localAgendado,
    String dataAgendada,
    String observacoes,
    String turno,
    List<EspecialidadeAgendadaViewDTO> especialidades
) {
    /**
     * {@code horaAgendada} (V97/V99): hora efetiva do paciente para este
     * exame — calculada automaticamente (cota de horario dinamico), informada
     * manualmente pelo operador, ou o override do operador quando a cota e
     * dinamica (V100). Nula quando a cota nao define horario e o operador nao
     * informou.
     *
     * {@code profissionalExecutanteNome} (V100): profissional sobrescrito pelo
     * operador no agendamento, ou, na ausencia, o espelhado pela cota usada.
     * Nulo quando nem o operador nem a cota definem profissional.
     */
    public record EspecialidadeAgendadaViewDTO(String codigo, String nome, LocalTime horaAgendada,
            String profissionalExecutanteNome) {
    }

    public static AgendamentoSolicitacaoSimpleViewDTO fromAgendamentoSolicitacao(AgendamentoSolicitacao agendamento) {
        return fromAgendamentoSolicitacao(agendamento, List.of());
    }

    /**
     * Usado pelos pontos de criacao do agendamento (que ja tem a lista de
     * especialidades agendadas em mao, via query explicita — evita depender
     * da colecao LAZY {@code AgendamentoSolicitacao.especialidades}).
     * {@code listAll}/{@code getById} continuam usando o factory de 1
     * argumento, sem essa lista, para nao introduzir N+1 onde nao foi pedido.
     */
    public static AgendamentoSolicitacaoSimpleViewDTO fromAgendamentoSolicitacao(
            AgendamentoSolicitacao agendamento, List<SolicitacaoEspecialidade> especialidadesAgendadas) {
        return new AgendamentoSolicitacaoSimpleViewDTO(
            agendamento.getId(),
            resolveLocal(agendamento),
            agendamento.getDataAgendada() != null ? agendamento.getDataAgendada().toString() : null,
            agendamento.getObservacoes(),
            agendamento.getTurno() != null ? agendamento.getTurno().toString() : null,
            especialidadesAgendadas.stream()
                .map(se -> {
                    var cota = se.getCotaUnidade();
                    var profissionalExecutante = se.getProfissionalExecutante() != null
                            ? se.getProfissionalExecutante()
                            : (cota != null ? cota.getProfissionalExecutante() : null);
                    return new EspecialidadeAgendadaViewDTO(
                        se.getEspecialidadeSolicitada() != null
                                ? se.getEspecialidadeSolicitada().getCodigo() : se.getEspecialidadeCodigoLegacy(),
                        se.getEspecialidadeSolicitada() != null ? se.getEspecialidadeSolicitada().getNome() : null,
                        se.getHoraAgendada(),
                        profissionalExecutante != null ? profissionalExecutante.getNome() : null);
                })
                .toList()
        );
    }

    private static String resolveLocal(AgendamentoSolicitacao agendamento) {
        if (agendamento.getLocalAgendamento() != null) {
            return agendamento.getLocalAgendamento().getNomeLocal();
        }
        if (agendamento.getLocalAgendado() != null) {
            return agendamento.getLocalAgendado().name().replace('_', ' ');
        }
        return null;
    }
}
