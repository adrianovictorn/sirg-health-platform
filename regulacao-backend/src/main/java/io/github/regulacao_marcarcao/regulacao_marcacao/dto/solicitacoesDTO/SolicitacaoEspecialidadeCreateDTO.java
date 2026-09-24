package io.github.regulacao_marcarcao.regulacao_marcacao.dto.solicitacoesDTO;

import java.time.LocalDate;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.PrioridadeDaMarcacaoEnum;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.StatusDaMarcacao;

// Compatível com ambos: pode enviar o ID da especialidade do catálogo
// ou o código. Se ambos vierem, o ID tem prioridade.
// O código vem como texto (e não como EspecialidadesEnum) porque o catálogo é
// cadastrável pelo admin — códigos novos não existem no enum legado.
// profissionalId é opcional — nulo significa sem profissional solicitante.
// dataColeta é opcional — usada no cadastro de exames/procedimentos.
public record SolicitacaoEspecialidadeCreateDTO(
    Long especialidadeId,
    String especialidadeSolicitada,
    Long profissionalId,
    LocalDate dataColeta,
    StatusDaMarcacao status,
    PrioridadeDaMarcacaoEnum prioridade
) { }
