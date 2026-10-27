package io.github.regulacao_marcarcao.regulacao_marcacao.dto.cota;

import java.util.List;

/** Opções dos selects de /admin/cotas: só o que existe em alguma cota. */
public record CotaOpcoesFiltroDTO(
        List<Opcao> especialidades,
        List<Opcao> profissionais,
        List<Opcao> executantes) {

    public record Opcao(Long id, String nome) {
    }
}
