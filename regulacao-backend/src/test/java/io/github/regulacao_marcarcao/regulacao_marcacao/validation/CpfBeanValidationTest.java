package io.github.regulacao_marcarcao.regulacao_marcacao.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.hibernate.validator.constraints.br.CPF;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

/**
 * Regressao do bug: RN sem CPF recebia 400 ao cadastrar, mesmo com a
 * validacao condicional de {@code SolicitacaoService} implementada.
 *
 * Causa raiz: o frontend enviava {@code cpfPaciente: ""} (string vazia) em
 * vez de {@code null} quando o campo ficava vazio. O {@code @CPF} do
 * Hibernate Validator trata {@code null} como valido ("ausente"), mas trata
 * string vazia como CPF INVALIDO — e essa validacao roda no Bean Validation
 * do controller, antes mesmo do service ser chamado. A correcao foi no
 * frontend (enviar null, nao ""); este teste documenta o comportamento do
 * validador para que a diferenca entre null e "" nunca mais passe despercebida.
 */
class CpfBeanValidationTest {

    private static class ComCpf {
        @CPF
        String cpf;
        ComCpf(String cpf) { this.cpf = cpf; }
    }

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    @DisplayName("@CPF aceita null (campo ausente)")
    void cpfNuloEValido() {
        assertThat(VALIDATOR.validate(new ComCpf(null))).isEmpty();
    }

    @Test
    @DisplayName("@CPF RECUSA string vazia — precisa ser null, nao \"\"")
    void cpfVazioEInvalido() {
        assertThat(VALIDATOR.validate(new ComCpf(""))).isNotEmpty();
    }

    @Test
    @DisplayName("@CPF recusa string só com espaços, pelo mesmo motivo")
    void cpfSoEspacosEInvalido() {
        assertThat(VALIDATOR.validate(new ComCpf("   "))).isNotEmpty();
    }

    @Test
    @DisplayName("@CPF aceita um CPF valido")
    void cpfValidoEAceito() {
        assertThat(VALIDATOR.validate(new ComCpf("11144477735"))).isEmpty();
    }
}
