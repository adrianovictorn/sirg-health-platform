package io.github.regulacao_marcarcao.regulacao_marcacao.service;

import java.util.List;

import org.springframework.stereotype.Service;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.grupo_relatorio.GrupoRelatorioCreateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.grupo_relatorio.GrupoRelatorioUpdateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.grupo_relatorio.GrupoRelatorioViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.GrupoRelatorio;
import io.github.regulacao_marcarcao.regulacao_marcacao.mapper.GrupoRelatorioMapper;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.GrupoRelatorioRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;

@Service
public class GrupoRelatorioService {

    private final GrupoRelatorioRepository grupoRelatorioRepository;
    private final GrupoRelatorioMapper mapper;

    public GrupoRelatorioService(GrupoRelatorioRepository grupoRelatorioRepository,
            GrupoRelatorioMapper mapper) {
        this.grupoRelatorioRepository = grupoRelatorioRepository;
        this.mapper = mapper;
    }

   public GrupoRelatorioViewDTO criarGrupo(GrupoRelatorioCreateDTO dto){
        GrupoRelatorio entity = mapper.toEntity(dto);
        GrupoRelatorio salvo = grupoRelatorioRepository.save(entity);
        return mapper.toViewDTO(salvo);
   }

   public List<GrupoRelatorioViewDTO> listarGrupos(){
        List<GrupoRelatorio> lista = grupoRelatorioRepository.findAll();
        return mapper.toViewDTOList(lista);
   }  

   @Transactional
   public GrupoRelatorioViewDTO atualizarGrupo(Long id, GrupoRelatorioUpdateDTO dto){
     GrupoRelatorio grupoRelatorioExistente = grupoRelatorioRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("Grupo de Relatório não encontrado !"));
     mapper.updateFromDto(dto, grupoRelatorioExistente);
     GrupoRelatorio salvo = grupoRelatorioRepository.save(grupoRelatorioExistente);
     return mapper.toViewDTO(salvo);
   }

   public void deletarGrupoRelatorio(Long id){
     GrupoRelatorio grupoRelatorioExistente = grupoRelatorioRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("Grupo Relatório não encontrado !"));
     grupoRelatorioRepository.delete(grupoRelatorioExistente);
   }
   
}

