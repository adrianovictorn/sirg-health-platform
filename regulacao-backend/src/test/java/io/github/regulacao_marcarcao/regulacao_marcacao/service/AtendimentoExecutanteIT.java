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
import org.springframework.data.domain.Page;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.agendamentoDTO.MultiAgendamentoCreateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.atendimento.AtendimentoResumoViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.atendimento.AtendimentoPacienteViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.atendimento.AtendimentoViewDTO;
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
import jakarta.persistence.EntityManager;

/**
 * FLUXO REAL: tela de Atendimentos do operador da unidade executante.
 *
 * Valida a visao municipal (ADMIN) do resumo e da lista nominal — sem filtro
 * obrigatorio por unidade —, que o USER sem executante configurada nao ve nada
 * (ADR 0013; o recorte por executante esta em {@link AtendimentoExecutanteEscopoIT}),
 * que os demais perfis levam acesso negado, que a solicitacao sem unidade aparece
 * na contagem e na lista, e que CPF/CNS saem completos.
 *
 * A base pode ter dados reais: o dia do teste fica tres anos a frente, onde nao ha
 * agenda nenhuma, e e isso que permite afirmar os totais do municipio.
 * <b>Nao suja a base:</b> classe {@code @Transactional}.
 */
@SpringBootTest
@Transactional
class AtendimentoExecutanteIT {

    @Autowired private AtendimentoExecutanteService service;
    @Autowired private AgendamentoService agendamentoService;
    @Autowired private SolicitacaoRepository solicitacaoRepository;
    @Autowired private UnidadeRepository unidadeRepository;
    @Autowired private EspecialidadeRepository especialidadeRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private GrupoRelatorioRepository grupoRelatorioRepository;
    @Autowired private EntityManager em;

    private String sufixo;
    private Unidade unidadeA;
    private Unidade unidadeB;
    private Unidade laboratorio;
    private Especialidade hemograma;
    private Especialidade cardiologia;
    private Especialidade glicemia;
    private User admin;
    private User executante;
    private User executanteSemLotacao;
    private LocalDate dia;

    @BeforeEach
    void setUp() {
        sufixo = "_ATEND_IT_" + System.nanoTime();
        dia = LocalDate.now(AtendimentoExecutanteService.FUSO).plusYears(3);

        unidadeA = unidade("Unidade A");
        unidadeB = unidade("Unidade B");
        laboratorio = unidade("Laboratorio");

        hemograma = especialidade("HEMO", ItemCategoria.EXAME_OU_PROCEDIMENTO);
        cardiologia = especialidade("CARDIO", ItemCategoria.ESPECIALIDADE_MEDICA);
        glicemia = especialidade("GLIC", ItemCategoria.EXAME_OU_PROCEDIMENTO);

        admin = usuario(Roles.ADMIN, null);
        executante = usuario(Roles.USER, laboratorio);
        executanteSemLotacao = usuario(Roles.USER, null);
    }

    @Test
    @DisplayName("ADMIN ve os quantitativos de todas as unidades; USER sem executante configurada ve tudo zerado")
    void resumoDoMunicipioParaOAdmin() {
        agendar(unidadeA, hemograma, "Ana");
        SolicitacaoEspecialidade confirmado = agendar(unidadeA, hemograma, "Bruno");
        SolicitacaoEspecialidade faltou = agendar(unidadeB, cardiologia, "Carla");
        SolicitacaoEspecialidade cancelado = agendar(unidadeB, cardiologia, "Davi");
        marcar(confirmado, StatusDaMarcacao.REALIZADO);
        marcar(faltou, StatusDaMarcacao.FALTOU);
        marcar(cancelado, StatusDaMarcacao.CANCELADO);
        limpar();

        // ADR 0013: lotado em unidade que nao e executante configurada, ou sem lotacao, nao ve nada.
        for (User u : List.of(executante, executanteSemLotacao)) {
            AtendimentoResumoViewDTO resumo = service.resumir(dia, null, u.getCpf());
            assertThat(resumo.semExecutante()).as(u.getNome()).isTrue();
            assertThat(resumo.totais().itens()).as(u.getNome()).isZero();
            assertThat(service.listar(dia, null, null, false, null, null, 0, 10, u.getCpf()).getTotalElements())
                    .as(u.getNome()).isZero();
        }

        {
            AtendimentoResumoViewDTO resumo = service.resumir(dia, null, admin.getCpf());
            assertThat(resumo.semExecutante()).isFalse();

            assertThat(resumo.data()).isEqualTo(dia);
            var a = unidadeDoResumo(resumo, unidadeA).contagem();
            assertThat(a.pacientes()).isEqualTo(2);
            assertThat(a.itens()).isEqualTo(2);
            assertThat(a.agendados()).isEqualTo(1);
            assertThat(a.confirmados()).isEqualTo(1);
            assertThat(a.faltas()).isZero();
            assertThat(a.cancelados()).isZero();

            // Faltas e cancelados saem separados (na Agenda do Dia do ADMIN saem somados).
            var b = unidadeDoResumo(resumo, unidadeB).contagem();
            assertThat(b.agendados()).isZero();
            assertThat(b.faltas()).isEqualTo(1);
            assertThat(b.cancelados()).isEqualTo(1);

            // A unidade do proprio executante nao solicitou nada: aparece, zerada.
            assertThat(unidadeDoResumo(resumo, laboratorio).contagem().itens()).isZero();

            assertThat(resumo.totais().itens()).isEqualTo(4);
            assertThat(resumo.totais().pacientes()).isEqualTo(4);
            assertThat(resumo.totais().confirmados()).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("Lista nominal traz o municipio inteiro, com CPF/CNS completos no paciente e categoria e coleta no item")
    void listaNominalDoMunicipio() {
        SolicitacaoEspecialidade exame = agendar(unidadeA, hemograma, "Ana");
        exame.setDataColeta(dia.minusDays(1));
        String cpfDaAna = exame.getSolicitacao().getCpfPaciente();
        String cnsDaAna = exame.getSolicitacao().getCns();
        agendar(unidadeB, cardiologia, "Bruno");
        limpar();

        var pagina = service.listar(dia, null, null, false, null, null, 0, 1000, admin.getCpf());

        assertThat(pagina.getSize()).isLessThanOrEqualTo(AtendimentoExecutanteService.TAMANHO_MAXIMO_PAGINA);
        assertThat(pagina.getTotalElements()).isEqualTo(2);
        assertThat(pagina.getContent()).extracting(AtendimentoPacienteViewDTO::unidadeNome)
                .containsExactly(unidadeA.getNome(), unidadeB.getNome());

        AtendimentoPacienteViewDTO ana = pagina.getContent().get(0);
        assertThat(ana.solicitacaoId()).isEqualTo(exame.getSolicitacao().getId());
        assertThat(ana.nomePaciente()).startsWith("Ana");
        // Completos, como estao gravados (ADR 0010): o operador confere o documento no balcao.
        assertThat(ana.cpf()).isEqualTo(cpfDaAna).hasSize(11).doesNotContain("*");
        assertThat(ana.cns()).isEqualTo(cnsDaAna).hasSize(15).doesNotContain("*");

        AtendimentoViewDTO item = ana.itens().get(0);
        assertThat(ana.itens()).hasSize(1);
        assertThat(item.id()).isEqualTo(exame.getId());
        assertThat(item.solicitacaoId()).isEqualTo(ana.solicitacaoId());
        assertThat(item.especialidadeNome()).isEqualTo(hemograma.getNome());
        assertThat(item.categoria()).isEqualTo("EXAME_OU_PROCEDIMENTO");
        assertThat(item.status()).isEqualTo("AGENDADO");
        assertThat(item.turno()).isEqualTo("MANHA");
        assertThat(item.dataColeta()).isEqualTo(dia.minusDays(1));
        assertThat(pagina.getContent().get(1).itens().get(0).categoria()).isEqualTo("ESPECIALIDADE_MEDICA");

        assertThat(service.listar(dia.plusDays(1), null, null, false, null, null, 0, 10, admin.getCpf()).getContent())
                .isEmpty();
    }

    @Test
    @DisplayName("Um bloco por paciente: os itens do dia vem juntos, a pagina conta pacientes e ninguem e dividido entre paginas")
    void agrupaPorPaciente() {
        Solicitacao ana = agendarVarios(unidadeA, "Ana", dia, hemograma, cardiologia);
        Solicitacao bruno = agendarVarios(unidadeA, "Bruno", dia, hemograma, cardiologia);
        // Mesma ficha, outro dia: nao entra no bloco de hoje.
        agendarNaFicha(ana, dia.plusDays(1), glicemia);
        // Homonimo em outra ficha: bloco proprio, nunca fundido por nome.
        Solicitacao homonimo = agendarVarios(unidadeB, "Ana", dia, hemograma);
        limpar();
        String cpf = admin.getCpf();

        var todos = service.listar(dia, null, null, false, null, null, 0, 10, cpf);
        assertThat(todos.getTotalElements()).isEqualTo(3);
        assertThat(todos.getContent()).extracting(AtendimentoPacienteViewDTO::solicitacaoId)
                .containsExactly(ana.getId(), homonimo.getId(), bruno.getId());
        assertThat(todos.getContent().get(0).itens()).hasSize(2)
                .allSatisfy(i -> assertThat(i.solicitacaoId()).isEqualTo(ana.getId()));
        assertThat(todos.getContent().get(1).itens()).hasSize(1);
        assertThat(todos.getContent().get(2).itens()).hasSize(2);

        // Pagina de 1 paciente: cada pagina traz o paciente inteiro.
        for (int p = 0; p < 3; p++) {
            var pagina = service.listar(dia, null, null, false, null, null, p, 1, cpf);
            assertThat(pagina.getTotalElements()).isEqualTo(3);
            assertThat(pagina.getTotalPages()).isEqualTo(3);
            assertThat(pagina.getContent()).singleElement().satisfies(
                    bloco -> assertThat(bloco.itens()).hasSize(bloco.solicitacaoId().equals(homonimo.getId()) ? 1 : 2));
        }

        // Pagina alem da ultima: vazia, mas com o total real, para a tela poder voltar.
        var alem = service.listar(dia, null, null, false, null, null, 5, 1, cpf);
        assertThat(alem.getContent()).isEmpty();
        assertThat(alem.getTotalElements()).isEqualTo(3);
        assertThat(alem.getTotalPages()).isEqualTo(3);

        // O numero de pacientes da unidade no resumo e o total da lista filtrada por ela.
        AtendimentoResumoViewDTO resumo = service.resumir(dia, null, cpf);
        assertThat(service.listar(dia, null, unidadeA.getId(), false, null, null, 0, 10, cpf).getTotalElements())
                .isEqualTo(unidadeDoResumo(resumo, unidadeA).contagem().pacientes()).isEqualTo(2);
        assertThat(resumo.totais().pacientes()).isEqualTo(3);
        assertThat(resumo.totais().itens()).isEqualTo(5);
    }

    @Test
    @DisplayName("Situacao seleciona o paciente que tem algum item nela e traz o bloco inteiro, sem recortar")
    void situacaoSelecionaOPacienteENaoRecortaOBloco() {
        Solicitacao ana = agendarVarios(unidadeA, "Ana", dia, hemograma, cardiologia);
        Solicitacao bruno = agendarVarios(unidadeA, "Bruno", dia, hemograma);
        limpar();
        SolicitacaoEspecialidade exameDaAna = itemDe(ana, hemograma);
        marcar(exameDaAna, StatusDaMarcacao.FALTOU);
        limpar();
        String cpf = admin.getCpf();

        var comFalta = service.listar(dia, null, null, false, "FALTOU", null, 0, 10, cpf);
        assertThat(comFalta.getTotalElements()).isEqualTo(1);
        assertThat(comFalta.getContent()).singleElement().satisfies(bloco -> {
            assertThat(bloco.solicitacaoId()).isEqualTo(ana.getId());
            assertThat(bloco.itens()).extracting(AtendimentoViewDTO::status)
                    .containsExactlyInAnyOrder("FALTOU", "AGENDADO");
        });

        // "Agendado" traz os dois pacientes; o bloco da Ana continua com os dois itens.
        var agendados = service.listar(dia, null, null, false, "AGENDADO", null, 0, 10, cpf);
        assertThat(agendados.getContent()).extracting(AtendimentoPacienteViewDTO::solicitacaoId)
                .containsExactly(ana.getId(), bruno.getId());
        assertThat(agendados.getContent().get(0).itens()).hasSize(2);

        assertThat(service.listar(dia, null, null, false, "CANCELADO", null, 0, 10, cpf).getContent()).isEmpty();
    }

    @Test
    @DisplayName("Filtros opcionais: unidade, status e busca por nome, CPF completo ou CNS completo")
    void filtrosOpcionais() {
        SolicitacaoEspecialidade ana = agendar(unidadeA, hemograma, "Ana");
        SolicitacaoEspecialidade bruno = agendar(unidadeB, hemograma, "Bruno");
        marcar(bruno, StatusDaMarcacao.FALTOU);
        // Nomes sem digito: a busca por nome tambem casa numeros, e o sufixo do teste tem varios.
        ana.getSolicitacao().setNomePaciente("Ana Paciente");
        bruno.getSolicitacao().setNomePaciente("Bruno Paciente");
        String cpfDaAna = ana.getSolicitacao().getCpfPaciente();
        String cnsDoBruno = bruno.getSolicitacao().getCns();
        limpar();
        String cpf = admin.getCpf();

        assertThat(itens(service.listar(dia, null, unidadeB.getId(), false, null, null, 0, 10, cpf)))
                .containsExactly(bruno.getId());
        assertThat(itens(service.listar(dia, null, null, false, "faltou", null, 0, 10, cpf)))
                .containsExactly(bruno.getId());
        assertThat(itens(service.listar(dia, null, null, false, null, "  ana ", 0, 10, cpf)))
                .containsExactly(ana.getId());
        assertThat(itens(service.listar(dia, null, null, false, null, cpfDaAna, 0, 10, cpf)))
                .containsExactly(ana.getId());
        String cpfPontuado = cpfDaAna.substring(0, 3) + "." + cpfDaAna.substring(3, 6) + "."
                + cpfDaAna.substring(6, 9) + "-" + cpfDaAna.substring(9);
        assertThat(itens(service.listar(dia, null, null, false, null, cpfPontuado, 0, 10, cpf)))
                .containsExactly(ana.getId());
        assertThat(itens(service.listar(dia, null, null, false, null, cnsDoBruno, 0, 10, cpf)))
                .containsExactly(bruno.getId());
        assertThat(itens(service.listar(dia, null, null, false, null,
                cnsDoBruno.substring(0, 3) + " " + cnsDoBruno.substring(3), 0, 10, cpf))).containsExactly(bruno.getId());

        // Documento so casa completo: um trecho de CPF ou de CNS nao lista paciente nenhum.
        for (String trecho : List.of(cpfDaAna.substring(0, 1), cpfDaAna.substring(0, 6), cpfDaAna.substring(0, 10),
                cpfDaAna.substring(1), cnsDoBruno.substring(0, 14), cnsDoBruno.substring(1),
                cnsDoBruno.substring(0, 12))) {
            assertThat(service.listar(dia, null, null, false, null, trecho, 0, 10, cpf).getContent())
                    .as("trecho de documento com " + trecho.length() + " digitos").isEmpty();
        }

        // Curingas e caracteres de controle sao texto comum, nunca erro.
        for (String termo : List.of("%", "_", "a'b\"c", "\\", "a\u0000b", "\u0001")) {
            service.listar(dia, null, null, false, null, termo, 0, 10, cpf);
        }
        assertThat(service.listar(dia, null, null, false, null, "%", 0, 10, cpf).getContent()).isEmpty();
        assertThat(service.listar(dia, null, null, false, null, "\u0001", 0, 10, cpf).getTotalElements()).isEqualTo(2);

        assertThatThrownBy(() -> service.listar(dia, null, null, false, "INEXISTENTE", null, 0, 10, cpf))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Filtro por grupo (a agenda do menu) restringe o resumo e a lista; codigo desconhecido vem vazio")
    void filtroPorGrupo() {
        GrupoRelatorio laboratorioGrupo = new GrupoRelatorio();
        laboratorioGrupo.setCodigo("lab" + sufixo);
        laboratorioGrupo.setNome("Laboratorio" + sufixo);
        laboratorioGrupo.setAtivo(true);
        laboratorioGrupo.setDirecionadoHospital(true);
        laboratorioGrupo = grupoRelatorioRepository.saveAndFlush(laboratorioGrupo);
        hemograma.setGrupoRelatorio(laboratorioGrupo);
        hemograma = especialidadeRepository.saveAndFlush(hemograma);

        SolicitacaoEspecialidade doGrupo = agendar(unidadeA, hemograma, "Ana");
        agendar(unidadeA, cardiologia, "Bruno"); // especialidade sem grupo
        agendar(unidadeB, cardiologia, "Carla");
        limpar();
        String cpf = admin.getCpf();
        String codigo = laboratorioGrupo.getCodigo();

        AtendimentoResumoViewDTO resumo = service.resumir(dia, codigo, cpf);
        assertThat(resumo.totais().itens()).isEqualTo(1);
        assertThat(unidadeDoResumo(resumo, unidadeA).contagem().itens()).isEqualTo(1);
        assertThat(unidadeDoResumo(resumo, unidadeB).contagem().itens()).isZero();
        assertThat(itens(service.listar(dia, codigo, null, false, null, null, 0, 10, cpf)))
                .containsExactly(doGrupo.getId());

        // Paciente com itens em duas agendas: filtrado pela agenda, o bloco so tem os itens dela.
        Solicitacao misto = agendarVarios(unidadeB, "Davi", dia, hemograma, cardiologia);
        limpar();
        var daAgenda = service.listar(dia, codigo, unidadeB.getId(), false, null, null, 0, 10, cpf);
        assertThat(daAgenda.getContent()).singleElement().satisfies(bloco -> {
            assertThat(bloco.solicitacaoId()).isEqualTo(misto.getId());
            assertThat(bloco.itens()).extracting(AtendimentoViewDTO::especialidadeNome)
                    .containsExactly(hemograma.getNome());
        });
        assertThat(service.listar(dia, null, unidadeB.getId(), false, null, "Davi", 0, 10, cpf).getContent())
                .singleElement().satisfies(bloco -> assertThat(bloco.itens()).hasSize(2));
        limpar();
        em.createNativeQuery("DELETE FROM solicitacao_especialidade WHERE solicitacao_id = :id")
                .setParameter("id", misto.getId()).executeUpdate();

        // Sem grupo (nulo ou em branco): todos, inclusive os de especialidade sem grupo.
        assertThat(service.resumir(dia, "  ", cpf).totais().itens()).isEqualTo(3);
        assertThat(service.listar(dia, null, null, false, null, null, 0, 10, cpf).getTotalElements()).isEqualTo(3);

        assertThat(service.resumir(dia, "grupo-que-nao-existe", cpf).totais().itens()).isZero();
        assertThat(service.listar(dia, "grupo-que-nao-existe", null, false, null, null, 0, 10, cpf).getContent())
                .isEmpty();
    }

    @Test
    @DisplayName("Solicitacao sem unidade aparece no resumo e na lista, e tem filtro proprio")
    void solicitacaoSemUnidadeAparece() {
        SolicitacaoEspecialidade orfa = agendar(null, hemograma, "Orfa");
        agendar(unidadeA, hemograma, "Ana");
        limpar();
        String cpf = admin.getCpf();

        AtendimentoResumoViewDTO resumo = service.resumir(dia, null, cpf);
        assertThat(resumo.unidades()).anySatisfy(u -> {
            assertThat(u.id()).isNull();
            assertThat(u.nome()).isEqualTo("Sem unidade");
            assertThat(u.contagem().itens()).isEqualTo(1);
        });
        assertThat(resumo.totais().itens()).isEqualTo(2);

        assertThat(service.listar(dia, null, null, false, null, null, 0, 10, cpf).getTotalElements()).isEqualTo(2);
        var soOrfas = service.listar(dia, null, null, true, null, null, 0, 10, cpf);
        assertThat(soOrfas.getTotalElements()).isEqualTo(1);
        assertThat(soOrfas.getContent()).singleElement().satisfies(bloco -> {
            assertThat(bloco.itens()).extracting(AtendimentoViewDTO::id).containsExactly(orfa.getId());
            assertThat(bloco.unidadeNome()).isNull();
        });
    }

    @Test
    @DisplayName("Perfis que nao operam atendimentos levam acesso negado, mesmo lotados na unidade")
    void outrosPerfisSaoNegados() {
        for (Roles role : List.of(Roles.GESTOR, Roles.ADMIN_UNIDADE, Roles.RECEPCAO, Roles.ENFERMEIRO,
                Roles.MEDICO, Roles.COORD_TRANSPORTE, Roles.PACIENTE)) {
            String cpf = usuario(role, unidadeA).getCpf();
            assertThatThrownBy(() -> service.resumir(dia, null, cpf))
                    .as(role.name()).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> service.listar(dia, null, unidadeA.getId(), false, null, null, 0, 10, cpf))
                    .as(role.name()).isInstanceOf(AccessDeniedException.class);
        }
        assertThatThrownBy(() -> service.resumir(dia, null, null)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.listar(dia, null, null, false, null, null, 0, 10, "00000000000"))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ------------------------------------------------------------------

    private static String cpfUnico() {
        return String.format("%011d", System.nanoTime() % 100_000_000_000L);
    }

    private void limpar() {
        em.flush();
        em.clear();
    }

    private Unidade unidade(String nome) {
        Unidade u = new Unidade();
        u.setNome(nome + sufixo);
        u.setCodigo(nome.replace(" ", "") + sufixo);
        u.setAtivo(true);
        return unidadeRepository.saveAndFlush(u);
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

    private User usuario(Roles role, Unidade daUnidade) {
        User u = new User();
        u.setCpf(cpfUnico());
        u.setNome(role + sufixo);
        u.setPassword("x");
        u.setRole(role);
        u.setAtivo(true);
        u.setUnidade(daUnidade);
        return userRepository.saveAndFlush(u);
    }

    /** Cria o pedido da unidade (pode ser nula) e agenda para o dia do teste. Devolve o item agendado. */
    private SolicitacaoEspecialidade agendar(Unidade daUnidade, Especialidade item, String nome) {
        Solicitacao s = new Solicitacao();
        s.setNomePaciente(nome + sufixo);
        s.setCpfPaciente(cpfUnico());
        s.setCns("700" + String.format("%012d", System.nanoTime() % 1_000_000_000_000L));
        s.setNomePai("Pai");
        s.setNomeMae("Mae");
        s.setEndereco("Rua");
        s.setUnidade(daUnidade);

        SolicitacaoEspecialidade se = new SolicitacaoEspecialidade();
        se.setSolicitacao(s);
        se.setEspecialidadeSolicitada(item);
        se.setEspecialidadeCodigoLegacy(item.getCodigo());
        se.setStatus(StatusDaMarcacao.AGUARDANDO);
        se.setPrioridade(PrioridadeDaMarcacaoEnum.NORMAL);
        List<SolicitacaoEspecialidade> itens = new ArrayList<>();
        itens.add(se);
        s.setEspecialidades(itens);
        s = solicitacaoRepository.saveAndFlush(s);

        agendamentoService.criarAgendamentoParaMultiplosExames(s.getId(),
                new MultiAgendamentoCreateDTO(List.of(item.getCodigo()), dia, null, null, TurnoEnum.MANHA,
                        "agendado no teste", null, null, null),
                admin.getCpf());
        return s.getEspecialidades().get(0);
    }

    /** Uma ficha com os itens dados, todos num unico agendamento na data informada. */
    private Solicitacao agendarVarios(Unidade daUnidade, String nome, LocalDate data, Especialidade... pedidos) {
        Solicitacao s = new Solicitacao();
        s.setNomePaciente(nome + sufixo);
        s.setCpfPaciente(cpfUnico());
        s.setCns("700" + String.format("%012d", System.nanoTime() % 1_000_000_000_000L));
        s.setNomePai("Pai");
        s.setNomeMae("Mae");
        s.setEndereco("Rua");
        s.setUnidade(daUnidade);
        List<SolicitacaoEspecialidade> itens = new ArrayList<>();
        List<String> codigos = new ArrayList<>();
        for (Especialidade pedido : pedidos) {
            itens.add(itemPendente(s, pedido));
            codigos.add(pedido.getCodigo());
        }
        s.setEspecialidades(itens);
        s = solicitacaoRepository.saveAndFlush(s);
        agendamentoService.criarAgendamentoParaMultiplosExames(s.getId(),
                new MultiAgendamentoCreateDTO(codigos, data, null, null, TurnoEnum.MANHA, "agendado no teste",
                        null, null, null),
                admin.getCpf());
        em.flush();
        return s;
    }

    /** Acrescenta um item a uma ficha existente e o agenda na data informada. */
    private void agendarNaFicha(Solicitacao ficha, LocalDate data, Especialidade pedido) {
        Solicitacao s = solicitacaoRepository.findById(ficha.getId()).orElseThrow();
        s.getEspecialidades().add(itemPendente(s, pedido));
        solicitacaoRepository.saveAndFlush(s);
        agendamentoService.criarAgendamentoParaMultiplosExames(s.getId(),
                new MultiAgendamentoCreateDTO(List.of(pedido.getCodigo()), data, null, null, TurnoEnum.MANHA,
                        "agendado no teste", null, null, null),
                admin.getCpf());
        em.flush();
    }

    private static SolicitacaoEspecialidade itemPendente(Solicitacao s, Especialidade pedido) {
        SolicitacaoEspecialidade se = new SolicitacaoEspecialidade();
        se.setSolicitacao(s);
        se.setEspecialidadeSolicitada(pedido);
        se.setEspecialidadeCodigoLegacy(pedido.getCodigo());
        se.setStatus(StatusDaMarcacao.AGUARDANDO);
        se.setPrioridade(PrioridadeDaMarcacaoEnum.NORMAL);
        return se;
    }

    private SolicitacaoEspecialidade itemDe(Solicitacao ficha, Especialidade pedido) {
        return solicitacaoRepository.findById(ficha.getId()).orElseThrow().getEspecialidades().stream()
                .filter(i -> pedido.getId().equals(i.getEspecialidadeSolicitada().getId()))
                .findFirst().orElseThrow();
    }

    /** Ids de todos os itens da pagina, na ordem em que aparecem. */
    private static List<Long> itens(Page<AtendimentoPacienteViewDTO> pagina) {
        return pagina.getContent().stream().flatMap(p -> p.itens().stream()).map(AtendimentoViewDTO::id).toList();
    }

    private void marcar(SolicitacaoEspecialidade item, StatusDaMarcacao status) {
        item.setStatus(status);
    }

    private AtendimentoResumoViewDTO.Unidade unidadeDoResumo(AtendimentoResumoViewDTO resumo, Unidade u) {
        return resumo.unidades().stream().filter(x -> u.getId().equals(x.id())).findFirst().orElseThrow();
    }
}
