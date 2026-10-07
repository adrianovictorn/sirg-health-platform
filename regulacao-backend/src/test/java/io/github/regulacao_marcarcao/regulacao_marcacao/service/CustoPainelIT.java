package io.github.regulacao_marcarcao.regulacao_marcacao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.agendamentoDTO.MultiAgendamentoCreateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.custo.CustoPainelLinhaViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.custo.CustoPainelViewDTO;
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
import jakarta.persistence.PersistenceContext;

/**
 * Painel de custos contra o banco real: as tres visoes (estimado da fila,
 * agendado, concluido), o valor da epoca e os itens sem preco.
 *
 * <p>Todas as consultas filtram pelo grupo criado no teste. Isso isola os
 * numeros do que ja existe na base de desenvolvimento.
 *
 * <b>Nao suja a base:</b> classe {@code @Transactional}, tudo sofre rollback.
 */
@SpringBootTest
@Transactional
class CustoPainelIT {

    @Autowired private CustoPainelService painelService;
    @Autowired private AgendamentoService agendamentoService;
    @Autowired private SolicitacaoRepository solicitacaoRepository;
    @Autowired private UnidadeRepository unidadeRepository;
    @Autowired private GrupoRelatorioRepository grupoRelatorioRepository;
    @Autowired private EspecialidadeRepository especialidadeRepository;
    @Autowired private UserRepository userRepository;
    @PersistenceContext private EntityManager em;

    private String sufixo;
    private Unidade unidadeA;
    private Unidade unidadeB;
    private GrupoRelatorio grupo;
    private Especialidade hemograma;
    private Especialidade exameSemPreco;
    private User admin;
    private LocalDate hoje;

    @BeforeEach
    void setUp() {
        sufixo = "_PAINEL_IT_" + System.nanoTime();
        unidadeA = unidade("A");
        unidadeB = unidade("B");

        grupo = new GrupoRelatorio();
        grupo.setCodigo("GRP" + sufixo);
        grupo.setNome("Grupo" + sufixo);
        grupo.setAtivo(true);
        grupo = grupoRelatorioRepository.saveAndFlush(grupo);

        hemograma = especialidade("HEMO", new BigDecimal("4.11"));
        exameSemPreco = especialidade("SEMPRECO", null);

        admin = new User();
        admin.setCpf(cpfUnico());
        admin.setNome("Admin" + sufixo);
        admin.setPassword("x");
        admin.setRole(Roles.ADMIN);
        admin.setAtivo(true);
        admin = userRepository.saveAndFlush(admin);

        hoje = LocalDate.now();
    }

    private Unidade unidade(String letra) {
        Unidade u = new Unidade();
        u.setNome("Unidade " + letra + sufixo);
        u.setCodigo("U" + letra + sufixo);
        u.setAtivo(true);
        return unidadeRepository.saveAndFlush(u);
    }

    private Especialidade especialidade(String prefixo, BigDecimal preco) {
        Especialidade e = new Especialidade();
        e.setCodigo(prefixo + sufixo);
        e.setNome(prefixo + sufixo);
        e.setCategoria(ItemCategoria.EXAME_OU_PROCEDIMENTO);
        e.setAtivo(true);
        e.setVagas(0);
        e.setGrupoRelatorio(grupo);
        e.setValorUnitario(preco);
        return especialidadeRepository.saveAndFlush(e);
    }

    private static String cpfUnico() {
        return String.format("%011d", System.nanoTime() % 100_000_000_000L);
    }

    /** Pedido na fila (AGUARDANDO). */
    private Solicitacao pedir(Unidade daUnidade, Especialidade exame) {
        Solicitacao s = new Solicitacao();
        s.setNomePaciente("Paciente" + sufixo);
        s.setCpfPaciente(cpfUnico());
        s.setCns("700" + String.format("%012d", System.nanoTime() % 1_000_000_000_000L));
        s.setNomePai("Pai");
        s.setNomeMae("Mae");
        s.setEndereco("Rua");
        s.setUnidade(daUnidade);

        SolicitacaoEspecialidade se = new SolicitacaoEspecialidade();
        se.setSolicitacao(s);
        se.setEspecialidadeSolicitada(exame);
        se.setEspecialidadeCodigoLegacy(exame.getCodigo());
        se.setStatus(StatusDaMarcacao.AGUARDANDO);
        se.setPrioridade(PrioridadeDaMarcacaoEnum.NORMAL);
        List<SolicitacaoEspecialidade> itens = new ArrayList<>();
        itens.add(se);
        s.setEspecialidades(itens);
        return solicitacaoRepository.saveAndFlush(s);
    }

    /** Pedido agendado pelo fluxo real, para a data informada. Devolve o id da solicitacao. */
    private Long agendar(Unidade daUnidade, Especialidade exame, LocalDate quando) {
        Solicitacao s = pedir(daUnidade, exame);
        agendamentoService.criarAgendamentoParaMultiplosExames(s.getId(),
                new MultiAgendamentoCreateDTO(List.of(exame.getCodigo()), quando, null, null, TurnoEnum.MANHA,
                        "teste do painel", null, null, null),
                admin.getCpf());
        em.flush();
        return s.getId();
    }

    private void marcarStatus(Long solicitacaoId, StatusDaMarcacao status) {
        em.createNativeQuery("UPDATE solicitacao_especialidade SET status = :status WHERE solicitacao_id = :id")
                .setParameter("status", status.name())
                .setParameter("id", solicitacaoId)
                .executeUpdate();
    }

    private CustoPainelViewDTO painel(Long unidadeId) {
        em.flush();
        em.clear();
        return painelService.painel(unidadeId, grupo.getId(), null,
                hoje.withDayOfMonth(1), hoje.withDayOfMonth(hoje.lengthOfMonth()), admin.getCpf());
    }

    // ==================================================================

    @Test
    @DisplayName("As tres visoes: fila pelo preco atual, agendado e concluido pelo valor gravado")
    void asTresVisoes() {
        pedir(unidadeA, hemograma);
        pedir(unidadeA, hemograma);
        agendar(unidadeA, hemograma, hoje);
        Long realizado = agendar(unidadeA, hemograma, hoje);
        marcarStatus(realizado, StatusDaMarcacao.REALIZADO);

        CustoPainelLinhaViewDTO total = painel(null).total();

        assertThat(total.estimado()).isEqualByComparingTo("8.22");
        assertThat(total.estimadoItens()).isEqualTo(2);
        assertThat(total.agendado()).isEqualByComparingTo("4.11");
        assertThat(total.agendadoItens()).isEqualTo(1);
        assertThat(total.concluido()).isEqualByComparingTo("4.11");
        assertThat(total.concluidoItens()).isEqualTo(1);
    }

    @Test
    @DisplayName("Reajuste muda o estimado da fila, mas NAO o agendado nem o concluido")
    void reajusteNaoAlteraOJaAgendado() {
        pedir(unidadeA, hemograma);
        agendar(unidadeA, hemograma, hoje);
        Long realizado = agendar(unidadeA, hemograma, hoje);
        marcarStatus(realizado, StatusDaMarcacao.REALIZADO);

        Especialidade atual = especialidadeRepository.findById(hemograma.getId()).orElseThrow();
        atual.setValorUnitario(new BigDecimal("10.00"));
        especialidadeRepository.saveAndFlush(atual);

        CustoPainelLinhaViewDTO total = painel(null).total();

        assertThat(total.estimado()).isEqualByComparingTo("10.00");
        assertThat(total.agendado()).isEqualByComparingTo("4.11");
        assertThat(total.concluido()).isEqualByComparingTo("4.11");
    }

    @Test
    @DisplayName("Sem preco nao soma zero: e contado a parte, na fila e no agendado")
    void semPrecoEContadoAParte() {
        pedir(unidadeA, exameSemPreco);
        agendar(unidadeA, exameSemPreco, hoje);
        pedir(unidadeA, hemograma);

        CustoPainelLinhaViewDTO total = painel(null).total();

        assertThat(total.estimado()).isEqualByComparingTo("4.11");
        assertThat(total.estimadoItens()).isEqualTo(1);
        assertThat(total.estimadoSemPreco()).isEqualTo(1);
        assertThat(total.agendado()).isEqualByComparingTo("0.00");
        assertThat(total.agendadoItens()).isZero();
        assertThat(total.agendadoSemValor()).isEqualTo(1);
    }

    @Test
    @DisplayName("Agendamento anterior a feature (sem valor gravado) fica fora do total, mesmo com preco hoje")
    void agendamentoAnteriorAFeatureFicaFora() {
        Long antigo = agendar(unidadeA, hemograma, hoje);
        em.createNativeQuery(
                        "UPDATE solicitacao_especialidade SET valor_unitario_agendado = NULL WHERE solicitacao_id = :id")
                .setParameter("id", antigo)
                .executeUpdate();

        CustoPainelLinhaViewDTO total = painel(null).total();

        assertThat(total.agendado()).isEqualByComparingTo("0.00");
        assertThat(total.agendadoSemValor()).isEqualTo(1);
    }

    @Test
    @DisplayName("O periodo vale para agendado e concluido (data agendada); a fila nao depende dele")
    void periodoValeParaAgendadoEConcluido() {
        pedir(unidadeA, hemograma);
        agendar(unidadeA, hemograma, hoje.plusMonths(2).withDayOfMonth(10));

        CustoPainelLinhaViewDTO total = painel(null).total();

        assertThat(total.estimadoItens()).isEqualTo(1);
        assertThat(total.agendadoItens()).isZero();
        assertThat(total.agendado()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("Por unidade: a soma das linhas fecha com o total, e solicitacao sem unidade tem linha propria")
    void porUnidadeFechaComOTotal() {
        agendar(unidadeA, hemograma, hoje);
        agendar(unidadeB, hemograma, hoje);
        agendar(unidadeB, hemograma, hoje);
        agendar(null, hemograma, hoje);

        CustoPainelViewDTO painel = painel(null);

        assertThat(painel.total().agendado()).isEqualByComparingTo("16.44");
        assertThat(painel.porUnidade()).hasSize(3);
        assertThat(painel.porUnidade().stream().map(CustoPainelLinhaViewDTO::agendado)
                .reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo(painel.total().agendado());

        CustoPainelLinhaViewDTO semUnidade = painel.porUnidade().get(2);
        assertThat(semUnidade.id()).isNull();
        assertThat(semUnidade.nome()).isNull();
        assertThat(semUnidade.agendado()).isEqualByComparingTo("4.11");
        assertThat(painel.porUnidade().get(1).id()).isEqualTo(unidadeB.getId());
        assertThat(painel.porUnidade().get(1).agendado()).isEqualByComparingTo("8.22");
    }

    @Test
    @DisplayName("Filtro por unidade restringe as tres visoes")
    void filtroPorUnidade() {
        pedir(unidadeA, hemograma);
        agendar(unidadeA, hemograma, hoje);
        agendar(unidadeB, hemograma, hoje);

        CustoPainelViewDTO painel = painel(unidadeA.getId());

        assertThat(painel.total().estimadoItens()).isEqualTo(1);
        assertThat(painel.total().agendado()).isEqualByComparingTo("4.11");
        assertThat(painel.porUnidade()).hasSize(1);
        assertThat(painel.porUnidade().get(0).id()).isEqualTo(unidadeA.getId());
    }

    @Test
    @DisplayName("Por especialidade: uma linha por exame com movimento, com o codigo SUS")
    void porEspecialidade() {
        Especialidade comCodigo = especialidadeRepository.findById(hemograma.getId()).orElseThrow();
        comCodigo.setCodigoSus("0202020380");
        especialidadeRepository.saveAndFlush(comCodigo);
        agendar(unidadeA, hemograma, hoje);
        pedir(unidadeA, exameSemPreco);

        CustoPainelViewDTO painel = painel(null);

        assertThat(painel.porEspecialidade()).hasSize(2);
        CustoPainelLinhaViewDTO primeira = painel.porEspecialidade().get(0);
        assertThat(primeira.id()).isEqualTo(hemograma.getId());
        assertThat(primeira.codigoSus()).isEqualTo("0202020380");
        assertThat(primeira.agendado()).isEqualByComparingTo("4.11");
        assertThat(painel.porEspecialidade().get(1).estimadoSemPreco()).isEqualTo(1);
    }

    @Test
    @DisplayName("Periodo invertido e tipo invalido devolvem 400")
    void parametroInvalido() {
        assertThatThrownBy(() -> painelService.painel(null, grupo.getId(), null, hoje, hoje.minusDays(1), admin.getCpf()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("data inicial");
        assertThatThrownBy(() -> painelService.painel(null, grupo.getId(), "LABORATORIO", hoje, hoje, admin.getCpf()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Tipo inválido");
    }
}
