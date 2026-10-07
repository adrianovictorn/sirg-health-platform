package io.github.regulacao_marcarcao.regulacao_marcacao.mapper;

import static org.mapstruct.NullValuePropertyMappingStrategy.IGNORE;

import java.util.List;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.especialidade.EspecialidadeCreateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.especialidade.EspecialidadeSimpleViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.especialidade.EspecialidadeUpdateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.especialidade.EspecialidadeViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Especialidade;

@Mapper(componentModel = "spring")
public interface EspecialidadeMapper {
    
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "ativo", constant = "true")
    @Mapping(target = "grupoRelatorio", ignore = true)
    // Custo (V105): so entra por CustoEspecialidadeService, nunca pelo cadastro.
    @Mapping(target = "valorUnitario", ignore = true)
    @Mapping(target = "codigoSus", ignore = true)
    @Mapping(target = "valorOrigem", ignore = true)
    @Mapping(target = "valorAtualizadoEm", ignore = true)
    @Mapping(target = "valorAtualizadoPor", ignore = true)
    Especialidade toEntity(EspecialidadeCreateDTO dto);

    EspecialidadeSimpleViewDTO toSimpleViewDTO(Especialidade especialidade);
    EspecialidadeViewDTO toViewDTO(Especialidade especialidade);
    List<EspecialidadeViewDTO> toViewDTOList(List<Especialidade> especialidades);

    @BeanMapping(nullValuePropertyMappingStrategy = IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "grupoRelatorio", ignore = true)
    @Mapping(target = "valorUnitario", ignore = true)
    @Mapping(target = "codigoSus", ignore = true)
    @Mapping(target = "valorOrigem", ignore = true)
    @Mapping(target = "valorAtualizadoEm", ignore = true)
    @Mapping(target = "valorAtualizadoPor", ignore = true)
    void updateFromDto(EspecialidadeUpdateDTO dto, @MappingTarget Especialidade entity);
}
