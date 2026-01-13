WITH novos_pacientes (
    usf_origem,
    nome_paciente,
    cpf_paciente,
    cns,
    telefone,
    data_nascimento,
    datanascimento,
    observacoes,
    data_malote
) AS (
    VALUES
        ('USF01', 'Ana Souza', '10000000100', '700000000000001', '71990000001', DATE '1988-05-12', DATE '1988-05-12', 'Paciente ficticio para testes.', DATE '2025-12-01'),
        ('USF02', 'Carlos Silva', '10000000200', '700000000000002', '71990000002', DATE '1979-11-03', DATE '1979-11-03', 'Paciente ficticio para testes.', DATE '2025-12-01'),
        ('USF03', 'Mariana Lima', '10000000300', '700000000000003', '71990000003', DATE '1992-02-21', DATE '1992-02-21', 'Paciente ficticio para testes.', DATE '2025-12-01'),
        ('USF04', 'Joao Pedro', '10000000400', '700000000000004', '71990000004', DATE '1985-07-30', DATE '1985-07-30', 'Paciente ficticio para testes.', DATE '2025-12-01'),
        ('USF05', 'Paula Ribeiro', '10000000500', '700000000000005', '71990000005', DATE '1996-09-14', DATE '1996-09-14', 'Paciente ficticio para testes.', DATE '2025-12-01'),
        ('USF06', 'Rafael Costa', '10000000600', '700000000000006', '71990000006', DATE '1973-01-19', DATE '1973-01-19', 'Paciente ficticio para testes.', DATE '2025-12-01'),
        ('HMCA', 'Fernanda Alves', '10000000700', '700000000000007', '71990000007', DATE '1990-12-08', DATE '1990-12-08', 'Paciente ficticio para testes.', DATE '2025-12-01')
)
INSERT INTO solicitacao (
    usf_origem,
    nome_paciente,
    cpf_paciente,
    cns,
    telefone,
    data_nascimento,
    datanascimento,
    observacoes,
    data_malote
)
SELECT
    n.usf_origem,
    n.nome_paciente,
    n.cpf_paciente,
    n.cns,
    n.telefone,
    n.data_nascimento,
    n.datanascimento,
    n.observacoes,
    n.data_malote
FROM novos_pacientes n
WHERE NOT EXISTS (
    SELECT 1
    FROM solicitacao s
    WHERE s.cpf_paciente = n.cpf_paciente
);
