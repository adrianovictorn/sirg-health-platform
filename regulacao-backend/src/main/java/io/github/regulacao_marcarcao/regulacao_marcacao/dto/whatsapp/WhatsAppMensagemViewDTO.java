package io.github.regulacao_marcarcao.regulacao_marcacao.dto.whatsapp;

import java.time.Instant;
import java.time.LocalDate;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.WhatsAppMensagem;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.WhatsAppMotivoNaoEnvio;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.WhatsAppOrigem;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.WhatsAppResultado;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.WhatsAppTipoMensagem;

/**
 * Linha da lista de operacao. Sem nome e sem telefone inteiro: a ficha do
 * paciente e alcancada pelo {@code solicitacaoId}, com o controle de acesso dela.
 */
public record WhatsAppMensagemViewDTO(
        Long id,
        Long agendamentoId,
        Long solicitacaoId,
        WhatsAppTipoMensagem tipo,
        WhatsAppOrigem origem,
        WhatsAppResultado resultado,
        WhatsAppMotivoNaoEnvio motivo,
        LocalDate dataReferencia,
        String telefoneFinal,
        int tentativas,
        String erroCodigo,
        Boolean cobravel,
        String categoriaCobranca,
        Instant criadoEm,
        Instant enviadoEm,
        Instant entregueEm,
        Instant lidoEm,
        Instant falhouEm) {

    public static WhatsAppMensagemViewDTO from(WhatsAppMensagem m) {
        return new WhatsAppMensagemViewDTO(
                m.getId(),
                m.getAlvoId(),
                m.getSolicitacaoId(),
                m.getTipo(),
                m.getOrigem(),
                m.getResultado(),
                m.getMotivo(),
                m.getDataReferencia(),
                m.getTelefoneFinal(),
                m.getTentativas(),
                m.getErroCodigo(),
                m.getCobravel(),
                m.getCategoriaCobranca(),
                m.getCriadoEm(),
                m.getEnviadoEm(),
                m.getEntregueEm(),
                m.getLidoEm(),
                m.getFalhouEm());
    }
}
