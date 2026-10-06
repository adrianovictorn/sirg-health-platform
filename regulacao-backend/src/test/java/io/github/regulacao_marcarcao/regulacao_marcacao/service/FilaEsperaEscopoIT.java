package io.github.regulacao_marcarcao.regulacao_marcacao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.dashboard.DashboardResumoDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.fila.FilaEsperaFiltroDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.fila.FilaEsperaItemViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.fila.FilaEsperaPacienteViewDTO;
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
import jakarta.persistence.EntityManager;

/**
 * Fila de espera: escopo por unidade, uma linha por paciente, filtros e a
 * garantia de que o numero do card do dashboard e o total da lista que ele abre.
 *
 * Fixture (dias de espera entre parenteses):
 * <ul>
 *   <li>Unidade A — Maria: Cardiologia AGUARDANDO NORMAL (40), USG AGUARDANDO
 *       URGENTE (10) e um pedido ja AGENDADO, que nunca entra na fila.</li>
 *   <li>Unidade A — Joao: Cardiologia RETORNO EMERGENCIA (100).</li>
 *   <li>Unidade A — Gel: USG GEL URGENTE (20) — GEL fica fora da fila.</li>
 *   <li>Unidade B — Bia: Cardiologia AGUARDANDO NORMAL (5).</li>
 *   <li>Sem unidade — Orfa: USG RETORNO_POLICLINICA sem prioridade (70).</li>
 * </ul>
 * O banco de teste nao e vazio: quem ve todas as unidades e sempre testado com
 * filtro de especialidade (as duas sao criadas aqui), que isola a fixture.
 *
 * <b>Nao suja a base:</b> classe {@code @Transactional}, tudo sofre rollback.
 */
@SpringBootTest
@Transactional
class FilaEsperaEscopoIT {

    @Autowired private FilaEsperaService filaEsperaService;
    @Autowired private SolicitacaoService solicitacaoService;
    @Autowired private SolicitacaoRepository solicitacaoRepository;
    @Autowired private UnidadeRepository unidadeRepository;
    @Autowired private EspecialidadeRepository especialidadeRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private EntityManager entityManager;

    private Unidade unidadeA;
    private Unidade unidadeB;
    private Especialidade cardiologia;
    private Especialidade usg;
    private User admin;
    private User gestorLotadoEmA;
    private User recepcaoA;
    private User medicoB;
    private String sufixo;

    @BeforeEach
    void setUp() {
        sufixo = "_FE_IT_" + System.nanoTime();

        unidadeA = criarUnidade("A");
        unidadeB = criarUnidade("B");
        cardiologia = criarEspecialidade("Cardiologia", ItemCategoria.ESPECIALIDADE_MEDICA);
        usg = criarEspecialidade("USG", ItemCategoria.EXAME_OU_PROCEDIMENTO);

        admin = criarUsuario(Roles.ADMIN, null);
        gestorLotadoEmA = criarUsuario(Roles.GESTOR, unidadeA);
        recepcaoA = criarUsuario(Roles.RECEPCAO, unidadeA);
        medicoB = criarUsuario(Roles.MEDICO, unidadeB);

        criarSolicitacao(unidadeA, "Maria",
                item(cardiologia, StatusDaMarcacao.AGUARDANDO, PrioridadeDaMarcacaoEnum.NORMAL, 40),
                item(usg, StatusDaMarcacao.AGUARDANDO, PrioridadeDaMarcacaoEnum.URGENTE, 10),
                item(cardiologia, StatusDaMarcacao.AGENDADO, PrioridadeDaMarcacaoEnum.NORMAL, 200));
        criarSolicitacao(unidadeA, "Joao",
                item(cardiologia, StatusDaMarcacao.RETORNO, PrioridadeDaMarcacaoEnum.EMERGENCIA, 100));
        criarSolicitacao(unidadeA, "Gel",
                item(usg, StatusDaMarcacao.GEL, PrioridadeDaMarcacaoEnum.URGENTE, 20));
        criarSolicitacao(unidadeB, "Bia",
                item(cardiologia, StatusDaMarcacao.AGUARDANDO, PrioridadeDaMarcacaoEnum.NORMAL, 5));
        criarSolicitacao(null, "Orfa",
                item(usg, StatusDaMarcacao.RETORNO_POLICLINICA, null, 70));
    }

    @AfterEach
    void limparContexto() {
        SecurityContextHolder.clearContext();
    }

    // ------------------------------------------------------------------ fixture

    private Unidade criarUnidade(String letra) {
        Unidade u = new Unidade();
        u.setNome("Unidade " + letra + sufixo);
        u.setCodigo("U" + letra + sufixo);
        u.setAtivo(true);
        return unidadeRepository.saveAndFlush(u);
    }

    private Especialidade criarEspecialidade(String nome, ItemCategoria categoria) {
        Especialidade e = new Especialidade();
        e.setCodigo(nome.toUpperCase() + sufixo);
        e.setNome(nome + sufixo);
        e.setCategoria(categoria);
        e.setAtivo(true);
        e.setVagas(0);
        return especialidadeRepository.saveAndFlush(e);
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

    /** Pedido ainda sem solicitacao; os dias de espera sao aplicados depois de salvar. */
    private record ItemFixture(SolicitacaoEspecialidade entidade, int diasEspera) {
    }

    private ItemFixture item(Especialidade especialidade, StatusDaMarcacao status,
            PrioridadeDaMarcacaoEnum prioridade, int diasEspera) {
        SolicitacaoEspecialidade se = new SolicitacaoEspecialidade();
        se.setEspecialidadeSolicitada(especialidade);
        se.setEspecialidadeCodigoLegacy(especialidade.getCodigo());
        se.setStatus(status);
        se.setPrioridade(prioridade);
        return new ItemFixture(se, diasEspera);
    }

    private void criarSolicitacao(Unidade unidade, String nome, ItemFixture... itens) {
        criarSolicitacao(unidade, nome, cpfUnico(), "700" + cpfUnico(), itens);
    }

    private void criarSolicitacao(Unidade unidade, String nome, String cpf, String cns, ItemFixture... itens) {
        Solicitacao s = new Solicitacao();
        s.setNomePaciente(nome + sufixo);
        s.setCpfPaciente(cpf);
        s.setCns(cns);
        s.setNomePai("Pai");
        s.setNomeMae("Mae");
        s.setEndereco("Rua");
        s.setUnidade(unidade);
        List<SolicitacaoEspecialidade> especs = new ArrayList<>();
        for (ItemFixture item : itens) {
            item.entidade().setSolicitacao(s);
            especs.add(item.entidade());
        }
        s.setEspecialidades(especs);
        solicitacaoRepository.saveAndFlush(s);

        // data_cadastro e @CreationTimestamp: o setter nao vale na insercao, entao a
        // espera da fixture e gravada direto na coluna.
        for (ItemFixture item : itens) {
            entityManager.createNativeQuery(
                    "UPDATE solicitacao_especialidade "
                            + "SET data_cadastro = now() - CAST(:dias AS integer) * interval '1 day' - interval '1 hour' "
                            + "WHERE id = :id")
                    .setParameter("dias", item.diasEspera())
                    .setParameter("id", item.entidade().getId())
                    .executeUpdate();
        }
    }

    // ------------------------------------------------------------------ apoio

    private FilaEsperaFiltroDTO filtro() {
        return new FilaEsperaFiltroDTO(null, null, null, null, null, null, null, null, null);
    }

    private FilaEsperaFiltroDTO porEspecialidade(Especialidade especialidade) {
        return new FilaEsperaFiltroDTO(especialidade.getId(), null, null, null, null, null, null, null, null);
    }

    private FilaEsperaFiltroDTO porTermo(String termo) {
        return new FilaEsperaFiltroDTO(null, null, null, null, null, null, null, null, null, termo);
    }

    /** 12345678909 -> 123.456.789-09 */
    private static String comPontuacao(String digitos) {
        return digitos.substring(0, 3) + "." + digitos.substring(3, 6) + "." + digitos.substring(6, 9)
                + "-" + digitos.substring(9);
    }

    private Page<FilaEsperaPacienteViewDTO> listar(FilaEsperaFiltroDTO filtro, User usuario) {
        return filaEsperaService.listar(filtro, 0, 50, usuario.getCpf());
    }

    private List<String> nomes(FilaEsperaFiltroDTO filtro, User usuario) {
        return listar(filtro, usuario).getContent().stream()
                .map(p -> p.nomePaciente().replace(sufixo, ""))
                .toList();
    }

    private FilaEsperaPacienteViewDTO linha(FilaEsperaFiltroDTO filtro, User usuario, String nome) {
        return listar(filtro, usuario).getContent().stream()
                .filter(p -> p.nomePaciente().equals(nome + sufixo))
                .findFirst()
                .orElseThrow();
    }

    // ==================================================================
    // Escopo por unidade
    // ==================================================================

    @Test
    @DisplayName("ADMIN e GESTOR veem todas as unidades e as solicitacoes sem unidade")
    void adminEGestorVeemTudo() {
        for (User global : List.of(admin, gestorLotadoEmA)) {
            assertThat(nomes(porEspecialidade(cardiologia), global))
                    .containsExactlyInAnyOrder("Maria", "Joao", "Bia");
            assertThat(nomes(porEspecialidade(usg), global))
                    .containsExactlyInAnyOrder("Maria", "Orfa");
        }
        assertThat(linha(porEspecialidade(usg), admin, "Orfa").unidadeId()).isNull();
    }

    @Test
    @DisplayName("Perfil com lotacao ve so a propria unidade, sem as solicitacoes sem unidade")
    void perfilComLotacaoVeSoAPropriaUnidade() {
        assertThat(nomes(filtro(), recepcaoA)).containsExactlyInAnyOrder("Maria", "Joao");
        assertThat(nomes(filtro(), medicoB)).containsExactly("Bia");
        assertThat(nomes(filtro(), criarUsuario(Roles.ADMIN_UNIDADE, unidadeA)))
                .containsExactlyInAnyOrder("Maria", "Joao");
        assertThat(nomes(filtro(), criarUsuario(Roles.ENFERMEIRO, unidadeA)))
                .containsExactlyInAnyOrder("Maria", "Joao");
    }

    @Test
    @DisplayName("Forcar o parametro de outra unidade e negado, nunca atendido")
    void forcarOutraUnidadeENegado() {
        FilaEsperaFiltroDTO deB = new FilaEsperaFiltroDTO(
                null, null, null, null, unidadeB.getId(), null, null, null, null);

        assertThatThrownBy(() -> listar(deB, recepcaoA)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> filaEsperaService.contar(deB, recepcaoA.getCpf()))
                .isInstanceOf(AccessDeniedException.class);
        // ADMIN pode filtrar por unidade.
        assertThat(nomes(deB, admin)).containsExactly("Bia");
    }

    @Test
    @DisplayName("Quem nao e ADMIN/GESTOR e nao tem lotacao recebe fila vazia, nao o municipio")
    void semLotacaoRecebeFilaVazia() {
        for (Roles role : List.of(Roles.RECEPCAO, Roles.ENFERMEIRO, Roles.MEDICO, Roles.ADMIN_UNIDADE)) {
            User semLotacao = criarUsuario(role, null);
            assertThat(listar(filtro(), semLotacao).getTotalElements()).as("%s", role).isZero();
            assertThat(filaEsperaService.contar(filtro(), semLotacao.getCpf())).as("%s", role).isZero();
        }
        assertThat(filaEsperaService.contar(filtro(), null)).isZero();
    }

    @Test
    @DisplayName("O escopo segue o perfil ATIVO: ADMIN atuando como ADMIN_UNIDADE fica restrito")
    void escopoSegueOPerfilAtivo() {
        User duplo = criarUsuario(Roles.ADMIN, unidadeA);
        duplo.setPerfis(new java.util.LinkedHashSet<>(Set.of(Roles.ADMIN_UNIDADE)));
        duplo = userRepository.saveAndFlush(duplo);

        assertThat(nomes(porEspecialidade(cardiologia), duplo)).contains("Bia");

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                duplo, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN_UNIDADE"))));

        assertThat(nomes(porEspecialidade(cardiologia), duplo)).containsExactlyInAnyOrder("Maria", "Joao");
    }

    // ==================================================================
    // Uma linha por paciente, ordenacao e filtros
    // ==================================================================

    @Test
    @DisplayName("Uma linha por paciente; a espera e a do pedido mais antigo que bate com os filtros")
    void umaLinhaPorPaciente() {
        FilaEsperaPacienteViewDTO maria = linha(filtro(), recepcaoA, "Maria");
        assertThat(maria.itens()).hasSize(2);
        assertThat(maria.diasEspera()).isEqualTo(40);
        assertThat(maria.unidadeId()).isEqualTo(unidadeA.getId());

        // Com filtro de especialidade a linha traz so o pedido que bate, e a espera muda.
        FilaEsperaPacienteViewDTO soUsg = linha(porEspecialidade(usg), recepcaoA, "Maria");
        assertThat(soUsg.itens()).extracting(FilaEsperaItemViewDTO::especialidadeNome)
                .containsExactly(usg.getNome());
        assertThat(soUsg.diasEspera()).isEqualTo(10);
    }

    @Test
    @DisplayName("Ordena pelos mais antigos por padrao e pelos mais recentes quando pedido")
    void ordenacao() {
        assertThat(nomes(filtro(), recepcaoA)).containsExactly("Joao", "Maria");

        FilaEsperaFiltroDTO recentes = new FilaEsperaFiltroDTO(
                null, null, null, null, null, null, null, null, "RECENTES");
        assertThat(nomes(recentes, recepcaoA)).containsExactly("Maria", "Joao");
    }

    @Test
    @DisplayName("Faixas de espera (30/60/90 dias) e periodo por data filtram por pedido")
    void faixasDeEsperaEPeriodo() {
        FilaEsperaFiltroDTO mais30 = new FilaEsperaFiltroDTO(null, null, null, null, null, 30, null, null, null);
        assertThat(nomes(mais30, recepcaoA)).containsExactly("Joao", "Maria");
        assertThat(linha(mais30, recepcaoA, "Maria").itens()).hasSize(1);

        for (int dias : List.of(60, 90)) {
            assertThat(nomes(new FilaEsperaFiltroDTO(null, null, null, null, null, dias, null, null, null), recepcaoA))
                    .as("mais de %d dias", dias)
                    .containsExactly("Joao");
        }

        LocalDate hoje = LocalDate.now();
        FilaEsperaFiltroDTO ultimos15 = new FilaEsperaFiltroDTO(
                null, null, null, null, null, null, hoje.minusDays(15), hoje, null);
        assertThat(nomes(ultimos15, recepcaoA)).containsExactly("Maria");
        assertThat(linha(ultimos15, recepcaoA, "Maria").diasEspera()).isEqualTo(10);

        assertThatThrownBy(() -> listar(new FilaEsperaFiltroDTO(
                null, null, null, null, null, null, hoje, hoje.minusDays(1), null), recepcaoA))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Filtros de tipo, status e prioridade; GEL e recusado")
    void filtrosDeTipoStatusEPrioridade() {
        FilaEsperaFiltroDTO exames = new FilaEsperaFiltroDTO(
                null, "EXAME_OU_PROCEDIMENTO", null, null, null, null, null, null, null);
        assertThat(nomes(exames, recepcaoA)).containsExactly("Maria");

        FilaEsperaFiltroDTO retorno = new FilaEsperaFiltroDTO(
                null, null, List.of("RETORNO"), null, null, null, null, null, null);
        assertThat(nomes(retorno, recepcaoA)).containsExactly("Joao");

        FilaEsperaFiltroDTO urgentes = new FilaEsperaFiltroDTO(
                null, null, null, List.of("URGENTE,EMERGENCIA"), null, null, null, null, null);
        assertThat(nomes(urgentes, recepcaoA)).containsExactlyInAnyOrder("Maria", "Joao");
        assertThat(linha(urgentes, recepcaoA, "Maria").itens()).hasSize(1);

        // Pedido sem prioridade aparece quando nao ha filtro de prioridade.
        assertThat(nomes(porEspecialidade(usg), admin)).contains("Orfa");

        FilaEsperaFiltroDTO gel = new FilaEsperaFiltroDTO(
                null, null, List.of("GEL"), null, null, null, null, null, null);
        assertThatThrownBy(() -> listar(gel, recepcaoA)).isInstanceOf(IllegalArgumentException.class);
        assertThat(nomes(filtro(), recepcaoA)).doesNotContain("Gel");
    }

    @Test
    @DisplayName("Paginacao conta pacientes, nao pedidos, e nao repete linha entre paginas")
    void paginacao() {
        Page<FilaEsperaPacienteViewDTO> primeira = filaEsperaService.listar(filtro(), 0, 1, recepcaoA.getCpf());
        Page<FilaEsperaPacienteViewDTO> segunda = filaEsperaService.listar(filtro(), 1, 1, recepcaoA.getCpf());

        assertThat(primeira.getTotalElements()).isEqualTo(2);
        assertThat(primeira.getContent()).hasSize(1);
        assertThat(segunda.getContent()).hasSize(1);
        assertThat(primeira.getContent().get(0).solicitacaoId())
                .isNotEqualTo(segunda.getContent().get(0).solicitacaoId());

        FilaEsperaFiltroDTO semResultado = new FilaEsperaFiltroDTO(
                null, null, null, null, null, 3650, null, null, null);
        assertThat(listar(semResultado, recepcaoA).getContent()).isEmpty();
    }

    // ==================================================================
    // Busca livre por nome, CPF ou CNS
    // ==================================================================

    @Test
    @DisplayName("Busca por nome: parcial e sem diferenciar maiusculas")
    void buscaPorNome() {
        assertThat(nomes(porTermo("mari"), recepcaoA)).containsExactly("Maria");
        assertThat(nomes(porTermo("MARIA"), recepcaoA)).containsExactly("Maria");
        assertThat(nomes(porTermo("  joao  "), recepcaoA)).containsExactly("Joao");

        // Achar o paciente pelo nome nao muda o que a linha mostra.
        FilaEsperaPacienteViewDTO maria = linha(porTermo("maria"), recepcaoA, "Maria");
        assertThat(maria.itens()).hasSize(2);
        assertThat(maria.diasEspera()).isEqualTo(40);
    }

    @Test
    @DisplayName("Busca por CPF: so digitos, com ou sem pontuacao, e parcial")
    void buscaPorCpf() {
        String digitosCarla = cpfUnico();
        String digitosDavi = cpfUnico();
        criarSolicitacao(unidadeA, "Carla", comPontuacao(digitosCarla), "898" + cpfUnico() + "1",
                item(cardiologia, StatusDaMarcacao.AGUARDANDO, PrioridadeDaMarcacaoEnum.NORMAL, 3));
        criarSolicitacao(unidadeA, "Davi", digitosDavi, "898" + cpfUnico() + "2",
                item(cardiologia, StatusDaMarcacao.AGUARDANDO, PrioridadeDaMarcacaoEnum.NORMAL, 2));

        // Gravado com pontuacao, buscado so com digitos.
        assertThat(nomes(porTermo(digitosCarla), recepcaoA)).containsExactly("Carla");
        // Gravado so com digitos, buscado com pontuacao.
        assertThat(nomes(porTermo(comPontuacao(digitosDavi)), recepcaoA)).containsExactly("Davi");
        // Parcial.
        assertThat(nomes(porTermo(digitosCarla.substring(1, 10)), recepcaoA)).containsExactly("Carla");
    }

    @Test
    @DisplayName("Paciente sem CPF nao quebra a busca e continua localizavel pelo nome")
    void buscaComPacienteSemCpf() {
        criarSolicitacao(unidadeA, "RecemNascido", null, "898" + cpfUnico() + "3",
                item(cardiologia, StatusDaMarcacao.AGUARDANDO, PrioridadeDaMarcacaoEnum.NORMAL, 1));

        assertThat(nomes(porTermo("recemnascido"), recepcaoA)).containsExactly("RecemNascido");
        assertThat(nomes(porTermo("00000000000"), recepcaoA)).isEmpty();
        assertThat(nomes(filtro(), recepcaoA)).contains("RecemNascido");
    }

    @Test
    @DisplayName("Busca por CNS parcial")
    void buscaPorCns() {
        String cns = "898" + cpfUnico() + "4";
        criarSolicitacao(unidadeA, "Elisa", cpfUnico(), cns,
                item(cardiologia, StatusDaMarcacao.AGUARDANDO, PrioridadeDaMarcacaoEnum.NORMAL, 4));

        assertThat(nomes(porTermo(cns), recepcaoA)).containsExactly("Elisa");
        assertThat(nomes(porTermo(cns.substring(2, 14)), recepcaoA)).containsExactly("Elisa");
    }

    @Test
    @DisplayName("Termo com letras e numeros procura so no nome, nao em CPF/CNS")
    void termoMistoProcuraSoNoNome() {
        assertThat(nomes(porTermo("Maria 7"), recepcaoA)).isEmpty();
        // So digito: vale para CPF/CNS — o CNS de toda a fixture comeca com 700.
        assertThat(nomes(porTermo("700"), recepcaoA)).contains("Maria", "Joao");
    }

    @Test
    @DisplayName("A busca combina com os demais filtros e se reflete no total e na paginacao")
    void buscaCombinaComFiltrosETotal() {
        FilaEsperaFiltroDTO retornoJoao = new FilaEsperaFiltroDTO(
                null, null, List.of("RETORNO"), null, null, null, null, null, null, "joao");
        FilaEsperaFiltroDTO retornoMaria = new FilaEsperaFiltroDTO(
                null, null, List.of("RETORNO"), null, null, null, null, null, null, "maria");
        assertThat(nomes(retornoJoao, recepcaoA)).containsExactly("Joao");
        assertThat(nomes(retornoMaria, recepcaoA)).isEmpty();

        assertThat(listar(porTermo("maria"), recepcaoA).getTotalElements()).isEqualTo(1);
        assertThat(filaEsperaService.contar(porTermo("maria"), recepcaoA.getCpf())).isEqualTo(1);

        // O sufixo da fixture esta no nome de todos: 2 pacientes de A na fila.
        Page<FilaEsperaPacienteViewDTO> primeira =
                filaEsperaService.listar(porTermo(sufixo), 0, 1, recepcaoA.getCpf());
        Page<FilaEsperaPacienteViewDTO> segunda =
                filaEsperaService.listar(porTermo(sufixo), 1, 1, recepcaoA.getCpf());
        assertThat(primeira.getTotalElements()).isEqualTo(2);
        assertThat(primeira.getContent().get(0).solicitacaoId())
                .isNotEqualTo(segunda.getContent().get(0).solicitacaoId());
    }

    @Test
    @DisplayName("Buscar paciente de outra unidade devolve vazio, igual a nenhum resultado")
    void buscaNaoAtravessaUnidades() {
        assertThat(listar(porTermo("Bia"), recepcaoA).getTotalElements()).isZero();
        assertThat(listar(porTermo("Orfa"), recepcaoA).getTotalElements()).isZero();
        assertThat(filaEsperaService.contar(porTermo("Bia"), recepcaoA.getCpf())).isZero();

        assertThat(nomes(porTermo("Bia" + sufixo), admin)).containsExactly("Bia");
        assertThat(nomes(porTermo("Bia" + sufixo), medicoB)).containsExactly("Bia");
    }

    @Test
    @DisplayName("Caracteres especiais sao texto comum: nao viram curinga nem geram erro")
    void buscaComCaracteresEspeciais() {
        for (String termo : List.of("%", "M_ria", "Mar%", "'", "\"", "\\", "%%%", "a' OR '1'='1")) {
            assertThat(listar(porTermo(termo), recepcaoA).getTotalElements()).as("termo [%s]", termo).isZero();
        }
        // O "_" do sufixo casa como caractere literal.
        assertThat(nomes(porTermo("Maria_FE_IT_"), recepcaoA)).containsExactly("Maria");

        // Caracteres de controle: NUL nao pode virar erro do banco, e um termo so
        // de controles e "sem busca", nao texto vazio (que casaria com tudo).
        List<String> semBusca = nomes(filtro(), recepcaoA);
        assertThat(nomes(porTermo("\u0001\u0000"), recepcaoA)).isEqualTo(semBusca);
        assertThat(nomes(porTermo("mar\u0000ia"), recepcaoA)).isEmpty();
        assertThat(nomes(porTermo("\u0000maria\u0001"), recepcaoA)).containsExactly("Maria");

        // So pontuacao nao tem digito: procura no nome e nao acha ninguem.
        assertThat(nomes(porTermo("..."), recepcaoA)).isEmpty();
        // Termo maior que o limite e cortado, sem erro.
        assertThat(nomes(porTermo("x".repeat(500)), recepcaoA)).isEmpty();
    }

    @Test
    @DisplayName("Termo vazio, so espacos ou nulo se comporta como sem busca")
    void termoVazioNaoFiltra() {
        List<String> semBusca = nomes(filtro(), recepcaoA);

        assertThat(nomes(porTermo(""), recepcaoA)).isEqualTo(semBusca);
        assertThat(nomes(porTermo("   "), recepcaoA)).isEqualTo(semBusca);
        assertThat(nomes(porTermo(null), recepcaoA)).isEqualTo(semBusca);
        // Os cards do dashboard contam sempre sem busca.
        assertThat(FilaEsperaFiltroDTO.de(List.of("AGUARDANDO"), null, null).termo()).isNull();
    }

    // ==================================================================
    // Card do dashboard = total da lista que ele abre
    // ==================================================================

    @Test
    @DisplayName("Os numeros por paciente do resumo batem com o total da fila, por perfil")
    void cardBateComALista() {
        FilaEsperaFiltroDTO pendentes = FilaEsperaFiltroDTO.de(List.of("AGUARDANDO"), null, null);
        FilaEsperaFiltroDTO urgentes = FilaEsperaFiltroDTO.de(null, List.of("URGENTE", "EMERGENCIA"), null);

        for (User usuario : List.of(admin, gestorLotadoEmA, recepcaoA, medicoB)) {
            DashboardResumoDTO resumo = solicitacaoService.obterResumoDashboard(usuario.getCpf());
            assertThat(resumo.pacientesPendentes())
                    .as("pendentes de %s", usuario.getRole())
                    .isEqualTo(listar(pendentes, usuario).getTotalElements());
            assertThat(resumo.pacientesUrgentes())
                    .as("urgentes de %s", usuario.getRole())
                    .isEqualTo(listar(urgentes, usuario).getTotalElements());
        }

        // Unidade A: so Maria esta pendente (Joao e retorno); urgentes sao Maria e
        // Joao — o paciente so com GEL nao conta.
        DashboardResumoDTO deA = solicitacaoService.obterResumoDashboard(recepcaoA.getCpf());
        assertThat(deA.pacientesPendentes()).isEqualTo(1);
        assertThat(deA.pacientesUrgentes()).isEqualTo(2);
        assertThat(deA.pacientesPendentesPorUnidade()).containsOnlyKeys(unidadeA.getId());

        DashboardResumoDTO doAdmin = solicitacaoService.obterResumoDashboard(admin.getCpf());
        assertThat(doAdmin.pacientesPendentesPorUnidade().get(unidadeA.getId())).isEqualTo(1);
        assertThat(doAdmin.pacientesPendentesPorUnidade().get(unidadeB.getId())).isEqualTo(1);
    }
}
