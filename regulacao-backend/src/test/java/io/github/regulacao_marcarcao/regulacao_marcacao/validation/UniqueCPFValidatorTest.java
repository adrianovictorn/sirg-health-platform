package io.github.regulacao_marcarcao.regulacao_marcacao.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Solicitacao;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.SolicitacaoRepository;

/**
 * Teste de CARACTERIZACAO (ajuste "CPF opcional para RN") — este validador NAO
 * muda neste ajuste; o objetivo e ter uma rede de seguranca contra regressao
 * acidental, ja que ele passa a ser usado com CPF nulo com muito mais
 * frequencia (pacientes RN).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UniqueCPFValidatorTest {

    @Mock private SolicitacaoRepository solicitacaoRepository;

    private UniqueCPFValidator novoValidador() {
        return new UniqueCPFValidator(solicitacaoRepository);
    }

    @Test
    @DisplayName("CPF nulo e considerado valido (obrigatoriedade e responsabilidade do @NotBlank/regra de negocio)")
    void cpfNuloEValido() {
        assertThat(novoValidador().isValid(null, null)).isTrue();
    }

    @Test
    @DisplayName("CPF em branco e considerado valido, pelo mesmo motivo")
    void cpfEmBrancoEValido() {
        assertThat(novoValidador().isValid("   ", null)).isTrue();
    }

    @Test
    @DisplayName("CPF que nao existe em nenhuma outra solicitacao e valido (unico)")
    void cpfInexistenteEValido() {
        when(solicitacaoRepository.findByCpfPacienteSemPonto("12345678900")).thenReturn(List.of());

        assertThat(novoValidador().isValid("123.456.789-00", null)).isTrue();
    }

    @Test
    @DisplayName("CPF ja cadastrado em outra solicitacao e invalido (duplicado)")
    void cpfDuplicadoEInvalido() {
        when(solicitacaoRepository.findByCpfPacienteSemPonto("12345678900"))
                .thenReturn(List.of(new Solicitacao()));

        assertThat(novoValidador().isValid("123.456.789-00", null)).isFalse();
    }
}
