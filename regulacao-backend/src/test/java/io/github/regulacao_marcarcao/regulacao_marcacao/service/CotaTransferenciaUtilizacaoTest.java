package io.github.regulacao_marcarcao.regulacao_marcacao.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.CotaUnidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Especialidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Profissional;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Unidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.TipoPeriodoCota;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.CotaUnidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.EspecialidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UnidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.service.CotaUnidadeService.TransferenciaItem;

/**
 * {@code transferirUtilizacao}: o acompanhamento das demais cotas quando os
 * agendamentos mudam de data na migracao de cota. As cotas aplicaveis de cada
 * data sao fixadas por data, para exercitar a diferenca entre os dois lados.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CotaTransferenciaUtilizacaoTest {

    private static final Long UNIDADE = 1L;
    private static final Long ESPECIALIDADE = 10L;
    private static final LocalDate ORIGEM = LocalDate.of(2026, 5, 20);
    private static final LocalDate MESMO_MES = LocalDate.of(2026, 5, 27);
    private static final LocalDate OUTRO_MES = LocalDate.of(2026, 6, 3);

    @Mock private CotaUnidadeRepository cotaRepository;
    @Mock private UnidadeRepository unidadeRepository;
    @Mock private EspecialidadeRepository especialidadeRepository;

    @InjectMocks private CotaUnidadeService service;

    private final Map<LocalDate, List<CotaUnidade>> aplicaveisPorData = new HashMap<>();
    private Unidade unidade;

    @BeforeEach
    void setUp() {
        aplicaveisPorData.clear();
        unidade = new Unidade();
        unidade.setId(UNIDADE);
        unidade.setNome("Unidade A");
        Especialidade especialidade = new Especialidade();
        especialidade.setId(ESPECIALIDADE);
        especialidade.setNome("Hemograma");

        lenient().when(unidadeRepository.findById(UNIDADE)).thenReturn(Optional.of(unidade));
        lenient().when(especialidadeRepository.findById(ESPECIALIDADE)).thenReturn(Optional.of(especialidade));
        lenient().when(cotaRepository.buscarCotasAplicaveis(anyLong(), any(), anyLong(), any(), any(), any()))
                .thenAnswer(inv -> aplicaveisPorData.getOrDefault(inv.<LocalDate>getArgument(5), List.of()));
        lenient().when(cotaRepository.consumirVaga(anyLong())).thenReturn(1);
    }

    private CotaUnidade cota(long id, TipoPeriodoCota tipo, boolean comProfissional) {
        CotaUnidade c = new CotaUnidade();
        c.setId(id);
        c.setUnidade(unidade);
        c.setTipoPeriodo(tipo);
        if (tipo == TipoPeriodoCota.MENSAL) {
            c.setPeriodo("2026-05");
        }
        c.setQuantidadeTotal(10);
        c.setQuantidadeUtilizada(3);
        c.setAtivo(true);
        if (comProfissional) {
            Profissional p = new Profissional();
            p.setId(77L);
            c.setProfissionalExecutante(p);
        }
        return c;
    }

    private void aplicaveis(LocalDate data, CotaUnidade... cotas) {
        aplicaveisPorData.put(data, List.of(cotas));
    }

    private TransferenciaItem item(LocalDate destino, Long cotaFixadaId) {
        return new TransferenciaItem(UNIDADE, ESPECIALIDADE, ORIGEM, destino, cotaFixadaId);
    }

    @Test
    @DisplayName("Cota que vale nos dois lados nao e tocada (mesmo mes: a mensal fica como esta)")
    void cotaPresenteNosDoisLadosNaoETocada() {
        CotaUnidade mensal = cota(1L, TipoPeriodoCota.MENSAL, false);
        aplicaveis(ORIGEM, mensal);
        aplicaveis(MESMO_MES, mensal);

        service.transferirUtilizacao(List.of(item(MESMO_MES, null)), Set.of(99L));

        verify(cotaRepository, never()).devolverVaga(anyLong());
        verify(cotaRepository, never()).consumirVaga(anyLong());
    }

    @Test
    @DisplayName("Mudanca de mes devolve na mensal antiga e consome na nova")
    void mudancaDeMesDevolveNaAntigaEConsomeNaNova() {
        CotaUnidade maio = cota(1L, TipoPeriodoCota.MENSAL, false);
        CotaUnidade junho = cota(2L, TipoPeriodoCota.MENSAL, false);
        junho.setPeriodo("2026-06");
        aplicaveis(ORIGEM, maio);
        aplicaveis(OUTRO_MES, junho);

        service.transferirUtilizacao(List.of(item(OUTRO_MES, null), item(OUTRO_MES, null)), Set.of(99L));

        verify(cotaRepository, times(2)).devolverVaga(1L);
        verify(cotaRepository, times(2)).consumirVaga(2L);
    }

    @Test
    @DisplayName("A cota que viaja com os agendamentos e ignorada nos dois lados")
    void cotaViajanteEIgnorada() {
        CotaUnidade viajante = cota(99L, TipoPeriodoCota.DATA, false);
        CotaUnidade doDestino = cota(98L, TipoPeriodoCota.DATA, false);
        aplicaveis(ORIGEM, viajante);
        aplicaveis(OUTRO_MES, viajante, doDestino);

        service.transferirUtilizacao(List.of(item(OUTRO_MES, null)), Set.of(99L));

        verify(cotaRepository, never()).devolverVaga(99L);
        verify(cotaRepository, never()).consumirVaga(99L);
        // a outra cota do destino, essa sim, passa a ser consumida
        verify(cotaRepository).consumirVaga(98L);
    }

    @Test
    @DisplayName("Item com FK para a cota que viaja nao gera operacao nenhuma")
    void itemComFkParaViajanteNaoGeraOperacao() {
        CotaUnidade viajante = cota(99L, TipoPeriodoCota.DATA, true);
        aplicaveis(ORIGEM, viajante);

        service.transferirUtilizacao(List.of(item(OUTRO_MES, 99L)), Set.of(99L));

        verify(cotaRepository, never()).devolverVaga(anyLong());
        verify(cotaRepository, never()).consumirVaga(anyLong());
    }

    @Test
    @DisplayName("Item com FK para outra cota com profissional que nao vale no destino: 409 e nada muda")
    void fkParaOutraCotaQueNaoValeNoDestino() {
        CotaUnidade outra = cota(55L, TipoPeriodoCota.DATA, true);
        aplicaveis(ORIGEM, outra);
        aplicaveis(OUTRO_MES); // nenhuma aplicavel

        assertThatThrownBy(() -> service.transferirUtilizacao(List.of(item(OUTRO_MES, 55L)), Set.of(99L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("id=55")
                .hasMessageContaining("Migre tambem essa cota");

        verify(cotaRepository, never()).devolverVaga(anyLong());
        verify(cotaRepository, never()).consumirVaga(anyLong());
    }

    @Test
    @DisplayName("Item com FK para outra cota que continua valendo no destino passa sem mexer em contador")
    void fkParaOutraCotaQueValeNoDestino() {
        CotaUnidade outra = cota(55L, TipoPeriodoCota.MENSAL, true);
        aplicaveis(ORIGEM, outra);
        aplicaveis(MESMO_MES, outra);

        assertThatCode(() -> service.transferirUtilizacao(List.of(item(MESMO_MES, 55L)), Set.of(99L)))
                .doesNotThrowAnyException();
        verify(cotaRepository, never()).devolverVaga(anyLong());
        verify(cotaRepository, never()).consumirVaga(anyLong());
    }

    @Test
    @DisplayName("Destino esgotado: 409 com a mensagem de cota esgotada")
    void destinoEsgotado() {
        CotaUnidade maio = cota(1L, TipoPeriodoCota.MENSAL, false);
        CotaUnidade junho = cota(2L, TipoPeriodoCota.MENSAL, false);
        junho.setPeriodo("2026-06");
        junho.setQuantidadeUtilizada(10);
        aplicaveis(ORIGEM, maio);
        aplicaveis(OUTRO_MES, junho);
        lenient().when(cotaRepository.consumirVaga(2L)).thenReturn(0);
        lenient().when(cotaRepository.findById(2L)).thenReturn(Optional.of(junho));

        assertThatThrownBy(() -> service.transferirUtilizacao(List.of(item(OUTRO_MES, null)), Set.of(99L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cota esgotada")
                .hasMessageContaining("2026-06")
                .hasMessageContaining("(10/10)");
    }

    @Test
    @DisplayName("Todas as devolucoes acontecem antes dos consumos")
    void devolucoesAntesDosConsumos() {
        CotaUnidade maio = cota(1L, TipoPeriodoCota.MENSAL, false);
        CotaUnidade junho = cota(2L, TipoPeriodoCota.MENSAL, false);
        junho.setPeriodo("2026-06");
        aplicaveis(ORIGEM, maio);
        aplicaveis(OUTRO_MES, junho);

        service.transferirUtilizacao(List.of(item(OUTRO_MES, null)), Set.of(99L));

        InOrder ordem = inOrder(cotaRepository);
        ordem.verify(cotaRepository).devolverVaga(eq(1L));
        ordem.verify(cotaRepository).consumirVaga(eq(2L));
    }

    @Test
    @DisplayName("Item sem unidade ou sem data e ignorado")
    void itemSemUnidadeOuDataEIgnorado() {
        CotaUnidade maio = cota(1L, TipoPeriodoCota.MENSAL, false);
        aplicaveis(ORIGEM, maio);

        service.transferirUtilizacao(List.of(
                new TransferenciaItem(null, ESPECIALIDADE, ORIGEM, OUTRO_MES, null),
                new TransferenciaItem(UNIDADE, ESPECIALIDADE, null, OUTRO_MES, null),
                new TransferenciaItem(UNIDADE, ESPECIALIDADE, ORIGEM, null, null)), Set.of(99L));

        verify(cotaRepository, never()).devolverVaga(anyLong());
        verify(cotaRepository, never()).consumirVaga(anyLong());
    }
}
