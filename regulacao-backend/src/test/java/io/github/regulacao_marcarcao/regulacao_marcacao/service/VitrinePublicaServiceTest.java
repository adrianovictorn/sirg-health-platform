package io.github.regulacao_marcarcao.regulacao_marcacao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.dao.DataAccessResourceFailureException;

import io.github.regulacao_marcarcao.regulacao_marcacao.config.InstanceContext;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.vitrine.VitrinePublicaViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.VitrinePublicaRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.VitrineTotaisProjection;

/**
 * Cache de uma hora e supressao de contagens pequenas do resumo publico — as
 * duas protecoes de uma rota que qualquer visitante anonimo alcanca.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VitrinePublicaServiceTest {

    private static final Instant INICIO = Instant.parse("2026-10-09T12:00:00Z");
    private static final long LIMIAR = 10;

    @Mock private VitrinePublicaRepository repository;
    @Mock private InstanceContext instanceContext;
    @Mock private ObjectProvider<BuildProperties> buildProperties;
    @Mock private Clock clock;

    private VitrinePublicaService service;

    private record Totais(Long solicitacoes, Long agendamentos, Long pacientesAtendidos,
            Long viagensTransporte, Long unidades, Long especialidades) implements VitrineTotaisProjection {
        @Override public Long getSolicitacoes() { return solicitacoes; }
        @Override public Long getAgendamentos() { return agendamentos; }
        @Override public Long getPacientesAtendidos() { return pacientesAtendidos; }
        @Override public Long getViagensTransporte() { return viagensTransporte; }
        @Override public Long getUnidades() { return unidades; }
        @Override public Long getEspecialidades() { return especialidades; }
    }

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(INICIO);
        when(instanceContext.getNomeIdentificador()).thenReturn("SAO_FELIPE");
        when(repository.totais()).thenReturn(new Totais(5000L, 3000L, 1200L, 400L, 8L, 60L));
        when(repository.nomesDasEspecialidadesPublicas()).thenReturn(List.of("Cardiologia", "Ortopedia"));
        service = new VitrinePublicaService(repository, instanceContext, buildProperties, clock, "1.7", LIMIAR);
    }

    private void passar(Duration tempo) {
        when(clock.instant()).thenReturn(INICIO.plus(tempo));
    }

    @Test
    void montaOResumoComOsTotaisOCatalogoEAVersao() {
        VitrinePublicaViewDTO resumo = service.resumo();

        assertThat(resumo.municipio()).isEqualTo("SAO_FELIPE");
        assertThat(resumo.totais().solicitacoes()).isEqualTo(5000L);
        assertThat(resumo.totais().agendamentos()).isEqualTo(3000L);
        assertThat(resumo.totais().pacientesAtendidos()).isEqualTo(1200L);
        assertThat(resumo.totais().viagensTransporte()).isEqualTo(400L);
        assertThat(resumo.catalogo().unidades()).isEqualTo(8);
        assertThat(resumo.catalogo().especialidades()).isEqualTo(60);
        assertThat(resumo.catalogo().nomesEspecialidades()).containsExactly("Cardiologia", "Ortopedia");
        assertThat(resumo.sistema().versao()).isEqualTo("1.7");
        assertThat(resumo.geradoEm()).isEqualTo(INICIO);
    }

    @Test
    void contagemAbaixoDoLimiarESuprimidaENoLimiarEPublicada() {
        when(repository.totais()).thenReturn(new Totais(5000L, LIMIAR, LIMIAR - 1, 0L, 8L, 60L));

        VitrinePublicaViewDTO resumo = service.resumo();

        assertThat(resumo.totais().solicitacoes()).isEqualTo(5000L);
        assertThat(resumo.totais().agendamentos()).isEqualTo(LIMIAR);
        assertThat(resumo.totais().pacientesAtendidos()).isNull();
        assertThat(resumo.totais().viagensTransporte()).isNull();
    }

    @Test
    void dentroDeUmaHoraNaoVoltaAoBanco() {
        service.resumo();
        passar(Duration.ofMinutes(59));
        service.resumo();
        service.resumo();

        verify(repository, times(1)).totais();
        verify(repository, times(1)).nomesDasEspecialidadesPublicas();
    }

    @Test
    void depoisDeUmaHoraRecalcula() {
        service.resumo();
        passar(Duration.ofHours(1));
        when(repository.totais()).thenReturn(new Totais(5001L, 3000L, 1200L, 400L, 8L, 60L));

        VitrinePublicaViewDTO resumo = service.resumo();

        verify(repository, times(2)).totais();
        assertThat(resumo.totais().solicitacoes()).isEqualTo(5001L);
        assertThat(resumo.geradoEm()).isEqualTo(INICIO.plus(Duration.ofHours(1)));
    }

    @Test
    void falhaAoRecalcularServeOResumoAntigoEEsperaParaTentarDeNovo() {
        VitrinePublicaViewDTO antigo = service.resumo();
        passar(Duration.ofHours(2));
        when(repository.totais()).thenThrow(new IllegalStateException("banco fora"));

        assertThat(service.resumo()).isSameAs(antigo);
        assertThat(service.resumo()).isSameAs(antigo);
        // Uma tentativa na primeira montagem, uma na falha; a segunda chamada nao insiste.
        verify(repository, times(2)).totais();

        passar(Duration.ofHours(2).plus(VitrinePublicaService.ESPERA_APOS_FALHA));
        service.resumo();
        verify(repository, times(3)).totais();
    }

    @Test
    void falhaSemResumoAnteriorVira409SemExporOErroENaoInsisteNoBanco() {
        when(repository.totais()).thenThrow(new DataAccessResourceFailureException("senha do banco: x"));

        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> service.resumo())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("temporariamente")
                    .hasMessageNotContaining("senha");
        }
        verify(repository, times(1)).totais();

        // Passada a espera, tenta de novo — e, com o banco de volta, monta o resumo.
        passar(VitrinePublicaService.ESPERA_APOS_FALHA);
        org.mockito.Mockito.doReturn(new Totais(5000L, 3000L, 1200L, 400L, 8L, 60L)).when(repository).totais();

        assertThat(service.resumo().totais().solicitacoes()).isEqualTo(5000L);
        verify(repository, times(2)).totais();
    }

    @Test
    void semBuildPropertiesADataDeDeployFicaNula() {
        when(buildProperties.getIfAvailable()).thenReturn(null);

        assertThat(service.resumo().sistema().deployEm()).isNull();
    }

    @Test
    void comBuildPropertiesADataDeDeployEADoBuild() {
        Properties p = new Properties();
        p.setProperty("time", "2026-09-25T18:30:00Z");
        when(buildProperties.getIfAvailable()).thenReturn(new BuildProperties(p));

        assertThat(service.resumo().sistema().deployEm()).isEqualTo(Instant.parse("2026-09-25T18:30:00Z"));
    }
}
