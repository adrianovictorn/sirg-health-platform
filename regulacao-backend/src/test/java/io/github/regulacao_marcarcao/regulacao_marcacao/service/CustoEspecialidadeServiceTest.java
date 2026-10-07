package io.github.regulacao_marcarcao.regulacao_marcacao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.custo.EspecialidadeCustoUpdateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Especialidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.User;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.ItemCategoria;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.OrigemValorEspecialidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.EspecialidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;

/**
 * Preco e codigo SUS: regras de formato e o que a edicao manual grava.
 *
 * <p>As regras de formato sao compartilhadas com a importacao de planilha, por
 * isso sao testadas aqui, direto nos metodos estaticos.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CustoEspecialidadeServiceTest {

    @Mock private EspecialidadeRepository especialidadeRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks private CustoEspecialidadeService service;

    private Especialidade hemograma() {
        Especialidade e = new Especialidade();
        e.setId(1L);
        e.setCodigo("HEMOGRAMA_COMPLETO");
        e.setNome("Hemograma Completo");
        e.setCategoria(ItemCategoria.EXAME_OU_PROCEDIMENTO);
        e.setAtivo(true);
        when(especialidadeRepository.findById(1L)).thenReturn(Optional.of(e));
        when(especialidadeRepository.save(any(Especialidade.class))).thenAnswer(inv -> inv.getArgument(0));
        return e;
    }

    // ------------------------------------------------------------------
    // Codigo SUS
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Codigo SUS com 10 digitos e mantido, inclusive o zero a esquerda")
    void codigoSusCom10Digitos() {
        assertThat(CustoEspecialidadeService.normalizarCodigoSus("0202020380")).isEqualTo("0202020380");
        assertThat(CustoEspecialidadeService.normalizarCodigoSus(" 02.02.02.038-0 ")).isEqualTo("0202020380");
    }

    @Test
    @DisplayName("Codigo SUS com 9 digitos (zero comido pela planilha) recebe o zero de volta")
    void codigoSusCom9DigitosRecebeZero() {
        assertThat(CustoEspecialidadeService.normalizarCodigoSus("202020380")).isEqualTo("0202020380");
    }

    @Test
    @DisplayName("Codigo SUS vazio vira nulo (campo opcional)")
    void codigoSusVazioViraNulo() {
        assertThat(CustoEspecialidadeService.normalizarCodigoSus(null)).isNull();
        assertThat(CustoEspecialidadeService.normalizarCodigoSus("   ")).isNull();
    }

    @Test
    @DisplayName("Codigo SUS de outro tamanho ou com letras e recusado, nunca completado com zeros")
    void codigoSusInvalidoERecusado() {
        for (String invalido : new String[] {"123", "12345678", "12345678901", "ABC1234567", "0202O20380"}) {
            assertThatThrownBy(() -> CustoEspecialidadeService.normalizarCodigoSus(invalido))
                    .as(invalido)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Código SUS inválido");
        }
    }

    // ------------------------------------------------------------------
    // Valor
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Valor e gravado com duas casas; nulo continua nulo (sem preco nao e zero)")
    void valorComDuasCasas() {
        assertThat(CustoEspecialidadeService.normalizarValor(new BigDecimal("4.11"))).isEqualByComparingTo("4.11");
        assertThat(CustoEspecialidadeService.normalizarValor(new BigDecimal("30"))).hasToString("30.00");
        assertThat(CustoEspecialidadeService.normalizarValor(new BigDecimal("2.500"))).hasToString("2.50");
        assertThat(CustoEspecialidadeService.normalizarValor(BigDecimal.ZERO)).hasToString("0.00");
        assertThat(CustoEspecialidadeService.normalizarValor(null)).isNull();
    }

    @Test
    @DisplayName("Valor negativo, com mais de duas casas ou grande demais e recusado")
    void valorInvalidoERecusado() {
        assertThatThrownBy(() -> CustoEspecialidadeService.normalizarValor(new BigDecimal("-0.01")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("negativo");
        assertThatThrownBy(() -> CustoEspecialidadeService.normalizarValor(new BigDecimal("4.115")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("duas casas");
        assertThatThrownBy(() -> CustoEspecialidadeService.normalizarValor(new BigDecimal("99999999999")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ------------------------------------------------------------------
    // Edicao manual
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Editar grava valor, codigo normalizado, origem MANUAL e autoria")
    void editarGravaValorCodigoOrigemEAutoria() {
        Especialidade e = hemograma();
        User admin = new User();
        admin.setNome("Admin");
        when(userRepository.findByCpf("111")).thenReturn(Optional.of(admin));

        var view = service.atualizar(1L, new EspecialidadeCustoUpdateDTO("202020380", new BigDecimal("4.11")), "111");

        assertThat(view.codigoSus()).isEqualTo("0202020380");
        assertThat(view.valorUnitario()).isEqualByComparingTo("4.11");
        assertThat(view.valorOrigem()).isEqualTo(OrigemValorEspecialidade.MANUAL);
        assertThat(view.valorAtualizadoPorNome()).isEqualTo("Admin");
        assertThat(e.getValorAtualizadoEm()).isNotNull();
    }

    @Test
    @DisplayName("Editar so o codigo SUS nao muda a origem nem a autoria do preco")
    void editarSoCodigoPreservaOrigemDoPreco() {
        Especialidade e = hemograma();
        e.setValorUnitario(new BigDecimal("4.11"));
        e.setValorOrigem(OrigemValorEspecialidade.IMPORTACAO);

        service.atualizar(1L, new EspecialidadeCustoUpdateDTO("0202020380", new BigDecimal("4.1100")), "111");

        assertThat(e.getValorOrigem()).isEqualTo(OrigemValorEspecialidade.IMPORTACAO);
        assertThat(e.getValorAtualizadoEm()).isNull();
        assertThat(e.getCodigoSus()).isEqualTo("0202020380");
    }

    @Test
    @DisplayName("Enviar valor nulo limpa o preco (volta a sem preco) e a origem")
    void valorNuloLimpaOPreco() {
        Especialidade e = hemograma();
        e.setValorUnitario(new BigDecimal("4.11"));
        e.setValorOrigem(OrigemValorEspecialidade.MANUAL);

        service.atualizar(1L, new EspecialidadeCustoUpdateDTO(null, null), "111");

        assertThat(e.getValorUnitario()).isNull();
        assertThat(e.getValorOrigem()).isNull();
        assertThat(e.getCodigoSus()).isNull();
    }

    @Test
    @DisplayName("Formato invalido e recusado sem gravar nada")
    void formatoInvalidoNaoGrava() {
        hemograma();

        assertThatThrownBy(() -> service.atualizar(1L,
                new EspecialidadeCustoUpdateDTO("123", new BigDecimal("4.11")), "111"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.atualizar(1L,
                new EspecialidadeCustoUpdateDTO("0202020380", new BigDecimal("-1")), "111"))
                .isInstanceOf(IllegalArgumentException.class);

        verify(especialidadeRepository, never()).save(any());
    }

    @Test
    @DisplayName("Especialidade inexistente devolve 404")
    void especialidadeInexistente() {
        when(especialidadeRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.atualizar(99L,
                new EspecialidadeCustoUpdateDTO(null, BigDecimal.ONE), "111"))
                .isInstanceOf(EntityNotFoundException.class);
    }
}
