package io.github.regulacao_marcarcao.regulacao_marcacao.dto.solicitacaoEspecialidadeDTO;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.PrioridadeDaMarcacaoEnum;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.StatusDaMarcacao;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

// Compatível com a nova entidade Especialidade: pode enviar o ID do catálogo
// ou o código da especialidade. Se ambos vierem, o ID tem prioridade.
// O código vem como texto (e não como EspecialidadesEnum) porque o catálogo é
// cadastrável pelo admin — códigos novos não existem no enum legado.
// profissionalId e dataColeta são opcionais.
public record EspecialidadeAdicionarDTO(
    Long especialidadeId,
    String especialidadeSolicitada,
    Long profissionalId,
    LocalDate dataColeta,
    @NotNull StatusDaMarcacao status,
    @NotNull PrioridadeDaMarcacaoEnum prioridade
) {

    @JsonIgnore
    @AssertTrue(message = "Informe a especialidade (especialidadeId ou especialidadeSolicitada).")
    public boolean isEspecialidadeInformada() {
        return especialidadeId != null
            || (especialidadeSolicitada != null && !especialidadeSolicitada.isBlank());
    }
}
