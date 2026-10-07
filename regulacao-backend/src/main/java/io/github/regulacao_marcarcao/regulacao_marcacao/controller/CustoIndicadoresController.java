package io.github.regulacao_marcarcao.regulacao_marcacao.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.custo.CoberturaPrecoViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.custo.CustoEvolucaoViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.custo.CustoFaltasViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.custo.TetoExecucaoViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.service.CustoIndicadoresService;
import lombok.RequiredArgsConstructor;

/**
 * Indicadores de custo exibidos em /indicadores (somente leitura, somente
 * agregados) — ADMIN e GESTOR.
 *
 * <p>Ficam em {@code /api/custos/**} de proposito, e nao junto dos demais
 * indicadores gerenciais: valor em reais so sai por este caminho.
 *
 * <p>As datas chegam como texto e sao validadas no servico, para que formato
 * invalido volte como 400 com mensagem.
 */
@RestController
@RequestMapping("/api/custos/indicadores")
@PreAuthorize("hasAnyRole('ADMIN', 'GESTOR')")
@RequiredArgsConstructor
public class CustoIndicadoresController {

    private final CustoIndicadoresService service;

    @GetMapping("/teto")
    public ResponseEntity<TetoExecucaoViewDTO> execucaoDoTeto(
            @RequestParam(required = false) Long unidadeId,
            @RequestParam(required = false) String de,
            @RequestParam(required = false) String ate,
            Authentication authentication) {
        return ResponseEntity.ok(service.execucaoDoTeto(unidadeId, de, ate, cpf(authentication)));
    }

    @GetMapping("/faltas")
    public ResponseEntity<CustoFaltasViewDTO> faltas(
            @RequestParam(required = false) Long unidadeId,
            @RequestParam(required = false) String de,
            @RequestParam(required = false) String ate,
            Authentication authentication) {
        return ResponseEntity.ok(service.faltas(unidadeId, de, ate, cpf(authentication)));
    }

    @GetMapping("/cobertura")
    public ResponseEntity<CoberturaPrecoViewDTO> coberturaDePreco(
            @RequestParam(required = false) Long unidadeId,
            @RequestParam(required = false) String de,
            @RequestParam(required = false) String ate,
            Authentication authentication) {
        return ResponseEntity.ok(service.coberturaDePreco(unidadeId, de, ate, cpf(authentication)));
    }

    @GetMapping("/evolucao")
    public ResponseEntity<CustoEvolucaoViewDTO> evolucao(
            @RequestParam(required = false) Long unidadeId,
            Authentication authentication) {
        return ResponseEntity.ok(service.evolucao(unidadeId, cpf(authentication)));
    }

    private static String cpf(Authentication authentication) {
        return authentication != null ? authentication.getName() : null;
    }
}
