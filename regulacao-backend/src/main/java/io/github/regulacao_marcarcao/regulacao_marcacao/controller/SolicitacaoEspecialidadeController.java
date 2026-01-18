package io.github.regulacao_marcarcao.regulacao_marcacao.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.agendamentoDTO.ObservacaoRequestDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.solicitacaoEspecialidadeDTO.EspecialidadeUpdateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.solicitacaoEspecialidadeDTO.EspecialidadesStatusUpdateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.solicitacaoEspecialidadeDTO.SolicitacaoEspecialidadeViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.PainelEspecialidadeProjection;
import io.github.regulacao_marcarcao.regulacao_marcacao.service.SolicitacaoEspecialidadeService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("api/especialidades")
@RequiredArgsConstructor
public class SolicitacaoEspecialidadeController {
    

    private final SolicitacaoEspecialidadeService service; 


    @GetMapping
    public ResponseEntity<List<SolicitacaoEspecialidadeViewDTO>> listarTodasEspecialidades(){
        List<SolicitacaoEspecialidadeViewDTO> views = service.listarTodasEspecialidades();
        return ResponseEntity.ok(views);
    }

    @PutMapping("/{id}")
    public ResponseEntity<SolicitacaoEspecialidadeViewDTO> atualizarEspecialidade(@PathVariable Long id, @RequestBody EspecialidadeUpdateDTO dto){
        SolicitacaoEspecialidadeViewDTO view = service.atualizarStatusEspecialidade(dto, id);

        return ResponseEntity.ok(view);
    }

    @PutMapping("/status/batch")
    public ResponseEntity<Void> atualizarStatusEmLote(@RequestBody EspecialidadesStatusUpdateDTO dto) {
        service.atualizarStatusEspecialidadesEmLote(dto);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/status/aguardando")
    public ResponseEntity<List<SolicitacaoEspecialidadeViewDTO>> listarStatusAguardando(){
        return ResponseEntity.ok(service.listarStatusAguardando());
    }

    @GetMapping("/status/agendado")
    public ResponseEntity<List<SolicitacaoEspecialidadeViewDTO>> listarStatusAgendado(){
        return ResponseEntity.ok(service.listarStatusAgendado());
    }

    @GetMapping("/status/realizado")
    public ResponseEntity<List<SolicitacaoEspecialidadeViewDTO>> listarStatusRealizado(){
        return ResponseEntity.ok(service.listarStatusRealizado());
    }

    @GetMapping("/status/retorno")
    public ResponseEntity<List<SolicitacaoEspecialidadeViewDTO>> listarStatusRetorno(){
        return ResponseEntity.ok(service.listarStatusRetorno());
    }

    @GetMapping("/status/retorno/policlinica")
    public ResponseEntity<List<SolicitacaoEspecialidadeViewDTO>> listarStatusRetornoPoliclinica(){
        return ResponseEntity.ok(service.listarStatusRetornoPoliClinica());
    }

     @GetMapping("/status/cancelado")
    public ResponseEntity<List<SolicitacaoEspecialidadeViewDTO>> listarStatusCancelado(){
        return ResponseEntity.ok(service.listarStatusCancelado());
    }

    @GetMapping("/listar/pacientes/por/grupo")
    public ResponseEntity<Page<PainelEspecialidadeProjection>> listarPacientesAgendadosPorDataEGrupoELocal(
        @RequestParam(defaultValue = "0", name = "page") int page,
        @RequestParam(defaultValue = "10", name =  "size") int size,
        @RequestParam(required = true, name = "grupo") String grupo,
        @RequestParam(required = true, name =  "data") LocalDate data
    ){
        return ResponseEntity.ok(service.listarPacientesAgendadosPorGrupo(page, size, grupo, data));
    }

     @GetMapping("/contar/pacientes/por/grupo")
    public ResponseEntity<Long> contarPacientesAgendadosPorDataEGrupoELocal(
        @RequestParam(required = true, name = "grupo") String grupo,
        @RequestParam(required = true, name =  "data") LocalDate data
    ){
        return ResponseEntity.ok(service.contarPacientesAgendadosPorDataEGrupo(grupo, data));
    }

     @PatchMapping  ("{id}/realizado")
     public ResponseEntity<Void> confirmarEspecialidade (@PathVariable Long id){
        service.confirmarProcedimento(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("{id}/faltou")
    public ResponseEntity<Void> faltouEspecialidade (@PathVariable Long id, @RequestBody(required = false) ObservacaoRequestDTO dto){
        service.faltouProcedimento(id, dto);
        return ResponseEntity.noContent().build();
    }

}
