package io.github.regulacao_marcarcao.regulacao_marcacao.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.TipoPeriodoCota;

/** Parte da listagem paginada de cotas que não depende de banco. */
class CotaListagemServiceTest {

    @Test
    @DisplayName("Cota por data usa a data; mensal usa o dia 1 do periodo")
    void chaveDeReferencia() {
        assertThat(CotaListagemService.chaveDe(TipoPeriodoCota.DATA, null, LocalDate.of(2026, 7, 10)))
                .isEqualTo("2026-07-10");
        assertThat(CotaListagemService.chaveDe(TipoPeriodoCota.MENSAL, "2026-09", null))
                .isEqualTo("2026-09-01");
    }

    @Test
    @DisplayName("Periodo legado com mes de um digito e tolerado, como na tela")
    void periodoLegado() {
        assertThat(CotaListagemService.chaveDe(TipoPeriodoCota.MENSAL, "2026-9", null))
                .isEqualTo("2026-09-01");
    }

    @Test
    @DisplayName("Cota sem data reconhecivel devolve nulo (vai para o fim da ordem)")
    void semData() {
        assertThat(CotaListagemService.chaveDe(TipoPeriodoCota.DATA, "2026-09", null)).isNull();
        assertThat(CotaListagemService.chaveDe(TipoPeriodoCota.MENSAL, null, LocalDate.of(2026, 1, 1))).isNull();
        assertThat(CotaListagemService.chaveDe(TipoPeriodoCota.MENSAL, "lixo", null)).isNull();
    }
}
