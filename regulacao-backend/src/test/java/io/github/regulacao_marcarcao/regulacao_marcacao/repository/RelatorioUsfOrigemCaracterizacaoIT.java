package io.github.regulacao_marcarcao.regulacao_marcacao.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.AgendamentoSolicitacao;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Especialidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.GrupoRelatorio;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Solicitacao;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.SolicitacaoEspecialidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Unidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.ItemCategoria;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.StatusDaMarcacao;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.UsfEnum;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.RelatorioGrupoAgendadoProjection;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.RelatorioGrupoPendenteProjection;

/**
 * Cobre a correcao da unidade de origem nos relatorios "por dia" (agendados) e
 * "de pendentes": antes do ajuste, as queries nativas liam apenas
 * {@code solicitacao.usf_origem}, que nao e mais gravado desde que o sistema
 * passou a usar {@code solicitacao.unidade_id}
 * (ver SolicitacaoService#createSolicitacao/updateSolicitacao) — a coluna
 * saia sempre vazia para solicitacoes atuais.
 *
 * Agora a query usa {@code COALESCE(u.nome, s.usf_origem)}: prioriza o nome
 * da unidade vinculada e cai para o campo legado quando so ele existe.
 *
 * <b>Nao suja a base:</b> classe {@code @Transactional}, tudo sofre rollback.
 */
@SpringBootTest
@Transactional
class RelatorioUsfOrigemCaracterizacaoIT {

    @Autowired private SolicitacaoRepository solicitacaoRepository;
    @Autowired private SolicitacaoEspecialidadeRepository solicitacaoEspecialidadeRepository;
    @Autowired private AgendamentoSolicitacaoRepository agendamentoSolicitacaoRepository;
    @Autowired private EspecialidadeRepository especialidadeRepository;
    @Autowired private GrupoRelatorioRepository grupoRelatorioRepository;
    @Autowired private UnidadeRepository unidadeRepository;

    private String sufixo;
    private GrupoRelatorio grupo;
    private Especialidade especialidade;
    private Unidade unidade;

    @BeforeEach
    void setUp() {
        sufixo = "_CAR_" + System.nanoTime();

        grupo = new GrupoRelatorio();
        grupo.setCodigo("GRUPO" + sufixo);
        grupo.setNome("Grupo Teste" + sufixo);
        grupo.setAtivo(true);
        grupo.setDirecionadoHospital(false);
        grupo = grupoRelatorioRepository.save(grupo);

        unidade = new Unidade();
        unidade.setNome("USF Modelo" + sufixo);
        unidade.setAtivo(true);
        unidade = unidadeRepository.save(unidade);

        especialidade = new Especialidade();
        especialidade.setCodigo("ESP" + sufixo);
        especialidade.setNome("Especialidade Teste" + sufixo);
        especialidade.setCategoria(ItemCategoria.ESPECIALIDADE_MEDICA);
        especialidade.setAtivo(true);
        especialidade.setVagas(0);
        especialidade.setGrupoRelatorio(grupo);
        especialidade = especialidadeRepository.save(especialidade);
    }

    private Solicitacao novaSolicitacao(String sufixoPaciente) {
        Solicitacao s = new Solicitacao();
        s.setNomePaciente("Paciente " + sufixoPaciente + sufixo);
        s.setCpfPaciente("1" + System.nanoTime() % 10_000_000_000L);
        s.setCns("700000000000000");
        return s;
    }

    private SolicitacaoEspecialidade novaSolicitacaoEspecialidade(Solicitacao solicitacao, StatusDaMarcacao status) {
        SolicitacaoEspecialidade se = new SolicitacaoEspecialidade();
        se.setSolicitacao(solicitacao);
        se.setEspecialidadeSolicitada(especialidade);
        se.setEspecialidadeCodigoLegacy(especialidade.getCodigo());
        se.setStatus(status);
        return se;
    }

    @Test
    @DisplayName("Relatorio de agendados mostra o nome da unidade vinculada, mesmo sem usf_origem preenchido")
    void relatorioAgendadosMostraNomeDaUnidadeVinculada() {
        Solicitacao solicitacao = novaSolicitacao("ComUnidade");
        solicitacao.setUnidade(unidade);
        solicitacao = solicitacaoRepository.save(solicitacao);

        AgendamentoSolicitacao agendamento = new AgendamentoSolicitacao();
        agendamento.setSolicitacao(solicitacao);
        agendamento.setDataAgendada(LocalDate.now());
        agendamento = agendamentoSolicitacaoRepository.save(agendamento);

        SolicitacaoEspecialidade se = novaSolicitacaoEspecialidade(solicitacao, StatusDaMarcacao.AGENDADO);
        se.setAgendamentoSolicitacao(agendamento);
        solicitacaoEspecialidadeRepository.save(se);

        List<RelatorioGrupoAgendadoProjection> linhas =
                solicitacaoEspecialidadeRepository.listarAgendadosPorGrupoEData(grupo.getCodigo(), LocalDate.now());

        assertThat(linhas).hasSize(1);
        assertThat(linhas.get(0).getUsfOrigem()).isEqualTo(unidade.getNome());
    }

    @Test
    @DisplayName("Relatorio de pendentes mostra o nome da unidade vinculada, mesmo sem usf_origem preenchido")
    void relatorioPendentesMostraNomeDaUnidadeVinculada() {
        Solicitacao solicitacao = novaSolicitacao("ComUnidade");
        solicitacao.setUnidade(unidade);
        solicitacao = solicitacaoRepository.save(solicitacao);

        SolicitacaoEspecialidade se = novaSolicitacaoEspecialidade(solicitacao, StatusDaMarcacao.AGUARDANDO);
        solicitacaoEspecialidadeRepository.save(se);

        List<RelatorioGrupoPendenteProjection> linhas =
                solicitacaoEspecialidadeRepository.listarPendentesPorGrupo(grupo.getCodigo());

        assertThat(linhas).hasSize(1);
        assertThat(linhas.get(0).getUsfOrigem()).isEqualTo(unidade.getNome());
    }

    @Test
    @DisplayName("LEGADO: registro so com usf_origem preenchido (sem unidade) continua mostrando o valor legado")
    void relatorioPendentesFallbackParaUsfOrigemLegado() {
        Solicitacao solicitacao = novaSolicitacao("SoUsfLegado");
        solicitacao.setUsfOrigem(UsfEnum.USF01);
        solicitacao = solicitacaoRepository.save(solicitacao);

        SolicitacaoEspecialidade se = novaSolicitacaoEspecialidade(solicitacao, StatusDaMarcacao.AGUARDANDO);
        solicitacaoEspecialidadeRepository.save(se);

        List<RelatorioGrupoPendenteProjection> linhas =
                solicitacaoEspecialidadeRepository.listarPendentesPorGrupo(grupo.getCodigo());

        assertThat(linhas).hasSize(1);
        assertThat(linhas.get(0).getUsfOrigem()).isEqualTo(UsfEnum.USF01.name());
    }

    @Test
    @DisplayName("ORFA: solicitacao sem unidade e sem usf_origem continua aparecendo no relatorio, com origem vazia")
    void relatorioPendentesSolicitacaoOrfaContinuaAparecendo() {
        Solicitacao solicitacao = novaSolicitacao("Orfa");
        solicitacao = solicitacaoRepository.save(solicitacao);

        SolicitacaoEspecialidade se = novaSolicitacaoEspecialidade(solicitacao, StatusDaMarcacao.AGUARDANDO);
        solicitacaoEspecialidadeRepository.save(se);

        List<RelatorioGrupoPendenteProjection> linhas =
                solicitacaoEspecialidadeRepository.listarPendentesPorGrupo(grupo.getCodigo());

        assertThat(linhas)
                .as("solicitacao orfa nao pode desaparecer do relatorio por causa do LEFT JOIN com unidade")
                .hasSize(1);
        assertThat(linhas.get(0).getUsfOrigem()).isNull();
    }
}
