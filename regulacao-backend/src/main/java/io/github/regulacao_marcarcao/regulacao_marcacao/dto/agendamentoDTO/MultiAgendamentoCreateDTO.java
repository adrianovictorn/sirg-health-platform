package io.github.regulacao_marcarcao.regulacao_marcacao.dto.agendamentoDTO;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.LocalDeAgendamentoEnum;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.TurnoEnum;

/**
 * {@code cotasSelecionadas} (V93, opcional): quando ha mais de uma cota com
 * profissional aplicavel ao mesmo exame/data (ex.: dois profissionais da
 * mesma especialidade), mapeia o codigo do exame para o id da cota escolhida
 * pela unidade — necessario so nesse caso; ausente ou sem a chave, o sistema
 * resolve sozinho quando ha no maximo uma opcao com profissional.
 *
 * {@code horariosSelecionados} (V97, opcional): mapeia o codigo do exame para
 * a hora informada manualmente pelo operador — sempre aceito, inclusive
 * quando a cota usada tem horario dinamico (V100: nesse caso sobrescreve o
 * calculo automatico, validado contra o periodo da cota). Sem a chave, a
 * especialidade usa o horario calculado (cota dinamica) ou fica sem hora
 * definida (a tela usa o periodo da cota so como referencia).
 *
 * {@code profissionaisSelecionados} (V100, opcional): mapeia o codigo do
 * exame para o id do profissional escolhido pelo operador — sobrescreve, so
 * para este agendamento, o profissional espelhado pela cota usada. Sem a
 * chave, mantem o profissional da cota (quando ela define um).
 */
public record MultiAgendamentoCreateDTO(
    List<String> examesSelecionados,
    LocalDate dataAgendada,
    LocalDeAgendamentoEnum localAgendado,
    Long localAgendamentoId,
    TurnoEnum turno,
    String observacoes,
    Map<String, Long> cotasSelecionadas,
    Map<String, LocalTime> horariosSelecionados,
    Map<String, Long> profissionaisSelecionados
) {}
