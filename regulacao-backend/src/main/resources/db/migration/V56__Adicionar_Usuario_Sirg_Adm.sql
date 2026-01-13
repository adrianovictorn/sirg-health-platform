INSERT INTO usuarios (cpf, nome, senha, cargo)
VALUES ('sirg_adm', 'SIRG Administrador', '$2b$12$WA2HuRzZ9xOnnaALcuC1teLLkFyvjzowUKCVBL2c3mPdCSFNMblcu', 'ADMIN')
ON CONFLICT (cpf) DO NOTHING;
