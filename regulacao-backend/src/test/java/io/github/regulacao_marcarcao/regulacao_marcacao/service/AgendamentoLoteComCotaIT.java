package io.github.regulacao_marcarcao.regulacao_marcacao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.agendamentoDTO.AgendamentoSolicitacaoSimpleViewDTO.EspecialidadeAgendadaViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.agendamentoDTO.MultiAgendamentoCreateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.CotaUnidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Especialidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Solicitacao;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.SolicitacaoEspecialidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Unidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.User;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.ItemCategoria;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.PrioridadeDaMarcacaoEnum;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.Roles;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.StatusDaMarcacao;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.TipoPeriodoCota;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.TurnoEnum;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.CotaUnidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.EspecialidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.SolicitacaoRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UnidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UserRepository;
import jakarta.persistence.EntityManager;

/**
 * Regressao: unidade (ADMIN_UNIDADE) com cota agendando em LOTE — varios itens
 * da mesma ficha num unico agendamento — quando a ficha tem tambem itens que
 * ficaram fora do lote.
 *
 * <p>{@code consumirVaga} usa {@code clearAutomatically = true}: ao consumir a
 * cota do 1o item, a solicitacao e seus itens ficam desanexados. O laco do
 * servico procura o 2o item percorrendo a mesma colecao, e ler o codigo da
 * especialidade de um item fora do lote (proxy LAZY ainda nao inicializado)
 * estourava {@code LazyInitializationException} (500 sem mensagem na tela).
 *
 * <p>O {@code flush} + {@code clear} antes de agendar e essencial, pelo mesmo
 * motivo de {@link AgendamentoLocalComCotaIT}: sem ele as especialidades seriam
 * as instancias reais criadas no teste, e nao proxies lidos do banco. As duas
 * ordens de selecao sao testadas porque so uma delas atravessa o item fora do
 * lote depois do consumo.
 */
@SpringBootTest
@Transactional
class AgendamentoLoteComCotaIT {

    @Autowired private AgendamentoService agendamentoService;
    @Autowired private SolicitacaoRepository solicitacaoRepository;
    @Autowired private CotaUnidadeRepository cotaRepository;
    @Autowired private UnidadeRepository unidadeRepository;
    @Autowired private EspecialidadeRepository especialidadeRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private EntityManager entityManager;

    private String sufixo;
    private LocalDate data;
    private Unidade unidade;
    private User operador;

    @BeforeEach
    void setUp() {
        sufixo = "_LOTE_" + System.nanoTime();
        data = LocalDate.now();

        unidade = new Unidade();
        unidade.setNome("Unidade Lote" + sufixo);
        unidade.setCodigo("ULT" + sufixo);
        unidade.setAtivo(true);
        unidade = unidadeRepository.saveAndFlush(unidade);

        operador = new User();
        operador.setCpf(cpfUnico());
        operador.setNome("Operador da Unidade");
        operador.setPassword("x");
        operador.setRole(Roles.ADMIN_UNIDADE);
        operador.setAtivo(true);
        operador.setUnidade(unidade);
        operador = userRepository.saveAndFlush(operador);
    }

    @Test
    @DisplayName("lote [A, C] com item pendente B fora do lote entre eles")
    void loteComItemPendenteForaDoLote() {
        Especialidade a = especialidade("A", ItemCategoria.EXAME_OU_PROCEDIMENTO);
        Especialidade b = especialidade("B", ItemCategoria.EXAME_OU_PROCEDIMENTO);
        Especialidade c = especialidade("C", ItemCategoria.EXAME_OU_PROCEDIMENTO);
        CotaUnidade cotaA = cota(a, 1);
        CotaUnidade cotaC = cota(c, 1);
        Solicitacao s = ficha(item(a, StatusDaMarcacao.AGUARDANDO), item(b, StatusDaMarcacao.AGUARDANDO),
                item(c, StatusDaMarcacao.AGUARDANDO));

        var resposta = agendar(s, a, c);

        assertThat(resposta.especialidades()).extracting(EspecialidadeAgendadaViewDTO::codigo)
                .containsExactlyInAnyOrder(a.getCodigo(), c.getCodigo());
        assertThat(utilizada(cotaA)).isEqualTo(1);
        assertThat(utilizada(cotaC)).isEqualTo(1);
        assertThat(statusDe(s, b)).isEqualTo(StatusDaMarcacao.AGUARDANDO);
        assertThat(statusDe(s, a)).isEqualTo(StatusDaMarcacao.AGENDADO);
        assertThat(statusDe(s, c)).isEqualTo(StatusDaMarcacao.AGENDADO);
    }

    @Test
    @DisplayName("lote [C, A] — ordem inversa da ficha")
    void loteEmOrdemInversa() {
        Especialidade a = especialidade("A", ItemCategoria.EXAME_OU_PROCEDIMENTO);
        Especialidade b = especialidade("B", ItemCategoria.EXAME_OU_PROCEDIMENTO);
        Especialidade c = especialidade("C", ItemCategoria.EXAME_OU_PROCEDIMENTO);
        CotaUnidade cotaA = cota(a, 1);
        CotaUnidade cotaC = cota(c, 1);
        Solicitacao s = ficha(item(a, StatusDaMarcacao.AGUARDANDO), item(b, StatusDaMarcacao.AGUARDANDO),
                item(c, StatusDaMarcacao.AGUARDANDO));

        var resposta = agendar(s, c, a);

        assertThat(resposta.especialidades()).extracting(EspecialidadeAgendadaViewDTO::codigo)
                .containsExactlyInAnyOrder(a.getCodigo(), c.getCodigo());
        assertThat(utilizada(cotaA)).isEqualTo(1);
        assertThat(utilizada(cotaC)).isEqualTo(1);
        assertThat(statusDe(s, b)).isEqualTo(StatusDaMarcacao.AGUARDANDO);
    }

    @Test
    @DisplayName("lote misto: consulta + exame, com item fora do lote entre eles")
    void loteMistoConsultaEExame() {
        Especialidade consulta = especialidade("CONS", ItemCategoria.ESPECIALIDADE_MEDICA);
        Especialidade fora1 = especialidade("FORA1", ItemCategoria.EXAME_OU_PROCEDIMENTO);
        Especialidade exame = especialidade("EXA", ItemCategoria.EXAME_OU_PROCEDIMENTO);
        Especialidade fora2 = especialidade("FORA2", ItemCategoria.ESPECIALIDADE_MEDICA);
        Especialidade procedimento = especialidade("PROC", ItemCategoria.EXAME_OU_PROCEDIMENTO);
        CotaUnidade cotaConsulta = cota(consulta, 1);
        CotaUnidade cotaExame = cota(exame, 1);
        CotaUnidade cotaProcedimento = cota(procedimento, 1);
        Solicitacao s = ficha(item(consulta, StatusDaMarcacao.AGUARDANDO), item(fora1, StatusDaMarcacao.AGUARDANDO),
                item(exame, StatusDaMarcacao.AGUARDANDO), item(fora2, StatusDaMarcacao.AGUARDANDO),
                item(procedimento, StatusDaMarcacao.AGUARDANDO));

        // O item do MEIO primeiro: assim sobra um item fora do lote ainda nao lido
        // no caminho de um dos outros dois, qualquer que seja a ordem da colecao.
        var resposta = agendar(s, exame, consulta, procedimento);

        assertThat(resposta.especialidades()).extracting(EspecialidadeAgendadaViewDTO::codigo)
                .containsExactlyInAnyOrder(consulta.getCodigo(), exame.getCodigo(), procedimento.getCodigo());
        assertThat(utilizada(cotaConsulta)).isEqualTo(1);
        assertThat(utilizada(cotaExame)).isEqualTo(1);
        assertThat(utilizada(cotaProcedimento)).isEqualTo(1);
        assertThat(statusDe(s, fora1)).isEqualTo(StatusDaMarcacao.AGUARDANDO);
        assertThat(statusDe(s, fora2)).isEqualTo(StatusDaMarcacao.AGUARDANDO);
    }

    @Test
    @DisplayName("lote com item historico (ja AGENDADO) entre os selecionados")
    void loteComItemHistoricoNaFicha() {
        Especialidade a = especialidade("A", ItemCategoria.EXAME_OU_PROCEDIMENTO);
        Especialidade historico1 = especialidade("HIST1", ItemCategoria.EXAME_OU_PROCEDIMENTO);
        Especialidade b = especialidade("B", ItemCategoria.EXAME_OU_PROCEDIMENTO);
        Especialidade historico2 = especialidade("HIST2", ItemCategoria.EXAME_OU_PROCEDIMENTO);
        Especialidade c = especialidade("C", ItemCategoria.EXAME_OU_PROCEDIMENTO);
        CotaUnidade cotaA = cota(a, 1);
        CotaUnidade cotaB = cota(b, 1);
        CotaUnidade cotaC = cota(c, 1);
        Solicitacao s = ficha(item(a, StatusDaMarcacao.AGUARDANDO), item(historico1, StatusDaMarcacao.AGENDADO),
                item(b, StatusDaMarcacao.AGUARDANDO), item(historico2, StatusDaMarcacao.AGENDADO),
                item(c, StatusDaMarcacao.AGUARDANDO));

        // Item do meio primeiro, pelo mesmo motivo do lote misto.
        var resposta = agendar(s, b, a, c);

        assertThat(resposta.especialidades()).extracting(EspecialidadeAgendadaViewDTO::codigo)
                .containsExactlyInAnyOrder(a.getCodigo(), b.getCodigo(), c.getCodigo());
        assertThat(utilizada(cotaA)).isEqualTo(1);
        assertThat(utilizada(cotaB)).isEqualTo(1);
        assertThat(utilizada(cotaC)).isEqualTo(1);
    }

    @Test
    @DisplayName("caracterizacao: cota do 2o item esgotada barra o lote inteiro com 'Cota esgotada'")
    void cotaDoSegundoItemEsgotadaBarraOLote() {
        Especialidade a = especialidade("A", ItemCategoria.EXAME_OU_PROCEDIMENTO);
        Especialidade c = especialidade("C", ItemCategoria.EXAME_OU_PROCEDIMENTO);
        cota(a, 1);
        cota(c, 0);
        Solicitacao s = ficha(item(a, StatusDaMarcacao.AGUARDANDO), item(c, StatusDaMarcacao.AGUARDANDO));

        // O desfazimento do consumo de A e provado em AgendamentoLoteRollbackIT,
        // que nao e @Transactional.
        assertThatThrownBy(() -> agendar(s, a, c))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cota esgotada");
    }

    // ------------------------------------------------------------------

    private static String cpfUnico() {
        return String.format("%011d", System.nanoTime() % 100_000_000_000L);
    }

    private Especialidade especialidade(String prefixo, ItemCategoria categoria) {
        Especialidade e = new Especialidade();
        e.setCodigo(prefixo + sufixo);
        e.setNome(prefixo + sufixo);
        e.setCategoria(categoria);
        e.setAtivo(true);
        e.setVagas(0);
        return especialidadeRepository.saveAndFlush(e);
    }

    private CotaUnidade cota(Especialidade especialidade, int total) {
        CotaUnidade cota = new CotaUnidade();
        cota.setUnidade(unidade);
        cota.setEspecialidade(especialidade);
        cota.setTipoPeriodo(TipoPeriodoCota.DATA);
        cota.setDataEspecifica(data);
        cota.setQuantidadeTotal(total);
        cota.setQuantidadeUtilizada(0);
        cota.setAtivo(true);
        return cotaRepository.saveAndFlush(cota);
    }

    private SolicitacaoEspecialidade item(Especialidade especialidade, StatusDaMarcacao status) {
        SolicitacaoEspecialidade se = new SolicitacaoEspecialidade();
        se.setEspecialidadeSolicitada(especialidade);
        se.setEspecialidadeCodigoLegacy(especialidade.getCodigo());
        se.setStatus(status);
        se.setPrioridade(PrioridadeDaMarcacaoEnum.NORMAL);
        return se;
    }

    /** Grava a ficha e limpa o contexto: dali em diante tudo e lido do banco, como em producao. */
    private Solicitacao ficha(SolicitacaoEspecialidade... itens) {
        Solicitacao s = new Solicitacao();
        s.setNomePaciente("Paciente" + sufixo);
        s.setCpfPaciente(cpfUnico());
        s.setCns("700" + String.format("%012d", System.nanoTime() % 1_000_000_000_000L));
        s.setNomePai("Pai");
        s.setNomeMae("Mae");
        s.setEndereco("Rua");
        s.setUnidade(unidade);
        List<SolicitacaoEspecialidade> especs = new ArrayList<>();
        for (SolicitacaoEspecialidade se : itens) {
            se.setSolicitacao(s);
            especs.add(se);
        }
        s.setEspecialidades(especs);
        s = solicitacaoRepository.saveAndFlush(s);

        entityManager.flush();
        entityManager.clear();
        return s;
    }

    private io.github.regulacao_marcarcao.regulacao_marcacao.dto.agendamentoDTO.AgendamentoSolicitacaoSimpleViewDTO agendar(
            Solicitacao s, Especialidade... selecionadas) {
        var dto = new MultiAgendamentoCreateDTO(
                List.of(selecionadas).stream().map(Especialidade::getCodigo).toList(), data, null, null,
                TurnoEnum.MANHA, "regressao lote", null, null, null);
        return agendamentoService.criarAgendamentoParaMultiplosExames(s.getId(), dto, operador.getCpf());
    }

    private int utilizada(CotaUnidade cota) {
        entityManager.flush();
        entityManager.clear();
        return cotaRepository.findById(cota.getId()).orElseThrow().getQuantidadeUtilizada();
    }

    private StatusDaMarcacao statusDe(Solicitacao s, Especialidade especialidade) {
        entityManager.flush();
        entityManager.clear();
        return solicitacaoRepository.findById(s.getId()).orElseThrow().getEspecialidades().stream()
                .filter(e -> e.getEspecialidadeSolicitada().getId().equals(especialidade.getId()))
                .findFirst().orElseThrow().getStatus();
    }
}
