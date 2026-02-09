package io.github.regulacao_marcarcao.regulacao_marcacao.controller;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.regulacao_marcarcao.regulacao_marcacao.service.AgendamentoService;
import io.github.regulacao_marcarcao.regulacao_marcacao.service.EspecialidadeService;
import io.github.regulacao_marcarcao.regulacao_marcacao.service.SolicitacaoService;

@RestController
@RequestMapping("/api/transparencia")
public class TransparenciaController {
    
    
    private final AgendamentoService agendamentoService;
    private final EspecialidadeService especialidadeService;
    
    
    private final SolicitacaoService solicitacaoService;
    public TransparenciaController(SolicitacaoService solicitacaoService, AgendamentoService agendamentoService,
            EspecialidadeService especialidadeService) {
        this.solicitacaoService = solicitacaoService;
        this.agendamentoService = agendamentoService;
        this.especialidadeService = especialidadeService;
    }

    @GetMapping("/total/pacientes")
    public ResponseEntity<Long> totalPacientes(){
        return ResponseEntity.ok(solicitacaoService.totalPacientesCadastrados());
    }

    @GetMapping("/total/agendamentos")
    public ResponseEntity<Long> totalAgendamentos(){
        return ResponseEntity.ok(agendamentoService.totalAgendamentos());
    }

    @GetMapping("/total/especialidades")
    public ResponseEntity<Long> totalEspecialidades(){
        return ResponseEntity.ok(especialidadeService.totalEspecialidades());
    }

    @GetMapping("/buscar/especialidade")
    public ResponseEntity<Page<String>> listarEspecialidades(
    @RequestParam(defaultValue = "0", name = "page") int page,
    @RequestParam(defaultValue = "20", name = "size") int size,
    @RequestParam(name = "nome", required = false) String nome
    ){
        return ResponseEntity.ok(especialidadeService.listarNomeDasEspecialidades(page, size, nome));
    }
}
