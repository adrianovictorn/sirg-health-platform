package io.github.regulacao_marcarcao.regulacao_marcacao.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.agendamentoDTO.ObservacaoRequestDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.solicitacaoEspecialidadeDTO.EspecialidadeUpdateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.solicitacaoEspecialidadeDTO.EspecialidadesStatusUpdateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.solicitacaoEspecialidadeDTO.SolicitacaoEspecialidadeViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.AgendamentoSolicitacao;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.SolicitacaoEspecialidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.StatusDaMarcacao;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.AgendamentoSolicitacaoRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.SolicitacaoEspecialidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.PainelEspecialidadeProjection;

import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SolicitacaoEspecialidadeService {
    
    private final AgendamentoSolicitacaoRepository agendamentoRepository;
    private final SolicitacaoEspecialidadeRepository especialidadeRepository;

    public SolicitacaoEspecialidadeViewDTO atualizarStatusEspecialidade(EspecialidadeUpdateDTO dto, Long id){
        SolicitacaoEspecialidade especialidade = especialidadeRepository.findById(id).orElseThrow(() -> new RuntimeException("Especialidade não encontrada!"));
        
        if (dto.prioridade() != null) {
            especialidade.setPrioridade(dto.prioridade());
        }

        if (dto.status() != null) {
            especialidade.setStatus(dto.status());
        }

        SolicitacaoEspecialidade especialidadeAtualizada = especialidadeRepository.save(especialidade);
        SolicitacaoEspecialidadeViewDTO viewDTO = SolicitacaoEspecialidadeViewDTO.fromSolicitacaoEspecialidade(especialidadeAtualizada);
        return viewDTO;


    }

   @Transactional // Garante que ou tudo funciona, ou nada é salvo no banco
    public void atualizarStatusEspecialidadesEmLote(EspecialidadesStatusUpdateDTO dto) {
        // Parte 1: Atualizar o status das especialidades
        if (dto.especialidadeIds() != null && !dto.especialidadeIds().isEmpty()) {
            List<SolicitacaoEspecialidade> especialidades = especialidadeRepository.findAllById(dto.especialidadeIds());
            for (SolicitacaoEspecialidade especialidade : especialidades) {
                especialidade.setStatus(dto.status());
            }
            especialidadeRepository.saveAll(especialidades);
        }

        // Parte 2: Atualizar a observação no agendamento
        // Verificamos se foi passado um agendamentoId e uma observação
        if (dto.agendamentoId() != null && dto.observacao() != null) {
            AgendamentoSolicitacao agendamento = agendamentoRepository.findById(dto.agendamentoId())
                .orElseThrow(() -> new RuntimeException("Agendamento com ID " + dto.agendamentoId() + " não encontrado!"));
            
            agendamento.setObservacoes(dto.observacao());
            agendamentoRepository.save(agendamento);
        }
    }

    public List<SolicitacaoEspecialidadeViewDTO> listarTodasEspecialidades(){
       List<SolicitacaoEspecialidade> dto = especialidadeRepository.findAll();
       List<SolicitacaoEspecialidadeViewDTO> dtos = dto.stream().map(SolicitacaoEspecialidadeViewDTO::fromSolicitacaoEspecialidade).toList();

       return dtos;     
    }

    public List<SolicitacaoEspecialidadeViewDTO> listarStatusAguardando(){
        List<SolicitacaoEspecialidade> statusAguardando = especialidadeRepository.findByStatus(StatusDaMarcacao.AGUARDANDO);
        List<SolicitacaoEspecialidadeViewDTO> viewDTOs = statusAguardando.stream().map(SolicitacaoEspecialidadeViewDTO::fromSolicitacaoEspecialidade).toList();
        return viewDTOs;
    }

    public List<SolicitacaoEspecialidadeViewDTO> listarStatusAgendado(){
        List<SolicitacaoEspecialidade> statusAgendado = especialidadeRepository.findByStatus(StatusDaMarcacao.AGENDADO);
        List<SolicitacaoEspecialidadeViewDTO> viewDTOs = statusAgendado.stream().map(solicitacaoEspecialidade -> SolicitacaoEspecialidadeViewDTO.fromSolicitacaoEspecialidade(solicitacaoEspecialidade)).toList();

        return viewDTOs;
    }

    public List<SolicitacaoEspecialidadeViewDTO> listarStatusRealizado(){
        List<SolicitacaoEspecialidade> statusRealizado = especialidadeRepository.findAll();
        List<SolicitacaoEspecialidadeViewDTO> viewDTOs = statusRealizado.stream().filter(especialidade -> especialidade.getStatus() == StatusDaMarcacao.REALIZADO).map(SolicitacaoEspecialidadeViewDTO::fromSolicitacaoEspecialidade).toList();
        return viewDTOs;
    }

    public List<SolicitacaoEspecialidadeViewDTO> listarStatusRetorno(){
        List<SolicitacaoEspecialidade> statusRetorno = especialidadeRepository.findAll();
        List<SolicitacaoEspecialidadeViewDTO> viewDTOs = statusRetorno.stream().filter(especialidade -> especialidade.getStatus() == StatusDaMarcacao.RETORNO).map(SolicitacaoEspecialidadeViewDTO::fromSolicitacaoEspecialidade).toList();
        return viewDTOs;
    }

    public List<SolicitacaoEspecialidadeViewDTO> listarStatusRetornoPoliClinica(){
        List<SolicitacaoEspecialidade> statusRetornoPoliclinica = especialidadeRepository.findAll();
        List<SolicitacaoEspecialidadeViewDTO> viewDTOs = statusRetornoPoliclinica.stream().filter(especialidade -> especialidade.getStatus() == StatusDaMarcacao.RETORNO_POLICLINICA).map(SolicitacaoEspecialidadeViewDTO::fromSolicitacaoEspecialidade).toList();

        return viewDTOs;
    }

     public List<SolicitacaoEspecialidadeViewDTO> listarStatusCancelado(){
        List<SolicitacaoEspecialidade> statusCancelado = especialidadeRepository.findAll();
        List<SolicitacaoEspecialidadeViewDTO> viewDTOs = statusCancelado.stream().filter(especialidade -> especialidade.getStatus() == StatusDaMarcacao.CANCELADO).map(SolicitacaoEspecialidadeViewDTO::fromSolicitacaoEspecialidade).toList();

        return viewDTOs;
    }

    public Page<PainelEspecialidadeProjection> listarPacientesAgendadosPorGrupo(int page, int size, String grupo, LocalDate data){
        Pageable pagina = PageRequest.of(page, size);
        return especialidadeRepository.listarPacientesAgendadosPorDataEGrupoELocal(grupo, data, pagina);
    }

    public long contarPacientesAgendadosPorDataEGrupo(String grupo, LocalDate data){
        return especialidadeRepository.totalPacientesAgendadosPorGrupoELocal(grupo, data);
    }


    @Transactional  
    public void confirmarProcedimento(Long id){
      SolicitacaoEspecialidade especialidadeExistente =  especialidadeRepository.findById(id).orElseThrow(() -> new RuntimeException("Especialidade não encontrada"));

      especialidadeExistente.setStatus(StatusDaMarcacao.REALIZADO);
      especialidadeRepository.save(especialidadeExistente);
    }


    @Transactional
     public void faltouProcedimento(Long id, ObservacaoRequestDTO dto){
      SolicitacaoEspecialidade especialidadeExistente =  especialidadeRepository.findById(id).orElseThrow(() -> new RuntimeException("Especialidade não encontrada"));
      especialidadeExistente.setStatus(StatusDaMarcacao.CANCELADO);

      String obs = (dto == null) ? null : dto.observacao();
      var agendamento = especialidadeExistente.getAgendamentoSolicitacao();
      
      if (agendamento != null && obs != null && !obs.isBlank()) {
        agendamento.setObservacoes(obs);
      }
    
    especialidadeRepository.save(especialidadeExistente);
    }
}
