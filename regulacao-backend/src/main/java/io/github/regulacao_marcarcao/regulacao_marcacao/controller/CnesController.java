package io.github.regulacao_marcarcao.regulacao_marcacao.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.cnes.CnesEstabelecimentoDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.service.CnesService;
import lombok.RequiredArgsConstructor;

/**
 * Consulta a base nacional do CNES para preencher o cadastro de estabelecimento.
 *
 * <p>Somente leitura: nada aqui grava no banco. O operador busca, confere o que
 * veio e so entao salva pela rota de unidades.
 */
@RestController
@RequestMapping("/api/cnes")
@RequiredArgsConstructor
public class CnesController {

    private final CnesService cnesService;

    /** Busca pelo codigo CNES — e o que preenche a tela inteira de uma vez. */
    @GetMapping("/estabelecimentos/{cnes}")
    @PreAuthorize("hasAnyRole('ADMIN', 'GESTOR')")
    public ResponseEntity<CnesEstabelecimentoDTO> buscarPorCnes(@PathVariable String cnes) {
        return ResponseEntity.ok(cnesService.buscarPorCnes(cnes));
    }

    /**
     * Lista os estabelecimentos do municipio, para o autocomplete.
     *
     * <p>Sem `municipio` usa o codigo configurado na instancia
     * (`app.cnes.codigo-municipio`) — cada VPS atende um municipio.
     */
    @GetMapping("/estabelecimentos")
    @PreAuthorize("hasAnyRole('ADMIN', 'GESTOR')")
    public ResponseEntity<List<CnesEstabelecimentoDTO>> listarPorMunicipio(
            @RequestParam(required = false) String municipio,
            @RequestParam(defaultValue = "50") int limite,
            @RequestParam(defaultValue = "0") int offset) {
        return ResponseEntity.ok(cnesService.listarPorMunicipio(municipio, limite, offset));
    }
}
