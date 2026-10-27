package io.github.regulacao_marcarcao.regulacao_marcacao.controller;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.relatorio.PlanilhaAgendadosFiltroDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.service.ExcelService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/exportar")
public class ExcelController {

    private final ExcelService excelService;

    public ExcelController(ExcelService excelService) {
        this.excelService = excelService;
    }

    /**
     * Planilha de agendados. {@code data} (dia unico) ou {@code dataInicio}/{@code dataFim}
     * (periodo de ate 31 dias); {@code unidadeId} e a unidade solicitante. Os demais
     * filtros sao opcionais: sem eles a planilha e a mesma de antes.
     */
    @GetMapping("/planilha")
    public ResponseEntity<InputStreamResource> exportarPlanilha(
            @RequestParam(name = "grupo",required = true) String grupo,
            @RequestParam( name =  "data", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data,
            @RequestParam(name = "dataInicio", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam(name = "dataFim", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim,
            @RequestParam(name = "label", required = false) String label,
            @RequestParam(name = "unidadeId", required = false) Long unidadeId,
            @RequestParam(name = "unidadeExecutanteId", required = false) Long unidadeExecutanteId,
            @RequestParam(name = "profissionalExecutanteId", required = false) Long profissionalExecutanteId,
            @RequestParam(name = "semExecutante", required = false, defaultValue = "false") boolean semExecutante,
            Authentication authentication
            ) throws IOException {

        String safeLabel = (label == null || label.isBlank()) ? grupo : label;
        PlanilhaAgendadosFiltroDTO filtro = new PlanilhaAgendadosFiltroDTO(grupo, data, dataInicio, dataFim,
                unidadeId, unidadeExecutanteId, profissionalExecutanteId, semExecutante);

        ByteArrayInputStream bais = excelService.gerarPlanilhaAgendamentos(filtro, cpfDe(authentication));

        HttpHeaders headers = new HttpHeaders();
        String sufixoData = data != null ? data.toString() : dataInicio + "_a_" + dataFim;
        String filename = "planilha_" + safeLabel.replaceAll("\\s+", "_").toLowerCase() + "_" + sufixoData + ".xlsx";
        headers.add("Content-Disposition", "inline; filename=" + filename);

        return ResponseEntity
                .ok()
                .headers(headers)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new InputStreamResource(bais));
    }

    @GetMapping("/planilha-aguardando")
    public ResponseEntity<InputStreamResource> exportarPlanilhaAguardando(
            @RequestParam(required = true, name = "grupo") String grupo,
            @RequestParam(required = false, defaultValue = "Pendentes") String label,
            @RequestParam(name = "unidadeId", required = false) Long unidadeId,
            Authentication authentication) throws IOException {

        ByteArrayInputStream bais = excelService.gerarPlanilhaAguardando(grupo, unidadeId, cpfDe(authentication));

        HttpHeaders headers = new HttpHeaders();
        String filename = "planilha_pendentes_" + label.replaceAll("\\s+", "_").toLowerCase() + ".xlsx";
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");

        return ResponseEntity
                .ok()
                .headers(headers)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new InputStreamResource(bais));
    }

    @GetMapping("/planilha-gel")
    public ResponseEntity<InputStreamResource> exportarPlanilhaGel(
            @RequestParam(name = "unidadeId", required = false) Long unidadeId,
            Authentication authentication) throws IOException {
        ByteArrayInputStream bais = excelService.gerarPlanilhaGel(unidadeId, cpfDe(authentication));

        HttpHeaders headers = new HttpHeaders();
        String filename = "planilha_gel_" + LocalDate.now().toString() + ".xlsx";
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");

        return ResponseEntity
                .ok()
                .headers(headers)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new InputStreamResource(bais));
    }

    /** Mesmos filtros de {@code /planilha}. */
    @GetMapping("/verificar-dados")
    public ResponseEntity<Map<String, Boolean>> verificarDados(
            @RequestParam(required = true, name = "grupo") String grupo,
            @RequestParam(name = "data", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data,
            @RequestParam(name = "dataInicio", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam(name = "dataFim", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim,
            @RequestParam(required = false) String label,
            @RequestParam(name = "unidadeId", required = false) Long unidadeId,
            @RequestParam(name = "unidadeExecutanteId", required = false) Long unidadeExecutanteId,
            @RequestParam(name = "profissionalExecutanteId", required = false) Long profissionalExecutanteId,
            @RequestParam(name = "semExecutante", required = false, defaultValue = "false") boolean semExecutante,
            Authentication authentication) {

        PlanilhaAgendadosFiltroDTO filtro = new PlanilhaAgendadosFiltroDTO(grupo, data, dataInicio, dataFim,
                unidadeId, unidadeExecutanteId, profissionalExecutanteId, semExecutante);
        boolean dadosDisponiveis = excelService.haDadosParaRelatorio(filtro, cpfDe(authentication));
        return ResponseEntity.ok(Map.of("dadosDisponiveis", dadosDisponiveis));
    }

    @GetMapping("/verificar-dados-aguardando")
    public ResponseEntity<Map<String, Boolean>> verificarDadosAguardando(
            @RequestParam(required = true, name = "grupo") String grupo,
            @RequestParam(required = false) String label,
            @RequestParam(name = "unidadeId", required = false) Long unidadeId,
            Authentication authentication) {
        
        boolean dadosDisponiveis = excelService.haDadosParaRelatorioAguardando(grupo, unidadeId, cpfDe(authentication));
        return ResponseEntity.ok(Map.of("dadosDisponiveis", dadosDisponiveis));
    }

    private static String cpfDe(Authentication authentication) {
        return authentication != null ? authentication.getName() : null;
    }
}
