package io.github.regulacao_marcarcao.regulacao_marcacao.dto.grupo_relatorio;

import java.util.List;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.especialidade.EspecialidadeSimpleViewDTO;

public record GrupoRelatorioViewDTO(
    Long id,
    String codigo,
    String nome,
    List<EspecialidadeSimpleViewDTO> especialidades,
    Boolean ativo
) {
   
} 
