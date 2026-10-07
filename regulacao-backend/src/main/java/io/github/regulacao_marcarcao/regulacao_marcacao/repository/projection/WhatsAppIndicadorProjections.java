package io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection;

/** Projecoes escalares dos indicadores gerenciais de WhatsApp ({@code IndicadoresWhatsAppRepository}). */
public final class WhatsAppIndicadorProjections {

    private WhatsAppIndicadorProjections() {
    }

    /**
     * Quantos agendamentos terminaram em cada situacao. {@code situacao} e
     * ALCANCADO, ACEITA, PENDENTE, FALHOU ou, para os nao enviados, o motivo.
     */
    public interface SituacaoDoAviso {
        String getSituacao();
        Long getTotal();
        Long getLidos();
    }

    /** Pacientes com telefone avaliado e, deles, os com telefone invalido ou ausente, de uma unidade. */
    public interface TelefoneInvalido {
        Long getId();
        String getNome();
        Long getAvaliados();
        Long getInvalidos();
    }
}
