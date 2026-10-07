package io.github.regulacao_marcarcao.regulacao_marcacao.service.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.AgendamentoSolicitacao;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Cidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.CotaUnidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Especialidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.LocalAgendamento;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Profissional;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Solicitacao;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.SolicitacaoEspecialidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.User;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.LocalDeAgendamentoEnum;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.TurnoEnum;

/**
 * O texto que chega ao celular do paciente — e, principalmente, o que NAO pode
 * chegar: CPF, CNS, quem agendou, observacao do operador e o nome de
 * especialidade sensivel.
 */
class WhatsAppConteudoServiceTest {

    private static final String CPF = "123.456.789-09";
    private static final String CNS = "700123456789012";
    private static final String OBSERVACAO = "Paciente HIV+, encaixe a pedido do Dr. Fulano";
    private static final String OPERADOR = "Operadora Josefa";

    private final WhatsAppConteudoService service = new WhatsAppConteudoService();

    private Solicitacao paciente(String nome) {
        Solicitacao s = new Solicitacao();
        s.setNomePaciente(nome);
        s.setCpfPaciente(CPF);
        s.setCns(CNS);
        s.setObservacoes(OBSERVACAO);
        s.setNomeMae("Mae da Paciente");
        s.setEndereco("Rua do Paciente, 10");
        return s;
    }

    /** Segunda-feira, 12/10/2026. */
    private AgendamentoSolicitacao agendamento() {
        Cidade cidade = new Cidade();
        cidade.setNomeCidade("Santo Antônio de Jesus");
        LocalAgendamento local = new LocalAgendamento();
        local.setNomeLocal("Policlínica Regional");
        local.setCidade(cidade);

        User operador = new User();
        operador.setNome(OPERADOR);

        AgendamentoSolicitacao a = new AgendamentoSolicitacao();
        a.setDataAgendada(LocalDate.of(2026, 10, 12));
        a.setTurno(TurnoEnum.MANHA);
        a.setLocalAgendamento(local);
        a.setObservacoes(OBSERVACAO);
        a.setCriadoPor(operador);
        return a;
    }

    private SolicitacaoEspecialidade item(String nome, boolean sensivel) {
        Especialidade e = new Especialidade();
        e.setNome(nome);
        e.setSensivel(sensivel);
        SolicitacaoEspecialidade se = new SolicitacaoEspecialidade();
        se.setEspecialidadeSolicitada(e);
        return se;
    }

    private Profissional profissional(String nome) {
        Profissional p = new Profissional();
        p.setNome(nome);
        return p;
    }

    @Test
    void confirmacaoDeUmItemTrazTodosOsCamposNaOrdemDoTemplate() {
        SolicitacaoEspecialidade cardio = item("Cardiologia", false);
        cardio.setHoraAgendada(LocalTime.of(7, 30));
        cardio.setProfissionalExecutante(profissional("Dra. Ana Lima"));

        List<String> variaveis = service.confirmacao(paciente("MARIA DA CONCEIÇÃO SOUZA"), agendamento(), List.of(cardio));

        assertThat(variaveis).containsExactly(
                "Maria",
                "Cardiologia",
                "Policlínica Regional - Santo Antônio de Jesus",
                "12/10/2026",
                "segunda-feira",
                "07:30",
                "Manhã",
                "Dra. Ana Lima");
    }

    @Test
    void lembreteTemOsMesmosDadosEmOutraOrdem() {
        SolicitacaoEspecialidade cardio = item("Cardiologia", false);

        List<String> variaveis = service.lembrete(paciente("joão"), agendamento(), List.of(cardio));

        assertThat(variaveis).containsExactly(
                "João",
                "12/10/2026",
                "segunda-feira",
                "Cardiologia",
                "Policlínica Regional - Santo Antônio de Jesus",
                WhatsAppConteudoService.HORARIO_PADRAO,
                "Manhã",
                WhatsAppConteudoService.PROFISSIONAL_PADRAO);
    }

    @Test
    void profissionalVemDaCotaQuandoOOperadorNaoSobrescreveu() {
        CotaUnidade cota = new CotaUnidade();
        cota.setProfissionalExecutante(profissional("Dr. Carlos"));
        SolicitacaoEspecialidade cardio = item("Cardiologia", false);
        cardio.setCotaUnidade(cota);

        assertThat(service.confirmacao(paciente("Maria"), agendamento(), List.of(cardio)).get(7))
                .isEqualTo("Dr. Carlos");
    }

    @Test
    void comVariosItensHoraEProfissionalNaoSaoCitados() {
        SolicitacaoEspecialidade hemograma = item("Hemograma", false);
        hemograma.setHoraAgendada(LocalTime.of(7, 0));
        hemograma.setProfissionalExecutante(profissional("Dra. Ana"));
        SolicitacaoEspecialidade glicemia = item("Glicemia", false);

        List<String> variaveis = service.confirmacao(paciente("Maria"), agendamento(), List.of(hemograma, glicemia));

        assertThat(variaveis.get(1)).isEqualTo("Hemograma, Glicemia");
        assertThat(variaveis.get(5)).isEqualTo(WhatsAppConteudoService.HORARIO_PADRAO);
        assertThat(variaveis.get(7)).isEqualTo(WhatsAppConteudoService.PROFISSIONAL_PADRAO);
    }

    @Test
    void maisDeTresItensViraContagem() {
        List<SolicitacaoEspecialidade> itens = new ArrayList<>();
        for (String nome : List.of("Hemograma", "Glicemia", "Ureia", "Creatinina")) {
            itens.add(item(nome, false));
        }

        assertThat(service.confirmacao(paciente("Maria"), agendamento(), itens).get(1))
                .isEqualTo("4 exames/procedimentos");
    }

    @Test
    void itemSensivelEscondeONomeDeTodosEOLocal() {
        List<SolicitacaoEspecialidade> itens = List.of(item("Hemograma", false), item("Anti-HIV", true));

        List<String> confirmacao = service.confirmacao(paciente("Maria"), agendamento(), itens);
        List<String> lembrete = service.lembrete(paciente("Maria"), agendamento(), itens);

        assertThat(confirmacao.get(1)).isEqualTo(WhatsAppConteudoService.ATENDIMENTO_SENSIVEL);
        assertThat(confirmacao.get(2)).isEqualTo(WhatsAppConteudoService.LOCAL_PADRAO);
        assertThat(String.join("|", confirmacao)).doesNotContain("HIV", "Hemograma", "Policlínica");
        assertThat(String.join("|", lembrete)).doesNotContain("HIV", "Hemograma", "Policlínica");
    }

    @Test
    void itemSensivelUnicoEscondeTambemOProfissional() {
        SolicitacaoEspecialidade psiquiatria = item("Psiquiatria", true);
        psiquiatria.setHoraAgendada(LocalTime.of(14, 0));
        psiquiatria.setProfissionalExecutante(profissional("Dr. Fulano Psiquiatra"));

        List<String> variaveis = service.confirmacao(paciente("Maria"), agendamento(), List.of(psiquiatria));

        assertThat(variaveis).containsExactly(
                "Maria",
                WhatsAppConteudoService.ATENDIMENTO_SENSIVEL,
                WhatsAppConteudoService.LOCAL_PADRAO,
                "12/10/2026",
                "segunda-feira",
                "14:00",
                "Manhã",
                WhatsAppConteudoService.PROFISSIONAL_PADRAO);
    }

    @Test
    void localSemNomeNaoViraTextoNull() {
        AgendamentoSolicitacao a = agendamento();
        a.getLocalAgendamento().setNomeLocal(null);

        assertThat(service.confirmacao(paciente("Maria"), a, List.of(item("Cardiologia", false))).get(2))
                .isEqualTo(WhatsAppConteudoService.LOCAL_PADRAO);
    }

    @Test
    void camposAusentesViramTextoSubstituto_nuncaVazio() {
        AgendamentoSolicitacao semNada = new AgendamentoSolicitacao();
        semNada.setDataAgendada(LocalDate.of(2026, 10, 17));
        SolicitacaoEspecialidade legado = new SolicitacaoEspecialidade(); // item antigo, sem especialidade vinculada

        List<String> variaveis = service.confirmacao(paciente("   "), semNada, List.of(legado));

        assertThat(variaveis).containsExactly(
                "Paciente",
                WhatsAppConteudoService.ATENDIMENTO_PADRAO,
                WhatsAppConteudoService.LOCAL_PADRAO,
                "17/10/2026",
                "sábado",
                WhatsAppConteudoService.HORARIO_PADRAO,
                WhatsAppConteudoService.TURNO_PADRAO,
                WhatsAppConteudoService.PROFISSIONAL_PADRAO);
        assertThat(variaveis).allSatisfy(v -> assertThat(v).isNotBlank());
    }

    @Test
    void localLegadoPorEnumETurnoNaoInformado() {
        AgendamentoSolicitacao a = agendamento();
        a.setLocalAgendamento(null);
        a.setLocalAgendado(LocalDeAgendamentoEnum.HOSPITAL_IRMA_DULCE);
        a.setTurno(TurnoEnum.NAO_INFORMADO);

        List<String> variaveis = service.confirmacao(paciente("Maria"), a, List.of(item("Cardiologia", false)));

        assertThat(variaveis.get(2)).isEqualTo("HOSPITAL IRMA DULCE");
        assertThat(variaveis.get(6)).isEqualTo(WhatsAppConteudoService.TURNO_PADRAO);
    }

    @Test
    void valoresSaoLimposParaCaberEmVariavelDeTemplate() {
        SolicitacaoEspecialidade cardio = item("Cardiologia\n\tadulto     geral", false);
        cardio.setProfissionalExecutante(profissional("x".repeat(300)));

        List<String> variaveis = service.confirmacao(paciente("Maria"), agendamento(), List.of(cardio));

        assertThat(variaveis.get(1)).isEqualTo("Cardiologia adulto geral");
        assertThat(variaveis.get(7)).hasSize(120);
        assertThat(variaveis).allSatisfy(v -> assertThat(v).doesNotContain("\n", "\r", "\t", "  "));
    }

    @Test
    void cancelamentoNaoCitaEspecialidadeNemLocal() {
        assertThat(service.cancelamento(paciente("Maria Souza"), LocalDate.of(2026, 10, 12)))
                .containsExactly("Maria", WhatsAppConteudoService.TIPO_CONSULTA_EXAME, "12/10/2026", "segunda-feira");
    }

    @Test
    void nenhumaMensagemLevaCpfCnsObservacaoOuQuemAgendou() {
        SolicitacaoEspecialidade cardio = item("Cardiologia", false);
        Solicitacao paciente = paciente("Maria Souza");
        AgendamentoSolicitacao agendamento = agendamento();

        String tudo = String.join("|", service.confirmacao(paciente, agendamento, List.of(cardio)))
                + "|" + String.join("|", service.lembrete(paciente, agendamento, List.of(cardio)))
                + "|" + String.join("|", service.cancelamento(paciente, agendamento.getDataAgendada()));

        assertThat(tudo).doesNotContain(CPF, "12345678909", CNS, OBSERVACAO, "HIV", OPERADOR, "Josefa",
                "Souza", "Mae da Paciente", "Rua do Paciente");
    }
}
