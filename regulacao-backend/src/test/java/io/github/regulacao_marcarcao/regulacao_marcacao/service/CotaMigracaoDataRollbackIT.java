package io.github.regulacao_marcarcao.regulacao_marcacao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.agendamentoDTO.MultiAgendamentoCreateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.cota.CotaMigracaoDataCreateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.CotaUnidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Especialidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Solicitacao;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.SolicitacaoEspecialidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Unidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.User;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.WhatsAppMensagem;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.ItemCategoria;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.PrioridadeDaMarcacaoEnum;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.Roles;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.StatusDaMarcacao;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.TipoPeriodoCota;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.TurnoEnum;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.WhatsAppMotivoNaoEnvio;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.WhatsAppResultado;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.WhatsAppTipoMensagem;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.CotaUnidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.EspecialidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.SolicitacaoRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UnidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UserRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.WhatsAppMensagemRepository;

/**
 * Migracao de data de cota COM commit de verdade: prova o tudo-ou-nada (cota
 * mensal do destino esgotada desfaz tudo) e que, depois do commit, o listener
 * enfileira a REMARCACAO do WhatsApp.
 *
 * <b>Por que esta classe NAO e @Transactional:</b> o rollback e o listener
 * AFTER_COMMIT so existem numa transacao de verdade (ver a nota do
 * {@code CotaRollbackIT} e a armadilha 5 de docs/mapas/O que consultar.md).
 * Como nada e desfeito automaticamente, o {@code @AfterEach} apaga tudo na ordem
 * inversa das dependencias.
 *
 * <p>Nenhuma mensagem sai: o agendador fica desligado e a URL aponta para um
 * endereco que nao existe.
 */
@SpringBootTest(properties = {
        "app.whatsapp.envio.access-token=token-de-teste",
        "app.whatsapp.envio.phone-number-id=109876543210",
        "app.whatsapp.envio.base-url=http://127.0.0.1:9",
        "app.whatsapp.envio.agendador-ligado=false" })
class CotaMigracaoDataRollbackIT {

    private static final DateTimeFormatter PERIODO = DateTimeFormatter.ofPattern("yyyy-MM");

    @Autowired private AgendamentoService agendamentoService;
    @Autowired private CotaMigracaoDataService migracaoService;
    @Autowired private SolicitacaoRepository solicitacaoRepository;
    @Autowired private CotaUnidadeRepository cotaRepository;
    @Autowired private UnidadeRepository unidadeRepository;
    @Autowired private EspecialidadeRepository especialidadeRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private WhatsAppMensagemRepository mensagemRepository;
    @Autowired private JdbcTemplate jdbc;

    private final List<Long> solicitacoes = new ArrayList<>();
    private final List<Long> cotas = new ArrayList<>();
    private Unidade unidade;
    private Especialidade cardiologia;
    private User operador;
    private User admin;
    private LocalDate d1;
    private LocalDate outroMes;

    @BeforeEach
    void setUp() {
        String sufixo = "_MIGRB_" + System.nanoTime();
        YearMonth mes = YearMonth.now().plusMonths(1);
        d1 = mes.atDay(10);
        outroMes = mes.plusMonths(1).atDay(3);

        unidade = new Unidade();
        unidade.setNome("Unidade" + sufixo);
        unidade.setCodigo("UM" + sufixo);
        unidade.setAtivo(true);
        unidade = unidadeRepository.saveAndFlush(unidade);

        cardiologia = new Especialidade();
        cardiologia.setCodigo("CARDIO" + sufixo);
        cardiologia.setNome("Cardiologia" + sufixo);
        cardiologia.setCategoria(ItemCategoria.ESPECIALIDADE_MEDICA);
        cardiologia.setAtivo(true);
        cardiologia.setVagas(0);
        cardiologia = especialidadeRepository.saveAndFlush(cardiologia);

        operador = usuario(Roles.ADMIN_UNIDADE, unidade, sufixo);
        admin = usuario(Roles.ADMIN, null, sufixo);
    }

    @AfterEach
    void limpar() {
        for (Long id : solicitacoes) {
            jdbc.update("DELETE FROM whatsapp_mensagem WHERE solicitacao_id = ?", id);
            jdbc.update("UPDATE solicitacao_especialidade SET agendamento_id = NULL WHERE solicitacao_id = ?", id);
            jdbc.update("DELETE FROM agendamento_solicitacao WHERE solicitacao_id = ?", id);
            jdbc.update("DELETE FROM solicitacao_especialidade WHERE solicitacao_id = ?", id);
            jdbc.update("DELETE FROM solicitacao WHERE id = ?", id);
        }
        for (Long id : cotas) {
            jdbc.update("DELETE FROM cota_migracao_data WHERE cota_origem_id = ? OR cota_resultante_id = ?", id, id);
            cotaRepository.deleteById(id);
        }
        jdbc.update("DELETE FROM cota_migracao_data WHERE realizado_por_id IN (?, ?)", operador.getId(), admin.getId());
        especialidadeRepository.deleteById(cardiologia.getId());
        userRepository.deleteById(operador.getId());
        userRepository.deleteById(admin.getId());
        unidadeRepository.deleteById(unidade.getId());
    }

    private User usuario(Roles role, Unidade lotacao, String sufixo) {
        User u = new User();
        u.setCpf(cpfUnico());
        u.setNome(role + sufixo);
        u.setPassword("x");
        u.setRole(role);
        u.setAtivo(true);
        u.setUnidade(lotacao);
        return userRepository.saveAndFlush(u);
    }

    private static String cpfUnico() {
        return String.format("%011d", System.nanoTime() % 100_000_000_000L);
    }

    private Long agendar(int seq, LocalDate data) {
        Solicitacao s = new Solicitacao();
        s.setNomePaciente("Paciente " + seq);
        s.setCpfPaciente(cpfUnico());
        s.setCns("700" + cpfUnico());
        s.setTelefone("75999990000");
        s.setUnidade(unidade);

        SolicitacaoEspecialidade se = new SolicitacaoEspecialidade();
        se.setSolicitacao(s);
        se.setEspecialidadeSolicitada(cardiologia);
        se.setEspecialidadeCodigoLegacy(cardiologia.getCodigo());
        se.setStatus(StatusDaMarcacao.AGUARDANDO);
        se.setPrioridade(PrioridadeDaMarcacaoEnum.NORMAL);
        List<SolicitacaoEspecialidade> itens = new ArrayList<>();
        itens.add(se);
        s.setEspecialidades(itens);
        Solicitacao salva = solicitacaoRepository.saveAndFlush(s);
        solicitacoes.add(salva.getId());

        var dto = new MultiAgendamentoCreateDTO(List.of(cardiologia.getCodigo()), data, null, null,
                TurnoEnum.MANHA, "teste", null, null, null);
        return agendamentoService.criarAgendamentoParaMultiplosExames(salva.getId(), dto, operador.getCpf()).id();
    }

    private CotaUnidade cota(TipoPeriodoCota tipo, LocalDate dia, int total) {
        CotaUnidade c = new CotaUnidade();
        c.setUnidade(unidade);
        c.setEspecialidade(cardiologia);
        c.setTipoPeriodo(tipo);
        if (tipo == TipoPeriodoCota.MENSAL) {
            c.setPeriodo(dia.format(PERIODO));
        } else {
            c.setDataEspecifica(dia);
        }
        c.setQuantidadeTotal(total);
        c.setQuantidadeUtilizada(0);
        c.setAtivo(true);
        c = cotaRepository.saveAndFlush(c);
        cotas.add(c.getId());
        return c;
    }

    private int utilizada(Long cotaId) {
        return cotaRepository.findById(cotaId).orElseThrow().getQuantidadeUtilizada();
    }

    private LocalDate dataDoAgendamento(Long id) {
        return jdbc.queryForObject("SELECT data_agendada FROM agendamento_solicitacao WHERE id = ?",
                java.sql.Date.class, id).toLocalDate();
    }

    private List<WhatsAppMensagem> fila(WhatsAppTipoMensagem tipo) {
        return mensagemRepository.findAll().stream()
                .filter(m -> m.getTipo() == tipo && m.getSolicitacaoId() != null && solicitacoes.contains(m.getSolicitacaoId()))
                .toList();
    }

    @Test
    @DisplayName("BANCO REAL: mensal do destino esgotada desfaz tudo (cota, contadores, datas, auditoria, fila)")
    void mensalDoDestinoEsgotadaDesfazTudo() {
        CotaUnidade mensalAntiga = cota(TipoPeriodoCota.MENSAL, d1, 10);
        CotaUnidade mensalNova = cota(TipoPeriodoCota.MENSAL, outroMes, 1); // so cabe um dos dois
        CotaUnidade porData = cota(TipoPeriodoCota.DATA, d1, 5);
        Long ag1 = agendar(1, d1);
        Long ag2 = agendar(2, d1);
        assertThat(utilizada(mensalAntiga.getId())).isEqualTo(2);

        assertThatThrownBy(() -> migracaoService.migrar(porData.getId(), new CotaMigracaoDataCreateDTO(outroMes), admin.getCpf()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cota esgotada");

        CotaUnidade depois = cotaRepository.findById(porData.getId()).orElseThrow();
        assertThat(depois.getDataEspecifica()).isEqualTo(d1);
        assertThat(depois.getQuantidadeUtilizada()).isEqualTo(2);
        assertThat(utilizada(mensalAntiga.getId())).isEqualTo(2);
        assertThat(utilizada(mensalNova.getId())).as("o consumo parcial da mensal nova foi desfeito").isZero();
        assertThat(dataDoAgendamento(ag1)).isEqualTo(d1);
        assertThat(dataDoAgendamento(ag2)).isEqualTo(d1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM agendamento_solicitacao WHERE data_agendada_original IS NOT NULL "
                + "AND id IN (?, ?)", Integer.class, ag1, ag2)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cota_migracao_data WHERE cota_origem_id = ?",
                Integer.class, porData.getId())).isZero();
        assertThat(fila(WhatsAppTipoMensagem.REMARCACAO)).isEmpty();
    }

    @Test
    @DisplayName("BANCO REAL: apos o commit, a confirmacao pendente e substituida por uma REMARCACAO com a data nova")
    void aposOSucessoExisteARemarcacao() {
        cota(TipoPeriodoCota.MENSAL, d1, 10);
        CotaUnidade mensalNova = cota(TipoPeriodoCota.MENSAL, outroMes, 10);
        CotaUnidade porData = cota(TipoPeriodoCota.DATA, d1, 5);
        Long ag1 = agendar(1, d1);
        agendar(2, d1);
        assertThat(fila(WhatsAppTipoMensagem.CONFIRMACAO)).hasSize(2);

        migracaoService.migrar(porData.getId(), new CotaMigracaoDataCreateDTO(outroMes), admin.getCpf());

        assertThat(dataDoAgendamento(ag1)).isEqualTo(outroMes);
        assertThat(utilizada(mensalNova.getId())).isEqualTo(2);

        List<WhatsAppMensagem> remarcacoes = fila(WhatsAppTipoMensagem.REMARCACAO);
        assertThat(remarcacoes).hasSize(2).allSatisfy(m -> {
            assertThat(m.getDataReferencia()).isEqualTo(outroMes);
            assertThat(m.getResultado()).isEqualTo(WhatsAppResultado.PENDENTE);
        });
        assertThat(fila(WhatsAppTipoMensagem.CONFIRMACAO)).hasSize(2).allSatisfy(m -> {
            assertThat(m.getResultado()).isEqualTo(WhatsAppResultado.NAO_ENVIADO);
            assertThat(m.getMotivo()).isEqualTo(WhatsAppMotivoNaoEnvio.SUBSTITUIDO_POR_REMARCACAO);
        });
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cota_migracao_data WHERE cota_origem_id = ?",
                Integer.class, porData.getId())).isEqualTo(1);
    }
}
