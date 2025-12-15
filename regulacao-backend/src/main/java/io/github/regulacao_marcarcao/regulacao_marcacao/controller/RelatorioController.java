package io.github.regulacao_marcarcao.regulacao_marcacao.controller;

import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.relatorio.producao.RelatorioProducaoViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.service.RelatorioProducaoService;

@RestController
@RequestMapping("/api/relatorio/producao")
public class RelatorioController {
    

    private final RelatorioProducaoService relatorioProducaoService;

    public RelatorioController(RelatorioProducaoService relatorioProducaoService) {
        this.relatorioProducaoService = relatorioProducaoService;
    }

    @GetMapping("/listar")
    public ResponseEntity<List<RelatorioProducaoViewDTO>> listarDadosParaRelatorio(){
        return ResponseEntity.ok(relatorioProducaoService.dadosDoRelatorio());
    }

    @GetMapping("/excel")
    public ResponseEntity<byte[]> downloadExcel() throws Exception{
        List<RelatorioProducaoViewDTO> dados = relatorioProducaoService.dadosDoRelatorio();
        byte[] excel = relatorioProducaoService.gerarExcel(dados);

        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Disposition", "attachment; filename=relatorio_producao.xlsx");

        return ResponseEntity.ok().headers(headers).body(excel);
    }
}
