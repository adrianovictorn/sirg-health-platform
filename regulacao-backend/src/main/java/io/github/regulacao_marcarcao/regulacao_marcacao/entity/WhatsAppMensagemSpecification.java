package io.github.regulacao_marcarcao.regulacao_marcacao.entity;

import java.time.Instant;

import org.springframework.data.jpa.domain.Specification;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.WhatsAppResultado;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.WhatsAppTipoMensagem;

/** Filtros da lista de operacao do WhatsApp. Filtro nulo nao restringe. */
public class WhatsAppMensagemSpecification {

    public static Specification<WhatsAppMensagem> criadaEntre(Instant inicio, Instant fim) {
        return (root, query, cb) -> cb.and(
                cb.greaterThanOrEqualTo(root.get("criadoEm"), inicio),
                cb.lessThan(root.get("criadoEm"), fim));
    }

    public static Specification<WhatsAppMensagem> doTipo(WhatsAppTipoMensagem tipo) {
        return (root, query, cb) -> tipo == null ? cb.conjunction() : cb.equal(root.get("tipo"), tipo);
    }

    public static Specification<WhatsAppMensagem> comResultado(WhatsAppResultado resultado) {
        return (root, query, cb) -> resultado == null ? cb.conjunction() : cb.equal(root.get("resultado"), resultado);
    }
}
