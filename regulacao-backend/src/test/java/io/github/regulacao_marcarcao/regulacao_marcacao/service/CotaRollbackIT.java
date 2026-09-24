package io.github.regulacao_marcarcao.regulacao_marcacao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.CotaUnidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Especialidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.GrupoRelatorio;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Unidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.ItemCategoria;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.TipoPeriodoCota;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.CotaUnidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.EspecialidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.GrupoRelatorioRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UnidadeRepository;

/**
 * Prova que o consumo parcial de cota e DESFEITO quando alguma cota incidente
 * esta esgotada.
 *
 * Esta e a propriedade de seguranca mais importante da funcionalidade: quando um
 * atendimento incide sobre mais de uma cota (por exemplo a do grupo Laboratorio e
 * a de Hemograma), o servico consome uma por vez. Se a segunda estiver esgotada,
 * a primeira <b>nao pode</b> ficar debitada — isso corromperia silenciosamente o
 * saldo, cobrando uma vaga por um agendamento que nunca aconteceu.
 *
 * <b>Por que esta classe NAO e @Transactional:</b> nas demais suites de
 * integracao o teste roda dentro de uma transacao propria, que absorve a excecao
 * e mascara justamente o rollback que se quer observar. Aqui cada chamada ao
 * servico abre a propria transacao, entao o rollback acontece de verdade.
 *
 * Como nada e desfeito automaticamente, o {@code @AfterEach} remove tudo o que
 * foi criado — na ordem inversa das dependencias.
 */
@SpringBootTest
class CotaRollbackIT {

    private static final DateTimeFormatter PERIODO_MENSAL = DateTimeFormatter.ofPattern("yyyy-MM");

    @Autowired private CotaUnidadeService service;
    @Autowired private CotaUnidadeRepository cotaRepository;
    @Autowired private UnidadeRepository unidadeRepository;
    @Autowired private EspecialidadeRepository especialidadeRepository;
    @Autowired private GrupoRelatorioRepository grupoRelatorioRepository;

    private final List<Long> cotasCriadas = new ArrayList<>();
    private final List<Long> especialidadesCriadas = new ArrayList<>();
    private final List<Long> gruposCriados = new ArrayList<>();
    private final List<Long> unidadesCriadas = new ArrayList<>();

    @AfterEach
    void limpar() {
        cotasCriadas.forEach(id -> cotaRepository.deleteById(id));
        especialidadesCriadas.forEach(id -> especialidadeRepository.deleteById(id));
        unidadesCriadas.forEach(id -> unidadeRepository.deleteById(id));
        gruposCriados.forEach(id -> grupoRelatorioRepository.deleteById(id));

        cotasCriadas.clear();
        especialidadesCriadas.clear();
        unidadesCriadas.clear();
        gruposCriados.clear();
    }

    @Test
    @DisplayName("BANCO REAL: cota esgotada desfaz o consumo ja feito nas demais cotas")
    void consumoParcialEDesfeito() {
        String sfx = "_RB_" + System.nanoTime();

        Unidade unidade = new Unidade();
        unidade.setNome("Unidade Rollback" + sfx);
        unidade.setCodigo("UR" + sfx);
        unidade.setAtivo(true);
        unidade = unidadeRepository.save(unidade);
        unidadesCriadas.add(unidade.getId());

        GrupoRelatorio lab = new GrupoRelatorio();
        lab.setCodigo("LAB" + sfx);
        lab.setNome("Laboratorio" + sfx);
        lab.setAtivo(true);
        lab = grupoRelatorioRepository.save(lab);
        gruposCriados.add(lab.getId());

        Especialidade hemograma = new Especialidade();
        hemograma.setCodigo("HEMO" + sfx);
        hemograma.setNome("Hemograma" + sfx);
        hemograma.setCategoria(ItemCategoria.EXAME_OU_PROCEDIMENTO);
        hemograma.setAtivo(true);
        hemograma.setVagas(0);
        hemograma.setGrupoRelatorio(lab);
        hemograma = especialidadeRepository.save(hemograma);
        especialidadesCriadas.add(hemograma.getId());

        LocalDate data = LocalDate.now();
        String periodo = data.format(PERIODO_MENSAL);

        // Grupo com saldo de sobra...
        CotaUnidade doGrupo = new CotaUnidade();
        doGrupo.setUnidade(unidade);
        doGrupo.setGrupoEspecialidades(lab);
        doGrupo.setTipoPeriodo(TipoPeriodoCota.MENSAL);
        doGrupo.setPeriodo(periodo);
        doGrupo.setQuantidadeTotal(10);
        doGrupo.setQuantidadeUtilizada(0);
        doGrupo.setAtivo(true);
        doGrupo = cotaRepository.save(doGrupo);
        cotasCriadas.add(doGrupo.getId());

        // ...mas a cota da especialidade ja nasce esgotada (0 vagas)
        CotaUnidade deHemograma = new CotaUnidade();
        deHemograma.setUnidade(unidade);
        deHemograma.setEspecialidade(hemograma);
        deHemograma.setTipoPeriodo(TipoPeriodoCota.MENSAL);
        deHemograma.setPeriodo(periodo);
        deHemograma.setQuantidadeTotal(0);
        deHemograma.setQuantidadeUtilizada(0);
        deHemograma.setAtivo(true);
        deHemograma = cotaRepository.save(deHemograma);
        cotasCriadas.add(deHemograma.getId());

        final Long unidadeId = unidade.getId();
        final Long hemogramaId = hemograma.getId();

        assertThatThrownBy(() -> service.incrementarUtilizacao(unidadeId, hemogramaId, data))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cota esgotada");

        // O ponto do teste: o grupo NAO pode ter ficado debitado.
        assertThat(cotaRepository.findById(doGrupo.getId()).orElseThrow().getQuantidadeUtilizada())
                .as("consumo do grupo deveria ter sido desfeito pelo rollback")
                .isZero();
        assertThat(cotaRepository.findById(deHemograma.getId()).orElseThrow().getQuantidadeUtilizada())
                .isZero();
    }
}
