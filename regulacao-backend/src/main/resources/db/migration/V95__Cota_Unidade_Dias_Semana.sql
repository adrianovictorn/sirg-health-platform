-- V95: cota_unidade MENSAL passa a aceitar profissional/local/horario (V92),
-- desde que os dias da semana de atendimento sejam informados.
--
-- Contexto: ate aqui, profissional/local/horario exigiam tipoPeriodo = DATA
-- (um dia especifico). Para cota MENSAL nao ha uma data unica, entao os dias
-- da semana (ex.: "SEG,QUA") substituem essa referencia — o sistema calcula,
-- em tempo de leitura, as proximas datas dentro do proprio mes da cota
-- (dataInicio..fimDoMes), sem gerar nenhuma linha/tabela nova: o saldo
-- continua sendo um so para o mes inteiro (CotaUnidadeService#cotasAplicaveis
-- filtra por dia da semana quando a cota MENSAL tiver dias_semana).
--
-- Mesmo padrao de Agenda.diasSemana (V89): string com siglas separadas por
-- virgula, sem CHECK de formato no banco — validado no service.
--
-- Operacao ADITIVA: nenhuma coluna removida, nenhum dado existente alterado.
-- Cotas ja cadastradas ficam com dias_semana = NULL, que preserva o
-- comportamento atual (cota "geral" do mes, aplicavel todo dia).

ALTER TABLE cota_unidade ADD COLUMN IF NOT EXISTS dias_semana VARCHAR(20);
