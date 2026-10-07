package io.github.regulacao_marcarcao.regulacao_marcacao.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Teto financeiro (V107): limite em reais que uma unidade pode agendar, num
 * mes, dentro de um grupo de especialidades (na pratica, Laboratorio).
 *
 * <p>Convive com {@link CotaUnidade} sem tocar nela: a cota limita em
 * quantidade, o teto em valor, e as duas regras incidem juntas sobre o mesmo
 * agendamento. Fica em tabela propria porque os endpoints de cota sao lidos
 * por perfis de unidade, que nao podem ver valor nenhum.
 *
 * <p>Sem teto cadastrado para a unidade/grupo/mes nao ha restricao, como na cota.
 */
@Entity
@Table(name = "teto_financeiro")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TetoFinanceiro {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unidade_id", nullable = false)
    private Unidade unidade;

    /** Grupo cujas ESPECIALIDADES debitam este teto (vinculo em Especialidade#grupoRelatorio). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grupo_especialidades_id", nullable = false)
    private GrupoRelatorio grupoEspecialidades;

    /** Mes da data agendada, formato YYYY-MM. */
    @Column(name = "periodo", nullable = false, length = 7)
    private String periodo;

    @Column(name = "valor_total", nullable = false, precision = 14, scale = 2)
    private BigDecimal valorTotal = BigDecimal.ZERO;

    /**
     * Somente leitura na entidade ({@code updatable = false}): o valor so muda
     * pelos UPDATEs atomicos de {@code TetoFinanceiroRepository} (debito e
     * estorno). Se a entidade pudesse regrava-lo, editar o teto ao mesmo tempo
     * em que uma unidade agenda apagaria o debito dela.
     */
    @Column(name = "valor_utilizado", nullable = false, updatable = false, precision = 14, scale = 2)
    private BigDecimal valorUtilizado = BigDecimal.ZERO;

    @Column(name = "ativo", nullable = false)
    private boolean ativo = true;

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "criado_por_id", nullable = true)
    private User criadoPor;

    @Version
    private Long version;
}
