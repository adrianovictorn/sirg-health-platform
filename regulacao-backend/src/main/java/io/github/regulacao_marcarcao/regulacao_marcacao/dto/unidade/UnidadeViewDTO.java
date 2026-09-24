package io.github.regulacao_marcarcao.regulacao_marcacao.dto.unidade;

import java.time.LocalDateTime;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Unidade;

public record UnidadeViewDTO(
        Long id,
        String nome,
        String codigo,
        String cnes,
        String telefone,
        String endereco,
        Long grupoRelatorioId,
        String grupoRelatorioNome,
        boolean ativo,
        LocalDateTime criadoEm) {

    public static UnidadeViewDTO from(Unidade u) {
        return new UnidadeViewDTO(
                u.getId(),
                u.getNome(),
                u.getCodigo(),
                u.getCnes(),
                u.getTelefone(),
                u.getEndereco(),
                u.getGrupoRelatorio() != null ? u.getGrupoRelatorio().getId() : null,
                u.getGrupoRelatorio() != null ? u.getGrupoRelatorio().getNome() : null,
                u.isAtivo(),
                u.getCriadoEm());
    }
}
