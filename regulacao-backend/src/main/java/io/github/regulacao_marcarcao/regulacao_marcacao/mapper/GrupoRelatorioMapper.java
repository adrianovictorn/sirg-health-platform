package io.github.regulacao_marcarcao.regulacao_marcacao.mapper;

import java.util.List;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import static org.mapstruct.NullValuePropertyMappingStrategy.IGNORE;


import io.github.regulacao_marcarcao.regulacao_marcacao.dto.grupo_relatorio.GrupoRelatorioCreateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.grupo_relatorio.GrupoRelatorioUpdateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.grupo_relatorio.GrupoRelatorioViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.GrupoRelatorio;

@Mapper(componentModel = "spring", uses = EspecialidadeMapper.class)
public interface GrupoRelatorioMapper {
    
    
    @BeanMapping(nullValuePropertyMappingStrategy = IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "ativo", constant = "true")
    @Mapping(target = "especialidades", ignore = true)
    GrupoRelatorio toEntity(GrupoRelatorioCreateDTO dto);
    
    GrupoRelatorioViewDTO toViewDTO(GrupoRelatorio entity);

    List<GrupoRelatorioViewDTO> toViewDTOList(List<GrupoRelatorio> entities);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "especialidades", ignore = true)
    void updateFromDto(GrupoRelatorioUpdateDTO dto, @MappingTarget GrupoRelatorio entity);

}
