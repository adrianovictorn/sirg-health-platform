🏛️ SIRG – Sistema Integrado de Regulação e Gestão

O SIRG (Sistema Integrado de Regulação e Gestão) é uma plataforma institucional desenvolvida para apoiar a gestão, regulação e organização de serviços públicos, com foco em escala, segurança e padronização de processos.

O projeto foi concebido com arquitetura moderna, separação clara de responsabilidades e boas práticas de engenharia de software, sendo aplicável a cenários reais de administração pública.

🎯 Objetivo do Sistema

O SIRG tem como objetivo central:

Centralizar informações institucionais

Organizar fluxos de regulação e gestão

Oferecer APIs seguras e escaláveis

Facilitar integrações com outros sistemas governamentais

Servir como base para evolução contínua de módulos públicos

🧩 Principais Características

Arquitetura full stack moderna

API REST segura com autenticação e autorização

Separação clara entre backend, frontend e infraestrutura

Suporte a múltiplos módulos institucionais

Preparado para ambientes on-premise ou cloud

Estrutura pensada para governança, manutenção e escalabilidade

🏗️ Arquitetura (Visão Geral)
sirg-platform
├── backend/        # API REST (Spring Boot)
├── frontend/       # Aplicação Web (Frontend moderno)
├── docs/           # Documentação e diagramas
├── compose.yaml    # Orquestração local (Docker Compose)
└── README.md

A plataforma segue princípios de:

Separação de camadas

Baixo acoplamento

Alta coesão

Configuração por ambiente

🛠️ Stack Tecnológica
Backend

Java 17+

Spring Boot

Spring Security

Autenticação baseada em JWT

JPA / Hibernate

Flyway (versionamento de banco)

PostgreSQL

Frontend

Framework frontend moderno (ex: Svelte / SvelteKit)

Comunicação via API REST

Separação por módulos e responsabilidades

Infraestrutura

Docker

Docker Compose

Configuração por variáveis de ambiente

🔐 Segurança

O SIRG foi projetado com foco em segurança institucional:

Autenticação via JWT

Controle de acesso por contexto do usuário

Nenhuma credencial sensível versionada

Configurações sensíveis via variáveis de ambiente

Preparado para políticas de CORS e gateway reverso

🚀 Execução em Ambiente Local (Demo)

⚠️ Este repositório representa uma versão demonstrativa e técnica do sistema.
Não contém dados reais, credenciais institucionais ou regras sensíveis de produção.

Subir stack completa com Docker
docker compose up --build
Acessos locais (exemplo)

Frontend: http://localhost:5173

Backend (API): http://localhost:8080

⚙️ Configuração por Variáveis de Ambiente

Exemplo de variáveis utilizadas pelo backend:

POSTGRES_DB=sirg
POSTGRES_USER=sirg_user
POSTGRES_PASSWORD=senha_exemplo
JWT_SECRET=chave_exemplo

Nenhuma credencial real deve ser versionada.

🌍 Ambientes de Produção

Em ambientes institucionais, recomenda-se:

Uso de reverse proxy (Nginx / Gateway)

Separação de domínios para frontend, API e autenticação

Configuração adequada de CORS

Gerenciamento seguro de segredos (Vault / Secrets Manager)

📌 Status do Projeto

🔧 Em evolução contínua

🧪 Versão pública demonstrativa

🏛️ Concebido para uso institucional e governamental

📄 Observação Institucional

Este repositório tem caráter técnico e demonstrativo, com foco em:

Arquitetura

Padrões de projeto

Boas práticas de desenvolvimento

Nenhuma informação sensível, operacional ou estratégica de instituições públicas é exposta.

👤 Autor

Desenvolvido por Adriano Victor Nascimento Ribeiro
Full Stack Developer – Backend & Sistemas Institucionais
Especialista em Spring Boot, APIs REST e Arquiteturas Governamentais
