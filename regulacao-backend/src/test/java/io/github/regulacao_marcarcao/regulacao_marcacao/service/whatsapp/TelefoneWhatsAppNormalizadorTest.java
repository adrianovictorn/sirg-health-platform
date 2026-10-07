package io.github.regulacao_marcarcao.regulacao_marcacao.service.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.WhatsAppMotivoNaoEnvio;

/**
 * O telefone do cadastro e texto livre. Numero errado aqui e dado de saude no
 * celular de outra pessoa: so se recupera o que e seguro.
 */
class TelefoneWhatsAppNormalizadorTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "75999990000        | 5575999990000",
            "(75) 99999-0000    | 5575999990000",
            "75 9 9999 0000     | 5575999990000",
            "5575999990000      | 5575999990000",
            "+55 (75) 99999-0000| 5575999990000",
            "075999990000       | 5575999990000",
            "7599990000         | 5575999990000",
            "7588880000         | 5575988880000",
            "557599990000       | 5575999990000",
    })
    void recuperaOQueESeguro(String cadastro, String esperado) {
        var resultado = TelefoneWhatsAppNormalizador.normalizar(cadastro);

        assertThat(resultado.valido()).isTrue();
        assertThat(resultado.numero()).isEqualTo(esperado);
        assertThat(resultado.motivo()).isNull();
        assertThat(resultado.finalDoNumero()).isEqualTo("0000");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "999990000",       // sem DDD
            "99990000",        // sem DDD e sem nono digito
            "7533330000",      // fixo
            "(75) 3333-0000",  // fixo com mascara
            "00999990000",     // DDD inexistente depois de tirar os zeros
            "20999990000",     // DDD inexistente
            "75899990000",     // 11 digitos sem o 9 na frente
            "759999900001",    // digito a mais
            "5575999990000123" // lixo no fim
    })
    void naoPresumeNemAdivinha(String cadastro) {
        var resultado = TelefoneWhatsAppNormalizador.normalizar(cadastro);

        assertThat(resultado.valido()).isFalse();
        assertThat(resultado.numero()).isNull();
        assertThat(resultado.motivo()).isEqualTo(WhatsAppMotivoNaoEnvio.TELEFONE_INVALIDO);
        assertThat(resultado.finalDoNumero()).isNull();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { "   ", "nao tem", "---" })
    void semDigitoNenhumESemTelefone(String cadastro) {
        assertThat(TelefoneWhatsAppNormalizador.normalizar(cadastro).motivo())
                .isEqualTo(WhatsAppMotivoNaoEnvio.SEM_TELEFONE);
    }

    @Test
    void soZerosNaoEstoura() {
        assertThat(TelefoneWhatsAppNormalizador.normalizar("0000").valido()).isFalse();
    }
}
