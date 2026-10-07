package io.github.regulacao_marcarcao.regulacao_marcacao.service.whatsapp;

import java.util.Set;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.WhatsAppMotivoNaoEnvio;

/**
 * Converte o telefone do cadastro (texto livre) no numero que a Meta espera:
 * so digitos, com 55 + DDD + 9 digitos.
 *
 * <p>Recupera o que e seguro recuperar (mascara, 55 ou zero na frente, celular
 * antigo sem o nono digito). NAO presume DDD: numero sem DDD poderia ir para
 * outra pessoa, e a mensagem leva dado de saude.
 */
public final class TelefoneWhatsAppNormalizador {

    private static final Set<String> DDDS = Set.of(
            "11", "12", "13", "14", "15", "16", "17", "18", "19",
            "21", "22", "24", "27", "28",
            "31", "32", "33", "34", "35", "37", "38",
            "41", "42", "43", "44", "45", "46", "47", "48", "49",
            "51", "53", "54", "55",
            "61", "62", "63", "64", "65", "66", "67", "68", "69",
            "71", "73", "74", "75", "77", "79",
            "81", "82", "83", "84", "85", "86", "87", "88", "89",
            "91", "92", "93", "94", "95", "96", "97", "98", "99");

    private TelefoneWhatsAppNormalizador() {
    }

    /** {@code numero} preenchido quando da para enviar; senao {@code motivo} diz por que nao. */
    public record Resultado(String numero, WhatsAppMotivoNaoEnvio motivo) {

        public boolean valido() {
            return numero != null;
        }

        /** Ultimos 4 digitos, unico pedaco do telefone que vai para o registro. */
        public String finalDoNumero() {
            return numero == null ? null : numero.substring(numero.length() - 4);
        }
    }

    public static Resultado normalizar(String telefone) {
        String digitos = telefone == null ? "" : telefone.replaceAll("\\D", "");
        if (digitos.isEmpty()) {
            return new Resultado(null, WhatsAppMotivoNaoEnvio.SEM_TELEFONE);
        }
        // Zero de operadora/tronco ("075 9...") e codigo do pais ja digitado.
        digitos = digitos.replaceFirst("^0+", "");
        if ((digitos.length() == 12 || digitos.length() == 13) && digitos.startsWith("55")) {
            digitos = digitos.substring(2);
        }

        if (digitos.length() == 10 && "6789".indexOf(digitos.charAt(2)) >= 0) {
            // Celular cadastrado antes do nono digito.
            digitos = digitos.substring(0, 2) + "9" + digitos.substring(2);
        }
        boolean celularComDdd = digitos.length() == 11
                && DDDS.contains(digitos.substring(0, 2))
                && digitos.charAt(2) == '9';
        if (!celularComDdd) {
            return new Resultado(null, WhatsAppMotivoNaoEnvio.TELEFONE_INVALIDO);
        }
        return new Resultado("55" + digitos, null);
    }
}
