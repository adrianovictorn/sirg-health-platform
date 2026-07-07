package io.github.regulacao_marcarcao.regulacao_marcacao.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.indicadores.FechamentoIndicadoresDiaDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.EspecialidadesMaisSolicitadasProjection;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.GraficoGrupoPorDataProjection;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.ProfissionalEspecialidadeRankingProjection;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.ProfissionalRankingProjection;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.TempoEsperaEspecialidadeProjection;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.TempoEsperaGeralProjection;
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


    @GetMapping("/especialidades/pendentes/top10")
    public ResponseEntity<List<EspecialidadesMaisSolicitadasProjection>> totalEspecialidadesPendentesTop10(){
        return ResponseEntity.ok(fechamentoIndicadoresDiaService.top10EspecialidadesPendentes());
    }

    @GetMapping("/profissionais/ranking")
    public ResponseEntity<List<ProfissionalRankingProjection>> rankingProfissionais(
            @RequestParam(name = "inicio", required = false) LocalDate inicio,
            @RequestParam(name = "fim",    required = false) LocalDate fim,
            @RequestParam(name = "limite", defaultValue = "10") int limite) {
        return ResponseEntity.ok(fechamentoIndicadoresDiaService.rankingProfissionais(inicio, fim, limite));
    }

    @GetMapping("/profissionais/{id}/especialidades")
    public ResponseEntity<List<ProfissionalEspecialidadeRankingProjection>> especialidadesPorProfissional(
            @PathVariable Long id,
            @RequestParam(name = "inicio", required = false) LocalDate inicio,
            @RequestParam(name = "fim",    required = false) LocalDate fim) {
        return ResponseEntity.ok(fechamentoIndicadoresDiaService.especialidadesPorProfissional(id, inicio, fim));
    }

    @GetMapping("/tempo-espera/por-especialidade")
    public ResponseEntity<List<TempoEsperaEspecialidadeProjection>> tempoEsperaPorEspecialidade(
            @RequestParam(name = "inicio", required = false) LocalDate inicio,
            @RequestParam(name = "fim",    required = false) LocalDate fim,
            @RequestParam(name = "unidadeId", required = false) Long unidadeId) {
        return ResponseEntity.ok(fechamentoIndicadoresDiaService.tempoEsperaPorEspecialidade(inicio, fim, unidadeId));
    }

    @GetMapping("/tempo-espera/geral")
    public ResponseEntity<TempoEsperaGeralProjection> tempoEsperaGeral(
            @RequestParam(name = "inicio", required = false) LocalDate inicio,
            @RequestParam(name = "fim",    required = false) LocalDate fim,
            @RequestParam(name = "unidadeId", required = false) Long unidadeId,
            @RequestParam(name = "especialidadeId", required = false) Long especialidadeId) {
        return ResponseEntity.ok(fechamentoIndicadoresDiaService.tempoEsperaGeral(inicio, fim, unidadeId, especialidadeId));
    }
}
