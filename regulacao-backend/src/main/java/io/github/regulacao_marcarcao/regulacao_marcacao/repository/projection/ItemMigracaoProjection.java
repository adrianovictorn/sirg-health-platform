package io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Item de um agendamento que sera migrado de data (migracao de cota). Leitura
 * escalar: depois dos bulk updates do motor de saldo o contexto de persistencia
 * e limpo, e entidades LAZY lidas depois disso estourariam.
 */
public interface ItemMigracaoProjection {
    Long getSeId();
    Long getAgendamentoId();
    Long getSolicitacaoId();
    Long getUnidadeId();
    Long getEspecialidadeId();
    String getEspecialidadeCodigo();
    Long getCotaUnidadeId();
    String getStatus();
    LocalDate getDataAgendada();
    UUID getOrigemMunicipioId();
    String getOrigemMunicipioNome();
    String getNomePaciente();
    String getCpfPaciente();
}
