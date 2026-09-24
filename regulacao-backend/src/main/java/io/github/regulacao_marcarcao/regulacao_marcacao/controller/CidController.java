package io.github.regulacao_marcarcao.regulacao_marcacao.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.cid.CIDCreateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.cid.CIDListDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.cid.CIDUpdateDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.cid.CIDViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.service.CidService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("api/cid")
@RequiredArgsConstructor
public class CidController {
    
    private final CidService cidService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMIN_UNIDADE', 'USER', 'RECEPCAO', 'ENFERMEIRO', 'MEDICO', 'COORD_TRANSPORTE')")
    public ResponseEntity<List<CIDListDTO>> listarCIDS(){
        return ResponseEntity.ok(cidService.listarCIDS());
    }

     @GetMapping("/buscar") 
     @PreAuthorize("hasAnyRole('ADMIN', 'ADMIN_UNIDADE', 'USER', 'RECEPCAO', 'ENFERMEIRO', 'MEDICO', 'COORD_TRANSPORTE')")

    public ResponseEntity<List<CIDListDTO>> buscarPorCodigo(@RequestParam String codigo) {
        List<CIDListDTO> cids = cidService.listarCIDsPorCodigo(codigo);
        return ResponseEntity.ok(cids);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMIN_UNIDADE', 'USER', 'RECEPCAO', 'ENFERMEIRO', 'MEDICO', 'COORD_TRANSPORTE')")
    public ResponseEntity<CIDViewDTO> cadastrarCID(@RequestBody CIDCreateDTO dto){
        return ResponseEntity.ok(cidService.cadastrarCID(dto));
    }

    @PutMapping("{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMIN_UNIDADE', 'USER', 'RECEPCAO', 'ENFERMEIRO', 'MEDICO', 'COORD_TRANSPORTE')")

    public ResponseEntity<CIDViewDTO> atualizarCID (@PathVariable Long id, @RequestBody CIDUpdateDTO dto){
        return ResponseEntity.ok(cidService.atualizarCid(id, dto));
    }

    @DeleteMapping("{id}")
    public ResponseEntity<Void> deletarCID (@PathVariable Long id){
        cidService.deletarCid(id);
        return ResponseEntity.noContent().build();
    }
}
