package io.github.regulacao_marcarcao.regulacao_marcacao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import io.github.regulacao_marcarcao.regulacao_marcacao.config.InstanceContext;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.relatorio.PlanilhaAgendadosFiltroDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.AtendimentoExecutanteRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.SolicitacaoEspecialidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UserRepository;

/**
 * Validacao dos filtros da planilha de agendados (sem banco): data unica OU periodo
 * de ate 31 dias, profissional e "sem executante" excludentes, acesso conferido
 * antes da validacao e o IN do filtro de executante nunca vazio.
 */
@ExtendWith(MockitoExtension.class)
class PlanilhaAgendadosValidacaoTest {

    private static final String CPF = "00000000000";
    private static final String GRUPO = "LAB";
    private static final LocalDate DIA = LocalDate.of(2026, 3, 10);

    @Mock private SolicitacaoEspecialidadeRepository especialidadeRepository;
    @Mock private AtendimentoExecutanteRepository atendimentoRepository;
    @Mock private UserRepository userRepository;
    @Mock private InstanceContext instanceContext;
    @Mock private UnidadeAcessoService unidadeAcessoService;

    @InjectMocks private ExcelService excelService;

    private static PlanilhaAgendadosFiltroDTO periodo(LocalDate inicio, LocalDate fim) {
        return new PlanilhaAgendadosFiltroDTO(GRUPO, null, inicio, fim, null, null, null, false);
    }

    private void assertRecusado(PlanilhaAgendadosFiltroDTO filtro, String trecho) {
        assertThatThrownBy(() -> excelService.haDadosParaRelatorio(filtro, CPF))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(trecho);
        assertThatThrownBy(() -> excelService.gerarPlanilhaAgendamentos(filtro, CPF))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(trecho);
    }

    @Test
    @DisplayName("Fim antes do inicio e recusado com 400")
    void fimAntesDoInicio() {
        assertRecusado(periodo(DIA, DIA.minusDays(1)), "anterior ao início");
    }

    @Test
    @DisplayName("31 dias inclusivos passam; 32 sao recusados")
    void limiteDe31Dias() {
        when(especialidadeRepository.countAgendados(any(), any(), any(), any(), anyBoolean(), any(), any(),
                anyBoolean())).thenReturn(0L);

        assertThat(excelService.haDadosParaRelatorio(periodo(DIA, DIA.plusDays(30)), CPF)).isFalse();
        assertRecusado(periodo(DIA, DIA.plusDays(31)), "no máximo 31 dias");
    }

    @Test
    @DisplayName("Data unica junto com periodo e recusada")
    void dataEPeriodoJuntos() {
        assertRecusado(new PlanilhaAgendadosFiltroDTO(GRUPO, DIA, DIA, DIA, null, null, null, false),
                "não os dois");
    }

    @Test
    @DisplayName("Sem data nem periodo completo e recusado")
    void semData() {
        assertRecusado(periodo(null, null), "Informe a data");
        assertRecusado(periodo(DIA, null), "Informe a data");
    }

    @Test
    @DisplayName("Profissional junto com 'sem executante' e recusado")
    void profissionalESemExecutante() {
        assertRecusado(new PlanilhaAgendadosFiltroDTO(GRUPO, DIA, null, null, null, null, 7L, true),
                "sem executante");
    }

    @Test
    @DisplayName("Data unica vai ao repositorio como inicio = fim, sem executante e com sentinela no IN")
    void dataUnicaViraPeriodoDeUmDia() {
        when(unidadeAcessoService.resolverUnidadeAlvo(CPF, null)).thenReturn(null);
        when(especialidadeRepository.countAgendados(any(), any(), any(), any(), anyBoolean(), any(), any(),
                anyBoolean())).thenReturn(1L);

        assertThat(excelService.haDadosParaRelatorio(GRUPO, DIA, null, CPF)).isTrue();

        verify(especialidadeRepository).countAgendados(eq(GRUPO), eq(DIA), eq(DIA), isNull(), eq(false),
                eq(List.of(-1L)), isNull(), eq(false));
    }

    @Test
    @DisplayName("Unidade executante liga o recorte e vai sozinha no IN")
    void unidadeExecutanteNoFiltro() {
        when(unidadeAcessoService.resolverUnidadeAlvo(CPF, 3L)).thenReturn(3L);
        when(especialidadeRepository.countAgendados(any(), any(), any(), any(), anyBoolean(), any(), any(),
                anyBoolean())).thenReturn(0L);

        excelService.haDadosParaRelatorio(
                new PlanilhaAgendadosFiltroDTO(GRUPO, DIA, null, null, 3L, 9L, 5L, false), CPF);

        verify(especialidadeRepository).countAgendados(eq(GRUPO), eq(DIA), eq(DIA), eq(3L), eq(true),
                eq(List.of(9L)), eq(5L), eq(false));
    }

    @Test
    @DisplayName("Escopo de acesso e conferido antes da validacao: 403 vence o 400")
    void acessoAntesDaValidacao() {
        when(unidadeAcessoService.resolverUnidadeAlvo(CPF, 2L))
                .thenThrow(new AccessDeniedException("Acesso negado aos dados de outra unidade."));
        PlanilhaAgendadosFiltroDTO invalidoEOutraUnidade =
                new PlanilhaAgendadosFiltroDTO(GRUPO, null, DIA, DIA.minusDays(5), 2L, null, null, false);

        assertThatThrownBy(() -> excelService.gerarPlanilhaAgendamentos(invalidoEOutraUnidade, CPF))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> excelService.haDadosParaRelatorio(invalidoEOutraUnidade, CPF))
                .isInstanceOf(AccessDeniedException.class);

        verify(unidadeAcessoService, times(2)).resolverUnidadeAlvo(CPF, 2L);
        verify(especialidadeRepository, never()).countAgendados(any(), any(), any(), any(), anyBoolean(),
                any(), any(), anyBoolean());
    }
}
