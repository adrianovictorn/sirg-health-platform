package io.github.regulacao_marcarcao.regulacao_marcacao.dto.profissional;

import java.time.LocalDateTime;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.ProfissionalVinculo;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.OrigemVinculoEnum;

/**
 * Um vinculo do profissional: onde atua e em que ocupacao.
 *
 * <p>Traz nome do estabelecimento e descricao do CBO junto do id para a tela nao
 * precisar de uma chamada por linha da lista.
 */
public record ProfissionalVinculoViewDTO(
        Long id,
        Long profissionalId,
        String profissionalNome,
        Long unidadeId,
        String unidadeNome,
        Long cboId,
        String cboCodigo,
        String cboDescricao,
        boolean ativo,
        OrigemVinculoEnum origem,
        LocalDateTime criadoEm) {

    public static ProfissionalVinculoViewDTO from(ProfissionalVinculo v) {
        return new ProfissionalVinculoViewDTO(
                v.getId(),
                v.getProfissional().getId(),
                v.getProfissional().getNome(),
                v.getUnidade().getId(),
                v.getUnidade().getNome(),
                v.getCbo() != null ? v.getCbo().getId() : null,
                v.getCbo() != null ? v.getCbo().getCodigo() : null,
                v.getCbo() != null ? v.getCbo().getDescricao() : null,
                v.isAtivo(),
                v.getOrigem(),
                v.getCriadoEm());
    }
}
