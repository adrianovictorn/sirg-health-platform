package io.github.regulacao_marcarcao.regulacao_marcacao.controller;

import java.time.LocalDate;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.indicadores.FechamentoIndicadoresDiaDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.GraficoGrupoPorDataProjection;
import io.github.regulacao_marcarcao.regulacao_marcacao.service.FechamentoIndicadoresDiaService;

@RestController
@RequestMapping("/api/fechamento")
public class FechamentoIndicadoresController {
    
    private final FechamentoIndicadoresDiaService fechamentoIndicadoresDiaService;

    public FechamentoIndicadoresController(FechamentoIndicadoresDiaService fechamentoIndicadoresDiaService) {
        this.fechamentoIndicadoresDiaService = fechamentoIndicadoresDiaService;
    }

    @GetMapping("/indicadores/dia")
    public ResponseEntity<FechamentoIndicadoresDiaDTO> obterIndicadores(@RequestParam(name = "data", required = false) LocalDate data, @RequestParam(name = "localId", required = true) Long localId, @RequestParam(name = "grupoId", required = true) Long grupoId){
        return ResponseEntity.ok(fechamentoIndicadoresDiaService.obterIndicadores(data, localId, grupoId));
    }

    @GetMapping("/total/agendados/dia")
    public ResponseEntity<Long> totalPacientesAgendadoDoDia(@RequestParam(name = "data",required = true) LocalDate data){
        return ResponseEntity.ok(fechamentoIndicadoresDiaService.totalDePacientesAgendadosDoDia(data));
    }

    @GetMapping("/total/pacientes/novos/dia")
    public ResponseEntity<Long> totalPacientesNovosDoDia(@RequestParam(name = "data", required = true) LocalDate data){
        return ResponseEntity.ok(fechamentoIndicadoresDiaService.totalDePacientesNovosCadastradosDoDia(data));
    }

    @GetMapping("/total/solicitacao/especialidade/dia")
    public ResponseEntity<Long> totalSolicitacaoEspecialidadeDia(@RequestParam(name = "data", required = true) LocalDate data){
        return ResponseEntity.ok(fechamentoIndicadoresDiaService.totalSolicitacaoEspecialidadeDoDia(data));
    }

    @GetMapping("/total/por/especialidade/por/tempo")
    public ResponseEntity<Page<GraficoGrupoPorDataProjection>> totalDeAgendamentoPorGrupoEPeriodo(
        @RequestParam(defaultValue = "0", name = "page", required = false)int page,
        @RequestParam(defaultValue = "10", name = "size", required = false)int size,
        @RequestParam(name = "inicio", required = true) LocalDate inicio,
        @RequestParam(name = "intervalo", required = true) LocalDate intervalo
    ){
        return ResponseEntity.ok(fechamentoIndicadoresDiaService.totalDeAgendamentoPorGrupoEPeriodo(page, size, inicio, intervalo));
    }
}
