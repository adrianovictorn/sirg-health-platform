package io.github.regulacao_marcarcao.regulacao_marcacao.dto.solicitacaoEspecialidadeDTO;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.CotaUnidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.SolicitacaoEspecialidade;

/**
 * {@code profissionalNome} (V79) e o solicitante — quem pediu o exame. Distinto de
 * {@code profissionalExecutanteNome} (V92/V93) — quem efetivamente atende: o
 * profissional sobrescrito pelo operador no agendamento (V100), ou, na
 * ausencia deste, o vindo da cota usada. {@code adicionadoPorNome}/{@code
 * agendadoPorNome} (V93) sao autoria do operador do sistema, nao do
 * profissional de saude. Todos nulos (exibidos como "-") em registros
 * anteriores a essas versoes.
 */
public record SolicitacaoEspecialidadeViewDTO (
    Long id,
    Long solicitacaoId,
    Long agendamentoId,
    String especialidadeSolicitada,
    Long profissionalId,
    String profissionalNome,
    LocalDate dataColeta,
    String status,
    String prioridade,
    LocalDateTime dataDeCadastro,
    Long profissionalExecutanteId,
    String profissionalExecutanteNome,
    String localExecucaoNome,
    LocalTime horaInicial,
    LocalTime horaFinal,
    LocalTime horaAgendada,
    String adicionadoPorNome,
    String agendadoPorNome,
    // V109: desfecho lancado na tela de Atendimentos (observacao da falta ou motivo
    // do cancelamento, e quem lancou) e se o item ja foi devolvido a fila.
    String atendimentoObservacao,
    String atendimentoRegistradoPorNome,
    Instant atendimentoRegistradoEm,
    boolean devolvidoAFila
) {

    /**
     * Sem o registro do desfecho (V109): os campos {@code atendimento*} saem nulos.
     * Este factory atende tambem as listagens de {@code /api/especialidades}, que nao
     * checam unidade — e a observacao do executante e texto livre sobre o paciente.
     * Quem pode ve-la e quem abre a ficha: use {@link #paraFicha}.
     */
    public static SolicitacaoEspecialidadeViewDTO fromSolicitacaoEspecialidade(SolicitacaoEspecialidade se) {
        return montar(se, false);
    }

    /** Para a ficha do paciente, cujo acesso ja passou pela checagem de unidade: inclui o desfecho. */
    public static SolicitacaoEspecialidadeViewDTO paraFicha(SolicitacaoEspecialidade se) {
        return montar(se, true);
    }

    private static SolicitacaoEspecialidadeViewDTO montar(SolicitacaoEspecialidade se, boolean comDesfecho) {
        CotaUnidade cota = se.getCotaUnidade();
        var agendamento = se.getAgendamentoSolicitacao();
        String localExecucao = cota != null && cota.getLocalAgendamento() != null
                ? cota.getLocalAgendamento().getNomeLocal()
                : (agendamento != null && agendamento.getLocalAgendamento() != null
                        ? agendamento.getLocalAgendamento().getNomeLocal() : null);

        // Profissional executante (V100): o sobrescrito pelo operador no
        // agendamento tem prioridade; sem ele, cai para o espelhado pela cota.
        var profissionalExecutante = se.getProfissionalExecutante() != null
                ? se.getProfissionalExecutante()
                : (cota != null ? cota.getProfissionalExecutante() : null);

        return new SolicitacaoEspecialidadeViewDTO(
            se.getId(),
            se.getSolicitacao() != null ? se.getSolicitacao().getId() : null,
            agendamento != null ? agendamento.getId() : null,
            se.getEspecialidadeSolicitada() != null ? se.getEspecialidadeSolicitada().getCodigo() : se.getEspecialidadeCodigoLegacy(),
            se.getProfissionalSolicitante() != null ? se.getProfissionalSolicitante().getId() : null,
            se.getProfissionalSolicitante() != null ? se.getProfissionalSolicitante().getNome() : null,
            se.getDataColeta(),
            se.getStatus() != null ? se.getStatus().name() : null,
            se.getPrioridade() != null ? se.getPrioridade().name() : null,
            se.getDataDeCadastro(),
            profissionalExecutante != null ? profissionalExecutante.getId() : null,
            profissionalExecutante != null ? profissionalExecutante.getNome() : null,
            localExecucao,
            cota != null ? cota.getHoraInicial() : null,
            cota != null ? cota.getHoraFinal() : null,
            se.getHoraAgendada(),
            se.getCriadoPor() != null ? se.getCriadoPor().getNome() : null,
            agendamento != null && agendamento.getCriadoPor() != null ? agendamento.getCriadoPor().getNome() : null,
            comDesfecho ? se.getAtendimentoObservacao() : null,
            comDesfecho && se.getAtendimentoRegistradoPor() != null ? se.getAtendimentoRegistradoPor().getNome() : null,
            comDesfecho ? se.getAtendimentoRegistradoEm() : null,
            se.getDevolvidoComo() != null
        );
    }
}
