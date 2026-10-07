package io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums;

/** Por que uma mensagem nao saiu (resultado NAO_ENVIADO). */
public enum WhatsAppMotivoNaoEnvio {
    SEM_TELEFONE,
    TELEFONE_INVALIDO,
    OPT_OUT,
    ENVIO_DESLIGADO,
    NAO_CONFIGURADO,
    LIMITE_DIARIO,
    FORA_DA_LISTA_DE_TESTE,
    AGENDAMENTO_REMOVIDO,
    SUBSTITUIDO_POR_REMARCACAO,
    PACIENTE_DE_OUTRO_MUNICIPIO,
    DATA_PASSADA
}
