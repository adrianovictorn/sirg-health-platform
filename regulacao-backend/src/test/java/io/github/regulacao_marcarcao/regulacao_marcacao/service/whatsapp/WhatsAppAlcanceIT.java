package io.github.regulacao_marcarcao.regulacao_marcacao.service.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

import io.github.regulacao_marcarcao.regulacao_marcacao.config.WhatsAppEnvioProperties;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.indicadores.WhatsAppAlcanceViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.indicadores.WhatsAppTelefoneInvalidoViewDTO;
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
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.WhatsAppAlvoTipo;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.WhatsAppMotivoNaoEnvio;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.WhatsAppOrigem;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.WhatsAppResultado;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.WhatsAppTipoMensagem;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.EspecialidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.SolicitacaoRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UnidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UserRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.WhatsAppMensagemRepository;

/**
 * Indicadores gerenciais de WhatsApp sobre mensagens gravadas direto na tabela,
 * cada uma num resultado conhecido — nada e enviado.
 *
 * <p>As mensagens sao de pacientes de uma unidade propria do teste, e a consulta
 * usa o filtro dessa unidade.
 */
@SpringBootTest
@Transactional
class WhatsAppAlcanceIT {

    // Ids de agendamento ficticios e altos: alvo_id nao tem FK.
    private static final AtomicLong PROXIMO_ALVO = new AtomicLong(System.nanoTime());

    @Autowired private WhatsAppAlcanceService alcanceService;
    @Autowired private WhatsAppEnvioProperties props;
    @Autowired private WhatsAppConfigService configService;
    @Autowired private WhatsAppMensagemRepository mensagemRepository;
    @Autowired private SolicitacaoRepository solicitacaoRepository;
    @Autowired private UnidadeRepository unidadeRepository;
    @Autowired private EspecialidadeRepository especialidadeRepository;
    @Autowired private UserRepository userRepository;

    private String sufixo;
    private Unidade unidade;
    private Unidade outraUnidade;
    private Especialidade exame;
    private User admin;
    private Instant agora;

    @BeforeEach
    void setUp() {
        sufixo = "_IND_WPP_" + System.nanoTime();
        unidade = unidade("A");
        outraUnidade = unidade("B");

        exame = new Especialidade();
        exame.setCodigo("EX" + sufixo);
        exame.setNome("EX" + sufixo);
        exame.setCategoria(ItemCategoria.EXAME_OU_PROCEDIMENTO);
        exame.setAtivo(true);
        exame.setVagas(0);
        exame = especialidadeRepository.saveAndFlush(exame);

        admin = usuario(Roles.ADMIN);
        agora = Instant.now();
    }

    @Test
    @DisplayName("alcance: conta agendamentos, nao mensagens; cada um em uma so situacao")
    void alcanceDoAviso() {
        // Entregue.
        mensagem(paciente(unidade), novoAlvo(), WhatsAppTipoMensagem.CONFIRMACAO, WhatsAppResultado.ENTREGUE, null, agora);
        // Barrada pelo limite diario e depois reenviada a mao e lida: UM agendamento, alcancado e lido.
        Solicitacao reenviado = paciente(unidade);
        long alvoReenviado = novoAlvo();
        mensagem(reenviado, alvoReenviado, WhatsAppTipoMensagem.CONFIRMACAO, WhatsAppResultado.NAO_ENVIADO,
                WhatsAppMotivoNaoEnvio.LIMITE_DIARIO, agora.minus(2, ChronoUnit.HOURS));
        mensagem(reenviado, alvoReenviado, WhatsAppTipoMensagem.CONFIRMACAO, WhatsAppResultado.LIDO, null, agora);
        // Aceita pela Meta, sem confirmacao de entrega.
        mensagem(paciente(unidade), novoAlvo(), WhatsAppTipoMensagem.REMARCACAO, WhatsAppResultado.ENVIADO, null, agora);
        mensagem(paciente(unidade), novoAlvo(), WhatsAppTipoMensagem.CONFIRMACAO, WhatsAppResultado.PENDENTE, null, agora);
        mensagem(paciente(unidade), novoAlvo(), WhatsAppTipoMensagem.CONFIRMACAO, WhatsAppResultado.FALHOU, null, agora);
        mensagem(paciente(unidade), novoAlvo(), WhatsAppTipoMensagem.CONFIRMACAO, WhatsAppResultado.NAO_ENVIADO,
                WhatsAppMotivoNaoEnvio.TELEFONE_INVALIDO, agora);
        mensagem(paciente(unidade), novoAlvo(), WhatsAppTipoMensagem.CONFIRMACAO, WhatsAppResultado.NAO_ENVIADO,
                WhatsAppMotivoNaoEnvio.OPT_OUT, agora);

        // Fora do universo: so lembrete; outra unidade; antes do periodo.
        mensagem(paciente(unidade), novoAlvo(), WhatsAppTipoMensagem.LEMBRETE, WhatsAppResultado.ENTREGUE, null, agora);
        mensagem(paciente(outraUnidade), novoAlvo(), WhatsAppTipoMensagem.CONFIRMACAO, WhatsAppResultado.ENTREGUE, null, agora);
        mensagem(paciente(unidade), novoAlvo(), WhatsAppTipoMensagem.CONFIRMACAO, WhatsAppResultado.ENTREGUE, null,
                agora.minus(60, ChronoUnit.DAYS));

        WhatsAppAlcanceViewDTO resultado = alcanceService.alcance(unidade.getId(), null, null, admin.getCpf());

        assertThat(resultado.agendamentos()).isEqualTo(7);
        assertThat(resultado.alcancados()).isEqualTo(2);
        assertThat(resultado.lidos()).isEqualTo(1);
        assertThat(resultado.aceitasSemConfirmacao()).isEqualTo(1);
        assertThat(resultado.pendentes()).isEqualTo(1);
        assertThat(resultado.falhas()).isEqualTo(1);
        assertThat(resultado.naoEnviadosPorMotivo())
                .isEqualTo(Map.of("TELEFONE_INVALIDO", 1L, "OPT_OUT", 1L));
        // As situacoes fecham o universo: nada contado duas vezes, nada perdido.
        assertThat(resultado.alcancados() + resultado.aceitasSemConfirmacao() + resultado.pendentes()
                + resultado.falhas() + resultado.naoEnviadosPorMotivo().values().stream().mapToLong(Long::longValue).sum())
                .isEqualTo(resultado.agendamentos());

        // A resposta diz o estado da instancia, para a tela nao mostrar zero como se fosse resultado.
        assertThat(resultado.configurado()).isEqualTo(props.configurado());
        assertThat(resultado.envioLigado()).isEqualTo(configService.envioLigado());
        assertThat(resultado.ate()).isEqualTo(LocalDate.now(WhatsAppEnvioProperties.FUSO));
    }

    @Test
    @DisplayName("alcance: sem mensagem no periodo devolve zeros e mapa vazio, nao erro")
    void alcanceVazio() {
        WhatsAppAlcanceViewDTO resultado = alcanceService.alcance(unidade.getId(), null, null, admin.getCpf());

        assertThat(resultado.agendamentos()).isZero();
        assertThat(resultado.alcancados()).isZero();
        assertThat(resultado.naoEnviadosPorMotivo()).isEmpty();
    }

    @Test
    @DisplayName("telefone invalido: em pacientes, so sobre quem teve o telefone avaliado")
    void telefoneInvalidoPorUnidade() {
        // Avaliados: saiu; telefone invalido (duas mensagens, um paciente); sem telefone; limite diario.
        mensagem(paciente(unidade), novoAlvo(), WhatsAppTipoMensagem.CONFIRMACAO, WhatsAppResultado.ENTREGUE, null, agora);
        Solicitacao invalido = paciente(unidade);
        mensagem(invalido, novoAlvo(), WhatsAppTipoMensagem.CONFIRMACAO, WhatsAppResultado.NAO_ENVIADO,
                WhatsAppMotivoNaoEnvio.TELEFONE_INVALIDO, agora);
        mensagem(invalido, novoAlvo(), WhatsAppTipoMensagem.LEMBRETE, WhatsAppResultado.NAO_ENVIADO,
                WhatsAppMotivoNaoEnvio.TELEFONE_INVALIDO, agora);
        mensagem(paciente(unidade), novoAlvo(), WhatsAppTipoMensagem.CONFIRMACAO, WhatsAppResultado.NAO_ENVIADO,
                WhatsAppMotivoNaoEnvio.SEM_TELEFONE, agora);
        mensagem(paciente(unidade), novoAlvo(), WhatsAppTipoMensagem.CONFIRMACAO, WhatsAppResultado.NAO_ENVIADO,
                WhatsAppMotivoNaoEnvio.LIMITE_DIARIO, agora);
        // Nao avaliados: barrados antes da checagem do telefone, ou ainda na fila.
        mensagem(paciente(unidade), novoAlvo(), WhatsAppTipoMensagem.CONFIRMACAO, WhatsAppResultado.NAO_ENVIADO,
                WhatsAppMotivoNaoEnvio.ENVIO_DESLIGADO, agora);
        mensagem(paciente(unidade), novoAlvo(), WhatsAppTipoMensagem.CONFIRMACAO, WhatsAppResultado.NAO_ENVIADO,
                WhatsAppMotivoNaoEnvio.OPT_OUT, agora);
        mensagem(paciente(unidade), novoAlvo(), WhatsAppTipoMensagem.CONFIRMACAO, WhatsAppResultado.PENDENTE, null, agora);
        // Outra unidade.
        mensagem(paciente(outraUnidade), novoAlvo(), WhatsAppTipoMensagem.CONFIRMACAO, WhatsAppResultado.NAO_ENVIADO,
                WhatsAppMotivoNaoEnvio.TELEFONE_INVALIDO, agora);

        WhatsAppTelefoneInvalidoViewDTO resultado =
                alcanceService.telefoneInvalido(unidade.getId(), null, null, admin.getCpf());

        assertThat(resultado.total().avaliados()).isEqualTo(4);
        assertThat(resultado.total().invalidos()).isEqualTo(2);
        assertThat(resultado.porUnidade()).hasSize(1);
        assertThat(resultado.porUnidade().get(0).nome()).isEqualTo(unidade.getNome());
        assertThat(resultado.porUnidade().get(0).invalidos()).isEqualTo(2);

        // Sem filtro, a outra unidade aparece na quebra.
        assertThat(alcanceService.telefoneInvalido(null, null, null, admin.getCpf()).porUnidade())
                .extracting(WhatsAppTelefoneInvalidoViewDTO.Linha::nome)
                .contains(unidade.getNome(), outraUnidade.getNome());
    }

    @Test
    @DisplayName("telefone invalido: so mensagens barradas antes da checagem — nada avaliado, e nao '0% invalido'")
    void telefoneNaoAvaliado() {
        mensagem(paciente(unidade), novoAlvo(), WhatsAppTipoMensagem.CONFIRMACAO, WhatsAppResultado.NAO_ENVIADO,
                WhatsAppMotivoNaoEnvio.ENVIO_DESLIGADO, agora);

        WhatsAppTelefoneInvalidoViewDTO resultado =
                alcanceService.telefoneInvalido(unidade.getId(), null, null, admin.getCpf());

        assertThat(resultado.total().avaliados()).isZero();
        assertThat(resultado.porUnidade()).isEmpty();
    }

    @Test
    @DisplayName("segunda barreira e periodo invalido")
    void acessoEPeriodo() {
        Long id = unidade.getId();
        alcanceService.alcance(id, null, null, usuario(Roles.GESTOR).getCpf());

        for (Roles role : List.of(Roles.RECEPCAO, Roles.ADMIN_UNIDADE, Roles.PACIENTE)) {
            String cpf = usuario(role).getCpf();
            assertThatThrownBy(() -> alcanceService.alcance(id, null, null, cpf))
                    .as(role.name()).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> alcanceService.telefoneInvalido(id, null, null, cpf))
                    .as(role.name()).isInstanceOf(AccessDeniedException.class);
        }
        assertThatThrownBy(() -> alcanceService.alcance(id, "2026-10-10", "2026-10-01", admin.getCpf()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ------------------------------------------------------------------

    private static long novoAlvo() {
        return PROXIMO_ALVO.incrementAndGet();
    }

    private static String cpfUnico() {
        return String.format("%011d", System.nanoTime() % 100_000_000_000L);
    }

    private Unidade unidade(String letra) {
        Unidade u = new Unidade();
        u.setNome("Unidade " + letra + sufixo);
        u.setCodigo("U" + letra + sufixo);
        u.setAtivo(true);
        return unidadeRepository.saveAndFlush(u);
    }

    private User usuario(Roles role) {
        User u = new User();
        u.setCpf(cpfUnico());
        u.setNome(role + sufixo);
        u.setPassword("x");
        u.setRole(role);
        u.setAtivo(true);
        if (role == Roles.RECEPCAO || role == Roles.ADMIN_UNIDADE) {
            u.setUnidade(unidade);
        }
        return userRepository.saveAndFlush(u);
    }

    private Solicitacao paciente(Unidade daUnidade) {
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
        se.setStatus(StatusDaMarcacao.AGENDADO);
        se.setPrioridade(PrioridadeDaMarcacaoEnum.NORMAL);
        List<SolicitacaoEspecialidade> itens = new ArrayList<>();
        itens.add(se);
        s.setEspecialidades(itens);
        return solicitacaoRepository.saveAndFlush(s);
    }

    private void mensagem(Solicitacao s, long alvoId, WhatsAppTipoMensagem tipo, WhatsAppResultado resultado,
            WhatsAppMotivoNaoEnvio motivo, Instant criadoEm) {
        WhatsAppMensagem m = new WhatsAppMensagem();
        m.setAlvoTipo(WhatsAppAlvoTipo.CONSULTA_EXAME);
        m.setAlvoId(alvoId);
        m.setSolicitacaoId(s.getId());
        m.setTipo(tipo);
        m.setOrigem(WhatsAppOrigem.AUTOMATICO);
        m.setResultado(resultado);
        m.setMotivo(motivo);
        m.setDataReferencia(LocalDate.now(WhatsAppEnvioProperties.FUSO));
        m.setCriadoEm(criadoEm);
        m.setEnviarApos(criadoEm);
        mensagemRepository.saveAndFlush(m);
    }
}
