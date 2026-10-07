package io.github.regulacao_marcarcao.regulacao_marcacao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.fila.FilaEsperaFiltroDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.indicadores.AntecedenciaViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.indicadores.BalancoFilaViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.indicadores.FaixasEsperaViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.indicadores.FilaEnvelhecimentoViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.indicadores.PeriodoIndicador;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.AgendamentoSolicitacao;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Especialidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Solicitacao;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.SolicitacaoEspecialidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Unidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.User;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.ItemCategoria;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.PrioridadeDaMarcacaoEnum;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.Roles;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.StatusDaMarcacao;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.AgendamentoSolicitacaoRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.EspecialidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.SolicitacaoRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UnidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UserRepository;
import jakarta.persistence.EntityManager;

/**
 * Indicadores gerenciais de fila e operacao, com massa conhecida e o valor
 * esperado calculado a mao.
 *
 * <p>Tudo e criado numa unidade propria do teste e consultado com o filtro
 * dessa unidade, para os numeros nao dependerem do que ja existe na base.
 *
 * <p>{@code data_cadastro} e {@code data_criacao} tem {@code @CreationTimestamp}:
 * para datar no passado e preciso {@code UPDATE} nativo depois de gravar.
 */
@SpringBootTest
@Transactional
class IndicadoresFilaIT {

    @Autowired private IndicadoresFilaService indicadoresService;
    @Autowired private FilaEsperaService filaEsperaService;
    @Autowired private SolicitacaoRepository solicitacaoRepository;
    @Autowired private AgendamentoSolicitacaoRepository agendamentoRepository;
    @Autowired private UnidadeRepository unidadeRepository;
    @Autowired private EspecialidadeRepository especialidadeRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private EntityManager entityManager;

    private String sufixo;
    private Unidade unidade;
    private User admin;
    private Especialidade cardio;
    private Especialidade usg;

    @BeforeEach
    void setUp() {
        sufixo = "_IND_FILA_" + System.nanoTime();

        unidade = new Unidade();
        unidade.setNome("Unidade Indicadores" + sufixo);
        unidade.setCodigo("UIF" + sufixo);
        unidade.setAtivo(true);
        unidade = unidadeRepository.saveAndFlush(unidade);

        admin = usuario(Roles.ADMIN);
        cardio = especialidade("CARDIO");
        usg = especialidade("USG");
    }

    @Test
    @DisplayName("envelhecimento: pacientes pela faixa do pedido mais antigo, pedidos por especialidade e prioridade")
    void envelhecimentoDaFila() {
        // Paciente 1: um pedido recente e um de 120 dias — o paciente esta ha mais de 90.
        Solicitacao p1 = ficha(item(cardio, StatusDaMarcacao.AGUARDANDO, PrioridadeDaMarcacaoEnum.NORMAL),
                item(usg, StatusDaMarcacao.RETORNO, PrioridadeDaMarcacaoEnum.NORMAL));
        cadastradoHa(p1.getEspecialidades().get(0), 10);
        cadastradoHa(p1.getEspecialidades().get(1), 120);
        // Paciente 2: 45 dias. Paciente 3: 75 dias, urgente.
        Solicitacao p2 = ficha(item(cardio, StatusDaMarcacao.AGUARDANDO, PrioridadeDaMarcacaoEnum.NORMAL));
        cadastradoHa(p2.getEspecialidades().get(0), 45);
        Solicitacao p3 = ficha(item(cardio, StatusDaMarcacao.AGUARDANDO, PrioridadeDaMarcacaoEnum.URGENTE));
        cadastradoHa(p3.getEspecialidades().get(0), 75);
        // Fora da fila: ja agendado e GEL nao contam.
        Solicitacao p4 = ficha(item(cardio, StatusDaMarcacao.AGENDADO, PrioridadeDaMarcacaoEnum.NORMAL),
                item(usg, StatusDaMarcacao.GEL, PrioridadeDaMarcacaoEnum.NORMAL));
        cadastradoHa(p4.getEspecialidades().get(0), 200);
        cadastradoHa(p4.getEspecialidades().get(1), 200);
        entityManager.clear();

        FilaEnvelhecimentoViewDTO resultado = indicadoresService.envelhecimento(unidade.getId(), admin.getCpf());

        FaixasEsperaViewDTO total = resultado.total();
        assertThat(List.of(total.ate30(), total.de31a60(), total.de61a90(), total.mais90()))
                .containsExactly(0L, 1L, 1L, 1L);
        assertThat(total.total()).isEqualTo(3);
        // O total em pacientes tem de ser o numero que a tela da fila mostra.
        assertThat(total.total()).isEqualTo(filaEsperaService.contar(
                FilaEsperaFiltroDTO.de(FilaEsperaService.STATUS_DA_FILA, null, unidade.getId()), admin.getCpf()));

        assertThat(resultado.porUnidade()).hasSize(1);
        assertThat(resultado.porUnidade().get(0).nome()).isEqualTo(unidade.getNome());
        assertThat(resultado.porUnidade().get(0).mais90()).isEqualTo(1);

        // Por especialidade conta PEDIDOS: cardio tem 3 (10, 45 e 75 dias), usg tem 1 (120).
        FaixasEsperaViewDTO deCardio = linha(resultado.porEspecialidade(), cardio.getNome());
        assertThat(List.of(deCardio.ate30(), deCardio.de31a60(), deCardio.de61a90(), deCardio.mais90()))
                .containsExactly(1L, 1L, 1L, 0L);
        FaixasEsperaViewDTO deUsg = linha(resultado.porEspecialidade(), usg.getNome());
        assertThat(List.of(deUsg.ate30(), deUsg.de31a60(), deUsg.de61a90(), deUsg.mais90()))
                .containsExactly(0L, 0L, 0L, 1L);
        // Quem tem mais gente acima de 90 dias vem primeiro.
        assertThat(resultado.porEspecialidade().get(0).nome()).isEqualTo(usg.getNome());

        assertThat(linha(resultado.porPrioridade(), "NORMAL").total()).isEqualTo(3);
        FaixasEsperaViewDTO urgentes = linha(resultado.porPrioridade(), "URGENTE");
        assertThat(urgentes.total()).isEqualTo(1);
        assertThat(urgentes.de61a90()).isEqualTo(1);
    }

    @Test
    @DisplayName("envelhecimento: os cortes de faixa sao os mesmos dias inteiros da fila (30 fica, 31 muda)")
    void fronteiraDasFaixas() {
        Solicitacao p1 = ficha(item(cardio, StatusDaMarcacao.AGUARDANDO, PrioridadeDaMarcacaoEnum.NORMAL));
        cadastradoHa(p1.getEspecialidades().get(0), 30);
        Solicitacao p2 = ficha(item(cardio, StatusDaMarcacao.AGUARDANDO, PrioridadeDaMarcacaoEnum.NORMAL));
        cadastradoHa(p2.getEspecialidades().get(0), 31);
        Solicitacao p3 = ficha(item(cardio, StatusDaMarcacao.AGUARDANDO, PrioridadeDaMarcacaoEnum.NORMAL));
        cadastradoHa(p3.getEspecialidades().get(0), 90);
        Solicitacao p4 = ficha(item(cardio, StatusDaMarcacao.AGUARDANDO, PrioridadeDaMarcacaoEnum.NORMAL));
        cadastradoHa(p4.getEspecialidades().get(0), 91);
        entityManager.clear();

        FaixasEsperaViewDTO total = indicadoresService.envelhecimento(unidade.getId(), admin.getCpf()).total();

        assertThat(List.of(total.ate30(), total.de31a60(), total.de61a90(), total.mais90()))
                .containsExactly(1L, 1L, 1L, 1L);
    }

    @Test
    @DisplayName("envelhecimento: unidade sem ninguem na fila devolve zeros, nao erro")
    void filaVazia() {
        FilaEnvelhecimentoViewDTO resultado = indicadoresService.envelhecimento(unidade.getId(), admin.getCpf());

        assertThat(resultado.total().total()).isZero();
        assertThat(resultado.porUnidade()).isEmpty();
        assertThat(resultado.porEspecialidade()).isEmpty();
    }

    @Test
    @DisplayName("antecedencia: data marcada menos o dia local da criacao; sem data e retroativo ficam a parte")
    void antecedenciaDoAgendamento() {
        Solicitacao s = ficha(item(cardio, StatusDaMarcacao.AGENDADO, PrioridadeDaMarcacaoEnum.NORMAL));
        LocalDate marcada = LocalDate.of(2026, 3, 20);
        // 11/03 01:30 em UTC ainda e 10/03 22:30 na Bahia: sao 10 dias, nao 9.
        agendamento(s, marcada, LocalDateTime.of(2026, 3, 11, 1, 30));
        agendamento(s, marcada, LocalDateTime.of(2026, 3, 19, 12, 0));
        // Criado depois da data marcada: registro retroativo.
        agendamento(s, marcada, LocalDateTime.of(2026, 3, 25, 12, 0));
        agendamento(s, marcada, null);
        // Fora do periodo consultado.
        agendamento(s, LocalDate.of(2026, 4, 2), LocalDateTime.of(2026, 3, 30, 12, 0));
        entityManager.clear();

        AntecedenciaViewDTO resultado =
                indicadoresService.antecedencia(unidade.getId(), "2026-03-01", "2026-03-31", admin.getCpf());

        assertThat(resultado.total()).isEqualTo(4);
        assertThat(resultado.semDataCriacao()).isEqualTo(1);
        assertThat(resultado.retroativos()).isEqualTo(1);
        // Mediveis: 10 dias e 1 dia.
        assertThat(resultado.mediaDias()).isEqualTo(5.5);
        assertThat(resultado.medianaDias()).isEqualTo(5.5);
        assertThat(List.of(resultado.ate1(), resultado.de2a7(), resultado.de8a15(), resultado.de16a30(),
                resultado.mais30())).containsExactly(1L, 0L, 1L, 0L, 0L);
        assertThat(resultado.de()).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(resultado.ate()).isEqualTo(LocalDate.of(2026, 3, 31));
    }

    @Test
    @DisplayName("antecedencia: periodo sem agendamento devolve zeros e media nula")
    void antecedenciaSemAgendamento() {
        AntecedenciaViewDTO resultado =
                indicadoresService.antecedencia(unidade.getId(), "2026-03-01", "2026-03-31", admin.getCpf());

        assertThat(resultado.total()).isZero();
        assertThat(resultado.mediaDias()).isNull();
        assertThat(resultado.medianaDias()).isNull();
    }

    @Test
    @DisplayName("balanco: 12 meses sem buraco; novos pelo mes local do cadastro, agendados e concluidos pela data marcada")
    void balancoDaFila() {
        YearMonth atual = YearMonth.now(PeriodoIndicador.FUSO);
        YearMonth anterior = atual.minusMonths(1);

        Solicitacao s = ficha(
                item(cardio, StatusDaMarcacao.AGENDADO, PrioridadeDaMarcacaoEnum.NORMAL),
                item(usg, StatusDaMarcacao.REALIZADO, PrioridadeDaMarcacaoEnum.NORMAL),
                item(usg, StatusDaMarcacao.AGUARDANDO, PrioridadeDaMarcacaoEnum.NORMAL));
        List<SolicitacaoEspecialidade> itens = s.getEspecialidades();
        cadastradoEm(itens.get(0), atual.atDay(15).atTime(12, 0));
        cadastradoEm(itens.get(1), atual.atDay(15).atTime(12, 0));
        // Dia 1 a 01:30 em UTC ainda e o ultimo dia do mes ANTERIOR na Bahia.
        cadastradoEm(itens.get(2), atual.atDay(1).atTime(1, 30));

        AgendamentoSolicitacao ag = agendamento(s, atual.atDay(10), atual.atDay(5).atTime(12, 0));
        vincular(itens.get(0), ag);
        vincular(itens.get(1), ag);
        entityManager.clear();

        BalancoFilaViewDTO resultado = indicadoresService.balanco(unidade.getId(), admin.getCpf());

        assertThat(resultado.meses()).hasSize(12);
        assertThat(resultado.meses().get(11).mes()).isEqualTo(atual.toString());
        assertThat(resultado.meses().get(0).mes()).isEqualTo(atual.minusMonths(11).toString());

        BalancoFilaViewDTO.Mes doMes = resultado.meses().get(11);
        assertThat(doMes.novos()).isEqualTo(2);
        assertThat(doMes.agendados()).isEqualTo(2);
        assertThat(doMes.concluidos()).isEqualTo(1);

        BalancoFilaViewDTO.Mes doAnterior = resultado.meses().get(10);
        assertThat(doAnterior.mes()).isEqualTo(anterior.toString());
        assertThat(doAnterior.novos()).isEqualTo(1);
        assertThat(doAnterior.agendados()).isZero();

        // Mes sem movimento entra com zero.
        assertThat(resultado.meses().get(0).novos()).isZero();
    }

    @Test
    @DisplayName("periodo invalido vira IllegalArgumentException (400 com mensagem), nunca 500")
    void periodoInvalido() {
        Long id = unidade.getId();
        String cpf = admin.getCpf();

        assertThatThrownBy(() -> indicadoresService.antecedencia(id, "2026-13-01", null, cpf))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("AAAA-MM-DD");
        assertThatThrownBy(() -> indicadoresService.antecedencia(id, "20/03/2026", null, cpf))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> indicadoresService.antecedencia(id, "2026-03-31", "2026-03-01", cpf))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("posterior");
        assertThatThrownBy(() -> indicadoresService.antecedencia(id, "2025-01-01", "2026-03-01", cpf))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("366");
    }

    @Test
    @DisplayName("sem datas, o periodo e os ultimos 30 dias do municipio")
    void periodoPadrao() {
        PeriodoIndicador padrao = PeriodoIndicador.de(null, " ");

        assertThat(padrao.ate()).isEqualTo(LocalDate.now(PeriodoIndicador.FUSO));
        assertThat(padrao.de()).isEqualTo(padrao.ate().minusDays(29));
    }

    @Test
    @DisplayName("segunda barreira: quem nao e ADMIN nem GESTOR nao passa pelo servico")
    void soGestaoPassaPeloServico() {
        Long id = unidade.getId();
        String gestor = usuario(Roles.GESTOR).getCpf();
        indicadoresService.envelhecimento(id, gestor);

        for (Roles role : List.of(Roles.RECEPCAO, Roles.ADMIN_UNIDADE, Roles.COORD_TRANSPORTE, Roles.PACIENTE)) {
            String cpf = usuario(role).getCpf();
            assertThatThrownBy(() -> indicadoresService.envelhecimento(id, cpf))
                    .as(role.name()).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> indicadoresService.balanco(id, cpf))
                    .as(role.name()).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> indicadoresService.antecedencia(id, null, null, cpf))
                    .as(role.name()).isInstanceOf(AccessDeniedException.class);
        }
        assertThatThrownBy(() -> indicadoresService.envelhecimento(id, null))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ------------------------------------------------------------------

    private static String cpfUnico() {
        return String.format("%011d", System.nanoTime() % 100_000_000_000L);
    }

    private static FaixasEsperaViewDTO linha(List<FaixasEsperaViewDTO> linhas, String nome) {
        return linhas.stream().filter(l -> nome.equals(l.nome())).findFirst().orElseThrow();
    }

    private User usuario(Roles role) {
        User u = new User();
        u.setCpf(cpfUnico());
        u.setNome(role + sufixo);
        u.setPassword("x");
        u.setRole(role);
        u.setAtivo(true);
        if (role == Roles.ADMIN_UNIDADE || role == Roles.RECEPCAO) {
            u.setUnidade(unidade);
        }
        return userRepository.saveAndFlush(u);
    }

    private Especialidade especialidade(String prefixo) {
        Especialidade e = new Especialidade();
        e.setCodigo(prefixo + sufixo);
        e.setNome(prefixo + sufixo);
        e.setCategoria(ItemCategoria.EXAME_OU_PROCEDIMENTO);
        e.setAtivo(true);
        e.setVagas(0);
        return especialidadeRepository.saveAndFlush(e);
    }

    private SolicitacaoEspecialidade item(Especialidade especialidade, StatusDaMarcacao status,
            PrioridadeDaMarcacaoEnum prioridade) {
        SolicitacaoEspecialidade se = new SolicitacaoEspecialidade();
        se.setEspecialidadeSolicitada(especialidade);
        se.setEspecialidadeCodigoLegacy(especialidade.getCodigo());
        se.setStatus(status);
        se.setPrioridade(prioridade);
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
        return solicitacaoRepository.saveAndFlush(s);
    }

    /** Mesmo relogio do servico (LocalDateTime.now() da JVM), com folga de uma hora para nao ficar na fronteira. */
    private void cadastradoHa(SolicitacaoEspecialidade item, int dias) {
        cadastradoEm(item, LocalDateTime.now().minusDays(dias).minusHours(1));
    }

    private void cadastradoEm(SolicitacaoEspecialidade item, LocalDateTime quando) {
        entityManager.createNativeQuery("UPDATE solicitacao_especialidade SET data_cadastro = :quando WHERE id = :id")
                .setParameter("quando", quando)
                .setParameter("id", item.getId())
                .executeUpdate();
    }

    private AgendamentoSolicitacao agendamento(Solicitacao s, LocalDate dataAgendada, LocalDateTime criadoEm) {
        AgendamentoSolicitacao ag = new AgendamentoSolicitacao();
        ag.setSolicitacao(s);
        ag.setDataAgendada(dataAgendada);
        ag = agendamentoRepository.saveAndFlush(ag);
        entityManager.createNativeQuery(
                        "UPDATE agendamento_solicitacao SET data_criacao = CAST(:quando AS timestamp) WHERE id = :id")
                .setParameter("quando", criadoEm != null ? criadoEm.toString() : null)
                .setParameter("id", ag.getId())
                .executeUpdate();
        return ag;
    }

    private void vincular(SolicitacaoEspecialidade item, AgendamentoSolicitacao ag) {
        entityManager.createNativeQuery("UPDATE solicitacao_especialidade SET agendamento_id = :ag WHERE id = :id")
                .setParameter("ag", ag.getId())
                .setParameter("id", item.getId())
                .executeUpdate();
    }
}
