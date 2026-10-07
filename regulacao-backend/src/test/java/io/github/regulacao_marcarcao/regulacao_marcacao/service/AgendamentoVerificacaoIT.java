package io.github.regulacao_marcarcao.regulacao_marcacao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.agendamentoDTO.AgendamentoVerificacaoDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.agendamentoDTO.AgendamentoVerificacaoDTO.ItemVerificadoDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.agendamentoDTO.MultiAgendamentoCreateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.CotaUnidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Especialidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.GrupoRelatorio;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Solicitacao;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.SolicitacaoEspecialidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.TetoFinanceiro;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Unidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.User;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.ItemCategoria;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.LocalDeAgendamentoEnum;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.PrioridadeDaMarcacaoEnum;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.Roles;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.StatusDaMarcacao;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.TipoPeriodoCota;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.TurnoEnum;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.CotaUnidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.EspecialidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.GrupoRelatorioRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.SolicitacaoRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.TetoFinanceiroRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UnidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;

/**
 * Pre-verificacao do agendamento em lote
 * ({@link AgendamentoService#verificarAgendamentoParaMultiplosExames}): diz o
 * que o POST faria com cada item, sem gravar.
 *
 * <p>O ponto central e a PARIDADE com o POST: item que a verificacao recusa e
 * item que derruba o POST, e o POST so com os itens aceitos passa. Se uma regra
 * nova entrar no POST e nao aqui, estes testes devem quebrar.
 *
 * <p>Que a verificacao nao deixa nada gravado e provado em
 * {@link AgendamentoVerificacaoSemEfeitoIT}, que nao e {@code @Transactional}.
 */
@SpringBootTest
@Transactional
class AgendamentoVerificacaoIT {

    private static final DateTimeFormatter PERIODO_MENSAL = DateTimeFormatter.ofPattern("yyyy-MM");

    @Autowired private AgendamentoService agendamentoService;
    @Autowired private SolicitacaoRepository solicitacaoRepository;
    @Autowired private CotaUnidadeRepository cotaRepository;
    @Autowired private UnidadeRepository unidadeRepository;
    @Autowired private EspecialidadeRepository especialidadeRepository;
    @Autowired private GrupoRelatorioRepository grupoRelatorioRepository;
    @Autowired private TetoFinanceiroRepository tetoRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private EntityManager entityManager;

    private String sufixo;
    private LocalDate data;
    private Unidade unidade;
    private User operador;

    @BeforeEach
    void setUp() {
        sufixo = "_VERIF_" + System.nanoTime();
        data = LocalDate.now();
        unidade = unidade("UV");
        operador = usuario(Roles.ADMIN_UNIDADE, unidade);
    }

    @Test
    @DisplayName("lote sem problema: todos os itens podem ser agendados")
    void loteSemProblema() {
        Especialidade a = especialidade("A", null, 0);
        Especialidade b = especialidade("B", null, 0);
        cota(a, 1);
        cota(b, 1);
        Solicitacao s = ficha(unidade, item(a, StatusDaMarcacao.AGUARDANDO), item(b, StatusDaMarcacao.AGUARDANDO));

        AgendamentoVerificacaoDTO resultado = verificar(s, operador, a, b);

        assertThat(resultado.itens()).extracting(ItemVerificadoDTO::codigo).containsExactly(a.getCodigo(), b.getCodigo());
        assertThat(resultado.itens()).allMatch(ItemVerificadoDTO::podeAgendar);
        assertThat(resultado.itens()).allMatch(i -> i.motivo() == null);
        assertThat(resultado.bloqueioDoLote()).isNull();
    }

    @Test
    @DisplayName("cota esgotada: o item fica de fora, os demais passam — e o POST concorda")
    void cotaEsgotadaFicaDeForaEPostConcorda() {
        Especialidade a = especialidade("A", null, 0);
        Especialidade b = especialidade("B", null, 0);
        Especialidade c = especialidade("C", null, 0);
        cota(a, 1);
        cota(b, 0);
        cota(c, 1);
        Solicitacao s = ficha(unidade, item(a, StatusDaMarcacao.AGUARDANDO), item(b, StatusDaMarcacao.AGUARDANDO),
                item(c, StatusDaMarcacao.AGUARDANDO));

        AgendamentoVerificacaoDTO resultado = verificar(s, operador, a, b, c);

        assertThat(resultado.itens()).extracting(ItemVerificadoDTO::podeAgendar).containsExactly(true, false, true);
        ItemVerificadoDTO recusado = resultado.itens().get(1);
        assertThat(recusado.corrigivel()).isFalse();
        assertThat(recusado.motivo()).contains("Cota esgotada");
        assertThat(recusado.nome()).isEqualTo(b.getNome());

        // Paridade: o POST do item recusado falha pelo mesmo motivo. Sozinho, e nao
        // no lote: aqui o teste roda numa transacao so, e o consumo de A feito
        // antes da falha nao seria desfeito (isso e provado em AgendamentoLoteRollbackIT).
        assertThatThrownBy(() -> agendar(s, operador, b))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(recusado.motivo());
        // ...e o POST so com os itens aceitos passa.
        entityManager.clear();
        var resposta = agendar(s, operador, a, c);
        assertThat(resposta.especialidades()).hasSize(2);
        assertThat(statusDe(s, b)).isEqualTo(StatusDaMarcacao.AGUARDANDO);
    }

    @Test
    @DisplayName("dois itens disputando a ultima vaga da cota de grupo: so o primeiro entra")
    void doisItensDisputandoAMesmaCota() {
        GrupoRelatorio lab = grupo("LAB");
        Especialidade a = especialidade("A", lab, 0);
        Especialidade b = especialidade("B", lab, 0);
        cotaDeGrupo(lab, 1);
        Solicitacao s = ficha(unidade, item(a, StatusDaMarcacao.AGUARDANDO), item(b, StatusDaMarcacao.AGUARDANDO));

        AgendamentoVerificacaoDTO resultado = verificar(s, operador, a, b);

        assertThat(resultado.itens()).extracting(ItemVerificadoDTO::podeAgendar).containsExactly(true, false);
        assertThat(resultado.itens().get(1).motivo()).contains("outro item do mesmo lote");

        // Paridade: o primeiro entra e, com a vaga tomada, o segundo e barrado.
        assertThatCode(() -> agendar(s, operador, a)).doesNotThrowAnyException();
        entityManager.flush();
        entityManager.clear();
        assertThatThrownBy(() -> agendar(s, operador, b))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cota esgotada");
    }

    @Test
    @DisplayName("item recusado nao reserva vaga: o seguinte ainda usa a cota de grupo")
    void itemRecusadoNaoReservaVaga() {
        GrupoRelatorio lab = grupo("LAB");
        Especialidade a = especialidade("A", lab, 0);
        Especialidade b = especialidade("B", lab, 0);
        cotaDeGrupo(lab, 1);
        // A incide tambem numa cota propria, esgotada: nao entra e nao pode
        // segurar a unica vaga do grupo.
        cota(a, 0);
        Solicitacao s = ficha(unidade, item(a, StatusDaMarcacao.AGUARDANDO), item(b, StatusDaMarcacao.AGUARDANDO));

        AgendamentoVerificacaoDTO resultado = verificar(s, operador, a, b);

        assertThat(resultado.itens()).extracting(ItemVerificadoDTO::podeAgendar).containsExactly(false, true);
        assertThatCode(() -> agendar(s, operador, b)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("nenhum item viavel: todos recusados, cada um com o seu motivo")
    void nenhumItemViavel() {
        Especialidade a = especialidade("A", null, 0);
        Especialidade b = especialidade("B", null, 0);
        cota(a, 0);
        Solicitacao s = ficha(unidade, item(a, StatusDaMarcacao.AGUARDANDO), item(b, StatusDaMarcacao.AGUARDANDO));

        AgendamentoVerificacaoDTO resultado = verificar(s, operador, a, b);

        assertThat(resultado.itens()).noneMatch(ItemVerificadoDTO::podeAgendar);
        assertThat(resultado.itens().get(0).motivo()).contains("Cota esgotada");
        // ADMIN_UNIDADE sem cota nenhuma para a especialidade: mesma mensagem do POST.
        assertThat(resultado.itens().get(1).motivo()).contains("Nao ha cota liberada");
        assertThatThrownBy(() -> agendar(s, operador, b))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(resultado.itens().get(1).motivo());
    }

    @Test
    @DisplayName("ADMIN e GESTOR nao estao sujeitos a cota")
    void perfisGlobaisIgnoramCota() {
        Especialidade a = especialidade("A", null, 0);
        cota(a, 0);
        Solicitacao s = ficha(unidade, item(a, StatusDaMarcacao.AGUARDANDO));

        assertThat(verificar(s, usuario(Roles.ADMIN, null), a).itens()).allMatch(ItemVerificadoDTO::podeAgendar);
        assertThat(verificar(s, usuario(Roles.GESTOR, null), a).itens()).allMatch(ItemVerificadoDTO::podeAgendar);
    }

    @Test
    @DisplayName("solicitacao orfa (sem unidade): sem cota a checar")
    void solicitacaoOrfaIgnoraCota() {
        Especialidade a = especialidade("A", null, 0);
        Solicitacao s = ficha(null, item(a, StatusDaMarcacao.AGUARDANDO));

        assertThat(verificar(s, operador, a).itens()).allMatch(ItemVerificadoDTO::podeAgendar);
        assertThatCode(() -> agendar(s, operador, a)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("capacidade global da especialidade esgotada na data: item de fora")
    void capacidadeGlobalExcedida() {
        Especialidade a = especialidade("A", null, 1);
        Especialidade b = especialidade("B", null, 0);
        User admin = usuario(Roles.ADMIN, null);
        Solicitacao outra = ficha(unidade, item(a, StatusDaMarcacao.AGUARDANDO));
        agendar(outra, admin, a);
        entityManager.flush();
        entityManager.clear();
        Solicitacao s = ficha(unidade, item(a, StatusDaMarcacao.AGUARDANDO), item(b, StatusDaMarcacao.AGUARDANDO));

        AgendamentoVerificacaoDTO resultado = verificar(s, admin, a, b);

        assertThat(resultado.itens()).extracting(ItemVerificadoDTO::podeAgendar).containsExactly(false, true);
        assertThat(resultado.itens().get(0).motivo()).contains("Capacidade excedida");
        assertThatThrownBy(() -> agendar(s, admin, a, b))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Capacidade excedida");
    }

    @Test
    @DisplayName("item que nao esta mais pendente: de fora")
    void itemNaoPendente() {
        Especialidade a = especialidade("A", null, 0);
        Especialidade b = especialidade("B", null, 0);
        cota(a, 1);
        cota(b, 1);
        Solicitacao s = ficha(unidade, item(a, StatusDaMarcacao.AGENDADO), item(b, StatusDaMarcacao.AGUARDANDO));

        AgendamentoVerificacaoDTO resultado = verificar(s, operador, a, b);

        assertThat(resultado.itens()).extracting(ItemVerificadoDTO::podeAgendar).containsExactly(false, true);
        assertThat(resultado.itens().get(0).corrigivel()).isFalse();
        assertThat(resultado.itens().get(0).motivo()).contains("não encontrado na solicitação");
        assertThatThrownBy(() -> agendar(s, operador, a, b)).isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("erro de entrada do operador (cota escolhida, profissional): corrigivel, nao 'item de fora'")
    void errosDeEntradaSaoCorrigiveis() {
        Especialidade a = especialidade("A", null, 0);
        Especialidade b = especialidade("B", null, 0);
        cota(a, 1);
        cota(b, 1);
        Solicitacao s = ficha(unidade, item(a, StatusDaMarcacao.AGUARDANDO), item(b, StatusDaMarcacao.AGUARDANDO));

        var dto = new MultiAgendamentoCreateDTO(
                List.of(a.getCodigo(), b.getCodigo()), data, null, null, TurnoEnum.MANHA, "verificacao",
                Map.of(a.getCodigo(), Long.MAX_VALUE), null, Map.of(b.getCodigo(), Long.MAX_VALUE));
        AgendamentoVerificacaoDTO resultado =
                agendamentoService.verificarAgendamentoParaMultiplosExames(s.getId(), dto, operador.getCpf());

        assertThat(resultado.itens()).noneMatch(ItemVerificadoDTO::podeAgendar);
        assertThat(resultado.itens()).allMatch(ItemVerificadoDTO::corrigivel);
        assertThat(resultado.itens().get(0).motivo()).contains("cota escolhida");
        assertThat(resultado.itens().get(1).motivo()).contains("Profissional nao encontrado");
    }

    @Test
    @DisplayName("erros do agendamento inteiro continuam sendo lancados, como no POST")
    void errosDeLoteSaoLancados() {
        Especialidade a = especialidade("A", null, 0);
        cota(a, 1);
        Solicitacao s = ficha(unidade, item(a, StatusDaMarcacao.AGUARDANDO));
        List<String> codigos = List.of(a.getCodigo());

        assertThatThrownBy(() -> agendamentoService.verificarAgendamentoParaMultiplosExames(Long.MAX_VALUE,
                new MultiAgendamentoCreateDTO(codigos, data, null, null, TurnoEnum.MANHA, null, null, null, null),
                operador.getCpf()))
                .isInstanceOf(EntityNotFoundException.class);

        assertThatThrownBy(() -> agendamentoService.verificarAgendamentoParaMultiplosExames(s.getId(),
                new MultiAgendamentoCreateDTO(codigos, data, LocalDeAgendamentoEnum.values()[0], 1L, TurnoEnum.MANHA,
                        null, null, null, null),
                operador.getCpf()))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> agendamentoService.verificarAgendamentoParaMultiplosExames(s.getId(),
                new MultiAgendamentoCreateDTO(codigos, data, null, Long.MAX_VALUE, TurnoEnum.MANHA, null, null, null,
                        null),
                operador.getCpf()))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("operador de outra unidade nao consulta a cota desta")
    void outraUnidadeNaoVerifica() {
        Especialidade a = especialidade("A", null, 0);
        cota(a, 1);
        Solicitacao s = ficha(unidade, item(a, StatusDaMarcacao.AGUARDANDO));
        User deFora = usuario(Roles.ADMIN_UNIDADE, unidade("UFORA"));

        assertThatThrownBy(() -> verificar(s, deFora, a)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("teto que nao comporta os itens viaveis: bloqueio do lote, sem valores — e o POST concorda")
    void tetoNaoComportaBloqueiaOLote() {
        GrupoRelatorio lab = grupo("LAB");
        Especialidade a = especialidade("A", lab, 0);
        Especialidade b = especialidade("B", lab, 0);
        a.setValorUnitario(new BigDecimal("4.11"));
        b.setValorUnitario(new BigDecimal("3.33"));
        especialidadeRepository.saveAndFlush(a);
        especialidadeRepository.saveAndFlush(b);
        cotaDeGrupo(lab, 10);
        // Comporta um exame ou o outro, nunca os dois.
        teto(lab, "5.00");
        Solicitacao s = ficha(unidade, item(a, StatusDaMarcacao.AGUARDANDO), item(b, StatusDaMarcacao.AGUARDANDO));

        AgendamentoVerificacaoDTO resultado = verificar(s, operador, a, b);

        assertThat(resultado.itens()).allMatch(ItemVerificadoDTO::podeAgendar);
        assertThat(resultado.bloqueioDoLote()).contains("Teto financeiro");
        assertThat(resultado.bloqueioDoLote()).doesNotContain("R$", "4,11", "4.11", "3,33", "3.33", "5,00", "5.00", "7,44", "7.44");
        assertThatThrownBy(() -> agendar(s, operador, a, b))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(resultado.bloqueioDoLote());

        // Um item so cabe: sem bloqueio.
        entityManager.clear();
        assertThat(verificar(s, operador, a).bloqueioDoLote()).isNull();
        // ADMIN nao e barrado pelo teto.
        assertThat(verificar(s, usuario(Roles.ADMIN, null), a, b).bloqueioDoLote()).isNull();
    }

    // ------------------------------------------------------------------

    private static String cpfUnico() {
        return String.format("%011d", System.nanoTime() % 100_000_000_000L);
    }

    private Unidade unidade(String prefixo) {
        Unidade u = new Unidade();
        u.setNome("Unidade " + prefixo + sufixo);
        u.setCodigo(prefixo + sufixo);
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

    private GrupoRelatorio grupo(String prefixo) {
        GrupoRelatorio g = new GrupoRelatorio();
        g.setCodigo(prefixo + sufixo);
        g.setNome(prefixo + sufixo);
        g.setAtivo(true);
        return grupoRelatorioRepository.saveAndFlush(g);
    }

    private Especialidade especialidade(String prefixo, GrupoRelatorio grupo, int vagas) {
        Especialidade e = new Especialidade();
        e.setCodigo(prefixo + sufixo);
        e.setNome("Exame " + prefixo + sufixo);
        e.setCategoria(ItemCategoria.EXAME_OU_PROCEDIMENTO);
        e.setAtivo(true);
        e.setVagas(vagas);
        e.setGrupoRelatorio(grupo);
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

    private CotaUnidade cotaDeGrupo(GrupoRelatorio grupo, int total) {
        CotaUnidade cota = new CotaUnidade();
        cota.setUnidade(unidade);
        cota.setGrupoEspecialidades(grupo);
        cota.setTipoPeriodo(TipoPeriodoCota.MENSAL);
        cota.setPeriodo(data.format(PERIODO_MENSAL));
        cota.setQuantidadeTotal(total);
        cota.setQuantidadeUtilizada(0);
        cota.setAtivo(true);
        return cotaRepository.saveAndFlush(cota);
    }

    private TetoFinanceiro teto(GrupoRelatorio grupo, String valorTotal) {
        TetoFinanceiro t = new TetoFinanceiro();
        t.setUnidade(unidade);
        t.setGrupoEspecialidades(grupo);
        t.setPeriodo(data.format(PERIODO_MENSAL));
        t.setValorTotal(new BigDecimal(valorTotal));
        t.setValorUtilizado(new BigDecimal("0.00"));
        t.setAtivo(true);
        return tetoRepository.saveAndFlush(t);
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
    private Solicitacao ficha(Unidade daFicha, SolicitacaoEspecialidade... itens) {
        Solicitacao s = new Solicitacao();
        s.setNomePaciente("Paciente" + sufixo);
        s.setCpfPaciente(cpfUnico());
        s.setCns("700" + String.format("%012d", System.nanoTime() % 1_000_000_000_000L));
        s.setNomePai("Pai");
        s.setNomeMae("Mae");
        s.setEndereco("Rua");
        s.setUnidade(daFicha);
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

    private MultiAgendamentoCreateDTO dto(Especialidade... selecionadas) {
        return new MultiAgendamentoCreateDTO(
                List.of(selecionadas).stream().map(Especialidade::getCodigo).toList(), data, null, null,
                TurnoEnum.MANHA, "verificacao", null, null, null);
    }

    private AgendamentoVerificacaoDTO verificar(Solicitacao s, User quem, Especialidade... selecionadas) {
        return agendamentoService.verificarAgendamentoParaMultiplosExames(s.getId(), dto(selecionadas), quem.getCpf());
    }

    private io.github.regulacao_marcarcao.regulacao_marcacao.dto.agendamentoDTO.AgendamentoSolicitacaoSimpleViewDTO agendar(
            Solicitacao s, User quem, Especialidade... selecionadas) {
        return agendamentoService.criarAgendamentoParaMultiplosExames(s.getId(), dto(selecionadas), quem.getCpf());
    }

    private StatusDaMarcacao statusDe(Solicitacao s, Especialidade especialidade) {
        entityManager.flush();
        entityManager.clear();
        return solicitacaoRepository.findById(s.getId()).orElseThrow().getEspecialidades().stream()
                .filter(e -> e.getEspecialidadeSolicitada().getId().equals(especialidade.getId()))
                .findFirst().orElseThrow().getStatus();
    }
}
