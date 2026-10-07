package io.github.regulacao_marcarcao.regulacao_marcacao.entity;

import java.time.Instant;
import java.time.LocalDate;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.WhatsAppAlvoTipo;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.WhatsAppMotivoNaoEnvio;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.WhatsAppOrigem;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.WhatsAppResultado;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.WhatsAppTipoMensagem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Uma tentativa de envio pelo WhatsApp (V101). E a fila (linhas PENDENTE) e o
 * registro ao mesmo tempo.
 *
 * <p>Nao guarda texto nem telefone: o conteudo e remontado na hora do envio.
 * {@code alvoId} e {@code solicitacaoId} sao ids soltos, sem associacao JPA —
 * o agendamento sofre hard delete e esta linha precisa sobreviver a ele.
 */
@Entity
@Table(name = "whatsapp_mensagem")
@Getter
@Setter
@NoArgsConstructor
public class WhatsAppMensagem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "alvo_tipo", nullable = false, length = 30)
    private WhatsAppAlvoTipo alvoTipo;

    /** Id do agendamento. Pode nao existir mais. */
    @Column(name = "alvo_id", nullable = false)
    private Long alvoId;

    @Column(name = "solicitacao_id")
    private Long solicitacaoId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 30)
    private WhatsAppTipoMensagem tipo;

    @Enumerated(EnumType.STRING)
    @Column(name = "origem", nullable = false, length = 20)
    private WhatsAppOrigem origem;

    @Enumerated(EnumType.STRING)
    @Column(name = "resultado", nullable = false, length = 20)
    private WhatsAppResultado resultado;

    @Enumerated(EnumType.STRING)
    @Column(name = "motivo", length = 40)
    private WhatsAppMotivoNaoEnvio motivo;

    /** Data do atendimento a que a mensagem se refere. */
    @Column(name = "data_referencia", nullable = false)
    private LocalDate dataReferencia;

    /** Ids de solicitacao_especialidade do agendamento, separados por virgula. */
    @Column(name = "itens_ref", length = 500)
    private String itensRef;

    @Column(name = "chave_idempotencia", length = 120)
    private String chaveIdempotencia;

    @Column(name = "meta_message_id", length = 255)
    private String metaMessageId;

    @Column(name = "telefone_final", length = 4)
    private String telefoneFinal;

    @Column(name = "tentativas", nullable = false)
    private int tentativas;

    @Column(name = "erro_codigo", length = 40)
    private String erroCodigo;

    @Column(name = "cobravel")
    private Boolean cobravel;

    @Column(name = "categoria_cobranca", length = 40)
    private String categoriaCobranca;

    @Column(name = "solicitado_por_id")
    private Long solicitadoPorId;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "enviar_apos", nullable = false)
    private Instant enviarApos;

    @Column(name = "enviado_em")
    private Instant enviadoEm;

    @Column(name = "entregue_em")
    private Instant entregueEm;

    @Column(name = "lido_em")
    private Instant lidoEm;

    @Column(name = "falhou_em")
    private Instant falhouEm;
}
