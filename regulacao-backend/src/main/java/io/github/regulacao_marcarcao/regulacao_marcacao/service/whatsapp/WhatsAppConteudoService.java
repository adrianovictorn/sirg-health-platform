package io.github.regulacao_marcarcao.regulacao_marcacao.service.whatsapp;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Component;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.AgendamentoSolicitacao;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Profissional;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Solicitacao;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.SolicitacaoEspecialidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.TurnoEnum;

/**
 * Monta as variaveis dos templates do WhatsApp, na ordem aprovada na Meta.
 *
 * <p>O que NAO entra, de proposito: CPF, CNS, quem agendou e o texto de
 * observacoes do operador (pode conter informacao clinica ou recado interno).
 * Este service nem le esses campos.
 *
 * <p>Especialidade marcada como sensivel nao e citada, nem o local e o
 * profissional — os dois podem revelar o servico tanto quanto o nome.
 *
 * <p>Variavel de template nao aceita valor vazio nem quebra de linha: todo
 * campo ausente vira um texto substituto e todo valor passa por
 * {@link #limpar(String, String)}.
 */
@Component
public class WhatsAppConteudoService {

    static final String ATENDIMENTO_SENSIVEL = "atendimento especializado";
    static final String ATENDIMENTO_PADRAO = "atendimento agendado";
    static final String LOCAL_PADRAO = "local informado no comprovante";
    static final String HORARIO_PADRAO = "conforme o turno";
    static final String TURNO_PADRAO = "não informado";
    static final String PROFISSIONAL_PADRAO = "a definir pela unidade";
    static final String TIPO_CONSULTA_EXAME = "consulta ou exame";

    private static final int TAMANHO_MAXIMO = 120;
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");
    private static final Map<DayOfWeek, String> DIAS = Map.of(
            DayOfWeek.MONDAY, "segunda-feira",
            DayOfWeek.TUESDAY, "terça-feira",
            DayOfWeek.WEDNESDAY, "quarta-feira",
            DayOfWeek.THURSDAY, "quinta-feira",
            DayOfWeek.FRIDAY, "sexta-feira",
            DayOfWeek.SATURDAY, "sábado",
            DayOfWeek.SUNDAY, "domingo");

    /** Ordem: nome, atendimento, local, data, dia da semana, horario, turno, profissional. */
    public List<String> confirmacao(Solicitacao solicitacao, AgendamentoSolicitacao agendamento,
                                    List<SolicitacaoEspecialidade> itens) {
        LocalDate data = agendamento.getDataAgendada();
        return List.of(
                primeiroNome(solicitacao),
                atendimento(itens),
                local(agendamento, itens),
                DATA.format(data),
                diaDaSemana(data),
                horario(itens),
                turno(agendamento.getTurno()),
                profissional(itens));
    }

    /** Ordem: nome, data, dia da semana, atendimento, local, horario, turno, profissional. */
    public List<String> lembrete(Solicitacao solicitacao, AgendamentoSolicitacao agendamento,
                                 List<SolicitacaoEspecialidade> itens) {
        LocalDate data = agendamento.getDataAgendada();
        return List.of(
                primeiroNome(solicitacao),
                DATA.format(data),
                diaDaSemana(data),
                atendimento(itens),
                local(agendamento, itens),
                horario(itens),
                turno(agendamento.getTurno()),
                profissional(itens));
    }

    /**
     * Ordem: nome, tipo, data, dia da semana. Nao cita a especialidade: o
     * agendamento ja foi apagado quando esta mensagem e montada.
     */
    public List<String> cancelamento(Solicitacao solicitacao, LocalDate data) {
        return List.of(primeiroNome(solicitacao), TIPO_CONSULTA_EXAME, DATA.format(data), diaDaSemana(data));
    }

    /** So o primeiro nome: o telefone costuma ser compartilhado na familia. */
    private String primeiroNome(Solicitacao solicitacao) {
        String nome = limpar(solicitacao.getNomePaciente(), "paciente");
        String primeiro = nome.split(" ")[0];
        return primeiro.substring(0, 1).toUpperCase(Locale.ROOT) + primeiro.substring(1).toLowerCase(Locale.ROOT);
    }

    private boolean temItemSensivel(List<SolicitacaoEspecialidade> itens) {
        return itens.stream()
                .map(SolicitacaoEspecialidade::getEspecialidadeSolicitada)
                .filter(Objects::nonNull)
                .anyMatch(e -> Boolean.TRUE.equals(e.getSensivel()));
    }

    private String atendimento(List<SolicitacaoEspecialidade> itens) {
        if (temItemSensivel(itens)) {
            return ATENDIMENTO_SENSIVEL;
        }
        List<String> nomes = itens.stream()
                .map(SolicitacaoEspecialidade::getEspecialidadeSolicitada)
                .filter(Objects::nonNull)
                .map(e -> e.getNome())
                .filter(n -> n != null && !n.isBlank())
                .toList();
        if (nomes.isEmpty()) {
            return ATENDIMENTO_PADRAO;
        }
        if (nomes.size() > 3) {
            return nomes.size() + " exames/procedimentos";
        }
        return limpar(String.join(", ", nomes), ATENDIMENTO_PADRAO);
    }

    /** Mesma origem do comprovante ({@code AgendamentoSolicitacaoSimpleViewDTO.resolveLocal}). */
    private String local(AgendamentoSolicitacao agendamento, List<SolicitacaoEspecialidade> itens) {
        if (temItemSensivel(itens)) {
            return LOCAL_PADRAO;
        }
        if (agendamento.getLocalAgendamento() != null) {
            var local = agendamento.getLocalAgendamento();
            var cidade = local.getCidade();
            String nomeLocal = local.getNomeLocal();
            if (nomeLocal == null || nomeLocal.isBlank()) {
                return LOCAL_PADRAO;
            }
            return limpar(cidade != null && cidade.getNomeCidade() != null
                    ? nomeLocal + " - " + cidade.getNomeCidade() : nomeLocal, LOCAL_PADRAO);
        }
        if (agendamento.getLocalAgendado() != null) {
            return limpar(agendamento.getLocalAgendado().name().replace('_', ' '), LOCAL_PADRAO);
        }
        return LOCAL_PADRAO;
    }

    /** Como no comprovante, hora e profissional so fazem sentido com um unico item. */
    private String horario(List<SolicitacaoEspecialidade> itens) {
        if (itens.size() == 1 && itens.get(0).getHoraAgendada() != null) {
            return HORA.format(itens.get(0).getHoraAgendada());
        }
        return HORARIO_PADRAO;
    }

    private String turno(TurnoEnum turno) {
        if (turno == TurnoEnum.MANHA) {
            return "Manhã";
        }
        if (turno == TurnoEnum.TARDE) {
            return "Tarde";
        }
        return TURNO_PADRAO;
    }

    private String profissional(List<SolicitacaoEspecialidade> itens) {
        if (itens.size() != 1 || temItemSensivel(itens)) {
            return PROFISSIONAL_PADRAO;
        }
        SolicitacaoEspecialidade item = itens.get(0);
        Profissional profissional = item.getProfissionalExecutante() != null
                ? item.getProfissionalExecutante()
                : (item.getCotaUnidade() != null ? item.getCotaUnidade().getProfissionalExecutante() : null);
        return profissional == null ? PROFISSIONAL_PADRAO : limpar(profissional.getNome(), PROFISSIONAL_PADRAO);
    }

    private String diaDaSemana(LocalDate data) {
        return DIAS.get(data.getDayOfWeek());
    }

    /** Sem quebra de linha, sem espacos repetidos, nunca vazio, com tamanho limitado. */
    static String limpar(String valor, String substituto) {
        if (valor == null) {
            return substituto;
        }
        String limpo = valor.replaceAll("[\\r\\n\\t]", " ").replaceAll("\\s{2,}", " ").strip();
        if (limpo.isEmpty()) {
            return substituto;
        }
        return limpo.length() > TAMANHO_MAXIMO ? limpo.substring(0, TAMANHO_MAXIMO).strip() : limpo;
    }
}
