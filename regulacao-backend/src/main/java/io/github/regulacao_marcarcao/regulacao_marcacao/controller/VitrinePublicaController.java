package io.github.regulacao_marcarcao.regulacao_marcacao.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.vitrine.VitrinePublicaViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.service.VitrinePublicaService;
import lombok.RequiredArgsConstructor;

/**
 * Resumo do municipio lido pela landing publica do SIRG (app {@code sirg-landing}).
 *
 * <p>Publico de proposito — fica sob {@code /api/transparencia/**}, que ja e
 * {@code permitAll} — porque so devolve totais acumulados do municipio, o
 * catalogo de especialidades e a versao do sistema, servidos de um cache de
 * uma hora.
 *
 * <p>Nunca devem sair por aqui: fila e tempo de espera, qualquer valor em reais,
 * nome de paciente, de profissional ou de usuario, e qualquer contagem quebrada
 * por especialidade, unidade ou periodo.
 */
@RestController
@RequestMapping("/api/transparencia")
@RequiredArgsConstructor
public class VitrinePublicaController {

    private final VitrinePublicaService vitrinePublicaService;

    @GetMapping("/resumo")
    public ResponseEntity<VitrinePublicaViewDTO> resumo() {
        return ResponseEntity.ok(vitrinePublicaService.resumo());
    }
}
