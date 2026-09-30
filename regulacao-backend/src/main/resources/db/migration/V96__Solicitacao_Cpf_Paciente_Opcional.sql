-- V96: cpf_paciente deixa de ser obrigatorio, para permitir cadastrar
-- pacientes recem-nascidos (RN) que ainda nao tem CPF emitido.
--
-- Mantem o indice UNIQUE como esta — o Postgres ja permite multiplos valores
-- NULL numa coluna UNIQUE sem conflito entre si (dois RNs sem CPF nao colidem).
--
-- A obrigatoriedade nao desaparece: passa a ser validada no service
-- (SolicitacaoService), condicional a o paciente ser RN ou nao — o banco so
-- deixa de impedir a ausencia do dado por constraint fixa.
--
-- Operacao ADITIVA na pratica: solta uma restricao, nao adiciona nenhuma.
-- Todos os registros existentes ja tem CPF preenchido (a constraint NOT NULL
-- vigente ate aqui garante isso), entao nao ha necessidade de backfill.

ALTER TABLE solicitacao ALTER COLUMN cpf_paciente DROP NOT NULL;
