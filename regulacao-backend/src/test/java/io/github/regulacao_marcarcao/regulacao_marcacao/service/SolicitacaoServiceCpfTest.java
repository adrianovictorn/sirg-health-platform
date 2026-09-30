package io.github.regulacao_marcarcao.regulacao_marcacao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

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
 * V96: CPF do paciente deixa de ser obrigatorio para recem-nascido (RN).
 *
 * As tres primeiras secoes (CARACTERIZACAO) documentam o que ja passava antes
 * deste ajuste e continua valendo: gravacao do CPF e dos demais campos. A
 * secao "NOVO COMPORTAMENTO" cobre a validacao condicional introduzida aqui.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SolicitacaoServiceCpfTest {

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
    }

    private SolicitacaoCreateDTO dto(String cpf, boolean recemNascido) {
        return new SolicitacaoCreateDTO(
                null, "Paciente Teste", cpf, "700000000000000", null,
                "Pai Teste", "Mae Teste", "Rua Teste", null, null, null,
                null, List.of(), recemNascido);
    }

    // ==================================================================
    // CARACTERIZACAO — comportamento que NAO muda neste ajuste
    // ==================================================================

    @Test
    @DisplayName("Cadastro grava o CPF exatamente como recebido")
    void gravaCpfComoRecebido() {
        var resultado = service.createSolicitacao(dto("12345678900", false), null);

        assertThat(resultado.cpfPaciente()).isEqualTo("12345678900");
    }

    @Test
    @DisplayName("Cadastro grava os demais campos do paciente sem depender do CPF")
    void gravaDemaisCamposIndependenteDoCpf() {
        var resultado = service.createSolicitacao(dto(null, true), null);

        assertThat(resultado.nomePaciente()).isEqualTo("Paciente Teste");
        assertThat(resultado.nomePai()).isEqualTo("Pai Teste");
        assertThat(resultado.nomeMae()).isEqualTo("Mae Teste");
        assertThat(resultado.endereco()).isEqualTo("Rua Teste");
    }

    // ==================================================================
    // V96 — validacao condicional de obrigatoriedade (novo comportamento)
    // ==================================================================

    @Test
    @DisplayName("Paciente comum (nao RN) sem CPF e recusado")
    void naoRnSemCpfERecusado() {
        assertThatThrownBy(() -> service.createSolicitacao(dto(null, false), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CPF");
    }

    @Test
    @DisplayName("Paciente comum (nao RN) sem CPF, so espacos, e recusado")
    void naoRnComCpfEmBrancoERecusado() {
        assertThatThrownBy(() -> service.createSolicitacao(dto("   ", false), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CPF");
    }

    @Test
    @DisplayName("Recem-nascido (RN) sem CPF e aceito")
    void recemNascidoSemCpfEAceito() {
        assertThatCode(() -> service.createSolicitacao(dto(null, true), null))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Recem-nascido (RN) com CPF informado tambem e aceito, e o CPF e gravado")
    void recemNascidoComCpfInformadoEAceito() {
        var resultado = service.createSolicitacao(dto("12345678900", true), null);

        assertThat(resultado.cpfPaciente()).isEqualTo("12345678900");
    }

    // ==================================================================
    // V96 — completar CPF via edicao (SolicitacaoUpdateDTO, novo campo)
    // ==================================================================

    private SolicitacaoUpdateDTO updateDto(String cpf) {
        return new SolicitacaoUpdateDTO(
                null, "Paciente Teste", cpf, null, "700000000000000", null,
                "Pai Teste", "Mae Teste", "Rua Teste", null, null, null, null, null);
    }

    private Solicitacao solicitacaoExistente(Long id, String cpfAtual) {
        Solicitacao s = new Solicitacao();
        s.setId(id);
        s.setNomePaciente("Paciente Teste");
        s.setCpfPaciente(cpfAtual);
        return s;
    }

    @Test
    @DisplayName("Editar solicitacao completando o CPF (antes vazio) grava o novo CPF")
    void editarCompletandoCpfGravaValor() {
        when(solicitacaoRepository.findById(10L)).thenReturn(Optional.of(solicitacaoExistente(10L, null)));
        when(solicitacaoRepository.findByCpfPacienteSemPonto("12345678900")).thenReturn(List.of());

        var resultado = service.updateSolicitacao(10L, updateDto("12345678900"), null);

        assertThat(resultado.cpfPaciente()).isEqualTo("12345678900");
    }

    @Test
    @DisplayName("Editar sem informar CPF (vazio) nao apaga o CPF ja gravado")
    void editarSemCpfNaoApagaCpfExistente() {
        when(solicitacaoRepository.findById(10L)).thenReturn(Optional.of(solicitacaoExistente(10L, "12345678900")));

        var resultado = service.updateSolicitacao(10L, updateDto(null), null);

        assertThat(resultado.cpfPaciente()).isEqualTo("12345678900");
    }

    @Test
    @DisplayName("Editar CPF que ja pertence a OUTRA solicitacao e recusado")
    void editarComCpfDeOutraSolicitacaoERecusado() {
        when(solicitacaoRepository.findById(10L)).thenReturn(Optional.of(solicitacaoExistente(10L, null)));
        when(solicitacaoRepository.findByCpfPacienteSemPonto("12345678900"))
                .thenReturn(List.of(solicitacaoExistente(99L, "12345678900")));

        assertThatThrownBy(() -> service.updateSolicitacao(10L, updateDto("12345678900"), null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CPF já cadastrado");
    }

    @Test
    @DisplayName("Editar reenviando o MESMO CPF que a propria solicitacao ja tem nao colide consigo mesma")
    void editarComMesmoCpfDaPropriaSolicitacaoNaoColide() {
        when(solicitacaoRepository.findById(10L)).thenReturn(Optional.of(solicitacaoExistente(10L, "12345678900")));
        when(solicitacaoRepository.findByCpfPacienteSemPonto("12345678900"))
                .thenReturn(List.of(solicitacaoExistente(10L, "12345678900")));

        assertThatCode(() -> service.updateSolicitacao(10L, updateDto("12345678900"), null))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Dois RNs sem CPF cadastrados em sequencia nao colidem entre si")
    void doisRnsSemCpfNaoColidem() {
        assertThatCode(() -> {
            service.createSolicitacao(dto(null, true), null);
            service.createSolicitacao(dto(null, true), null);
        }).doesNotThrowAnyException();
    }
}
