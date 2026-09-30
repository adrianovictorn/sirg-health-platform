package io.github.regulacao_marcarcao.regulacao_marcacao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.agendamentoDTO.MultiAgendamentoCreateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Especialidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.GrupoRelatorio;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Solicitacao;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.SolicitacaoEspecialidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Unidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.User;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.ItemCategoria;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.PrioridadeDaMarcacaoEnum;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.Roles;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.StatusDaMarcacao;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.TurnoEnum;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.EspecialidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.GrupoRelatorioRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.SolicitacaoRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UnidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UserRepository;

/**
 * FLUXO REAL: Agenda do Dia por Unidade (v1.6).
 *
 * Antes desta versao a "agenda do hospital" (paginas /agendas/[grupo]) era uma
 * unica lista fixa — {@code ag.local_agendamento_id = 3} no SQL — igual para
 * qualquer usuario autenticado, sem relacao nenhuma com a Unidade de lotacao do
 * operador. Este teste valida a troca para {@code solicitacao.unidade_id}: cada
 * unidade ve so os proprios pacientes, um operador restrito nao consegue ver a
 * agenda de outra unidade trocando o parametro, e o ADMIN precisa escolher
 * explicitamente qual unidade quer ver.
 *
 * <b>Nao suja a base:</b> classe {@code @Transactional}, tudo sofre rollback.
 */
@SpringBootTest
@Transactional
class AgendaPorUnidadeIT {

    @Autowired private SolicitacaoEspecialidadeService service;
    @Autowired private AgendamentoService agendamentoService;
    @Autowired private SolicitacaoRepository solicitacaoRepository;
    @Autowired private UnidadeRepository unidadeRepository;
    @Autowired private EspecialidadeRepository especialidadeRepository;
    @Autowired private GrupoRelatorioRepository grupoRelatorioRepository;
    @Autowired private UserRepository userRepository;

    private Unidade unidadeA;
    private Unidade unidadeB;
    private Especialidade cardiologia;
    private GrupoRelatorio grupo;
    private User operadorA;
    private User admin;
    private LocalDate hoje;
    private String sufixo;

    @BeforeEach
    void setUp() {
        sufixo = "_AGU_IT_" + System.nanoTime();
        hoje = LocalDate.now();

        unidadeA = new Unidade();
        unidadeA.setNome("Unidade A" + sufixo);
        unidadeA.setCodigo("UA" + sufixo);
        unidadeA.setAtivo(true);
        unidadeA = unidadeRepository.saveAndFlush(unidadeA);

        unidadeB = new Unidade();
        unidadeB.setNome("Unidade B" + sufixo);
        unidadeB.setCodigo("UB" + sufixo);
        unidadeB.setAtivo(true);
        unidadeB = unidadeRepository.saveAndFlush(unidadeB);

        grupo = new GrupoRelatorio();
        grupo.setCodigo("cardiologia" + sufixo);
        grupo.setNome("Cardiologia" + sufixo);
        grupo.setAtivo(true);
        grupo.setDirecionadoHospital(true);
        grupo = grupoRelatorioRepository.saveAndFlush(grupo);

        cardiologia = new Especialidade();
        cardiologia.setCodigo("CARDIO" + sufixo);
        cardiologia.setNome("Cardiologia" + sufixo);
        cardiologia.setCategoria(ItemCategoria.ESPECIALIDADE_MEDICA);
        cardiologia.setAtivo(true);
        cardiologia.setVagas(0);
        cardiologia.setGrupoRelatorio(grupo);
        cardiologia = especialidadeRepository.saveAndFlush(cardiologia);

        operadorA = new User();
        operadorA.setCpf(cpfUnico());
        operadorA.setNome("Operador da Unidade A");
        operadorA.setPassword("x");
        operadorA.setRole(Roles.USER);
        operadorA.setAtivo(true);
        operadorA.setUnidade(unidadeA);
        operadorA = userRepository.saveAndFlush(operadorA);

        admin = new User();
        admin.setCpf(cpfUnico());
        admin.setNome("Admin Global");
        admin.setPassword("x");
        admin.setRole(Roles.ADMIN);
        admin.setAtivo(true);
        admin = userRepository.saveAndFlush(admin);
    }

    private static String cpfUnico() {
        return String.format("%011d", System.nanoTime() % 100_000_000_000L);
    }

    /** Cria uma solicitacao pendente da unidade informada e agenda para hoje. */
    private void agendarPacienteNaUnidade(Unidade unidade, int seq, String cpfOperador) {
        Solicitacao s = new Solicitacao();
        s.setNomePaciente("Paciente " + seq + sufixo);
        s.setCpfPaciente(cpfUnico());
        s.setCns("700" + String.format("%012d", seq));
        s.setNomePai("Pai");
        s.setNomeMae("Mae");
        s.setEndereco("Rua");
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
        s = solicitacaoRepository.saveAndFlush(s);

        MultiAgendamentoCreateDTO dto = new MultiAgendamentoCreateDTO(
                List.of(cardiologia.getCodigo()), hoje, null, null, TurnoEnum.MANHA, "agendado no teste", null, null, null);
        agendamentoService.criarAgendamentoParaMultiplosExames(s.getId(), dto, cpfOperador);
    }

    @Test
    @DisplayName("FLUXO REAL: Usuario Padrao ve so os pacientes da propria unidade")
    void operadorVeSoAPropriaUnidade() {
        agendarPacienteNaUnidade(unidadeA, 1, operadorA.getCpf());
        agendarPacienteNaUnidade(unidadeB, 2, admin.getCpf());

        var pagina = service.listarPacientesAgendadosPorGrupo(0, 10, grupo.getCodigo(), hoje, null, operadorA.getCpf());

        assertThat(pagina.getTotalElements()).isEqualTo(1);
        assertThat(pagina.getContent().get(0).getNomePaciente()).contains("Paciente 1");

        long total = service.contarPacientesAgendadosPorDataEGrupo(grupo.getCodigo(), hoje, null, operadorA.getCpf());
        assertThat(total).isEqualTo(1);
    }

    @Test
    @DisplayName("FLUXO REAL: Usuario Padrao nao consegue ver a agenda de outra unidade trocando o parametro")
    void operadorNaoVeOutraUnidadeMesmoInformandoId() {
        agendarPacienteNaUnidade(unidadeB, 1, admin.getCpf());

        assertThatThrownBy(() -> service.listarPacientesAgendadosPorGrupo(
                0, 10, grupo.getCodigo(), hoje, unidadeB.getId(), operadorA.getCpf()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("FLUXO REAL: ADMIN ve a agenda de qualquer unidade escolhendo o id")
    void adminVeQualquerUnidadeEscolhendoId() {
        agendarPacienteNaUnidade(unidadeA, 1, operadorA.getCpf());
        agendarPacienteNaUnidade(unidadeB, 2, admin.getCpf());

        var paginaA = service.listarPacientesAgendadosPorGrupo(0, 10, grupo.getCodigo(), hoje, unidadeA.getId(), admin.getCpf());
        assertThat(paginaA.getTotalElements()).isEqualTo(1);
        assertThat(paginaA.getContent().get(0).getNomePaciente()).contains("Paciente 1");

        var paginaB = service.listarPacientesAgendadosPorGrupo(0, 10, grupo.getCodigo(), hoje, unidadeB.getId(), admin.getCpf());
        assertThat(paginaB.getTotalElements()).isEqualTo(1);
        assertThat(paginaB.getContent().get(0).getNomePaciente()).contains("Paciente 2");
    }

    @Test
    @DisplayName("FLUXO REAL: ADMIN sem escolher unidade e recusado em vez de ver tudo misturado")
    void adminSemEscolherUnidadeERecusado() {
        assertThatThrownBy(() -> service.listarPacientesAgendadosPorGrupo(
                0, 10, grupo.getCodigo(), hoje, null, admin.getCpf()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Selecione uma unidade");
    }

    @Test
    @DisplayName("FLUXO REAL: agenda de uma unidade nao mostra paciente sem agendamento (so AGUARDANDO)")
    void naoMostraPacienteNaoAgendado() {
        // Cria a solicitacao mas nao agenda — fica AGUARDANDO.
        Solicitacao s = new Solicitacao();
        s.setNomePaciente("Paciente sem agenda" + sufixo);
        s.setCpfPaciente(cpfUnico());
        s.setCns("700999999999");
        s.setNomePai("Pai");
        s.setNomeMae("Mae");
        s.setEndereco("Rua");
        s.setUnidade(unidadeA);

        SolicitacaoEspecialidade se = new SolicitacaoEspecialidade();
        se.setSolicitacao(s);
        se.setEspecialidadeSolicitada(cardiologia);
        se.setEspecialidadeCodigoLegacy(cardiologia.getCodigo());
        se.setStatus(StatusDaMarcacao.AGUARDANDO);
        se.setPrioridade(PrioridadeDaMarcacaoEnum.NORMAL);
        s.setEspecialidades(List.of(se));
        solicitacaoRepository.saveAndFlush(s);

        assertThatCode(() -> {
            var pagina = service.listarPacientesAgendadosPorGrupo(0, 10, grupo.getCodigo(), hoje, unidadeA.getId(), operadorA.getCpf());
            assertThat(pagina.getTotalElements()).isZero();
        }).doesNotThrowAnyException();
    }
}
