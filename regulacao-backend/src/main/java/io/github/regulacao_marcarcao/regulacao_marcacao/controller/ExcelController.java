package io.github.regulacao_marcarcao.regulacao_marcacao.controller;

import io.github.regulacao_marcarcao.regulacao_marcacao.service.ExcelService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

    @GetMapping("/planilha")
    public ResponseEntity<InputStreamResource> exportarPlanilha(
            @RequestParam(name = "grupo",required = true) String grupo,
            @RequestParam( name =  "data", required = true) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data,
            @RequestParam(name = "label", required = false) String label
            ) throws IOException {

        String safeLabel = (label == null || label.isBlank()) ? grupo : label;

        ByteArrayInputStream bais = excelService.gerarPlanilhaAgendamentos(grupo, data);

        HttpHeaders headers = new HttpHeaders();
        String filename = "planilha_" + safeLabel.replaceAll("\\s+", "_").toLowerCase() + "_" + data.toString() + ".xlsx";
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
            @RequestParam(required = false, defaultValue = "Pendentes") String label) throws IOException {

        ByteArrayInputStream bais = excelService.gerarPlanilhaAguardando(grupo);

        HttpHeaders headers = new HttpHeaders();
        String filename = "planilha_pendentes_" + label.replaceAll("\\s+", "_").toLowerCase() + ".xlsx";
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");

        return ResponseEntity
                .ok()
                .headers(headers)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new InputStreamResource(bais));
    }

    @GetMapping("/verificar-dados")
    public ResponseEntity<Map<String, Boolean>> verificarDados(
            @RequestParam(required = true, name = "grupo") String grupo,
            @RequestParam(name = "data", required = true) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data,
            @RequestParam(required = false) String label) {
        
        boolean dadosDisponiveis = excelService.haDadosParaRelatorio(grupo, data);
        return ResponseEntity.ok(Map.of("dadosDisponiveis", dadosDisponiveis));
    }

    @GetMapping("/verificar-dados-aguardando")
    public ResponseEntity<Map<String, Boolean>> verificarDadosAguardando(
            @RequestParam(required = true, name = "grupo") String grupo,
            @RequestParam(required = false) String label) {
        
        boolean dadosDisponiveis = excelService.haDadosParaRelatorioAguardando(grupo);
        return ResponseEntity.ok(Map.of("dadosDisponiveis", dadosDisponiveis));
    }
}
