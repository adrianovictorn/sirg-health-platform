package io.github.regulacao_marcarcao.regulacao_marcacao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.dashboard.DashboardResumoDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Especialidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Solicitacao;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.SolicitacaoEspecialidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Unidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.User;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.ItemCategoria;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.PrioridadeDaMarcacaoEnum;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.Roles;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.StatusDaMarcacao;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.EspecialidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.SolicitacaoRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UnidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UserRepository;

/**
 * Escopo por unidade do resumo do dashboard e das listas que os cards abrem.
 *
 * Fixture: unidade A com um paciente de 2 pedidos pendentes (um URGENTE) e um
 * agendado; unidade B com 1 pendente; uma solicitacao orfa (sem unidade) com 1
 * pendente. Os totais do ADMIN sao comparados por diferenca contra a base que ja
 * existia, porque o banco de teste nao e vazio.
 *
 * <b>Nao suja a base:</b> classe {@code @Transactional}, tudo sofre rollback.
 */
@SpringBootTest
@Transactional
class DashboardResumoEscopoIT {

    @Autowired private SolicitacaoService solicitacaoService;
    @Autowired private SolicitacaoRepository solicitacaoRepository;
    @Autowired private UnidadeRepository unidadeRepository;
    @Autowired private EspecialidadeRepository especialidadeRepository;
    @Autowired private UserRepository userRepository;

    private Unidade unidadeA;
    private Unidade unidadeB;
    private Especialidade cardiologia;
    private User admin;
    private User recepcaoA;
    private String sufixo;
    private DashboardResumoDTO baseAdmin;

    @BeforeEach
    void setUp() {
        sufixo = "_DRE_IT_" + System.nanoTime();

        unidadeA = criarUnidade("A");
        unidadeB = criarUnidade("B");

        cardiologia = new Especialidade();
        cardiologia.setCodigo("CARDIO" + sufixo);
        cardiologia.setNome("Cardiologia" + sufixo);
        cardiologia.setCategoria(ItemCategoria.ESPECIALIDADE_MEDICA);
        cardiologia.setAtivo(true);
        cardiologia.setVagas(0);
        cardiologia = especialidadeRepository.saveAndFlush(cardiologia);

        admin = criarUsuario(Roles.ADMIN, null);
        recepcaoA = criarUsuario(Roles.RECEPCAO, unidadeA);

        baseAdmin = solicitacaoService.obterResumoDashboard(admin.getCpf());

        criarSolicitacao(unidadeA, "PacienteA",
                item(StatusDaMarcacao.AGUARDANDO, PrioridadeDaMarcacaoEnum.NORMAL),
                item(StatusDaMarcacao.AGUARDANDO, PrioridadeDaMarcacaoEnum.URGENTE),
                item(StatusDaMarcacao.AGENDADO, PrioridadeDaMarcacaoEnum.NORMAL));
        criarSolicitacao(unidadeB, "PacienteB",
                item(StatusDaMarcacao.AGUARDANDO, PrioridadeDaMarcacaoEnum.NORMAL));
        criarSolicitacao(null, "PacienteOrfao",
                item(StatusDaMarcacao.AGUARDANDO, PrioridadeDaMarcacaoEnum.NORMAL));
    }

    private Unidade criarUnidade(String letra) {
        Unidade u = new Unidade();
        u.setNome("Unidade " + letra + sufixo);
        u.setCodigo("U" + letra + sufixo);
        u.setAtivo(true);
        return unidadeRepository.saveAndFlush(u);
    }

    private User criarUsuario(Roles role, Unidade unidade) {
        User u = new User();
        u.setCpf(cpfUnico());
        u.setNome(role + sufixo);
        u.setPassword("x");
        u.setRole(role);
        u.setAtivo(true);
        u.setUnidade(unidade);
        return userRepository.saveAndFlush(u);
    }

    private static String cpfUnico() {
        return String.format("%011d", System.nanoTime() % 100_000_000_000L);
    }

    private SolicitacaoEspecialidade item(StatusDaMarcacao status, PrioridadeDaMarcacaoEnum prioridade) {
        SolicitacaoEspecialidade se = new SolicitacaoEspecialidade();
        se.setEspecialidadeSolicitada(cardiologia);
        se.setEspecialidadeCodigoLegacy(cardiologia.getCodigo());
        se.setStatus(status);
        se.setPrioridade(prioridade);
        return se;
    }

    private Solicitacao criarSolicitacao(Unidade unidade, String nome, SolicitacaoEspecialidade... itens) {
        Solicitacao s = new Solicitacao();
        s.setNomePaciente(nome + sufixo);
        s.setCpfPaciente(cpfUnico());
        s.setCns("700" + cpfUnico());
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
        return solicitacaoRepository.saveAndFlush(s);
    }

    // ------------------------------------------------------------------
    // Caracterizacao: comportamento que ja existia e nao pode regredir
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Operador com unidade ve no resumo so os numeros da propria unidade")
    void operadorComUnidadeVeSoAPropriaUnidade() {
        DashboardResumoDTO resumo = solicitacaoService.obterResumoDashboard(recepcaoA.getCpf());

        assertThat(resumo.totalSolicitacoes()).isEqualTo(1);
        assertThat(resumo.totalPendentes()).isEqualTo(2);
        assertThat(resumo.totalAgendadas()).isEqualTo(1);
        assertThat(resumo.totalUrgentes()).isEqualTo(1);
        assertThat(resumo.pendentesPorUnidade().get(unidadeA.getId())).isEqualTo(2);
    }

    @Test
    @DisplayName("ADMIN ve no resumo todas as unidades, inclusive solicitacoes sem unidade")
    void adminVeTodasAsUnidades() {
        DashboardResumoDTO resumo = solicitacaoService.obterResumoDashboard(admin.getCpf());

        assertThat(resumo.totalSolicitacoes() - baseAdmin.totalSolicitacoes()).isEqualTo(3);
        assertThat(resumo.totalPendentes() - baseAdmin.totalPendentes()).isEqualTo(4);
        assertThat(resumo.totalAgendadas() - baseAdmin.totalAgendadas()).isEqualTo(1);
        assertThat(resumo.totalUrgentes() - baseAdmin.totalUrgentes()).isEqualTo(1);
        assertThat(resumo.pendentesPorUnidade().get(unidadeA.getId())).isEqualTo(2);
        assertThat(resumo.pendentesPorUnidade().get(unidadeB.getId())).isEqualTo(1);
    }

    // ------------------------------------------------------------------
    // Ajuste: escopo de listagem no resumo e nas listas que os cards abrem
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Operador com unidade nao recebe no resumo os numeros das outras unidades")
    void resumoNaoTrafegaNumerosDeOutrasUnidades() {
        DashboardResumoDTO resumo = solicitacaoService.obterResumoDashboard(recepcaoA.getCpf());

        assertThat(resumo.pendentesPorUnidade()).containsOnlyKeys(unidadeA.getId());
        assertThat(resumo.pacientesPendentesPorUnidade()).containsOnlyKeys(unidadeA.getId());
    }

    @Test
    @DisplayName("Sem unidade de lotacao o resumo vem zerado, em vez do total do municipio")
    void semLotacaoRecebeResumoZerado() {
        for (Roles role : List.of(Roles.RECEPCAO, Roles.ENFERMEIRO, Roles.MEDICO, Roles.USER, Roles.ADMIN_UNIDADE)) {
            DashboardResumoDTO resumo = solicitacaoService.obterResumoDashboard(criarUsuario(role, null).getCpf());

            assertThat(resumo.totalSolicitacoes()).as("%s", role).isZero();
            assertThat(resumo.totalPendentes()).as("%s", role).isZero();
            assertThat(resumo.totalAgendadas()).as("%s", role).isZero();
            assertThat(resumo.totalUrgentes()).as("%s", role).isZero();
            assertThat(resumo.pacientesPendentes()).as("%s", role).isZero();
            assertThat(resumo.pacientesUrgentes()).as("%s", role).isZero();
            assertThat(resumo.pendentesPorUnidade()).as("%s", role).isEmpty();
            assertThat(resumo.pacientesPendentesPorUnidade()).as("%s", role).isEmpty();
        }
    }

    @Test
    @DisplayName("COORD_TRANSPORTE sem lotacao continua vendo o total do municipio")
    void coordTransporteSemLotacaoContinuaGlobal() {
        DashboardResumoDTO doAdmin = solicitacaoService.obterResumoDashboard(admin.getCpf());
        DashboardResumoDTO doCoord = solicitacaoService.obterResumoDashboard(
                criarUsuario(Roles.COORD_TRANSPORTE, null).getCpf());

        assertThat(doCoord.totalPendentes()).isEqualTo(doAdmin.totalPendentes());
        assertThat(doCoord.pendentesPorUnidade()).containsKeys(unidadeA.getId(), unidadeB.getId());
    }

    @Test
    @DisplayName("As listas que os cards abrem seguem o mesmo escopo de unidade")
    void listasDosCardsSeguemOEscopo() {
        criarSolicitacao(unidadeB, "OutraB",
                item(StatusDaMarcacao.AGENDADO, PrioridadeDaMarcacaoEnum.NORMAL),
                item(StatusDaMarcacao.REALIZADO, PrioridadeDaMarcacaoEnum.NORMAL),
                item(StatusDaMarcacao.AGUARDANDO, PrioridadeDaMarcacaoEnum.EMERGENCIA),
                item(StatusDaMarcacao.GEL, PrioridadeDaMarcacaoEnum.NORMAL));
        criarSolicitacao(unidadeA, "OutraA",
                item(StatusDaMarcacao.REALIZADO, PrioridadeDaMarcacaoEnum.NORMAL),
                item(StatusDaMarcacao.GEL, PrioridadeDaMarcacaoEnum.NORMAL));
        String cpfA = recepcaoA.getCpf();
        String cpfAdmin = admin.getCpf();

        // Pendentes
        assertThat(solicitacaoService.buscarPendentesPorUnidade(0, 50, null, sufixo, cpfA).getContent())
                .extracting(p -> p.getNomePaciente()).containsExactly("PacienteA" + sufixo);
        assertThat(solicitacaoService.buscarPendentesPorUnidade(0, 50, null, sufixo, cpfAdmin).getContent())
                .extracting(p -> p.getNomePaciente())
                .containsExactlyInAnyOrder("PacienteA" + sufixo, "PacienteB" + sufixo, "PacienteOrfao" + sufixo,
                        "OutraB" + sufixo);
        // /unidade/{id} de outra unidade: negado, nunca atendido.
        assertThatThrownBy(() -> solicitacaoService.buscarPendentesPorUnidade(0, 50, unidadeB.getId(), sufixo, cpfA))
                .isInstanceOf(AccessDeniedException.class);

        // Agendados
        assertThat(solicitacaoService.buscarPorStatusAguardando(0, 50, sufixo, cpfA).getContent())
                .extracting(p -> p.getNomePaciente()).containsExactly("PacienteA" + sufixo);
        assertThat(solicitacaoService.buscarPorStatusAguardando(0, 50, sufixo, cpfAdmin).getContent())
                .extracting(p -> p.getNomePaciente())
                .containsExactlyInAnyOrder("PacienteA" + sufixo, "OutraB" + sufixo);

        // Concluidos
        assertThat(solicitacaoService.buscarPorStatusConcluido(0, 50, sufixo, cpfA).getContent())
                .extracting(p -> p.getNomePaciente()).containsExactly("OutraA" + sufixo);
        assertThat(solicitacaoService.buscarPorStatusConcluido(0, 50, sufixo, cpfAdmin).getContent())
                .extracting(p -> p.getNomePaciente())
                .containsExactlyInAnyOrder("OutraA" + sufixo, "OutraB" + sufixo);

        // Urgentes
        assertThat(solicitacaoService.buscarPorUrgenteeEmergencia(0, 50, sufixo, cpfA).getContent())
                .extracting(p -> p.getNomePaciente()).containsExactly("PacienteA" + sufixo);
        assertThat(solicitacaoService.buscarPorUrgenteeEmergencia(0, 50, sufixo, cpfAdmin).getContent())
                .extracting(p -> p.getNomePaciente())
                .containsExactlyInAnyOrder("PacienteA" + sufixo, "OutraB" + sufixo);

        // GEL
        assertThat(solicitacaoService.listarPacientesGel(0, 50, sufixo, cpfA).getContent())
                .extracting(p -> p.getNomePaciente()).containsExactly("OutraA" + sufixo);
        assertThat(solicitacaoService.listarPacientesGel(0, 50, sufixo, cpfAdmin).getContent())
                .extracting(p -> p.getNomePaciente())
                .containsExactlyInAnyOrder("OutraA" + sufixo, "OutraB" + sufixo);
    }

    @Test
    @DisplayName("Sem unidade de lotacao as listas dos cards vem vazias")
    void semLotacaoRecebeListasVazias() {
        String cpf = criarUsuario(Roles.RECEPCAO, null).getCpf();

        assertThat(solicitacaoService.buscarPendentesPorUnidade(0, 50, null, sufixo, cpf).getContent()).isEmpty();
        assertThat(solicitacaoService.buscarPorStatusAguardando(0, 50, sufixo, cpf).getContent()).isEmpty();
        assertThat(solicitacaoService.buscarPorStatusConcluido(0, 50, sufixo, cpf).getContent()).isEmpty();
        assertThat(solicitacaoService.buscarPorUrgenteeEmergencia(0, 50, sufixo, cpf).getContent()).isEmpty();
        assertThat(solicitacaoService.listarPacientesGel(0, 50, sufixo, cpf).getContent()).isEmpty();
    }
}
