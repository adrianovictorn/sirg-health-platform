package io.github.regulacao_marcarcao.regulacao_marcacao.dto.unidade_executante;

import java.util.List;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.LocalAgendamento;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Unidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.UnidadeExecutanteEspecialidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.UnidadeExecutanteGrupo;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.UsuarioUnidadeExecutante;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.Roles;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.TipoUnidadeEnum;

/** Parametrizacao completa de uma unidade executante (V110). */
public record UnidadeExecutanteViewDTO(
        Long unidadeId,
        String unidadeNome,
        String tipo,
        boolean configurada,
        List<Grupo> grupos,
        List<Especialidade> especialidades,
        List<Local> locais,
        List<Operador> operadores) {

    public record Grupo(Long id, String codigo, String nome) {
    }

    public record Especialidade(Long id, String nome, String grupoNome) {
    }

    public record Local(Long id, String nomeLocal) {
    }

    /** {@code podeOperar} falso = vinculo que ficou invalido (perfil USER retirado ou usuario inativo). */
    public record Operador(Long id, String nome, boolean podeOperar) {
    }

    public static UnidadeExecutanteViewDTO from(Unidade unidade, List<UnidadeExecutanteGrupo> grupos,
            List<UnidadeExecutanteEspecialidade> especialidades, List<LocalAgendamento> locais,
            List<UsuarioUnidadeExecutante> operadores) {
        return new UnidadeExecutanteViewDTO(
                unidade.getId(),
                unidade.getNome(),
                unidade.getTipo() != null ? unidade.getTipo().name() : null,
                // Mesma definicao de UsuarioUnidadeExecutanteRepository#findExecutantesConfiguradasDoUsuario.
                unidade.isAtivo() && unidade.getTipo() != TipoUnidadeEnum.SOLICITANTE
                        && !locais.isEmpty() && (!grupos.isEmpty() || !especialidades.isEmpty()),
                grupos.stream()
                        .map(x -> new Grupo(x.getGrupoRelatorio().getId(), x.getGrupoRelatorio().getCodigo(),
                                x.getGrupoRelatorio().getNome()))
                        .toList(),
                especialidades.stream()
                        .map(x -> new Especialidade(x.getEspecialidade().getId(), x.getEspecialidade().getNome(),
                                x.getEspecialidade().getGrupoRelatorio() != null
                                        ? x.getEspecialidade().getGrupoRelatorio().getNome()
                                        : null))
                        .toList(),
                locais.stream().map(l -> new Local(l.getId(), l.getNomeLocal())).toList(),
                operadores.stream()
                        .map(x -> new Operador(x.getUsuario().getId(), x.getUsuario().getNome(),
                                x.getUsuario().isAtivo() && x.getUsuario().podeAssumir(Roles.USER)))
                        .toList());
    }
}
