package io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums;

/**
 * Situacao de uma mensagem. PENDENTE e ENVIANDO sao estados da fila; ENVIADO,
 * ENTREGUE e LIDO vem da Meta e so avancam; FALHOU e NAO_ENVIADO sao finais.
 */
public enum WhatsAppResultado {
    PENDENTE,
    ENVIANDO,
    ENVIADO,
    ENTREGUE,
    LIDO,
    FALHOU,
    NAO_ENVIADO
}
