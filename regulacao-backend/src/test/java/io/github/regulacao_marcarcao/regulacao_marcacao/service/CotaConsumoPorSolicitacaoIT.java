package io.github.regulacao_marcarcao.regulacao_marcacao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.agendamentoDTO.AgendamentoSolicitacaoSimpleViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.agendamentoDTO.AgendamentoVerificacaoDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.agendamentoDTO.AgendamentoVerificacaoDTO.ItemVerificadoDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.agendamentoDTO.MultiAgendamentoCreateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.CotaUnidade;
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
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.TipoPeriodoCota;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.TurnoEnum;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.CotaUnidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.EspecialidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.GrupoRelatorioRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.SolicitacaoRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UnidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UserRepository;
import jakarta.persistence.EntityManager;

/**
 * V113: a cota e consumida por SOLICITACAO (paciente), nao por item. Uma
 * solicitacao ocupa no maximo uma vaga de cada cota que incide sobre ela,
 * quantos exames forem agendados nessa cota — num lote so ou em agendamentos
 * separados. A vaga so volta quando sai o ultimo item da solicitacao que a
 * ocupava.
 */
@SpringBootTest
@Transactional
class CotaConsumoPorSolicitacaoIT {

    private static final DateTimeFormatter PERIODO_MENSAL = DateTimeFormatter.ofPattern("yyyy-MM");

    @Autowired private AgendamentoService agendamentoService;
    @Autowired private SolicitacaoService solicitacaoService;
    @Autowired private SolicitacaoRepository solicitacaoRepository;
    @Autowired private CotaUnidadeRepository cotaRepository;
    @Autowired private UnidadeRepository unidadeRepository;
    @Autowired private EspecialidadeRepository especialidadeRepository;
    @Autowired private GrupoRelatorioRepository grupoRelatorioRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private EntityManager entityManager;

    private String sufixo;
    private LocalDate data;
    private Unidade unidade;
    private User operador;
    private GrupoRelatorio lab;

    @BeforeEach
    void setUp() {
        sufixo = "_CPS_" + System.nanoTime();
        data = LocalDate.now();
        unidade = unidade();
        operador = usuario(Roles.ADMIN_UNIDADE, unidade);
        lab = grupo();
    }

    @Test
    @DisplayName("lote de 19 exames numa cota de grupo com faixa de horario (5/15 usadas): todos entram, consome 1 vaga")
    void loteGrandeNaCotaDeGrupoComHorarioConsomeUmaVaga() {
        CotaUnidade cota = cotaDeGrupoPorData(15, 5, LocalTime.of(7, 0), LocalTime.of(9, 0));
        Especialidade[] exames = new Especialidade[19];
        SolicitacaoEspecialidade[] itens = new SolicitacaoEspecialidade[19];
        for (int i = 0; i < 19; i++) {
            exames[i] = especialidade("E" + i);
            itens[i] = item(exames[i]);
        }
        Solicitacao s = ficha(itens);

        AgendamentoVerificacaoDTO verificacao = verificar(s, exames);
        assertThat(verificacao.itens()).allMatch(ItemVerificadoDTO::podeAgendar);

        AgendamentoSolicitacaoSimpleViewDTO resposta = agendar(s, exames);
        assertThat(resposta.especialidades()).hasSize(19);
        assertThat(utilizadaDe(cota)).isEqualTo(6);
    }

    @Test
    @DisplayName("cota de grupo sem horario (por incidencia) e cota da especialidade: 1 vaga em cada, nao uma por item")
    void incidenciaConjuntaConsomeUmaVagaEmCadaCota() {
        CotaUnidade grupo = cotaDeGrupoMensal(1);
        Especialidade a = especialidade("A");
        Especialidade b = especialidade("B");
        Especialidade c = especialidade("C");
        CotaUnidade daA = cotaDaEspecialidade(a, 1);
        Solicitacao s = ficha(item(a), item(b), item(c));

        assertThat(verificar(s, a, b, c).itens()).allMatch(ItemVerificadoDTO::podeAgendar);
        agendar(s, a, b, c);

        assertThat(utilizadaDe(grupo)).isEqualTo(1);
        assertThat(utilizadaDe(daA)).isEqualTo(1);
    }

    @Test
    @DisplayName("mesma solicitacao em dois agendamentos separados na mesma cota: o segundo nao consome")
    void segundoAgendamentoDaMesmaSolicitacaoNaoConsome() {
        CotaUnidade cota = cotaDeGrupoMensal(1);
        Especialidade a = especialidade("A");
        Especialidade b = especialidade("B");
        Solicitacao s = ficha(item(a), item(b));

        agendar(s, a);
        limpar();
        assertThat(verificar(s, b).itens()).allMatch(ItemVerificadoDTO::podeAgendar);
        agendar(s, b);

        assertThat(utilizadaDe(cota)).isEqualTo(1);
    }

    @Test
    @DisplayName("duas solicitacoes diferentes ocupam duas vagas: com a cota cheia, a segunda e recusada")
    void duasSolicitacoesConsomemDuasVagas() {
        CotaUnidade cota = cotaDeGrupoMensal(1);
        Especialidade a = especialidade("A");
        Especialidade b = especialidade("B");
        Solicitacao s1 = ficha(item(a), item(b));
        Solicitacao s2 = ficha(item(a));

        agendar(s1, a, b);
        limpar();

        ItemVerificadoDTO recusado = verificar(s2, a).itens().get(0);
        assertThat(recusado.podeAgendar()).isFalse();
        assertThat(recusado.motivo()).contains("Cota esgotada");
        assertThatThrownBy(() -> agendar(s2, a))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(recusado.motivo());
        assertThat(utilizadaDe(cota)).isEqualTo(1);
    }

    @Test
    @DisplayName("excluir um de dois agendamentos da solicitacao nao devolve; excluir o ultimo devolve 1")
    void estornoSoNoUltimoAgendamentoDaSolicitacao() {
        CotaUnidade cota = cotaDeGrupoMensal(2);
        Especialidade a = especialidade("A");
        Especialidade b = especialidade("B");
        Solicitacao s = ficha(item(a), item(b));

        Long primeiro = agendar(s, a).id();
        limpar();
        Long segundo = agendar(s, b).id();
        limpar();
        assertThat(utilizadaDe(cota)).isEqualTo(1);

        agendamentoService.deleteAgendamento(primeiro, operador.getCpf());
        assertThat(utilizadaDe(cota)).isEqualTo(1);

        agendamentoService.deleteAgendamento(segundo, operador.getCpf());
        assertThat(utilizadaDe(cota)).isZero();
    }

    @Test
    @DisplayName("excluir um agendamento com varios itens na mesma cota devolve 1 vaga, nao uma por item")
    void excluirLoteDevolveUmaVaga() {
        CotaUnidade cota = cotaDeGrupoPorData(10, 3, LocalTime.of(7, 0), LocalTime.of(9, 0));
        Especialidade a = especialidade("A");
        Especialidade b = especialidade("B");
        Especialidade c = especialidade("C");
        Solicitacao s = ficha(item(a), item(b), item(c));

        Long id = agendar(s, a, b, c).id();
        limpar();
        assertThat(utilizadaDe(cota)).isEqualTo(4);

        agendamentoService.deleteAgendamento(id, operador.getCpf());
        assertThat(utilizadaDe(cota)).isEqualTo(3);
    }

    @Test
    @DisplayName("remover da ficha um item agendado: devolve a vaga so se for o ultimo da solicitacao na cota")
    void removerItemAgendadoDevolveSoNoUltimo() {
        CotaUnidade cota = cotaDeGrupoMensal(2);
        Especialidade a = especialidade("A");
        Especialidade b = especialidade("B");
        Solicitacao s = ficha(item(a), item(b));

        agendar(s, a, b);
        limpar();
        assertThat(utilizadaDe(cota)).isEqualTo(1);

        solicitacaoService.removerEspecialidade(idDoItem(s, a), operador.getCpf());
        limpar();
        assertThat(utilizadaDe(cota)).isEqualTo(1);

        solicitacaoService.removerEspecialidade(idDoItem(s, b), operador.getCpf());
        limpar();
        assertThat(utilizadaDe(cota)).isZero();
    }

    @Test
    @DisplayName("horario dinamico: itens da mesma solicitacao (no lote e depois) ficam no horario dela; o proximo paciente avanca")
    void horarioDinamicoPorSolicitacao() {
        // 5 vagas de 07:00 a 09:00 = passo de 30 min. Comeca com 2 usadas: o 1o
        // paciente novo cai no slot 07:30, o seguinte em 08:00.
        CotaUnidade cota = cotaDeGrupoPorData(5, 2, LocalTime.of(7, 0), LocalTime.of(9, 0));
        cota.setHorarioDinamico(true);
        cotaRepository.saveAndFlush(cota);
        Especialidade a = especialidade("A");
        Especialidade b = especialidade("B");
        Especialidade c = especialidade("C");
        Solicitacao s1 = ficha(item(a), item(b), item(c));
        Solicitacao s2 = ficha(item(a));

        var lote = agendar(s1, a, b);
        limpar();
        var depois = agendar(s1, c);
        limpar();
        var outroPaciente = agendar(s2, a);

        assertThat(lote.especialidades()).extracting(e -> e.horaAgendada())
                .containsOnly(LocalTime.of(7, 30));
        assertThat(depois.especialidades()).extracting(e -> e.horaAgendada())
                .containsOnly(LocalTime.of(7, 30));
        assertThat(outroPaciente.especialidades()).extracting(e -> e.horaAgendada())
                .containsOnly(LocalTime.of(8, 0));
        assertThat(utilizadaDe(cota)).isEqualTo(4);
    }

    @Test
    @DisplayName("limitacao aceita (ADR 0014): item agendado por ADMIN ja faz a solicitacao ocupar a cota")
    void itemDeAdminFazASolicitacaoOcupar() {
        CotaUnidade cota = cotaDeGrupoMensal(1);
        Especialidade a = especialidade("A");
        Especialidade b = especialidade("B");
        Solicitacao s = ficha(item(a), item(b));
        User admin = usuario(Roles.ADMIN, null);

        agendamentoService.criarAgendamentoParaMultiplosExames(s.getId(), dto(a), admin.getCpf());
        limpar();
        assertThat(utilizadaDe(cota)).isZero();

        agendar(s, b);
        // A derivacao nao distingue quem agendou: a solicitacao ja "ocupa" a cota.
        assertThat(utilizadaDe(cota)).isZero();
    }

    @Test
    @DisplayName("V113: recalcula o contador antigo (1 por item) para solicitacoes distintas, so reduzindo")
    void migrationRecalculaPorSolicitacao() throws Exception {
        // Data futura: a V113 so toca cotas vigentes, e o CURRENT_DATE do banco pode
        // estar em outro fuso que a JVM.
        data = LocalDate.now().plusDays(2);
        CotaUnidade comHorario = cotaDeGrupoPorData(15, 0, LocalTime.of(7, 0), LocalTime.of(9, 0));
        Especialidade a = especialidade("A");
        Especialidade b = especialidade("B");
        Especialidade c = especialidade("C");
        Solicitacao s1 = ficha(item(a), item(b), item(c));
        Solicitacao s2 = ficha(item(a), item(b));
        agendar(s1, a, b, c);
        limpar();
        agendar(s2, a, b);
        limpar();
        CotaUnidade semConsumidor = cotaDaEspecialidade(especialidade("D"), 5);

        // Estado deixado pela regra antiga: 5 itens = 5 vagas; e um contador que so
        // pode subir (a V113 nunca aumenta).
        entityManager.createNativeQuery("UPDATE cota_unidade SET quantidade_utilizada = 5 WHERE id = :id")
                .setParameter("id", comHorario.getId()).executeUpdate();
        entityManager.createNativeQuery("UPDATE cota_unidade SET quantidade_utilizada = 1 WHERE id = :id")
                .setParameter("id", semConsumidor.getId()).executeUpdate();

        String v113 = new String(getClass().getResourceAsStream(
                "/db/migration/V113__Cota_Consumo_Por_Solicitacao.sql").readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8);
        entityManager.createNativeQuery(v113).executeUpdate();
        assertThat(utilizadaDe(comHorario)).isEqualTo(2);
        assertThat(utilizadaDe(semConsumidor)).isZero();

        // Idempotente.
        entityManager.createNativeQuery(v113).executeUpdate();
        assertThat(utilizadaDe(comHorario)).isEqualTo(2);
    }

    // ------------------------------------------------------------------

    private static String cpfUnico() {
        return String.format("%011d", System.nanoTime() % 100_000_000_000L);
    }

    private Unidade unidade() {
        Unidade u = new Unidade();
        u.setNome("Unidade" + sufixo);
        u.setCodigo("U" + sufixo);
        u.setAtivo(true);
        return unidadeRepository.saveAndFlush(u);
    }

    private User usuario(Roles role, Unidade lotacao) {
        User u = new User();
        u.setCpf(cpfUnico());
        u.setNome(role + sufixo);
        u.setPassword("x");
        u.setRole(role);
        u.setAtivo(true);
        u.setUnidade(lotacao);
        return userRepository.saveAndFlush(u);
    }

    private GrupoRelatorio grupo() {
        GrupoRelatorio g = new GrupoRelatorio();
        g.setCodigo("LAB" + sufixo);
        g.setNome("LAB" + sufixo);
        g.setAtivo(true);
        return grupoRelatorioRepository.saveAndFlush(g);
    }

    /** Exame do grupo LAB, sem capacidade global (vagas = 0). */
    private Especialidade especialidade(String prefixo) {
        Especialidade e = new Especialidade();
        e.setCodigo(prefixo + sufixo);
        e.setNome("Exame " + prefixo + sufixo);
        e.setCategoria(ItemCategoria.EXAME_OU_PROCEDIMENTO);
        e.setAtivo(true);
        e.setVagas(0);
        e.setGrupoRelatorio(lab);
        return especialidadeRepository.saveAndFlush(e);
    }

    /** Como a cota do relato: grupo, por data, com faixa de horario (cota especifica, grava a FK). */
    private CotaUnidade cotaDeGrupoPorData(int total, int utilizada, LocalTime de, LocalTime ate) {
        CotaUnidade cota = new CotaUnidade();
        cota.setUnidade(unidade);
        cota.setGrupoEspecialidades(lab);
        cota.setTipoPeriodo(TipoPeriodoCota.DATA);
        cota.setDataEspecifica(data);
        cota.setHoraInicial(de);
        cota.setHoraFinal(ate);
        cota.setQuantidadeTotal(total);
        cota.setQuantidadeUtilizada(utilizada);
        cota.setAtivo(true);
        return cotaRepository.saveAndFlush(cota);
    }

    /** Cota geral de grupo (sem profissional, local nem horario): consumida por incidencia. */
    private CotaUnidade cotaDeGrupoMensal(int total) {
        CotaUnidade cota = new CotaUnidade();
        cota.setUnidade(unidade);
        cota.setGrupoEspecialidades(lab);
        cota.setTipoPeriodo(TipoPeriodoCota.MENSAL);
        cota.setPeriodo(data.format(PERIODO_MENSAL));
        cota.setQuantidadeTotal(total);
        cota.setQuantidadeUtilizada(0);
        cota.setAtivo(true);
        return cotaRepository.saveAndFlush(cota);
    }

    private CotaUnidade cotaDaEspecialidade(Especialidade especialidade, int total) {
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

    private SolicitacaoEspecialidade item(Especialidade especialidade) {
        SolicitacaoEspecialidade se = new SolicitacaoEspecialidade();
        se.setEspecialidadeSolicitada(especialidade);
        se.setEspecialidadeCodigoLegacy(especialidade.getCodigo());
        se.setStatus(StatusDaMarcacao.AGUARDANDO);
        se.setPrioridade(PrioridadeDaMarcacaoEnum.NORMAL);
        return se;
    }

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
        limpar();
        return s;
    }

    private MultiAgendamentoCreateDTO dto(Especialidade... selecionadas) {
        return new MultiAgendamentoCreateDTO(
                List.of(selecionadas).stream().map(Especialidade::getCodigo).toList(), data, null, null,
                TurnoEnum.MANHA, "consumo por solicitacao", null, null, null);
    }

    private AgendamentoVerificacaoDTO verificar(Solicitacao s, Especialidade... selecionadas) {
        return agendamentoService.verificarAgendamentoParaMultiplosExames(s.getId(), dto(selecionadas),
                operador.getCpf());
    }

    private AgendamentoSolicitacaoSimpleViewDTO agendar(Solicitacao s, Especialidade... selecionadas) {
        return agendamentoService.criarAgendamentoParaMultiplosExames(s.getId(), dto(selecionadas),
                operador.getCpf());
    }

    /**
     * Limpa o contexto depois de ler: a ficha carregada com os itens (cascade ALL)
     * desfaria no flush a exclusao feita em seguida, o que nao acontece numa
     * requisicao real, que comeca com o contexto vazio.
     */
    private Long idDoItem(Solicitacao s, Especialidade especialidade) {
        Long id = solicitacaoRepository.findById(s.getId()).orElseThrow().getEspecialidades().stream()
                .filter(e -> e.getEspecialidadeSolicitada().getId().equals(especialidade.getId()))
                .findFirst().orElseThrow().getId();
        limpar();
        return id;
    }

    private int utilizadaDe(CotaUnidade cota) {
        limpar();
        return cotaRepository.findById(cota.getId()).orElseThrow().getQuantidadeUtilizada();
    }

    private void limpar() {
        entityManager.flush();
        entityManager.clear();
    }
}
