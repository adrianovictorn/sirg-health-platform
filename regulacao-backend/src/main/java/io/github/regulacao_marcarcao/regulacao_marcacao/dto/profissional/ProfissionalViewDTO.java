package io.github.regulacao_marcarcao.regulacao_marcacao.dto.profissional;

import java.time.LocalDateTime;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Profissional;

public record ProfissionalViewDTO(
        Long id,
        String nome,
        String conselho,
        String numeroRegistro,
        String especialidadeAtuacao,
        String telefone,
        Long unidadeId,
        String unidadeNome,
        boolean ativo,
        LocalDateTime criadoEm,
        LocalDateTime atualizadoEm) {

    public static ProfissionalViewDTO from(Profissional p) {
        return new ProfissionalViewDTO(
                p.getId(),
                p.getNome(),
                p.getConselho(),
                p.getNumeroRegistro(),
                p.getEspecialidadeAtuacao(),
                p.getTelefone(),
                p.getUnidade() != null ? p.getUnidade().getId() : null,
                p.getUnidade() != null ? p.getUnidade().getNome() : null,
                p.isAtivo(),
                p.getCriadoEm(),
                p.getAtualizadoEm());
    }
}
