package io.github.regulacao_marcarcao.regulacao_marcacao.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

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
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.EspecialidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.GrupoRelatorioRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.SolicitacaoRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UnidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UserRepository;

/**
 * RF01: a tela /agendar (busca de solicitacoes pendentes para marcar) passa a
 * restringir o resultado a unidade de lotacao do chamador quando o perfil
 * efetivo e ADMIN_UNIDADE. ADMIN continua vendo tudo; demais perfis e
 * solicitacoes orfas (sem unidade) nao sao afetados por este ajuste.
 *
 * <b>Nao suja a base:</b> classe {@code @Transactional}, tudo sofre rollback.
 */
@SpringBootTest
@Transactional
class AgendamentoPendentesPorUnidadeIT {

    @Autowired private AgendamentoService agendamentoService;
    @Autowired private SolicitacaoRepository solicitacaoRepository;
    @Autowired private UnidadeRepository unidadeRepository;
    @Autowired private EspecialidadeRepository especialidadeRepository;
    @Autowired private GrupoRelatorioRepository grupoRelatorioRepository;
    @Autowired private UserRepository userRepository;

    private Unidade unidadeA;
    private Unidade unidadeB;
    private Especialidade cardiologia;
    private User adminGlobal;
    private User adminUnidadeA;
    private User adminUnidadeSemLotacao;
    private User recepcaoUnidadeB;
    private String sufixo;

    @BeforeEach
    void setUp() {
        sufixo = "_APU_IT_" + System.nanoTime();

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

        GrupoRelatorio grupo = new GrupoRelatorio();
        grupo.setCodigo("grp" + sufixo);
        grupo.setNome("Grupo" + sufixo);
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

        adminGlobal = criarUsuario(Roles.ADMIN, null);
        adminUnidadeA = criarUsuario(Roles.ADMIN_UNIDADE, unidadeA);
        adminUnidadeSemLotacao = criarUsuario(Roles.ADMIN_UNIDADE, null);
        recepcaoUnidadeB = criarUsuario(Roles.RECEPCAO, unidadeB);

        criarSolicitacaoPendente(unidadeA, "PacienteA");
        criarSolicitacaoPendente(unidadeB, "PacienteB");
        criarSolicitacaoPendente(null, "PacienteOrfao");
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

    private void criarSolicitacaoPendente(Unidade unidade, String nome) {
        Solicitacao s = new Solicitacao();
        s.setNomePaciente(nome + sufixo);
        s.setCpfPaciente(cpfUnico());
        s.setCns("700" + cpfUnico());
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
        solicitacaoRepository.saveAndFlush(s);
    }

    @Test
    @DisplayName("RF01: ADMIN_UNIDADE ve so a propria unidade, mais as orfas")
    void adminUnidadeVeSoAPropriaUnidadeMaisOrfas() {
        var pagina = agendamentoService.buscarPendentesParaAutoComplete(
                "" + sufixo, PageRequest.of(0, 20), adminUnidadeA.getCpf());

        assertThat(pagina.getContent())
                .extracting(dto -> dto.nomePaciente())
                .containsExactlyInAnyOrder("PacienteA" + sufixo, "PacienteOrfao" + sufixo);
    }

    @Test
    @DisplayName("RF01: ADMIN continua vendo todas as unidades, sem filtro")
    void adminGlobalContinuaVendoTudo() {
        var pagina = agendamentoService.buscarPendentesParaAutoComplete(
                "" + sufixo, PageRequest.of(0, 20), adminGlobal.getCpf());

        assertThat(pagina.getContent())
                .extracting(dto -> dto.nomePaciente())
                .containsExactlyInAnyOrder("PacienteA" + sufixo, "PacienteB" + sufixo, "PacienteOrfao" + sufixo);
    }

    @Test
    @DisplayName("RF01: ADMIN_UNIDADE sem unidade vinculada recebe pagina vazia, sem excecao")
    void adminUnidadeSemLotacaoRecebeListaVazia() {
        var pagina = agendamentoService.buscarPendentesParaAutoComplete(
                "" + sufixo, PageRequest.of(0, 20), adminUnidadeSemLotacao.getCpf());

        assertThat(pagina.getContent()).isEmpty();
    }

    @Test
    @DisplayName("RF01: demais perfis (ex.: RECEPCAO) nao sao filtrados por este ajuste")
    void demaisPerfisContinuamSemFiltro() {
        var pagina = agendamentoService.buscarPendentesParaAutoComplete(
                "" + sufixo, PageRequest.of(0, 20), recepcaoUnidadeB.getCpf());

        assertThat(pagina.getContent())
                .extracting(dto -> dto.nomePaciente())
                .containsExactlyInAnyOrder("PacienteA" + sufixo, "PacienteB" + sufixo, "PacienteOrfao" + sufixo);
    }

    @Test
    @DisplayName("RF01: chamada sem autenticacao (cpf nulo) continua sem filtro, como hoje")
    void chamadaSemAutenticacaoNaoQuebra() {
        var pagina = agendamentoService.buscarPendentesParaAutoComplete(
                "" + sufixo, PageRequest.of(0, 20), null);

        assertThat(pagina.getContent())
                .extracting(dto -> dto.nomePaciente())
                .containsExactlyInAnyOrder("PacienteA" + sufixo, "PacienteB" + sufixo, "PacienteOrfao" + sufixo);
    }
}
