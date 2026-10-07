package io.github.regulacao_marcarcao.regulacao_marcacao.dto.indicadores;

import java.time.LocalDate;
import java.util.Map;

/**
 * Alcance do aviso de agendamento pelo WhatsApp, em <b>agendamentos</b>.
 *
 * <p>O universo sao os agendamentos que tiveram mensagem de confirmacao ou de
 * remarcacao registrada no periodo. Agendamento feito enquanto o WhatsApp nao
 * estava configurado na instancia nao gera registro e fica fora.
 *
 * <p>So contagens: nenhum telefone, mensagem ou dado de paciente.
 *
 * @param configurado           a instancia tem credenciais do WhatsApp
 * @param envioLigado           o envio esta ligado pelo administrador
 * @param agendamentos          total do universo
 * @param alcancados            a mensagem chegou ao aparelho (entregue ou lida)
 * @param lidos                 dos alcancados, os que leram
 * @param aceitasSemConfirmacao aceitas pela Meta, sem confirmacao de entrega ate agora
 * @param pendentes             ainda na fila de envio
 * @param falhas                a Meta recusou ou o envio falhou
 * @param naoEnviadosPorMotivo  nao enviadas, pelo motivo da mensagem mais recente (chave = motivo)
 */
public record WhatsAppAlcanceViewDTO(
        LocalDate de,
        LocalDate ate,
        boolean configurado,
        boolean envioLigado,
        long agendamentos,
        long alcancados,
        long lidos,
        long aceitasSemConfirmacao,
        long pendentes,
        long falhas,
        Map<String, Long> naoEnviadosPorMotivo) {
}
