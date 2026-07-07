package io.github.regulacao_marcarcao.regulacao_marcacao.controller;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.ProfissionalQuantitativoProjection;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.ProfissionalSolicitacaoDetalheProjection;
import io.github.regulacao_marcarcao.regulacao_marcacao.service.RelatorioProfissionalExcelService;
import io.github.regulacao_marcarcao.regulacao_marcacao.service.RelatorioSolicitacaoProfissionalService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/relatorio/profissional")
@RequiredArgsConstructor
public class RelatorioSolicitacaoProfissionalController {

    private final RelatorioSolicitacaoProfissionalService service;
    private final RelatorioProfissionalExcelService excelService;

    @GetMapping("/detalhado")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<ProfissionalSolicitacaoDetalheProjection>> listarDetalhado(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam(required = false) Long profissionalId,
            @RequestParam(required = false) Long unidadeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(service.listarDetalhado(
                inicio, fim, profissionalId, unidadeId, PageRequest.of(page, size)));
    }

    @GetMapping("/quantitativo")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<ProfissionalQuantitativoProjection>> listarQuantitativo(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam(required = false) Long unidadeId) {
        return ResponseEntity.ok(service.listarQuantitativo(inicio, fim, unidadeId));
    }

    @GetMapping("/detalhado/excel")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> exportarDetalhadoExcel(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam(required = false) Long profissionalId,
            @RequestParam(required = false) Long unidadeId) throws IOException {
        List<ProfissionalSolicitacaoDetalheProjection> dados =
                service.listarDetalhadoCompleto(inicio, fim, profissionalId, unidadeId);
        byte[] excel = excelService.gerarExcelDetalhado(dados);

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=relatorio_solicitacoes_por_profissional.xlsx");
        return ResponseEntity.ok().headers(headers).body(excel);
    }

    @GetMapping("/quantitativo/excel")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> exportarQuantitativoExcel(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam(required = false) Long unidadeId) throws IOException {
        List<ProfissionalQuantitativoProjection> dados = service.listarQuantitativo(inicio, fim, unidadeId);
        byte[] excel = excelService.gerarExcelQuantitativo(dados);

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=relatorio_quantitativo_por_profissional.xlsx");
        return ResponseEntity.ok().headers(headers).body(excel);
    }
}
