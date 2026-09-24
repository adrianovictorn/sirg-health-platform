package io.github.regulacao_marcarcao.regulacao_marcacao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

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

/**
 * Simulacao do FLUXO REAL de agendamento pela unidade, estourando a cota.
 *
 * Aqui nao se chama {@code CotaUnidadeService} diretamente: o teste passa por
 * {@code AgendamentoService.criarAgendamentoParaMultiplosExames}, que e o caminho
 * que a tela /agendar percorre. Isso valida a ligacao inteira — se a cota e
 * consumida no ponto certo, se o bloqueio realmente impede o agendamento e se o
 * cancelamento devolve a vaga.
 *
 * <b>Nao suja a base:</b> classe {@code @Transactional}, tudo sofre rollback.
 */
@SpringBootTest
@Transactional
class AgendamentoCotaFluxoIT {

    private static final DateTimeFormatter PERIODO_MENSAL = DateTimeFormatter.ofPattern("yyyy-MM");

    @Autowired private AgendamentoService agendamentoService;
    @Autowired private SolicitacaoRepository solicitacaoRepository;
    @Autowired private CotaUnidadeRepository cotaRepository;
    @Autowired private UnidadeRepository unidadeRepository;
    @Autowired private EspecialidadeRepository especialidadeRepository;
    @Autowired private UserRepository userRepository;

    private Unidade unidade;
    private Especialidade cardiologia;
    private User operadorDaUnidade;
    private LocalDate data;
    private String periodo;
    private String sufixo;

    @BeforeEach
    void setUp() {
        sufixo = "_IT_" + System.nanoTime();

        unidade = new Unidade();
        unidade.setNome("Unidade A" + sufixo);
        unidade.setCodigo("UA" + sufixo);
        unidade.setAtivo(true);
        unidade = unidadeRepository.saveAndFlush(unidade);

        cardiologia = new Especialidade();
        cardiologia.setCodigo("CARDIO" + sufixo);
        cardiologia.setNome("Cardiologia" + sufixo);
        cardiologia.setCategoria(ItemCategoria.ESPECIALIDADE_MEDICA);
        cardiologia.setAtivo(true);
        cardiologia.setVagas(0); // 0 = sem limite de capacidade global; quem limita e a cota
        cardiologia = especialidadeRepository.saveAndFlush(cardiologia);

        // Operador lotado na unidade: NAO e admin global, portanto sujeito a cota.
        operadorDaUnidade = new User();
        operadorDaUnidade.setCpf(cpfUnico());
        operadorDaUnidade.setNome("Operador da Unidade");
        operadorDaUnidade.setPassword("x");
        operadorDaUnidade.setRole(Roles.ADMIN_UNIDADE);
        operadorDaUnidade.setAtivo(true);
        operadorDaUnidade.setUnidade(unidade);
        operadorDaUnidade = userRepository.saveAndFlush(operadorDaUnidade);

        data = LocalDate.now();
        periodo = data.format(PERIODO_MENSAL);
    }

    /** CPF ficticio unico que cabe em varchar(15). */
    private static String cpfUnico() {
        return String.format("%011d", System.nanoTime() % 100_000_000_000L);
    }

    /** Cria uma solicitacao da unidade com uma especialidade pendente. */
    private Solicitacao novaSolicitacaoPendente(int seq) {
        Solicitacao s = new Solicitacao();
        s.setNomePaciente("Paciente " + seq + sufixo);
        s.setCpfPaciente(cpfUnico());
        s.setCns("700" + String.format("%012d", seq));
        s.setNomePai("Pai " + seq);
        s.setNomeMae("Mae " + seq);
        s.setEndereco("Rua " + seq);
        s.setUnidade(unidade);

        SolicitacaoEspecialidade se = new SolicitacaoEspecialidade();
        se.setSolicitacao(s);
        se.setEspecialidadeSolicitada(cardiologia);
        se.setEspecialidadeCodigoLegacy(cardiologia.getCodigo());
        se.setStatus(StatusDaMarcacao.AGUARDANDO);
        se.setPrioridade(PrioridadeDaMarcacaoEnum.NORMAL);

        List<SolicitacaoEspecialidade> especs = new ArrayList<>();
        especs.add(se);
        s.setEspecialidades(especs);

        return solicitacaoRepository.saveAndFlush(s);
    }

    private MultiAgendamentoCreateDTO dtoAgendamento() {
        return new MultiAgendamentoCreateDTO(
                List.of(cardiologia.getCodigo()),
                data,
                null,
                null,
                TurnoEnum.MANHA,
                "agendado no teste de cota");
    }

    private CotaUnidade criarCota(int total) {
        CotaUnidade c = new CotaUnidade();
        c.setUnidade(unidade);
        c.setEspecialidade(cardiologia);
        c.setTipoPeriodo(TipoPeriodoCota.MENSAL);
        c.setPeriodo(periodo);
        c.setQuantidadeTotal(total);
        c.setQuantidadeUtilizada(0);
        c.setAtivo(true);
        return cotaRepository.saveAndFlush(c);
    }

    private int utilizada(Long cotaId) {
        return cotaRepository.findById(cotaId).orElseThrow().getQuantidadeUtilizada();
    }

    // ==================================================================

    @Test
    @DisplayName("FLUXO REAL: Unidade A / Cardiologia / 5 vagas — o 6o agendamento e recusado")
    void unidadeEstouraCotaNoFluxoDeAgendamento() {
        CotaUnidade cota = criarCota(5);
        String cpf = operadorDaUnidade.getCpf();

        // 5 pacientes distintos, todos agendados pela mesma unidade
        for (int i = 1; i <= 5; i++) {
            Solicitacao s = novaSolicitacaoPendente(i);
            final int n = i;
            assertThatCode(() -> agendamentoService.criarAgendamentoParaMultiplosExames(
                    s.getId(), dtoAgendamento(), cpf))
                    .as("agendamento %d deveria passar", n)
                    .doesNotThrowAnyException();
        }

        assertThat(utilizada(cota.getId())).isEqualTo(5);

        // 6o paciente: a unidade atingiu o limite mensal para Cardiologia
        Solicitacao sexto = novaSolicitacaoPendente(6);
        assertThatThrownBy(() -> agendamentoService.criarAgendamentoParaMultiplosExames(
                sexto.getId(), dtoAgendamento(), cpf))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cota esgotada")
                .hasMessageContaining("Unidade A");

        assertThat(utilizada(cota.getId())).isEqualTo(5);
    }

    @Test
    @DisplayName("FLUXO REAL: cancelar um agendamento devolve a vaga e destrava o proximo")
    void cancelamentoNoFluxoDevolveVaga() {
        CotaUnidade cota = criarCota(1);
        String cpf = operadorDaUnidade.getCpf();

        Solicitacao primeira = novaSolicitacaoPendente(1);
        var agendamento = agendamentoService.criarAgendamentoParaMultiplosExames(
                primeira.getId(), dtoAgendamento(), cpf);
        assertThat(utilizada(cota.getId())).isEqualTo(1);

        // Segundo paciente e barrado
        Solicitacao segunda = novaSolicitacaoPendente(2);
        assertThatThrownBy(() -> agendamentoService.criarAgendamentoParaMultiplosExames(
                segunda.getId(), dtoAgendamento(), cpf))
                .isInstanceOf(IllegalStateException.class);

        // Cancela o primeiro -> a vaga volta
        agendamentoService.deleteAgendamento(agendamento.id(), cpf);
        assertThat(utilizada(cota.getId())).isZero();

        // Agora o segundo consegue agendar
        Solicitacao segundaDeNovo = novaSolicitacaoPendente(3);
        assertThatCode(() -> agendamentoService.criarAgendamentoParaMultiplosExames(
                segundaDeNovo.getId(), dtoAgendamento(), cpf))
                .doesNotThrowAnyException();
        assertThat(utilizada(cota.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("FLUXO REAL: ADMIN global nao e limitado pela cota da unidade")
    void adminGlobalNaoSofreCota() {
        CotaUnidade cota = criarCota(1);

        User admin = new User();
        admin.setCpf(cpfUnico());
        admin.setNome("Admin Global");
        admin.setPassword("x");
        admin.setRole(Roles.ADMIN);
        admin.setAtivo(true);
        admin = userRepository.saveAndFlush(admin);

        String cpfAdmin = admin.getCpf();
        for (int i = 1; i <= 3; i++) {
            Solicitacao s = novaSolicitacaoPendente(i);
            assertThatCode(() -> agendamentoService.criarAgendamentoParaMultiplosExames(
                    s.getId(), dtoAgendamento(), cpfAdmin))
                    .doesNotThrowAnyException();
        }

        // Nenhum consumo: o admin global nao esta sujeito a cota
        assertThat(utilizada(cota.getId())).isZero();
    }

    @Test
    @DisplayName("FLUXO REAL: unidade sem cota cadastrada agenda sem limite (legado)")
    void unidadeSemCotaAgendaLivremente() {
        String cpf = operadorDaUnidade.getCpf();

        for (int i = 1; i <= 8; i++) {
            Solicitacao s = novaSolicitacaoPendente(i);
            assertThatCode(() -> agendamentoService.criarAgendamentoParaMultiplosExames(
                    s.getId(), dtoAgendamento(), cpf))
                    .doesNotThrowAnyException();
        }
    }

    /**
     * Registro anterior a V72/V73: solicitacao sem unidade vinculada. Nao pode ser
     * barrada por cota nenhuma, nem provocar erro — continua agendavel como antes.
     */
    @Test
    @DisplayName("LEGADO no fluxo real: solicitacao sem unidade continua agendavel")
    void solicitacaoLegadaSemUnidadeContinuaAgendavel() {
        criarCota(0); // cota zerada na unidade: se fosse aplicada, barraria tudo
        String cpf = operadorDaUnidade.getCpf();

        Solicitacao orfa = novaSolicitacaoPendente(1);
        orfa.setUnidade(null);
        solicitacaoRepository.saveAndFlush(orfa);

        assertThatCode(() -> agendamentoService.criarAgendamentoParaMultiplosExames(
                orfa.getId(), dtoAgendamento(), cpf))
                .doesNotThrowAnyException();
    }
}
