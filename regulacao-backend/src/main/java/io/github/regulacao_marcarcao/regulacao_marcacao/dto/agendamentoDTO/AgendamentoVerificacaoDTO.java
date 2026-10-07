package io.github.regulacao_marcarcao.regulacao_marcacao.dto.agendamentoDTO;

import java.util.List;

/**
 * Resultado da pre-verificacao de um agendamento em lote: o que o POST faria
 * com cada item, sem gravar nada. A tela usa isto para perguntar ao operador se
 * agenda so os itens possiveis antes de enviar.
 *
 * <p>E consultivo: quem decide de fato e o POST, que revalida tudo.
 *
 * <p>{@code itens} segue a ordem de {@code examesSelecionados}.
 *
 * <p>{@code bloqueioDoLote}: preenchido quando o conjunto de itens agendaveis
 * nao passa por uma regra do agendamento inteiro (teto financeiro). Nulo quando
 * nao ha bloqueio. Nunca cita valor em reais.
 */
public record AgendamentoVerificacaoDTO(List<ItemVerificadoDTO> itens, String bloqueioDoLote) {

    /**
     * {@code corrigivel}: true quando o item so nao entra por um dado que o
     * operador ajusta na propria tela (profissional, cota escolhida, hora) —
     * a tela pede a correcao em vez de oferecer agendar sem ele. False quando
     * falta vaga, cota ou o item nao esta mais pendente.
     */
    public record ItemVerificadoDTO(String codigo, String nome, boolean podeAgendar, boolean corrigivel,
            String motivo) {

        public static ItemVerificadoDTO viavel(String codigo, String nome) {
            return new ItemVerificadoDTO(codigo, nome, true, false, null);
        }

        public static ItemVerificadoDTO recusado(String codigo, String nome, String motivo) {
            return new ItemVerificadoDTO(codigo, nome, false, false, motivo);
        }

        public static ItemVerificadoDTO corrigivel(String codigo, String nome, String motivo) {
            return new ItemVerificadoDTO(codigo, nome, false, true, motivo);
        }
    }
}
