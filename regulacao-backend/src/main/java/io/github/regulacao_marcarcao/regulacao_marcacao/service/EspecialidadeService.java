package io.github.regulacao_marcarcao.regulacao_marcacao.service;

import java.util.List;
import java.util.Locale;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.especialidade.EspecialidadeCreateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.especialidade.EspecialidadeUpdateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.especialidade.EspecialidadeViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Especialidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.GrupoRelatorio;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.ItemCategoria;
import io.github.regulacao_marcarcao.regulacao_marcacao.mapper.EspecialidadeMapper;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.EspecialidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.GrupoRelatorioRepository;
import jakarta.persistence.EntityNotFoundException;

@Service
public class EspecialidadeService {
    

    private final EspecialidadeRepository especialidadeRepository;
    private final EspecialidadeMapper mapper;
    private final GrupoRelatorioRepository grupoRelatorioRepository;

    public EspecialidadeService(EspecialidadeRepository especialidadeRepository, EspecialidadeMapper mapper, GrupoRelatorioRepository grupoRelatorioRepository) {
        this.especialidadeRepository = especialidadeRepository;
        this.grupoRelatorioRepository = grupoRelatorioRepository;
        this.mapper = mapper;
    }

    public EspecialidadeViewDTO criarEspecialidade(EspecialidadeCreateDTO dto){
        Especialidade novaEspecialidade = mapper.toEntity(dto);
        if (dto.grupoRelatorioId() == null || dto.grupoRelatorioId() <= 0) {
            throw new IllegalArgumentException("Você precisa preencher o grupo do relatório");
        }

        if (dto.codigo().isBlank() || dto.codigo() == null) {
            String codigo = gerarCodigo(dto.nome());
            novaEspecialidade.setCodigo(codigo);
        }

        novaEspecialidade.setAtivo(true);
        GrupoRelatorio grupoRelatorio = grupoRelatorioRepository.findById(dto.grupoRelatorioId()).orElseThrow(() -> new EntityNotFoundException("Grupo de Relatório não encontrado !"));
        novaEspecialidade.setGrupoRelatorio(grupoRelatorio);
        System.out.println("OBJETO VINDO DO ENDPOINT" + dto);
        Especialidade especialidadeSalva = especialidadeRepository.save(novaEspecialidade);
        return mapper.toViewDTO(especialidadeSalva);
    }
    
    public Page<EspecialidadeViewDTO> listarTodasEspecialidadesPaginado(int page, int size, String nome){
        Pageable pagina = PageRequest.of(page, size, Sort.by("nome").ascending());
        if(nome == null || nome.isBlank()){
            return especialidadeRepository.findAll(pagina).map(mapper::toViewDTO);
        }
        return especialidadeRepository.findByNomeContainingIgnoreCase(nome, pagina).map(mapper::toViewDTO);
    }

    //Listar todas as Especialidades
    public List<EspecialidadeViewDTO> listarTodasEspecialidades(){
        List<Especialidade> especialidades = especialidadeRepository.findAll(Sort.by(Sort.Direction.ASC, "nome"));
        return mapper.toViewDTOList(especialidades);
        
    }

    //Atualizar Especialidade
    public EspecialidadeViewDTO atualizarEspecialidade(Long id, EspecialidadeUpdateDTO dto){
        Especialidade especialidadeExistente = especialidadeRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("Especialidade não encontrada !"));
        if (dto.grupoRelatorioId() == null || dto.grupoRelatorioId() <= 0) {
            throw new IllegalArgumentException("Grupo de Relatório Inválido !");
        }
        GrupoRelatorio grupoRelatorio = grupoRelatorioRepository.findById(dto.grupoRelatorioId()).orElseThrow(() -> new EntityNotFoundException("Grupo de Relatório não encontrado !"));
        if (grupoRelatorio.getAtivo() == false) {
            throw new IllegalArgumentException("O grupo relatório precisa estar ativo !");
        }
        especialidadeExistente.setGrupoRelatorio(grupoRelatorio);
        if (dto.ativo() == null) {
            throw new IllegalArgumentException("O campo Ativo precisar ser True or False");
        }
        mapper.updateFromDto(dto,especialidadeExistente);
        Especialidade especialidadeSalva = especialidadeRepository.save(especialidadeExistente);
        return mapper.toViewDTO(especialidadeSalva);
    }
    
    //Ativar ou Desativar - Especialidade por Id
    public EspecialidadeViewDTO ativarOrDesativarEspecialidade(Long id){
        Especialidade especialidadeExistente = especialidadeRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("Especialidade não encontrada !"));
        especialidadeExistente.setAtivo(!especialidadeExistente.getAtivo());
        Especialidade salva = especialidadeRepository.save(especialidadeExistente);
        return mapper.toViewDTO(salva);
    }
    
    //Listar por Categoria = ESPECIALIDADE_MEDICA
    public List<EspecialidadeViewDTO> listarEspecialidadesMedicas(){
        List<Especialidade> especialidadesMedicas = especialidadeRepository.findByCategoriaAndAtivoTrue(ItemCategoria.ESPECIALIDADE_MEDICA);
        return mapper.toViewDTOList(especialidadesMedicas);
    }
    
    //Listar por Categoria = EXAME_OU_PROCEDIMENTO
    public List<EspecialidadeViewDTO> listarEspecialidadesExames(){
       List<Especialidade> especialidades =  especialidadeRepository.findByCategoriaAndAtivoTrue(ItemCategoria.EXAME_OU_PROCEDIMENTO);
       return mapper.toViewDTOList(especialidades);
    }


    private String gerarCodigo(String nome) {
        String base = nome == null ? "ESPECIALIDADE" : nome;
        return base.trim().toUpperCase(Locale.ROOT)
        .replace('Á','A').replace('À','A').replace('Ã','A').replace('Â','A')
                .replace('É','E').replace('Ê','E')
                .replace('Í','I')
                .replace('Ó','O').replace('Õ','O').replace('Ô','O')
                .replace('Ú','U')
                .replace('Ç','C')
                .replaceAll("[^A-Z0-9]+","_");
    }


}
