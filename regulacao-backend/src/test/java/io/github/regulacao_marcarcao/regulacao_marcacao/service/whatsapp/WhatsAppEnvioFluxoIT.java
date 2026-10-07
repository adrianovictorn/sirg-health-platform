package io.github.regulacao_marcarcao.regulacao_marcacao.service.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.agendamentoDTO.MultiAgendamentoCreateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Especialidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Solicitacao;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.SolicitacaoEspecialidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.User;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.WhatsAppMensagem;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.ItemCategoria;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.PrioridadeDaMarcacaoEnum;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.Roles;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.StatusDaMarcacao;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.TurnoEnum;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.WhatsAppMotivoNaoEnvio;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.WhatsAppOrigem;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.WhatsAppResultado;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.WhatsAppTipoMensagem;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.EspecialidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.SolicitacaoRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UserRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.WhatsAppMensagemRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.service.AgendamentoService;

/**
 * Ligacao entre o agendamento e a fila do WhatsApp, pelo caminho real:
 * {@code AgendamentoService} -> evento -> listener pos-commit -> linha na fila.
 *
 * <p><b>Esta classe NAO e {@code @Transactional}</b>, ao contrario dos outros
 * ITs: o listener so roda depois de um commit de verdade, e num teste com
 * rollback ele nunca seria chamado. Por isso os dados sao gravados e apagados
 * a mao em {@link #limpar()}.
 *
 * <p>Nenhuma mensagem sai: o agendador fica desligado e a URL aponta para um
 * endereco que nao existe.
 */
@SpringBootTest(properties = {
        "app.whatsapp.envio.access-token=token-de-teste",
        "app.whatsapp.envio.phone-number-id=109876543210",
        "app.whatsapp.envio.base-url=http://127.0.0.1:9",
        "app.whatsapp.envio.agendador-ligado=false" })
class WhatsAppEnvioFluxoIT {

    @Autowired private AgendamentoService agendamentoService;
    @Autowired private SolicitacaoRepository solicitacaoRepository;
    @Autowired private EspecialidadeRepository especialidadeRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private WhatsAppMensagemRepository mensagemRepository;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TransactionTemplate transacao;

    private final List<Long> solicitacoes = new ArrayList<>();
    private Especialidade cardiologia;
    private User admin;
    private LocalDate data;

    @BeforeEach
    void setUp() {
        String sufixo = "_WPP_IT_" + System.nanoTime();
        data = LocalDate.now().plusDays(30);

        cardiologia = new Especialidade();
        cardiologia.setCodigo("CARDIO" + sufixo);
        cardiologia.setNome("Cardiologia" + sufixo);
        cardiologia.setCategoria(ItemCategoria.ESPECIALIDADE_MEDICA);
        cardiologia.setAtivo(true);
        cardiologia.setVagas(1); // capacidade 1 por dia: o segundo agendamento da 409
        cardiologia = especialidadeRepository.saveAndFlush(cardiologia);

        admin = new User();
        admin.setCpf(cpfUnico());
        admin.setNome("Admin" + sufixo);
        admin.setPassword("x");
        admin.setRole(Roles.ADMIN);
        admin.setAtivo(true);
        admin = userRepository.saveAndFlush(admin);
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
        especialidadeRepository.deleteById(cardiologia.getId());
        userRepository.deleteById(admin.getId());
    }

    private static String cpfUnico() {
        return String.format("%011d", System.nanoTime() % 100_000_000_000L);
    }

    private Solicitacao novaSolicitacao() {
        Solicitacao s = new Solicitacao();
        s.setNomePaciente("Paciente WPP IT");
        s.setCpfPaciente(cpfUnico());
        s.setCns("700" + cpfUnico());
        s.setTelefone("75999990000");

        SolicitacaoEspecialidade se = new SolicitacaoEspecialidade();
        se.setSolicitacao(s);
        se.setEspecialidadeSolicitada(cardiologia);
        se.setEspecialidadeCodigoLegacy(cardiologia.getCodigo());
        se.setStatus(StatusDaMarcacao.AGUARDANDO);
        se.setPrioridade(PrioridadeDaMarcacaoEnum.NORMAL);
        List<SolicitacaoEspecialidade> especs = new ArrayList<>();
        especs.add(se);
        s.setEspecialidades(especs);

        Solicitacao salva = solicitacaoRepository.saveAndFlush(s);
        solicitacoes.add(salva.getId());
        return salva;
    }

    private Long agendar(Solicitacao solicitacao, LocalDate dia) {
        var dto = new MultiAgendamentoCreateDTO(List.of(cardiologia.getCodigo()), dia, null, null,
                TurnoEnum.MANHA, "observacao interna do operador", null, null, null);
        return agendamentoService.criarAgendamentoParaMultiplosExames(solicitacao.getId(), dto, admin.getCpf()).id();
    }

    private List<WhatsAppMensagem> fila(Solicitacao solicitacao) {
        return mensagemRepository.findAll().stream()
                .filter(m -> solicitacao.getId().equals(m.getSolicitacaoId()))
                .sorted((a, b) -> a.getId().compareTo(b.getId()))
                .toList();
    }

    @Test
    @DisplayName("Agendar grava UMA confirmacao pendente na fila, depois do commit")
    void agendarEnfileiraConfirmacao() {
        Solicitacao paciente = novaSolicitacao();

        Long agendamentoId = agendar(paciente, data);

        assertThat(fila(paciente)).singleElement().satisfies(m -> {
            assertThat(m.getTipo()).isEqualTo(WhatsAppTipoMensagem.CONFIRMACAO);
            assertThat(m.getOrigem()).isEqualTo(WhatsAppOrigem.AUTOMATICO);
            assertThat(m.getResultado()).isEqualTo(WhatsAppResultado.PENDENTE);
            assertThat(m.getAlvoId()).isEqualTo(agendamentoId);
            assertThat(m.getDataReferencia()).isEqualTo(data);
            assertThat(m.getItensRef()).isNotBlank();
            // A fila nao guarda telefone nem conteudo: so sao montados na hora do envio.
            assertThat(m.getTelefoneFinal()).isNull();
            assertThat(m.getMetaMessageId()).isNull();
        });
    }

    @Test
    @DisplayName("Agendamento recusado (409 de capacidade) NAO gera mensagem")
    void agendamentoRecusadoNaoEnfileira() {
        Solicitacao primeiro = novaSolicitacao();
        Solicitacao segundo = novaSolicitacao();
        agendar(primeiro, data);

        assertThatThrownBy(() -> agendar(segundo, data)).isInstanceOf(IllegalStateException.class);

        assertThat(fila(segundo)).isEmpty();
    }

    @Test
    @DisplayName("Excluir depois de a confirmacao ter saido enfileira o cancelamento, com atraso")
    void excluirEnfileiraCancelamento() {
        Solicitacao paciente = novaSolicitacao();
        Long agendamentoId = agendar(paciente, data);
        marcarComoEnviada(fila(paciente).get(0));

        agendamentoService.deleteAgendamento(agendamentoId, admin.getCpf());

        List<WhatsAppMensagem> fila = fila(paciente);
        assertThat(fila).hasSize(2);
        WhatsAppMensagem cancelamento = fila.get(1);
        assertThat(cancelamento.getTipo()).isEqualTo(WhatsAppTipoMensagem.CANCELAMENTO);
        assertThat(cancelamento.getResultado()).isEqualTo(WhatsAppResultado.PENDENTE);
        assertThat(cancelamento.getAlvoId()).isEqualTo(agendamentoId);
        assertThat(cancelamento.getDataReferencia()).isEqualTo(data);
        assertThat(cancelamento.getEnviarApos()).isAfter(cancelamento.getCriadoEm().plusSeconds(60));
    }

    @Test
    @DisplayName("Remarcar (excluir e agendar de novo) vira uma unica mensagem de remarcacao")
    void remarcarSubstituiOCancelamento() {
        Solicitacao paciente = novaSolicitacao();
        Long antigo = agendar(paciente, data);
        marcarComoEnviada(fila(paciente).get(0));
        agendamentoService.deleteAgendamento(antigo, admin.getCpf());

        Long novo = agendar(paciente, data.plusDays(1));

        List<WhatsAppMensagem> fila = fila(paciente);
        assertThat(fila).hasSize(3);
        assertThat(fila.get(1).getTipo()).isEqualTo(WhatsAppTipoMensagem.CANCELAMENTO);
        assertThat(fila.get(1).getResultado()).isEqualTo(WhatsAppResultado.NAO_ENVIADO);
        assertThat(fila.get(1).getMotivo()).isEqualTo(WhatsAppMotivoNaoEnvio.SUBSTITUIDO_POR_REMARCACAO);
        assertThat(fila.get(2).getTipo()).isEqualTo(WhatsAppTipoMensagem.REMARCACAO);
        assertThat(fila.get(2).getResultado()).isEqualTo(WhatsAppResultado.PENDENTE);
        assertThat(fila.get(2).getAlvoId()).isEqualTo(novo);
        assertThat(fila.get(2).getDataReferencia()).isEqualTo(data.plusDays(1));
    }

    @Test
    @DisplayName("Agendar e excluir antes de a confirmacao sair nao gera mensagem nenhuma")
    void excluirAntesDoEnvioDescartaAConfirmacao() {
        Solicitacao paciente = novaSolicitacao();
        Long agendamentoId = agendar(paciente, data);

        agendamentoService.deleteAgendamento(agendamentoId, admin.getCpf());

        assertThat(fila(paciente)).singleElement().satisfies(m -> {
            assertThat(m.getTipo()).isEqualTo(WhatsAppTipoMensagem.CONFIRMACAO);
            assertThat(m.getResultado()).isEqualTo(WhatsAppResultado.NAO_ENVIADO);
            assertThat(m.getMotivo()).isEqualTo(WhatsAppMotivoNaoEnvio.AGENDAMENTO_REMOVIDO);
        });
    }

    @Test
    @DisplayName("Lembrete: o indice unico barra a duplicata; reenvio manual (sem chave) passa")
    void lembreteNaoDuplica() {
        Solicitacao paciente = novaSolicitacao();
        Long agendamentoId = agendar(paciente, data);
        String chave = WhatsAppLembreteService.chave(agendamentoId, data);

        int primeira = transacao.execute(tx -> mensagemRepository.enfileirarLembrete(
                agendamentoId, paciente.getId(), "AUTOMATICO", data, chave, null));
        int segunda = transacao.execute(tx -> mensagemRepository.enfileirarLembrete(
                agendamentoId, paciente.getId(), "MANUAL", data, chave, admin.getId()));
        jdbc.update("UPDATE whatsapp_config SET envio_ligado = TRUE WHERE id = 1");
        try {
            // Dois reenvios manuais seguidos: sem chave, o indice unico nao os barra.
            mensagemServiceReenviar(agendamentoId);
            mensagemServiceReenviar(agendamentoId);
        } finally {
            jdbc.update("UPDATE whatsapp_config SET envio_ligado = FALSE WHERE id = 1");
        }

        assertThat(primeira).isEqualTo(1);
        assertThat(segunda).isZero();
        assertThat(fila(paciente))
                .filteredOn(m -> m.getTipo() == WhatsAppTipoMensagem.LEMBRETE)
                .extracting(WhatsAppMensagem::getOrigem)
                .containsExactly(WhatsAppOrigem.AUTOMATICO, WhatsAppOrigem.MANUAL, WhatsAppOrigem.MANUAL);
    }

    @Autowired private WhatsAppMensagemService mensagemService;

    private void mensagemServiceReenviar(Long agendamentoId) {
        mensagemService.reenviar(agendamentoId, WhatsAppTipoMensagem.LEMBRETE, admin.getCpf());
    }

    /** Simula a confirmacao ja despachada, sem chamar a Meta. */
    private void marcarComoEnviada(WhatsAppMensagem mensagem) {
        jdbc.update("UPDATE whatsapp_mensagem SET resultado = 'ENVIADO', enviado_em = now() WHERE id = ?",
                mensagem.getId());
    }
}
