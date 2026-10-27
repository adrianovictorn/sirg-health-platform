package io.github.regulacao_marcarcao.regulacao_marcacao.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.PaginaDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.grupo_relatorio.GrupoRelatorioCreateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.grupo_relatorio.GrupoRelatorioFiltroDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.grupo_relatorio.GrupoRelatorioListaDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.grupo_relatorio.GrupoRelatorioUpdateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.grupo_relatorio.GrupoRelatorioViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.GrupoRelatorio;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.GrupoRelatorioSpecification;
import io.github.regulacao_marcarcao.regulacao_marcacao.mapper.GrupoRelatorioMapper;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.CotaUnidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.GrupoRelatorioRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UnidadeExecutanteGrupoRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UnidadeRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;

@Service
public class GrupoRelatorioService {

    private final GrupoRelatorioRepository grupoRelatorioRepository;
    private final GrupoRelatorioMapper mapper;
    private final UnidadeRepository unidadeRepository;
    private final CotaUnidadeRepository cotaUnidadeRepository;
    private final UnidadeExecutanteGrupoRepository unidadeExecutanteGrupoRepository;

    public GrupoRelatorioService(GrupoRelatorioRepository grupoRelatorioRepository,
            GrupoRelatorioMapper mapper,
            UnidadeRepository unidadeRepository,
            CotaUnidadeRepository cotaUnidadeRepository,
            UnidadeExecutanteGrupoRepository unidadeExecutanteGrupoRepository) {
        this.grupoRelatorioRepository = grupoRelatorioRepository;
        this.mapper = mapper;
        this.unidadeRepository = unidadeRepository;
        this.cotaUnidadeRepository = cotaUnidadeRepository;
        this.unidadeExecutanteGrupoRepository = unidadeExecutanteGrupoRepository;
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

   /** Listagem paginada de /cadastrar/grupo-relatorio: nome crescente, desempate por id. */
   @Transactional
   public PaginaDTO<GrupoRelatorioListaDTO> listarPagina(GrupoRelatorioFiltroDTO bruto, int page, int size){
        PaginaDTO.validarPagina(page);
        int tamanho = PaginaDTO.limitarTamanho(size);

        String q = bruto == null || bruto.q() == null || bruto.q().isBlank() ? null : bruto.q().trim();
        String status = valorDeLista(bruto == null ? null : bruto.status(), "status", "ativo", "inativo");
        String hospital = valorDeLista(bruto == null ? null : bruto.direcionadoHospital(),
                "direcionadoHospital", "sim", "nao");

        Page<GrupoRelatorio> pagina = grupoRelatorioRepository.findAll(
                GrupoRelatorioSpecification.comFiltros(new GrupoRelatorioFiltroDTO(q, status, hospital)),
                PageRequest.of(page, tamanho, Sort.by("nome").ascending().and(Sort.by("id").ascending())));
        return PaginaDTO.de(pagina.getContent().stream().map(GrupoRelatorioListaDTO::from).toList(), page, tamanho,
                pagina.getTotalElements());
   }

   private static String valorDeLista(String valor, String campo, String... permitidos){
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String v = valor.trim();
        for (String p : permitidos) {
            if (p.equals(v)) {
                return v;
            }
        }
        throw new IllegalArgumentException("Valor inválido para " + campo + ": " + v);
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

     // V110: o grupo pode estar na parametrizacao de uma unidade executante (FK RESTRICT).
     if (unidadeExecutanteGrupoRepository.existsByGrupoRelatorioId(id)) {
       throw new IllegalStateException(
           "Este grupo está vinculado a uma unidade executante. "
         + "Remova-o da parametrização da executante (em Unidades) antes de excluí-lo.");
     }

     grupoRelatorioRepository.delete(grupoRelatorioExistente);
   }

   
   
}

