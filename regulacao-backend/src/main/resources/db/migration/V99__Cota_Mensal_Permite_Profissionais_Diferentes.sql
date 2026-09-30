-- V99: uk_cota_mensal (V84) passa a considerar o profissional executante (V92),
-- mesmo ajuste que a V94 ja fez para uk_cota_data.
--
-- Contexto: a V94 corrigiu uk_cota_data para aceitar duas cotas por DATA com
-- profissionais diferentes, com o comentario explicito de que uk_cota_mensal
-- "fica como estava" porque, na epoca, profissional/horario/local exigiam
-- tipoPeriodo = DATA. A V95 mudou essa premissa: passou a aceitar profissional
-- em cota MENSAL, desde que com diasSemana preenchido — mas ninguem atualizou
-- uk_cota_mensal para acompanhar. Resultado: CotaUnidadeService.criar aceita a
-- combinacao (exigirCotaInexistente ja inclui profissional na chave em
-- memoria), mas o INSERT da segunda cota MENSAL quebra em uk_cota_mensal, que
-- nao conhece profissional_id — reproduzido em
-- CotaUnidadeIntegracaoIT#duasCotasMensaisComProfissionaisDiferentesSaoAceitas.
--
-- Nova regra: no maximo uma cota MENSAL por unidade/escopo/periodo/profissional.
-- Uma cota MENSAL sem profissional continua limitada a uma por periodo
-- (profissional_id nulo entra na chave via COALESCE, como os demais campos
-- opcionais) — comportamento anterior preservado para o caso sem profissional.
--
-- Operacao ADITIVA na pratica: recria um indice ja existente com uma coluna a
-- mais na chave — nenhuma linha e removida ou alterada, e nenhuma cota MENSAL
-- duplicada com profissionais diferentes existe hoje (o indice antigo impedia).

DROP INDEX IF EXISTS uk_cota_mensal;

CREATE UNIQUE INDEX IF NOT EXISTS uk_cota_mensal
    ON cota_unidade (
        COALESCE(unidade_id, -1),
        COALESCE(grupo_unidades_id, -1),
        COALESCE(especialidade_id, -1),
        COALESCE(grupo_especialidades_id, -1),
        COALESCE(profissional_id, -1),
        periodo
    )
    WHERE tipo_periodo = 'MENSAL';
