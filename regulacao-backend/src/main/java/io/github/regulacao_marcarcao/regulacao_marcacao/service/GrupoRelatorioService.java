package io.github.regulacao_marcarcao.regulacao_marcacao.service;

import java.util.List;

import org.springframework.stereotype.Service;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.grupo_relatorio.GrupoRelatorioCreateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.grupo_relatorio.GrupoRelatorioUpdateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.grupo_relatorio.GrupoRelatorioViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.GrupoRelatorio;
import io.github.regulacao_marcarcao.regulacao_marcacao.mapper.GrupoRelatorioMapper;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.CotaUnidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.GrupoRelatorioRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UnidadeRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;

@Service
public class GrupoRelatorioService {

    private final GrupoRelatorioRepository grupoRelatorioRepository;
    private final GrupoRelatorioMapper mapper;
    private final UnidadeRepository unidadeRepository;
    private final CotaUnidadeRepository cotaUnidadeRepository;

    public GrupoRelatorioService(GrupoRelatorioRepository grupoRelatorioRepository,
            GrupoRelatorioMapper mapper,
            UnidadeRepository unidadeRepository,
            CotaUnidadeRepository cotaUnidadeRepository) {
        this.grupoRelatorioRepository = grupoRelatorioRepository;
        this.mapper = mapper;
        this.unidadeRepository = unidadeRepository;
        this.cotaUnidadeRepository = cotaUnidadeRepository;
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

   public void ativarOrInativarGrupo(Long id){
    GrupoRelatorio grupoRelatorioExistente = grupoRelatorioRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("Grupo Relatório não encontrado !"));
    grupoRelatorioExistente.setAtivo(!grupoRelatorioExistente.getAtivo());

    grupoRelatorioRepository.save(grupoRelatorioExistente);
    
   }

   /**
    * Exclui um grupo.
    *
    * Desde a V82 o mesmo registro de grupo agrupa Especialidades (para relatório)
    * e Unidades (para cota coletiva). Sem esta verificação, excluir um grupo de
    * relatório apagaria junto a configuração de cotas atrelada a ele — uma perda
    * silenciosa de dado que não existia antes de o grupo passar a ter esse papel.
    * A FK de `cota_unidade` é RESTRICT, então o banco também barra; aqui a recusa
    * vira uma mensagem que explica o que fazer.
    */
   public void deletarGrupoRelatorio(Long id){
     GrupoRelatorio grupoRelatorioExistente = grupoRelatorioRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("Grupo Relatório não encontrado !"));

     // O grupo pode estar em dois papeis numa cota: titular (pool de unidades) ou
     // escopo (conjunto de especialidades). Ambos impedem a exclusao.
     long cotasComoTitular = cotaUnidadeRepository.findByGrupoUnidadesId(id).size();
     long cotasComoEscopo = cotaUnidadeRepository.findByGrupoEspecialidadesId(id).size();
     long cotas = cotasComoTitular + cotasComoEscopo;
     if (cotas > 0) {
       throw new IllegalStateException(
           "Este grupo possui " + cotas + " cota(s) cadastrada(s) e não pode ser excluído. "
         + "Exclua ou transfira as cotas do grupo antes de removê-lo.");
     }

     long unidadesVinculadas = unidadeRepository.findByGrupoRelatorioId(id).size();
     if (unidadesVinculadas > 0) {
       throw new IllegalStateException(
           "Este grupo possui " + unidadesVinculadas + " unidade(s) vinculada(s). "
         + "Desvincule as unidades (em Unidades) antes de excluir o grupo.");
     }

     grupoRelatorioRepository.delete(grupoRelatorioExistente);
   }

   
   
}

