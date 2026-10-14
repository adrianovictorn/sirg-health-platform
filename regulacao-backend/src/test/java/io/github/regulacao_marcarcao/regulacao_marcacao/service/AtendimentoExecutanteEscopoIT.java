package io.github.regulacao_marcarcao.regulacao_marcacao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.transaction.annotation.Transactional;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.agendamento.localAgendamentoDTO.LocalAgendamentoUpdateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.agendamentoDTO.MultiAgendamentoCreateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.atendimento.AgendaGrupoViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.atendimento.AtendimentoPacienteViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.atendimento.AtendimentoViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.unidade_executante.LocalExecutanteVinculoDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.unidade_executante.UnidadeExecutanteUpdateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Especialidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.GrupoRelatorio;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.LocalAgendamento;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Solicitacao;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.SolicitacaoEspecialidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Unidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.User;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.ItemCategoria;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.PrioridadeDaMarcacaoEnum;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.Roles;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.StatusDaMarcacao;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.TipoUnidadeEnum;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.TurnoEnum;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.AtendimentoExecutanteRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.EspecialidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.GrupoRelatorioRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.LocalAgendamentoRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.SolicitacaoRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UnidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UserRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.AtendimentoRelatorioProjection;
import jakarta.persistence.EntityManager;

/**
 * FLUXO REAL: recorte da tela de Atendimentos por unidade executante (V110) e a
 * parametrizacao que o define.
 *
 * Cenario: Fisioterapia realiza o grupo "Fisio" no local "Centro de Fisio";
 * Laboratorio realiza a especialidade avulsa Hemograma no local "Labocliv".
 * Pertencimento = especialidade da executante E local dela (ou sem local, se a
 * especialidade nao for compartilhada). O USER ve as executantes da lotacao e da
 * lista de operadores; sem nenhuma, nada (ADR 0013).
 *
 * Mesmo dia tres anos a frente de {@link AtendimentoExecutanteIT}, onde nao ha
 * agenda real. <b>Nao suja a base:</b> classe {@code @Transactional}.
 */
@SpringBootTest
@Transactional
class AtendimentoExecutanteEscopoIT {

    @Autowired private AtendimentoExecutanteService service;
    @Autowired private UnidadeExecutanteService parametrizacao;
    @Autowired private GrupoRelatorioService grupoRelatorioService;
    @Autowired private LocalAgendamentoService localAgendamentoService;
    @Autowired private AgendamentoService agendamentoService;
    @Autowired private AtendimentoExecutanteRepository atendimentoRepository;
    @Autowired private ExcelService excelService;
    @Autowired private SolicitacaoRepository solicitacaoRepository;
    @Autowired private UnidadeRepository unidadeRepository;
    @Autowired private EspecialidadeRepository especialidadeRepository;
    @Autowired private GrupoRelatorioRepository grupoRelatorioRepository;
    @Autowired private LocalAgendamentoRepository localRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private EntityManager em;

    private String sufixo;
    private LocalDate dia;
    private Unidade usf;
    private Unidade fisioterapia;
    private Unidade laboratorio;
    private GrupoRelatorio grupoFisio;
    private Especialidade sessaoFisio;
    private Especialidade hemograma;
    private Especialidade cardiologia;
    private LocalAgendamento localFisio;
    private LocalAgendamento localLab;
    private User admin;
    private User operadorFisio;
    private User operadorLab;
    private User operadorDosDois;
    private User operadorSemVinculo;

    @BeforeEach
    void setUp() {
        sufixo = "_EXEC_IT_" + System.nanoTime();
        dia = LocalDate.now(AtendimentoExecutanteService.FUSO).plusYears(3);

        usf = unidade("USF", TipoUnidadeEnum.SOLICITANTE);
        fisioterapia = unidade("Fisioterapia", TipoUnidadeEnum.EXECUTANTE);
        laboratorio = unidade("Laboratorio", TipoUnidadeEnum.AMBOS);

        grupoFisio = grupo("fisio", false);
        sessaoFisio = especialidade("SESSAO", grupoFisio);
        hemograma = especialidade("HEMO", null);
        cardiologia = especialidade("CARDIO", null);

        localFisio = local("Centro de Fisio");
        localLab = local("Labocliv");

        admin = usuario(Roles.ADMIN);
        operadorFisio = usuario(Roles.USER);
        operadorLab = usuario(Roles.USER);
        operadorDosDois = usuario(Roles.USER);
        operadorSemVinculo = usuario(Roles.USER);

        parametrizacao.atualizar(fisioterapia.getId(), new UnidadeExecutanteUpdateDTO(List.of(grupoFisio.getId()),
                List.of(), List.of(localFisio.getId()), List.of(operadorFisio.getId(), operadorDosDois.getId())));
        parametrizacao.atualizar(laboratorio.getId(), new UnidadeExecutanteUpdateDTO(List.of(),
                List.of(hemograma.getId()), List.of(localLab.getId()), List.of(operadorLab.getId(), operadorDosDois.getId())));
        limpar();
    }

    @Test
    @DisplayName("Operador vinculado ve so especialidade E local da sua executante, na lista, na contagem e no resumo")
    void recortePorEspecialidadeELocal() {
        SolicitacaoEspecialidade fisioNoLocal = agendar("Ana", localFisio, sessaoFisio);
        SolicitacaoEspecialidade hemoNoLab = agendar("Bruno", localLab, hemograma);
        SolicitacaoEspecialidade cardioNoLab = agendar("Carla", localLab, cardiologia); // de ninguem
        // Especialidade da Fisio marcada no local do Lab: nao e de nenhuma das duas.
        SolicitacaoEspecialidade fisioNoLab = agendar("Davi", localLab, sessaoFisio);
        limpar();

        assertThat(itens(listar(operadorFisio))).containsExactly(fisioNoLocal.getId());
        assertThat(itens(listar(operadorLab))).containsExactly(hemoNoLab.getId());
        assertThat(itens(listar(operadorDosDois))).containsExactlyInAnyOrder(fisioNoLocal.getId(), hemoNoLab.getId());

        assertThat(listar(operadorFisio).getTotalElements()).isEqualTo(1);
        assertThat(service.resumir(dia, null, operadorFisio.getCpf()).totais().itens()).isEqualTo(1);
        assertThat(service.resumir(dia, null, operadorDosDois.getCpf()).totais().itens()).isEqualTo(2);

        // Regressao: ADMIN continua vendo o municipio inteiro.
        assertThat(itens(listar(admin))).containsExactlyInAnyOrder(fisioNoLocal.getId(),
                hemoNoLab.getId(), cardioNoLab.getId(), fisioNoLab.getId());
        assertThat(service.resumir(dia, null, admin.getCpf()).totais().itens()).isEqualTo(4);
        assertThat(service.resumir(dia, null, admin.getCpf()).semExecutante()).isFalse();

        // ADR 0013: USER sem executante (nem lotacao nem lista) nao ve nada.
        assertThat(itens(listar(operadorSemVinculo))).isEmpty();
        assertThat(listar(operadorSemVinculo).getTotalElements()).isZero();
        var resumoSemVinculo = service.resumir(dia, null, operadorSemVinculo.getCpf());
        assertThat(resumoSemVinculo.totais().itens()).isZero();
        assertThat(resumoSemVinculo.semExecutante()).isTrue();
        assertThat(service.resumir(dia, null, operadorFisio.getCpf()).semExecutante()).isFalse();
    }

    @Test
    @DisplayName("Lotacao numa executante configurada da o mesmo recorte da lista de operadores")
    void lotacaoNaExecutante() throws Exception {
        SolicitacaoEspecialidade fisio = agendar("Ana", localFisio, sessaoFisio);
        SolicitacaoEspecialidade hemo = agendar("Bruno", localLab, hemograma);
        fisio.setStatus(StatusDaMarcacao.REALIZADO);
        hemo.setStatus(StatusDaMarcacao.REALIZADO);
        User lotadoNaFisio = usuario(Roles.USER, fisioterapia);
        limpar();

        assertThat(itens(listar(lotadoNaFisio))).containsExactly(fisio.getId());
        assertThat(service.resumir(dia, null, lotadoNaFisio.getCpf()).totais().itens()).isEqualTo(1);
        assertThat(nomesDaPlanilha(lotadoNaFisio)).containsExactly("Ana" + sufixo);
        assertThat(service.listarAgendas(lotadoNaFisio.getCpf())).extracting(AgendaGrupoViewDTO::codigo)
                .containsExactly(grupoFisio.getCodigo());
    }

    @Test
    @DisplayName("Lotacao em unidade que nao e executante configurada: nao ve nada, nem as agendas")
    void lotacaoForaDeExecutanteNaoVeNada() throws Exception {
        SolicitacaoEspecialidade fisio = agendar("Ana", localFisio, sessaoFisio);
        fisio.setStatus(StatusDaMarcacao.REALIZADO);
        User lotadoNaUsf = usuario(Roles.USER, usf);
        limpar();

        assertThat(itens(listar(lotadoNaUsf))).isEmpty();
        assertThat(service.resumir(dia, null, lotadoNaUsf.getCpf()).semExecutante()).isTrue();
        assertThat(nomesDaPlanilha(lotadoNaUsf)).isEmpty();
        assertThat(service.listarAgendas(lotadoNaUsf.getCpf())).isEmpty();
    }

    @Test
    @DisplayName("Lotacao e lista somam: lotado na Fisio e operador do Lab ve as duas, sem duplicar")
    void lotacaoMaisLista() {
        SolicitacaoEspecialidade fisio = agendar("Ana", localFisio, sessaoFisio);
        SolicitacaoEspecialidade hemo = agendar("Bruno", localLab, hemograma);
        agendar("Carla", localLab, cardiologia); // de ninguem
        User lotadoNaFisio = usuario(Roles.USER, fisioterapia);
        parametrizacao.atualizar(laboratorio.getId(), new UnidadeExecutanteUpdateDTO(List.of(),
                List.of(hemograma.getId()), List.of(localLab.getId()), List.of(lotadoNaFisio.getId())));
        // Lotado E na lista da mesma executante: nao duplica.
        parametrizacao.atualizar(fisioterapia.getId(), new UnidadeExecutanteUpdateDTO(List.of(grupoFisio.getId()),
                List.of(), List.of(localFisio.getId()), List.of(lotadoNaFisio.getId())));
        limpar();

        assertThat(itens(listar(lotadoNaFisio))).containsExactlyInAnyOrder(fisio.getId(), hemo.getId());
        assertThat(listar(lotadoNaFisio).getTotalElements()).isEqualTo(2);
        assertThat(service.resumir(dia, null, lotadoNaFisio.getCpf()).totais().itens()).isEqualTo(2);
    }

    @Test
    @DisplayName("Lotacao em executante incompleta (sem local) nao ve nada")
    void lotacaoEmExecutanteIncompleta() {
        agendar("Ana", localLab, hemograma);
        User lotadoNoLab = usuario(Roles.USER, laboratorio);
        parametrizacao.vincularLocal(localLab.getId(), new LocalExecutanteVinculoDTO(null));
        limpar();

        assertThat(itens(listar(lotadoNoLab))).isEmpty();
        assertThat(service.resumir(dia, null, lotadoNoLab.getCpf()).semExecutante()).isTrue();
    }

    @Test
    @DisplayName("Paginacao recortada: a countQuery conta so os pacientes da executante")
    void contagemComPaginaDeUm() {
        agendar("Ana", localFisio, sessaoFisio);
        agendar("Bruno", localLab, hemograma);
        agendar("Carla", localLab, cardiologia); // de ninguem
        limpar();

        // Pagina de 1 forca a countQuery (na pagina 0 incompleta o Spring deduz o total).
        var pagina = service.listar(dia, null, null, false, null, null, 0, 1, operadorDosDois.getCpf());
        assertThat(pagina.getTotalElements()).isEqualTo(2);
        assertThat(pagina.getTotalPages()).isEqualTo(2);
        var segunda = service.listar(dia, null, null, false, null, null, 1, 1, operadorDosDois.getCpf());
        assertThat(segunda.getContent()).hasSize(1);
        assertThat(service.listar(dia, null, null, false, null, null, 0, 1, operadorLab.getCpf()).getTotalElements())
                .isEqualTo(1);
        assertThat(service.listar(dia, null, null, false, null, null, 0, 1, admin.getCpf()).getTotalElements())
                .isEqualTo(3);
    }

    @Test
    @DisplayName("O bloco do paciente vem recortado: item de outra executante na mesma ficha nao aparece")
    void blocoDoPacienteRecortado() {
        Solicitacao ficha = agendarVarios("Ana", localLab, hemograma, cardiologia);
        limpar();

        Page<AtendimentoPacienteViewDTO> pagina = listar(operadorLab);
        assertThat(pagina.getContent()).singleElement().satisfies(bloco -> {
            assertThat(bloco.solicitacaoId()).isEqualTo(ficha.getId());
            assertThat(bloco.itens()).extracting(AtendimentoViewDTO::especialidadeNome)
                    .containsExactly(hemograma.getNome());
        });
        assertThat(listar(admin).getContent()).singleElement()
                .satisfies(bloco -> assertThat(bloco.itens()).hasSize(2));
    }

    @Test
    @DisplayName("Agendamento sem local pertence pela especialidade; item sem especialidade nao pertence a ninguem")
    void semLocalCaiNaEspecialidade() {
        SolicitacaoEspecialidade semLocal = agendar("Ana", null, hemograma);
        SolicitacaoEspecialidade semEspecialidade = agendar("Bruno", null, hemograma);
        limpar();
        em.createNativeQuery("UPDATE solicitacao_especialidade SET especialidade_id = NULL WHERE id = :id")
                .setParameter("id", semEspecialidade.getId()).executeUpdate();
        limpar();

        assertThat(itens(listar(operadorLab))).containsExactly(semLocal.getId());
        assertThat(itens(listar(operadorFisio))).isEmpty();
        assertThat(itens(listar(admin))).containsExactlyInAnyOrder(semLocal.getId(), semEspecialidade.getId());
    }

    @Test
    @DisplayName("Executante incompleta (sem local) nao conta: o operador so dela nao ve nada (ADR 0013)")
    void executanteIncompletaNaoVeNada() {
        agendar("Ana", localLab, hemograma);
        agendar("Bruno", null, cardiologia);
        parametrizacao.vincularLocal(localLab.getId(), new LocalExecutanteVinculoDTO(null));
        limpar();

        assertThat(itens(listar(operadorLab))).isEmpty();
        assertThat(service.listarAgendas(operadorLab.getCpf())).isEmpty();
    }

    @Test
    @DisplayName("Relatorio de Atendimento aplica o mesmo recorte da lista")
    void relatorioRecortado() {
        SolicitacaoEspecialidade hemo = agendar("Ana", localLab, hemograma);
        SolicitacaoEspecialidade fisio = agendar("Bruno", localFisio, sessaoFisio);
        hemo.setStatus(StatusDaMarcacao.REALIZADO);
        fisio.setStatus(StatusDaMarcacao.REALIZADO);
        limpar();

        var escopo = new UnidadeAcessoService.EscopoAtendimento(List.of(laboratorio.getId()));
        List<AtendimentoRelatorioProjection> linhas = atendimentoRepository.listarRelatorio(dia, dia, null, true,
                AtendimentoExecutanteService.idsDoFiltro(escopo));
        assertThat(linhas).extracting(AtendimentoRelatorioProjection::getNomePaciente)
                .containsExactly("Ana" + sufixo);

        List<AtendimentoRelatorioProjection> municipal = atendimentoRepository.listarRelatorio(dia, dia, null, false,
                AtendimentoExecutanteService.idsDoFiltro(UnidadeAcessoService.EscopoAtendimento.MUNICIPAL));
        assertThat(municipal).hasSize(2);
    }

    @Test
    @DisplayName("Planilha do Relatorio de Atendimento gerada pelo operador restrito so traz a executante dele")
    void planilhaDoOperadorRestrito() throws Exception {
        SolicitacaoEspecialidade hemo = agendar("Ana", localLab, hemograma);
        SolicitacaoEspecialidade fisio = agendar("Bruno", localFisio, sessaoFisio);
        hemo.setStatus(StatusDaMarcacao.REALIZADO);
        fisio.setStatus(StatusDaMarcacao.REALIZADO);
        limpar();

        assertThat(nomesDaPlanilha(operadorLab)).containsExactly("Ana" + sufixo);
        assertThat(nomesDaPlanilha(operadorFisio)).containsExactly("Bruno" + sufixo);
        assertThat(nomesDaPlanilha(admin)).containsExactlyInAnyOrder("Ana" + sufixo, "Bruno" + sufixo);
    }

    @Test
    @DisplayName("Agendas do menu: o restrito ve os grupos das suas executantes, sem depender da flag do hospital")
    void agendasNoEscopo() {
        GrupoRelatorio grupoLab = grupo("lab", true);
        hemograma.setGrupoRelatorio(grupoLab);
        especialidadeRepository.saveAndFlush(hemograma);
        limpar();

        assertThat(service.listarAgendas(operadorFisio.getCpf())).extracting(AgendaGrupoViewDTO::codigo)
                .containsExactly(grupoFisio.getCodigo());
        assertThat(service.listarAgendas(operadorDosDois.getCpf())).extracting(AgendaGrupoViewDTO::codigo)
                .containsExactlyInAnyOrder(grupoFisio.getCodigo(), grupoLab.getCodigo());

        // Municipal: so os direcionados ao hospital (Fisio nao e).
        List<String> municipais = service.listarAgendas(admin.getCpf()).stream().map(AgendaGrupoViewDTO::codigo)
                .toList();
        assertThat(municipais).contains(grupoLab.getCodigo()).doesNotContain(grupoFisio.getCodigo());
    }

    // ------------------------------------------------------------------
    // Parametrizacao
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Compartilhados (V111): grupo, avulsa e cruzamentos com outra executante salvam; reenvio e idempotente")
    void grupoEAvulsaCompartilhados() {
        Unidade outra = unidade("Clinica", TipoUnidadeEnum.EXECUTANTE);

        var grupo = new UnidadeExecutanteUpdateDTO(List.of(grupoFisio.getId()), null, null, null);
        parametrizacao.atualizar(outra.getId(), grupo);
        parametrizacao.atualizar(outra.getId(), grupo);
        parametrizacao.atualizar(outra.getId(), new UnidadeExecutanteUpdateDTO(null, List.of(hemograma.getId()), null, null));
        parametrizacao.atualizar(outra.getId(), new UnidadeExecutanteUpdateDTO(null, List.of(sessaoFisio.getId()), null, null));

        // Grupo que contem uma avulsa de outra executante tambem salva.
        GrupoRelatorio grupoLab = grupo("lab", true);
        hemograma.setGrupoRelatorio(grupoLab);
        especialidadeRepository.saveAndFlush(hemograma);
        parametrizacao.atualizar(outra.getId(), new UnidadeExecutanteUpdateDTO(List.of(grupoFisio.getId(), grupoLab.getId()),
                List.of(), null, null));
        limpar();

        assertThat(parametrizacao.buscar(outra.getId()).grupos()).extracting(g -> g.id())
                .containsExactlyInAnyOrder(grupoFisio.getId(), grupoLab.getId());
        assertThat(parametrizacao.buscar(fisioterapia.getId()).grupos()).extracting(g -> g.id())
                .containsExactly(grupoFisio.getId());
        assertThat(parametrizacao.buscar(laboratorio.getId()).especialidades()).extracting(e -> e.id())
                .containsExactly(hemograma.getId());
    }

    @Test
    @DisplayName("Grupo compartilhado: com local decide o dono do local; sem local nao e de ninguem, so do ADMIN")
    void grupoCompartilhadoRecorte() {
        Unidade clinica = unidade("Clinica", TipoUnidadeEnum.EXECUTANTE);
        LocalAgendamento localClinica = local("Clinica local");
        User operadorClinica = usuario(Roles.USER);
        User operadorFisioEClinica = usuario(Roles.USER);
        parametrizacao.atualizar(clinica.getId(), new UnidadeExecutanteUpdateDTO(List.of(grupoFisio.getId()), List.of(),
                List.of(localClinica.getId()), List.of(operadorClinica.getId(), operadorFisioEClinica.getId())));
        parametrizacao.atualizar(fisioterapia.getId(), new UnidadeExecutanteUpdateDTO(List.of(grupoFisio.getId()),
                List.of(), List.of(localFisio.getId()), List.of(operadorFisio.getId(), operadorFisioEClinica.getId())));
        SolicitacaoEspecialidade naFisio = agendar("Ana", localFisio, sessaoFisio);
        SolicitacaoEspecialidade naClinica = agendar("Bruno", localClinica, sessaoFisio);
        SolicitacaoEspecialidade semLocal = agendar("Carla", null, sessaoFisio);
        limpar();

        assertThat(itens(listar(operadorFisio))).containsExactly(naFisio.getId());
        assertThat(itens(listar(operadorClinica))).containsExactly(naClinica.getId());
        // Uniforme por executante: nem o operador das duas ve o item sem local.
        assertThat(itens(listar(operadorFisioEClinica))).containsExactlyInAnyOrder(naFisio.getId(), naClinica.getId());
        assertThat(service.resumir(dia, null, operadorFisioEClinica.getCpf()).totais().itens()).isEqualTo(2);
        assertThat(service.listar(dia, null, null, false, null, null, 0, 1, operadorFisioEClinica.getCpf())
                .getTotalElements()).isEqualTo(2);
        assertThat(itens(listar(admin))).contains(semLocal.getId());

        // Agendas: o grupo compartilhado aparece uma vez para cada operador.
        assertThat(service.listarAgendas(operadorClinica.getCpf())).extracting(AgendaGrupoViewDTO::codigo)
                .containsExactly(grupoFisio.getCodigo());
        assertThat(service.listarAgendas(operadorFisioEClinica.getCpf())).extracting(AgendaGrupoViewDTO::codigo)
                .containsExactly(grupoFisio.getCodigo());
    }

    @Test
    @DisplayName("Cruzamento grupo x avulsa: avulsa de outra executante tambem tira o item sem local")
    void grupoDeUmaAvulsaDeOutraSemLocal() {
        Unidade clinica = unidade("Clinica", TipoUnidadeEnum.EXECUTANTE);
        parametrizacao.atualizar(clinica.getId(), new UnidadeExecutanteUpdateDTO(List.of(), List.of(sessaoFisio.getId()),
                List.of(local("Clinica local").getId()), List.of()));
        SolicitacaoEspecialidade semLocal = agendar("Ana", null, sessaoFisio);
        limpar();

        assertThat(itens(listar(operadorFisio))).isEmpty();
        assertThat(itens(listar(admin))).contains(semLocal.getId());
    }

    @Test
    @DisplayName("Concorrente inativa ou so solicitante nao conta: o item sem local continua da executante unica")
    void concorrenteInvalidaNaoTiraSemLocal() {
        Unidade clinica = unidade("Clinica", TipoUnidadeEnum.EXECUTANTE);
        parametrizacao.atualizar(clinica.getId(), new UnidadeExecutanteUpdateDTO(List.of(grupoFisio.getId()), List.of(),
                List.of(), List.of()));
        SolicitacaoEspecialidade semLocal = agendar("Ana", null, sessaoFisio);
        limpar();
        assertThat(itens(listar(operadorFisio))).isEmpty();

        Unidade c = unidadeRepository.findById(clinica.getId()).orElseThrow();
        c.setAtivo(false);
        unidadeRepository.saveAndFlush(c);
        limpar();
        assertThat(itens(listar(operadorFisio))).containsExactly(semLocal.getId());

        c = unidadeRepository.findById(clinica.getId()).orElseThrow();
        c.setAtivo(true);
        c.setTipo(TipoUnidadeEnum.SOLICITANTE);
        unidadeRepository.saveAndFlush(c);
        limpar();
        assertThat(itens(listar(operadorFisio))).containsExactly(semLocal.getId());
    }

    @Test
    @DisplayName("Validacoes: unidade so solicitante e usuario sem perfil USER sao 400; o PUT substitui o conjunto")
    void validacoesESubstituicao() {
        assertThatThrownBy(() -> parametrizacao.atualizar(usf.getId(), new UnidadeExecutanteUpdateDTO(null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> parametrizacao.atualizar(laboratorio.getId(),
                new UnidadeExecutanteUpdateDTO(null, null, null, List.of(admin.getId()))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> parametrizacao.atualizar(laboratorio.getId(),
                new UnidadeExecutanteUpdateDTO(null, List.of(-1L), null, null)))
                .isInstanceOf(IllegalArgumentException.class);

        var view = parametrizacao.atualizar(laboratorio.getId(),
                new UnidadeExecutanteUpdateDTO(null, List.of(cardiologia.getId()), List.of(localFisio.getId()), null));
        limpar();
        assertThat(view.especialidades()).extracting(e -> e.id()).containsExactly(cardiologia.getId());
        assertThat(view.operadores()).isEmpty();
        // Local da Fisio foi MOVIDO para o Lab; o Labocliv, que saiu da lista, ficou sem executante.
        assertThat(view.locais()).extracting(l -> l.id()).containsExactly(localFisio.getId());
        assertThat(localRepository.findById(localLab.getId()).orElseThrow().getUnidadeExecutante()).isNull();
        assertThat(parametrizacao.buscar(fisioterapia.getId()).configurada()).isFalse();
    }

    @Test
    @DisplayName("Editar o local pela tela de Locais nao apaga o vinculo; excluir grupo vinculado e 409")
    void vinculoPreservadoEGrupoProtegido() {
        localAgendamentoService.atualizarLocalAgendamento(localLab.getId(),
                new LocalAgendamentoUpdateDTO("Labocliv renomeado" + sufixo, null, "Rua", "1"));
        limpar();
        assertThat(localRepository.findById(localLab.getId()).orElseThrow().getUnidadeExecutante().getId())
                .isEqualTo(laboratorio.getId());

        assertThatThrownBy(() -> grupoRelatorioService.deletarGrupoRelatorio(grupoFisio.getId()))
                .isInstanceOf(IllegalStateException.class);

        // Compartilhado com outra executante continua protegido.
        Unidade clinica = unidade("Clinica", TipoUnidadeEnum.EXECUTANTE);
        parametrizacao.atualizar(clinica.getId(), new UnidadeExecutanteUpdateDTO(List.of(grupoFisio.getId()), null, null, null));
        limpar();
        assertThatThrownBy(() -> grupoRelatorioService.deletarGrupoRelatorio(grupoFisio.getId()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Vincular local pela tela de Locais move de uma executante para outra; unidade invalida e 400")
    void vincularLocalMoveEValida() {
        parametrizacao.vincularLocal(localFisio.getId(), new LocalExecutanteVinculoDTO(laboratorio.getId()));
        limpar();
        assertThat(localRepository.findById(localFisio.getId()).orElseThrow().getUnidadeExecutante().getId())
                .isEqualTo(laboratorio.getId());
        assertThat(parametrizacao.buscar(fisioterapia.getId()).configurada()).isFalse();

        assertThatThrownBy(() -> parametrizacao.vincularLocal(localLab.getId(), new LocalExecutanteVinculoDTO(usf.getId())))
                .isInstanceOf(IllegalArgumentException.class);
        Unidade inativa = unidade("Inativa", TipoUnidadeEnum.EXECUTANTE);
        inativa.setAtivo(false);
        unidadeRepository.saveAndFlush(inativa);
        assertThatThrownBy(() -> parametrizacao.vincularLocal(localLab.getId(), new LocalExecutanteVinculoDTO(inativa.getId())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Operador que perdeu o perfil USER aparece marcado como invalido e pode ser desmarcado")
    void operadorInvalidoPodeSerDesmarcado() {
        User perdeuPerfil = userRepository.findById(operadorLab.getId()).orElseThrow();
        perdeuPerfil.setRole(Roles.RECEPCAO);
        userRepository.saveAndFlush(perdeuPerfil);
        User desativado = userRepository.findById(operadorDosDois.getId()).orElseThrow();
        desativado.setAtivo(false);
        userRepository.saveAndFlush(desativado);
        limpar();

        var view = parametrizacao.buscar(laboratorio.getId());
        assertThat(view.operadores()).allSatisfy(o -> assertThat(o.podeOperar()).isFalse()).hasSize(2);

        // Reenviar os invalidos e 400; sem eles, salva.
        assertThatThrownBy(() -> parametrizacao.atualizar(laboratorio.getId(), new UnidadeExecutanteUpdateDTO(null,
                List.of(hemograma.getId()), List.of(localLab.getId()), List.of(operadorLab.getId()))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> parametrizacao.atualizar(laboratorio.getId(), new UnidadeExecutanteUpdateDTO(null,
                List.of(hemograma.getId()), List.of(localLab.getId()), List.of(operadorDosDois.getId()))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(parametrizacao.atualizar(laboratorio.getId(), new UnidadeExecutanteUpdateDTO(null,
                List.of(hemograma.getId()), List.of(localLab.getId()), List.of())).operadores()).isEmpty();
    }

    @Test
    @DisplayName("Executante desativada nao aparece como configurada")
    void desativadaNaoEConfigurada() {
        assertThat(parametrizacao.buscar(laboratorio.getId()).configurada()).isTrue();
        Unidade lab = unidadeRepository.findById(laboratorio.getId()).orElseThrow();
        lab.setAtivo(false);
        unidadeRepository.saveAndFlush(lab);
        limpar();
        assertThat(parametrizacao.buscar(laboratorio.getId()).configurada()).isFalse();
    }

    // ------------------------------------------------------------------

    private List<String> nomesDaPlanilha(User quem) throws Exception {
        List<String> nomes = new ArrayList<>();
        try (XSSFWorkbook workbook = new XSSFWorkbook(excelService.gerarPlanilhaAtendimentos(dia, dia, null, quem.getCpf()))) {
            for (Row linha : workbook.getSheetAt(0)) {
                for (Cell celula : linha) {
                    // So nomes de paciente: unidade e especialidade tambem terminam com o sufixo.
                    if (celula.getCellType() == CellType.STRING
                            && List.of("Ana" + sufixo, "Bruno" + sufixo).contains(celula.getStringCellValue())) {
                        nomes.add(celula.getStringCellValue());
                    }
                }
            }
        }
        return nomes;
    }

    private Page<AtendimentoPacienteViewDTO> listar(User u) {
        return service.listar(dia, null, null, false, null, null, 0, 50, u.getCpf());
    }

    private static List<Long> itens(Page<AtendimentoPacienteViewDTO> pagina) {
        return pagina.getContent().stream().flatMap(p -> p.itens().stream()).map(AtendimentoViewDTO::id).toList();
    }

    private static String cpfUnico() {
        return String.format("%011d", System.nanoTime() % 100_000_000_000L);
    }

    private void limpar() {
        em.flush();
        em.clear();
    }

    private Unidade unidade(String nome, TipoUnidadeEnum tipo) {
        Unidade u = new Unidade();
        u.setNome(nome + sufixo);
        u.setCodigo(nome + sufixo);
        u.setAtivo(true);
        u.setTipo(tipo);
        return unidadeRepository.saveAndFlush(u);
    }

    private GrupoRelatorio grupo(String codigo, boolean hospital) {
        GrupoRelatorio g = new GrupoRelatorio();
        g.setCodigo(codigo + sufixo);
        g.setNome(codigo + sufixo);
        g.setAtivo(true);
        g.setDirecionadoHospital(hospital);
        return grupoRelatorioRepository.saveAndFlush(g);
    }

    private Especialidade especialidade(String prefixo, GrupoRelatorio grupo) {
        Especialidade e = new Especialidade();
        e.setCodigo(prefixo + sufixo);
        e.setNome(prefixo + sufixo);
        e.setCategoria(ItemCategoria.EXAME_OU_PROCEDIMENTO);
        e.setAtivo(true);
        e.setVagas(0);
        e.setGrupoRelatorio(grupo);
        return especialidadeRepository.saveAndFlush(e);
    }

    private LocalAgendamento local(String nome) {
        LocalAgendamento l = new LocalAgendamento();
        l.setNomeLocal(nome + sufixo);
        return localRepository.saveAndFlush(l);
    }

    private User usuario(Roles role) {
        return usuario(role, null);
    }

    private User usuario(Roles role, Unidade lotacao) {
        User u = new User();
        u.setUnidade(lotacao);
        u.setCpf(cpfUnico());
        u.setNome(role + sufixo + System.nanoTime());
        u.setPassword("x");
        u.setRole(role);
        u.setAtivo(true);
        return userRepository.saveAndFlush(u);
    }

    private SolicitacaoEspecialidade agendar(String nome, LocalAgendamento local, Especialidade item) {
        Solicitacao s = agendarVarios(nome, local, item);
        return solicitacaoRepository.findById(s.getId()).orElseThrow().getEspecialidades().get(0);
    }

    private Solicitacao agendarVarios(String nome, LocalAgendamento local, Especialidade... pedidos) {
        Solicitacao s = new Solicitacao();
        s.setNomePaciente(nome + sufixo);
        s.setCpfPaciente(cpfUnico());
        s.setCns("700" + String.format("%012d", System.nanoTime() % 1_000_000_000_000L));
        s.setNomePai("Pai");
        s.setNomeMae("Mae");
        s.setEndereco("Rua");
        s.setUnidade(usf);
        List<SolicitacaoEspecialidade> itens = new ArrayList<>();
        List<String> codigos = new ArrayList<>();
        for (Especialidade pedido : pedidos) {
            SolicitacaoEspecialidade se = new SolicitacaoEspecialidade();
            se.setSolicitacao(s);
            se.setEspecialidadeSolicitada(pedido);
            se.setEspecialidadeCodigoLegacy(pedido.getCodigo());
            se.setStatus(StatusDaMarcacao.AGUARDANDO);
            se.setPrioridade(PrioridadeDaMarcacaoEnum.NORMAL);
            itens.add(se);
            codigos.add(pedido.getCodigo());
        }
        s.setEspecialidades(itens);
        s = solicitacaoRepository.saveAndFlush(s);
        agendamentoService.criarAgendamentoParaMultiplosExames(s.getId(),
                new MultiAgendamentoCreateDTO(codigos, dia, null, local != null ? local.getId() : null, TurnoEnum.MANHA,
                        "agendado no teste", null, null, null),
                admin.getCpf());
        em.flush();
        return s;
    }
}
