package io.github.regulacao_marcarcao.regulacao_marcacao.dto.indicadores;

import java.time.LocalDate;
import java.util.List;

import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.WhatsAppIndicadorProjections;

/**
 * Pacientes cujo aviso nao saiu por telefone invalido ou ausente, sobre os
 * pacientes cujo telefone chegou a ser avaliado.
 *
 * <p>O telefone so e avaliado quando o envio esta configurado e ligado. Com
 * {@code avaliados = 0} a leitura correta e "nao avaliado", nunca "0% invalido".
 *
 * <p>So contagens: nenhum telefone nem dado de paciente.
 */
public record WhatsAppTelefoneInvalidoViewDTO(
        LocalDate de,
        LocalDate ate,
        boolean configurado,
        boolean envioLigado,
        Linha total,
        List<Linha> porUnidade) {

    /**
     * @param id   id da unidade; nulo no total e em "Sem unidade"
     * @param nome nulo no total e em "Sem unidade"
     */
    public record Linha(Long id, String nome, long avaliados, long invalidos) {

        public static Linha from(WhatsAppIndicadorProjections.TelefoneInvalido p) {
            return new Linha(p.getId(), p.getNome(),
                    p.getAvaliados() != null ? p.getAvaliados() : 0L,
                    p.getInvalidos() != null ? p.getInvalidos() : 0L);
        }

        /** Cada paciente pertence a uma unica unidade: o total e a soma das linhas. */
        public static Linha somar(List<Linha> linhas) {
            return new Linha(null, null,
                    linhas.stream().mapToLong(Linha::avaliados).sum(),
                    linhas.stream().mapToLong(Linha::invalidos).sum());
        }
    }
}
