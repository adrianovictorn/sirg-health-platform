package io.github.regulacao_marcarcao.regulacao_marcacao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.solicitacoesDTO.SolicitacaoCreateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.solicitacoesDTO.SolicitacaoUpdateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Solicitacao;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.AgendamentoSolicitacaoRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.CidRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.EspecialidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.ProfissionalRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.SolicitacaoEspecialidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.SolicitacaoRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UnidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UserRepository;

/**
 * V102: opt-out do WhatsApp no cadastro do paciente.
 *
 * O caso que este teste existe para proteger: a ficha do paciente tem um botao
 * (salvar CIDs) que envia PUT sem a maioria dos campos, e o service grava null
 * no que nao veio. Se o opt-out seguisse essa regra, salvar um CID faria o
 * paciente voltar a receber mensagens que pediu para nao receber.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SolicitacaoServiceOptOutTest {

    @Mock private SolicitacaoRepository solicitacaoRepository;
    @Mock private AgendamentoSolicitacaoRepository agendamentoRepository;
    @Mock private SolicitacaoEspecialidadeRepository especialidadeRepository;
    @Mock private CidRepository cidRepository;
    @Mock private EspecialidadeRepository especialidadeRepo;
    @Mock private ProfissionalRepository profissionalRepository;
    @Mock private UserRepository userRepository;
    @Mock private UnidadeRepository unidadeRepository;
    @Mock private UnidadeAcessoService unidadeAcessoService;

    @InjectMocks private SolicitacaoService service;

    private Solicitacao existente;

    @BeforeEach
    void setUp() {
        when(unidadeAcessoService.resolverUnidadeAlvo(any(), any())).thenReturn(null);
        when(unidadeAcessoService.contextoDe((String) any())).thenReturn(UnidadeAcessoService.UnidadeContexto.GLOBAL);
        when(solicitacaoRepository.save(any(Solicitacao.class))).thenAnswer(inv -> {
            Solicitacao s = inv.getArgument(0);
            if (s.getId() == null) {
                s.setId(1L);
            }
            return s;
        });
        existente = new Solicitacao();
        existente.setId(10L);
        existente.setNomePaciente("Paciente Teste");
        when(solicitacaoRepository.findById(10L)).thenReturn(Optional.of(existente));
    }

    private SolicitacaoUpdateDTO edicao(Boolean optOut) {
        return new SolicitacaoUpdateDTO(
                null, "Paciente Teste", null, null, "700000000000000", "75999990000",
                "Pai", "Mae", "Rua", null, null, null, null, null, optOut);
    }

    private SolicitacaoCreateDTO cadastro(Boolean optOut) {
        return new SolicitacaoCreateDTO(
                null, "Paciente Teste", "12345678900", "700000000000000", null,
                "Pai Teste", "Mae Teste", "Rua Teste", null, null, null,
                null, List.of(), false, optOut);
    }

    @Test
    @DisplayName("PUT sem o campo (ex.: salvar CIDs) NAO desfaz o opt-out do paciente")
    void putSemOCampoNaoMexeNoOptOut() {
        Instant marcadoEm = Instant.parse("2026-10-01T12:00:00Z");
        existente.setWhatsappOptOut(true);
        existente.setWhatsappOptOutEm(marcadoEm);

        var resultado = service.updateSolicitacao(10L, edicao(null), null);

        assertThat(existente.getWhatsappOptOut()).isTrue();
        assertThat(existente.getWhatsappOptOutEm()).isEqualTo(marcadoEm);
        assertThat(resultado.whatsappOptOut()).isTrue();
    }

    @Test
    @DisplayName("Marcar o opt-out grava a escolha e quando foi feita")
    void marcarGravaADataDaEscolha() {
        var resultado = service.updateSolicitacao(10L, edicao(true), null);

        assertThat(existente.getWhatsappOptOut()).isTrue();
        assertThat(existente.getWhatsappOptOutEm()).isNotNull();
        assertThat(resultado.whatsappOptOut()).isTrue();
    }

    @Test
    @DisplayName("Salvar de novo com o opt-out ja marcado preserva a data original")
    void salvarDeNovoNaoRenovaAData() {
        Instant marcadoEm = Instant.parse("2026-10-01T12:00:00Z");
        existente.setWhatsappOptOut(true);
        existente.setWhatsappOptOutEm(marcadoEm);

        service.updateSolicitacao(10L, edicao(true), null);

        assertThat(existente.getWhatsappOptOutEm()).isEqualTo(marcadoEm);
    }

    @Test
    @DisplayName("Desmarcar o opt-out limpa a data")
    void desmarcarLimpaAData() {
        existente.setWhatsappOptOut(true);
        existente.setWhatsappOptOutEm(Instant.parse("2026-10-01T12:00:00Z"));

        var resultado = service.updateSolicitacao(10L, edicao(false), null);

        assertThat(existente.getWhatsappOptOut()).isFalse();
        assertThat(existente.getWhatsappOptOutEm()).isNull();
        assertThat(resultado.whatsappOptOut()).isFalse();
    }

    @Test
    @DisplayName("Cadastro sem o campo nasce recebendo mensagens (opt-out false, nunca null)")
    void cadastroSemOCampoNasceFalse() {
        var resultado = service.createSolicitacao(cadastro(null), null);

        assertThat(resultado.whatsappOptOut()).isFalse();
    }

    @Test
    @DisplayName("Cadastro ja pode nascer com o opt-out marcado")
    void cadastroPodeNascerComOptOut() {
        var resultado = service.createSolicitacao(cadastro(true), null);

        assertThat(resultado.whatsappOptOut()).isTrue();
    }
}
