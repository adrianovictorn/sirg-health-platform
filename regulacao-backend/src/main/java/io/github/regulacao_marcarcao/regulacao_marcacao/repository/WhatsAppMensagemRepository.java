package io.github.regulacao_marcarcao.regulacao_marcacao.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.WhatsAppMensagem;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.WhatsAppAlvoTipo;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.WhatsAppMotivoNaoEnvio;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.WhatsAppResultado;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.WhatsAppTipoMensagem;

public interface WhatsAppMensagemRepository
        extends JpaRepository<WhatsAppMensagem, Long>, JpaSpecificationExecutor<WhatsAppMensagem> {

    /** Proximas da fila, em lote pequeno: a tarefa agendada tem uma thread so. */
    List<WhatsAppMensagem> findTop20ByResultadoAndEnviarAposLessThanEqualOrderByIdAsc(
            WhatsAppResultado resultado, Instant agora);

    /** Reivindica a linha para envio. Devolve 0 se outra execucao ja a pegou. */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE WhatsAppMensagem m SET m.resultado = :para WHERE m.id = :id AND m.resultado = :de")
    int trocarResultado(@Param("id") Long id,
                        @Param("de") WhatsAppResultado de,
                        @Param("para") WhatsAppResultado para);

    /**
     * Tira da fila uma mensagem que AINDA esta pendente. Update condicional de
     * proposito: se a tarefa de envio ja a reivindicou, devolve 0 e nada muda —
     * um save() da entidade inteira sobrescreveria o ENVIANDO.
     */
    @Modifying
    @Query("UPDATE WhatsAppMensagem m SET m.resultado = 'NAO_ENVIADO', m.motivo = :motivo "
            + "WHERE m.id = :id AND m.resultado = 'PENDENTE'")
    int descartarPendente(@Param("id") Long id, @Param("motivo") WhatsAppMotivoNaoEnvio motivo);

    List<WhatsAppMensagem> findByResultado(WhatsAppResultado resultado);

    List<WhatsAppMensagem> findBySolicitacaoIdAndTipoAndResultado(
            Long solicitacaoId, WhatsAppTipoMensagem tipo, WhatsAppResultado resultado);

    List<WhatsAppMensagem> findByAlvoTipoAndAlvoIdAndResultado(
            WhatsAppAlvoTipo alvoTipo, Long alvoId, WhatsAppResultado resultado);

    Optional<WhatsAppMensagem> findByMetaMessageId(String metaMessageId);

    /** Mensagens aceitas pela Meta desde {@code inicio} — base do limite diario. */
    long countByEnviadoEmGreaterThanEqual(Instant inicio);

    /**
     * Enfileira um lembrete. {@code ON CONFLICT DO NOTHING} sobre a chave de
     * idempotencia: reinicio, tarefa repetida e "rodar agora" nao duplicam.
     *
     * @return 1 se enfileirou, 0 se ja existia lembrete para este agendamento e data
     */
    @Modifying
    @Query(value = """
            INSERT INTO whatsapp_mensagem
                (alvo_tipo, alvo_id, solicitacao_id, tipo, origem, resultado, data_referencia,
                 chave_idempotencia, solicitado_por_id, tentativas, criado_em, enviar_apos)
            VALUES
                ('CONSULTA_EXAME', :alvoId, :solicitacaoId, 'LEMBRETE', :origem, 'PENDENTE', :dataReferencia,
                 :chave, :solicitadoPorId, 0, now(), now())
            ON CONFLICT (chave_idempotencia) DO NOTHING
            """, nativeQuery = true)
    int enfileirarLembrete(@Param("alvoId") Long alvoId,
                           @Param("solicitacaoId") Long solicitacaoId,
                           @Param("origem") String origem,
                           @Param("dataReferencia") LocalDate dataReferencia,
                           @Param("chave") String chave,
                           @Param("solicitadoPorId") Long solicitadoPorId);

    @Query("SELECT m.resultado, m.motivo, COUNT(m) FROM WhatsAppMensagem m "
            + "WHERE m.criadoEm >= :inicio AND m.criadoEm < :fim GROUP BY m.resultado, m.motivo")
    List<Object[]> contarPorResultadoEMotivo(@Param("inicio") Instant inicio, @Param("fim") Instant fim);

    @Query("SELECT m.categoriaCobranca, COUNT(m) FROM WhatsAppMensagem m "
            + "WHERE m.criadoEm >= :inicio AND m.criadoEm < :fim AND m.cobravel = TRUE GROUP BY m.categoriaCobranca")
    List<Object[]> contarCobraveisPorCategoria(@Param("inicio") Instant inicio, @Param("fim") Instant fim);

    /** Soma das mensagens recebidas de pacientes no periodo (V104). */
    @Query(value = "SELECT COALESCE(SUM(quantidade), 0) FROM whatsapp_recebidas_dia WHERE data BETWEEN :de AND :ate",
            nativeQuery = true)
    long somarRecebidas(@Param("de") LocalDate de, @Param("ate") LocalDate ate);

    @Modifying
    @Query(value = """
            INSERT INTO whatsapp_recebidas_dia (data, quantidade) VALUES (:data, :quantidade)
            ON CONFLICT (data) DO UPDATE SET quantidade = whatsapp_recebidas_dia.quantidade + EXCLUDED.quantidade
            """, nativeQuery = true)
    void somarRecebidasDoDia(@Param("data") LocalDate data, @Param("quantidade") int quantidade);
}
