package io.github.regulacao_marcarcao.regulacao_marcacao.controller;

import java.util.List;
import org.springframework.data.domain.Page;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.solicitacaoEspecialidadeDTO.EspecialidadeAdicionarDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.solicitacoesDTO.SolicitacaoAgendamentoViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.solicitacoesDTO.SolicitacaoCreateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.solicitacoesDTO.SolicitacaoPublicViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.solicitacoesDTO.SolicitacaoSimpleViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.solicitacoesDTO.SolicitacaoUpdateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.paciente.PacienteResumoDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.solicitacoesDTO.SolicitacaoViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.dashboard.DashboardResumoDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.User;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.Roles;
import io.github.regulacao_marcarcao.regulacao_marcacao.service.SolicitacaoService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

@RequestMapping("/api/solicitacoes")
@RestController
@AllArgsConstructor
public class SolicitacaoController {
    
    private final SolicitacaoService service; 

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','USER', 'RECEPCAO', 'ENFERMEIRO', 'MEDICO', 'COORD_TRANSPORTE')") 
    public ResponseEntity<SolicitacaoViewDTO> criarSolicitacao(@Valid @RequestBody SolicitacaoCreateDTO dto){
        SolicitacaoViewDTO viewDTO = service.createSolicitacao(dto);
        return ResponseEntity.ok(viewDTO);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'USER', 'RECEPCAO', 'ENFERMEIRO', 'MEDICO', 'COORD_TRANSPORTE')") 
    public ResponseEntity<Page<SolicitacaoViewDTO>> listarSolicitacoes(
        @RequestParam(defaultValue = "0", name = "page") int page,
        @RequestParam(defaultValue = "10", name = "size") int size
    ){
       return ResponseEntity.ok(service.todasSolicitacoes(page, size));
    }

    @GetMapping("/pacientes")
    @PreAuthorize("hasAnyRole('ADMIN', 'USER', 'RECEPCAO', 'ENFERMEIRO', 'MEDICO', 'PACIENTE', 'COORD_TRANSPORTE')")
    public ResponseEntity<Page<PacienteResumoDTO>> listarPacientes(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(name = "search", required = false) String search,
            Authentication authentication) {
        User usuarioAutenticado = null;
        if (authentication != null && authentication.getPrincipal() instanceof User) {
            usuarioAutenticado = (User) authentication.getPrincipal();
        }

        if (usuarioAutenticado != null && usuarioAutenticado.getRole() == Roles.PACIENTE) {
            Page<PacienteResumoDTO> pacientes = service.buscarPacientesDoPaciente(usuarioAutenticado.getCpf(), page, size);
            return ResponseEntity.ok(pacientes);
        }

        Page<PacienteResumoDTO> pacientes = service.buscarPacientes(search, page, size);
        return ResponseEntity.ok(pacientes);
    }

    @GetMapping("/resumo-dashboard")
    @PreAuthorize("hasAnyRole('ADMIN', 'USER', 'RECEPCAO', 'ENFERMEIRO', 'MEDICO', 'COORD_TRANSPORTE')")
    public ResponseEntity<DashboardResumoDTO> obterResumoDashboard() {
        return ResponseEntity.ok(service.obterResumoDashboard());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'USER', 'RECEPCAO', 'ENFERMEIRO', 'MEDICO', 'COORD_TRANSPORTE')")
    public ResponseEntity<SolicitacaoViewDTO> buscarPorId (@PathVariable Long id){
        SolicitacaoViewDTO solicitacao = service.getSolicitacaoById(id);
        return ResponseEntity.ok(solicitacao);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'USER', 'RECEPCAO', 'ENFERMEIRO', 'MEDICO', 'COORD_TRANSPORTE')")     
    public ResponseEntity<SolicitacaoViewDTO> atualizarSolicitacao (@PathVariable Long id, @RequestBody SolicitacaoUpdateDTO dto){
        SolicitacaoViewDTO view = service.updateSolicitacao(id, dto);
        return ResponseEntity.ok(view);
    }

     @PostMapping("/{solicitacaoId}/especialidades")
     @PreAuthorize("hasAnyRole('ADMIN', 'USER', 'RECEPCAO', 'ENFERMEIRO', 'MEDICO')") 
    public ResponseEntity<SolicitacaoViewDTO> adicionarEspecialidadeASolicitacao(
            @PathVariable Long solicitacaoId,
            @RequestBody EspecialidadeAdicionarDTO dto) {
        SolicitacaoViewDTO updatedSolicitacao = service.adicionarEspecialidadeASolicitacao(solicitacaoId, dto);
        return ResponseEntity.ok(updatedSolicitacao);
    }

   @DeleteMapping("especialidades/{id}") 
    @PreAuthorize("hasAnyRole('ADMIN', 'USER', 'RECEPCAO', 'ENFERMEIRO', 'MEDICO')")
    public ResponseEntity<Void> deletarEspecialidade(@PathVariable Long id){
        service.removerEspecialidade(id); // Chamando o servico correto
        return ResponseEntity.noContent().build(); // Retorna 204 No Content
    }

    @GetMapping("/public/cpf/{cpf}")
    public ResponseEntity<List<SolicitacaoPublicViewDTO>> getPublicSolicitacoesByCpf(@PathVariable String cpf){
        List<SolicitacaoPublicViewDTO> solicitacoes = service.buscarPacientePorCpf(cpf);
        return ResponseEntity.ok(solicitacoes);
    }

    @GetMapping("/buscar/{id}")
    public ResponseEntity<SolicitacaoAgendamentoViewDTO> buscarPorId2 (@PathVariable Long id){
    return ResponseEntity.ok(service.buscarSolicitacaoPorId(id));
    }

    @GetMapping("/buscar/por/nome/cpf")
    public ResponseEntity<Page<SolicitacaoSimpleViewDTO>> buscarPorNomeOuCPF(
        @RequestParam(defaultValue = "0", name = "page") int page,
        @RequestParam(defaultValue = "10", name = "size") int size,
        @RequestParam(required = false, name = "termo") String termo
    ){
        return ResponseEntity.ok(service.buscarPorNomeOuCpf(page, size, termo));
    }
}






