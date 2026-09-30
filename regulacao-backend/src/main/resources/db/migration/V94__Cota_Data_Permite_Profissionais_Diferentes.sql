-- V94: uk_cota_data (V84) passa a considerar o profissional executante (V92).
--
-- Contexto: o indice unico uk_cota_data garantia no maximo UMA cota por
-- (unidade/grupo, especialidade/grupo, data) — sem considerar profissional.
-- Isso tornava impossivel o cenario que motivou a V92 (cota com profissional
-- executante): dois profissionais da mesma especialidade atendendo no mesmo
-- dia, cada um com sua propria cota. Descoberto ao rodar o teste de
-- integracao contra o banco real (AgendamentoCotaFluxoIT).
--
-- Nova regra: no maximo uma cota por unidade/escopo/data/profissional. Uma
-- cota SEM profissional continua limitada a uma por data (profissional_id
-- nulo entra na chave via COALESCE, como os demais campos opcionais) —
-- comportamento anterior preservado para o caso sem profissional.
--
-- uk_cota_mensal (cota MENSAL) fica como estava: profissional/horario/local
-- exigem tipoPeriodo = DATA (validado em CotaUnidadeService), entao uma cota
-- MENSAL nunca tem profissional preenchido.
--
-- Operacao ADITIVA na pratica: recria um indice ja existente com uma coluna a
-- mais na chave — nenhuma linha e removida ou alterada.

DROP INDEX IF EXISTS uk_cota_data;

CREATE UNIQUE INDEX IF NOT EXISTS uk_cota_data
    ON cota_unidade (
        COALESCE(unidade_id, -1),
        COALESCE(grupo_unidades_id, -1),
        COALESCE(especialidade_id, -1),
        COALESCE(grupo_especialidades_id, -1),
        COALESCE(profissional_id, -1),
        data_especifica
    )
    WHERE tipo_periodo = 'DATA';
