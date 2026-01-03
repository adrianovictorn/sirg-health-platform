package io.github.regulacao_marcarcao.regulacao_marcacao.service;

import java.util.List;

import org.springframework.stereotype.Service;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.especialidade.EspecialidadeDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Especialidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.ItemCategoria;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.EspecialidadeRepository;

@Service
public class EspecialidadeService {
    

    private final EspecialidadeRepository especialidadeRepository;

    public EspecialidadeService(EspecialidadeRepository especialidadeRepository) {
        this.especialidadeRepository = especialidadeRepository;
    }

    public List<EspecialidadeDTO> listarEspecialidades(){
       List<Especialidade> especialidades =  especialidadeRepository.findByCategoriaAndAtivoTrue(ItemCategoria.EXAME_OU_PROCEDIMENTO);
       List<EspecialidadeDTO> listaDtos = especialidades.stream().map(EspecialidadeDTO::fromEntity).toList();

       return listaDtos;
    }
}
