package io.github.regulacao_marcarcao.regulacao_marcacao.dto.cota;

import java.time.LocalDate;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.TipoPeriodoCota;

/**
 * Criacao de cota, com duas dimensoes independentes.
 *
 * <b>Titular</b> (exatamente um):
 * {@code unidadeId} = cota de uma unidade;
 * {@code grupoUnidadesId} = pool compartilhado entre as unidades do grupo.
 *
 * <b>Escopo</b> (no maximo um):
 * {@code especialidadeId} = limita uma especialidade;
 * {@code grupoEspecialidadesId} = limita todas as especialidades do grupo, com
 * saldo unico compartilhado entre elas (evita cadastrar uma cota por
 * especialidade — o grupo "Laboratorio" tem 172);
 * ambos nulos = cota geral, vale para qualquer especialidade.
 *
 * Os dois grupos referenciam a mesma tabela {@code grupo_relatorio}, em papeis
 * distintos: um agrupa unidades, o outro agrupa especialidades.
 */
public record CotaUnidadeCreateDTO(
        Long unidadeId,
        Long grupoUnidadesId,
        Long especialidadeId,
        Long grupoEspecialidadesId,
        TipoPeriodoCota tipoPeriodo,
        String periodo,
        LocalDate dataEspecifica,
        Integer quantidadeTotal) {
}
