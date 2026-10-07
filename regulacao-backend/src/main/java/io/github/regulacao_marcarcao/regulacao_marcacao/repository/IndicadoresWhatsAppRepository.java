package io.github.regulacao_marcarcao.regulacao_marcacao.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.WhatsAppMensagem;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.WhatsAppIndicadorProjections.SituacaoDoAviso;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.WhatsAppIndicadorProjections.TelefoneInvalido;

/**
 * Agregacoes dos indicadores gerenciais de WhatsApp. Somente leitura e somente
 * contagens: nada aqui devolve telefone, id de mensagem, id de agendamento ou
 * de paciente.
 *
 * <p>Separado de {@link WhatsAppMensagemRepository} (fila de envio e painel do
 * ADMIN) para nao misturar leitura gerencial com o que grava e reserva mensagem.
 *
 * <p>O periodo e pelo instante em que a mensagem entrou na fila
 * ({@code criado_em}, {@code timestamptz}); os limites chegam ja convertidos do
 * dia do municipio. A unidade e a da solicitacao; mensagem cuja solicitacao foi
 * removida fica sem unidade.
 */
public interface IndicadoresWhatsAppRepository extends Repository<WhatsAppMensagem, Long> {

    /**
     * Alcance do aviso de agendamento. A unidade de contagem e o AGENDAMENTO
     * ({@code alvo_id}), nao a mensagem: o reenvio manual cria outra linha para
     * o mesmo agendamento e nao pode contar duas vezes.
     *
     * <p>Remarcar e excluir e criar OUTRO agendamento: a confirmacao do antigo
     * fica no {@code alvo_id} antigo (sem FK, sobrevive a exclusao) e a
     * remarcacao vai no novo. Os dois entram no universo — o antigo, em geral,
     * como AGENDAMENTO_REMOVIDO ou ja alcancado. A tela avisa.
     *
     * <p>Cada agendamento cai em uma so situacao, na ordem: chegou ao aparelho
     * (ENTREGUE ou LIDO em qualquer mensagem); aceita pela Meta sem confirmacao
     * de entrega; ainda na fila; falhou; ou nao enviada, com o motivo da
     * mensagem mais recente.
     */
    @Query(value = """
            WITH por_agendamento AS (
                SELECT w.alvo_id,
                       BOOL_OR(w.resultado IN ('ENTREGUE', 'LIDO'))    AS alcancado,
                       BOOL_OR(w.resultado = 'LIDO')                   AS lido,
                       BOOL_OR(w.resultado = 'ENVIADO')                AS aceita,
                       BOOL_OR(w.resultado IN ('PENDENTE', 'ENVIANDO')) AS pendente,
                       (ARRAY_AGG(w.resultado ORDER BY w.criado_em DESC, w.id DESC))[1] AS ultimo_resultado,
                       (ARRAY_AGG(w.motivo ORDER BY w.criado_em DESC, w.id DESC))[1]    AS ultimo_motivo
                FROM whatsapp_mensagem w
                LEFT JOIN solicitacao s ON s.id = w.solicitacao_id
                WHERE w.alvo_tipo = 'CONSULTA_EXAME'
                  AND w.tipo IN ('CONFIRMACAO', 'REMARCACAO')
                  AND w.criado_em >= :inicio AND w.criado_em < :fim
                  AND (CAST(:unidadeId AS bigint) IS NULL OR s.unidade_id = :unidadeId)
                GROUP BY w.alvo_id
            )
            SELECT CASE
                       WHEN alcancado THEN 'ALCANCADO'
                       WHEN aceita THEN 'ACEITA'
                       WHEN pendente THEN 'PENDENTE'
                       WHEN ultimo_resultado = 'FALHOU' THEN 'FALHOU'
                       ELSE COALESCE(ultimo_motivo, 'SEM_MOTIVO')
                   END AS situacao,
                   COUNT(*) AS total,
                   COUNT(*) FILTER (WHERE lido) AS lidos
            FROM por_agendamento
            GROUP BY 1
            """, nativeQuery = true)
    List<SituacaoDoAviso> alcancePorSituacao(
            @Param("unidadeId") Long unidadeId,
            @Param("inicio") Instant inicio,
            @Param("fim") Instant fim);

    /**
     * Telefone invalido ou ausente, em PACIENTES, uma linha por unidade.
     *
     * <p>O denominador sao os pacientes cujo telefone chegou a ser avaliado:
     * mensagem que saiu (ENVIADO, ENTREGUE, LIDO, FALHOU) ou que parou num
     * motivo decidido DEPOIS da checagem do telefone. Mensagem barrada antes
     * (envio desligado, opt-out, data passada...) nao diz nada sobre o telefone
     * e fica fora — senao o envio desligado apareceria como "0% invalido".
     *
     * <p>Mensagem sem solicitacao conta como um paciente proprio.
     */
    @Query(value = """
            SELECT s.unidade_id AS id, MIN(u.nome) AS nome,
                   COUNT(DISTINCT COALESCE(w.solicitacao_id, -w.id)) AS avaliados,
                   COUNT(DISTINCT COALESCE(w.solicitacao_id, -w.id))
                       FILTER (WHERE w.motivo IN ('TELEFONE_INVALIDO', 'SEM_TELEFONE')) AS invalidos
            FROM whatsapp_mensagem w
            LEFT JOIN solicitacao s ON s.id = w.solicitacao_id
            LEFT JOIN unidade u ON u.id = s.unidade_id
            WHERE w.criado_em >= :inicio AND w.criado_em < :fim
              AND (CAST(:unidadeId AS bigint) IS NULL OR s.unidade_id = :unidadeId)
              AND (w.resultado IN ('ENVIADO', 'ENTREGUE', 'LIDO', 'FALHOU')
                   OR w.motivo IN ('TELEFONE_INVALIDO', 'SEM_TELEFONE', 'FORA_DA_LISTA_DE_TESTE', 'LIMITE_DIARIO'))
            GROUP BY s.unidade_id
            """, nativeQuery = true)
    List<TelefoneInvalido> telefoneInvalidoPorUnidade(
            @Param("unidadeId") Long unidadeId,
            @Param("inicio") Instant inicio,
            @Param("fim") Instant fim);
}
