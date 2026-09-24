package io.github.regulacao_marcarcao.regulacao_marcacao.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.cota.CotaUnidadeCreateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.cota.CotaUnidadeUpdateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.CotaUnidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Especialidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.GrupoRelatorio;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Unidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.TipoPeriodoCota;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.CotaUnidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.EspecialidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.GrupoRelatorioRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UnidadeRepository;

/**
 * Validacoes de CADASTRO de cota — as regras que recusam uma configuracao
 * incoerente antes de ela chegar ao banco.
 *
 * O comportamento de consumo fica em {@code CotaUnidadeSimulacaoTest} (sequencias
 * e concorrencia) e {@code CotaUnidadeIntegracaoIT} (SQL real).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CotaUnidadeValidacaoTest {

    private static final Long UNIDADE_ID = 1L;
    private static final Long GRUPO_UNIDADES_ID = 50L;
    private static final Long GRUPO_LABORATORIO_ID = 60L;
    private static final Long HEMOGRAMA_ID = 10L;
    private static final String PERIODO = "2026-05";

    @Mock private CotaUnidadeRepository cotaRepository;
    @Mock private UnidadeRepository unidadeRepository;
    @Mock private GrupoRelatorioRepository grupoRelatorioRepository;
    @Mock private EspecialidadeRepository especialidadeRepository;

    @InjectMocks private CotaUnidadeService service;

    private Unidade unidade;
    private GrupoRelatorio grupoLaboratorio;

    @BeforeEach
    void setUp() {
        unidade = new Unidade();
        unidade.setId(UNIDADE_ID);
        unidade.setNome("Unidade A");

        grupoLaboratorio = new GrupoRelatorio();
        grupoLaboratorio.setId(GRUPO_LABORATORIO_ID);
        grupoLaboratorio.setNome("Laboratorio");

        Especialidade hemograma = new Especialidade();
        hemograma.setId(HEMOGRAMA_ID);
        hemograma.setNome("Hemograma");

        when(unidadeRepository.findById(UNIDADE_ID)).thenReturn(Optional.of(unidade));
        when(especialidadeRepository.findById(HEMOGRAMA_ID)).thenReturn(Optional.of(hemograma));
        when(grupoRelatorioRepository.findById(GRUPO_LABORATORIO_ID)).thenReturn(Optional.of(grupoLaboratorio));
        when(cotaRepository.findByUnidadeId(UNIDADE_ID)).thenReturn(List.of());
        when(cotaRepository.save(any(CotaUnidade.class))).thenAnswer(inv -> {
            CotaUnidade c = inv.getArgument(0);
            c.setId(999L);
            return c;
        });
    }

    private CotaUnidadeCreateDTO dto(Long unidadeId, Long grupoUnidadesId,
            Long especialidadeId, Long grupoEspecialidadesId, String periodo, Integer qtd) {
        return new CotaUnidadeCreateDTO(unidadeId, grupoUnidadesId, especialidadeId,
                grupoEspecialidadesId, TipoPeriodoCota.MENSAL, periodo, null, qtd);
    }

    // ==================================================================
    // Titular — exatamente um
    // ==================================================================

    @Test
    @DisplayName("Titular ausente e recusado")
    void semTitularERecusado() {
        assertThatThrownBy(() -> service.criar(dto(null, null, HEMOGRAMA_ID, null, PERIODO, 5)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exatamente um titular");
    }

    @Test
    @DisplayName("Dois titulares ao mesmo tempo sao recusados")
    void doisTitularesSaoRecusados() {
        assertThatThrownBy(() -> service.criar(
                dto(UNIDADE_ID, GRUPO_UNIDADES_ID, HEMOGRAMA_ID, null, PERIODO, 5)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exatamente um titular");
    }

    // ==================================================================
    // Escopo — no maximo um
    // ==================================================================

    @Test
    @DisplayName("Especialidade e grupo de especialidades juntos sao recusados")
    void doisEscoposSaoRecusados() {
        assertThatThrownBy(() -> service.criar(
                dto(UNIDADE_ID, null, HEMOGRAMA_ID, GRUPO_LABORATORIO_ID, PERIODO, 5)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("apenas um escopo");
    }

    @Test
    @DisplayName("Escopo por especialidade e aceito")
    void escopoPorEspecialidadeEAceito() {
        when(especialidadeRepository.countByGrupoRelatorioId(GRUPO_LABORATORIO_ID)).thenReturn(172L);

        assertThatCode(() -> service.criar(dto(UNIDADE_ID, null, HEMOGRAMA_ID, null, PERIODO, 5)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Escopo por grupo de especialidades e aceito quando o grupo tem especialidades")
    void escopoPorGrupoEAceito() {
        when(especialidadeRepository.countByGrupoRelatorioId(GRUPO_LABORATORIO_ID)).thenReturn(172L);

        assertThatCode(() -> service.criar(dto(UNIDADE_ID, null, null, GRUPO_LABORATORIO_ID, PERIODO, 10)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Sem escopo nenhum = cota geral, aceita")
    void semEscopoECotaGeral() {
        assertThatCode(() -> service.criar(dto(UNIDADE_ID, null, null, null, PERIODO, 20)))
                .doesNotThrowAnyException();
    }

    /**
     * Cota num grupo vazio nao limitaria nada — seria uma configuracao silenciosamente
     * inofensiva, que o administrador so descobriria quando o limite nao fosse aplicado.
     */
    @Test
    @DisplayName("Cota em grupo de especialidades SEM especialidades e recusada")
    void grupoVazioERecusado() {
        when(especialidadeRepository.countByGrupoRelatorioId(GRUPO_LABORATORIO_ID)).thenReturn(0L);

        assertThatThrownBy(() -> service.criar(dto(UNIDADE_ID, null, null, GRUPO_LABORATORIO_ID, PERIODO, 10)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nao possui especialidades");
    }

    // ==================================================================
    // Periodo e quantidade
    // ==================================================================

    @Test
    @DisplayName("Periodo mensal fora do formato YYYY-MM e recusado")
    void periodoInvalidoERecusado() {
        assertThatThrownBy(() -> service.criar(dto(UNIDADE_ID, null, HEMOGRAMA_ID, null, "05/2026", 5)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("YYYY-MM");
    }

    @Test
    @DisplayName("Cota por DATA exige data especifica")
    void cotaPorDataExigeData() {
        var semData = new CotaUnidadeCreateDTO(UNIDADE_ID, null, HEMOGRAMA_ID, null,
                TipoPeriodoCota.DATA, null, null, 5);

        assertThatThrownBy(() -> service.criar(semData))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Data especifica");
    }

    @Test
    @DisplayName("Quantidade negativa e recusada")
    void quantidadeNegativaERecusada() {
        assertThatThrownBy(() -> service.criar(dto(UNIDADE_ID, null, HEMOGRAMA_ID, null, PERIODO, -1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maior ou igual a zero");
    }

    // ==================================================================
    // Duplicidade
    // ==================================================================

    @Test
    @DisplayName("Cota duplicada (mesmo titular, escopo e periodo) e recusada")
    void duplicadaERecusada() {
        Especialidade hemograma = new Especialidade();
        hemograma.setId(HEMOGRAMA_ID);
        hemograma.setNome("Hemograma");

        CotaUnidade existente = new CotaUnidade();
        existente.setId(1L);
        existente.setUnidade(unidade);
        existente.setEspecialidade(hemograma);
        existente.setTipoPeriodo(TipoPeriodoCota.MENSAL);
        existente.setPeriodo(PERIODO);
        existente.setQuantidadeTotal(5);
        existente.setQuantidadeUtilizada(0);
        existente.setAtivo(true);

        when(cotaRepository.findByUnidadeId(UNIDADE_ID)).thenReturn(List.of(existente));

        assertThatThrownBy(() -> service.criar(dto(UNIDADE_ID, null, HEMOGRAMA_ID, null, PERIODO, 5)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Ja existe cota");
    }

    @Test
    @DisplayName("Cota de grupo nao colide com cota de especialidade do mesmo periodo")
    void grupoEEspecialidadeNaoColidem() {
        Especialidade hemograma = new Especialidade();
        hemograma.setId(HEMOGRAMA_ID);
        hemograma.setNome("Hemograma");

        CotaUnidade daEspecialidade = new CotaUnidade();
        daEspecialidade.setId(1L);
        daEspecialidade.setUnidade(unidade);
        daEspecialidade.setEspecialidade(hemograma);
        daEspecialidade.setTipoPeriodo(TipoPeriodoCota.MENSAL);
        daEspecialidade.setPeriodo(PERIODO);
        daEspecialidade.setQuantidadeTotal(2);
        daEspecialidade.setQuantidadeUtilizada(0);
        daEspecialidade.setAtivo(true);

        when(cotaRepository.findByUnidadeId(UNIDADE_ID)).thenReturn(List.of(daEspecialidade));
        when(especialidadeRepository.countByGrupoRelatorioId(GRUPO_LABORATORIO_ID)).thenReturn(172L);

        // Escopos diferentes: a cota do grupo pode conviver com a da especialidade
        assertThatCode(() -> service.criar(dto(UNIDADE_ID, null, null, GRUPO_LABORATORIO_ID, PERIODO, 10)))
                .doesNotThrowAnyException();
    }

    // ==================================================================
    // Atualizacao
    // ==================================================================

    @Test
    @DisplayName("Reduzir a cota abaixo do ja utilizado e recusado")
    void naoPermiteReduzirAbaixoDoUtilizado() {
        CotaUnidade cota = new CotaUnidade();
        cota.setId(100L);
        cota.setUnidade(unidade);
        cota.setQuantidadeTotal(5);
        cota.setQuantidadeUtilizada(4);
        cota.setAtivo(true);
        when(cotaRepository.findById(100L)).thenReturn(Optional.of(cota));

        assertThatThrownBy(() -> service.atualizar(100L, new CotaUnidadeUpdateDTO(2, true)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ja utilizado");

        verify(cotaRepository, never()).save(any(CotaUnidade.class));
    }

    @Test
    @DisplayName("Estorno sem unidade ou sem data nao faz nada")
    void estornoSemParametrosNaoFazNada() {
        service.estornarUtilizacao(null, HEMOGRAMA_ID, LocalDate.now());
        service.estornarUtilizacao(UNIDADE_ID, HEMOGRAMA_ID, null);

        verify(cotaRepository, never()).devolverVaga(any());
    }
}
