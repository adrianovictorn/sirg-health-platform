# 🏛️ SIRG – Sistema Integrado de Regulação e Gestão

O **SIRG (Sistema Integrado de Regulação e Gestão)** é uma **plataforma institucional** desenvolvida para apoiar a **gestão, regulação e organização de serviços públicos**, com foco em **escala, segurança e padronização de processos**.

O projeto foi concebido com **arquitetura moderna**, separação clara de responsabilidades e boas práticas de engenharia de software, sendo aplicável a cenários reais de administração pública.

---

## 🎯 Objetivo do Sistema

O SIRG tem como objetivo central:

- Centralizar informações institucionais  
- Organizar fluxos de regulação e gestão  
- Oferecer APIs seguras e escaláveis  
- Facilitar integrações com outros sistemas governamentais  
- Servir como base para evolução contínua de módulos públicos  

---

## 🧩 Principais Características

- Arquitetura **full stack** moderna  
- API REST segura com **autenticação e autorização**  
- Separação clara entre **backend, frontend e infraestrutura**  
- Suporte a múltiplos módulos institucionais  
- Preparado para ambientes **on-premise** ou **cloud**  
- Estrutura pensada para **governança, manutenção e escalabilidade**

---

## 🏗️ Arquitetura (Visão Geral)

```text
Regula-o/
├── regulacao-backend/          # API REST (Spring Boot 3.4 / Java 21)
├── regulacao-frontend/         # Aplicação Web (SvelteKit 2 / Svelte 5)
├── nginx/                      # Template de proxy reverso (produção)
├── docker-compose.prod.yaml    # Orquestração de produção
├── CHANGELOG.md
├── DOCUMENTACAO_TECNICA.md
├── INFRA.md
└── README.md
```

---

## 📚 Documentação

| Arquivo | Para quê |
|---|---|
| [DOCUMENTACAO_TECNICA.md](DOCUMENTACAO_TECNICA.md) | Referência completa: entidades, DTOs, fluxos, banco, endpoints, segurança e guia de execução |
| [CHANGELOG.md](CHANGELOG.md) | O que mudou em cada versão, com a causa raiz das correções |
| [INFRA.md](INFRA.md) | Deploy, VPS, SSL, multi-município e checklist de migrations |

**Começando pelo código?** Leia a `DOCUMENTACAO_TECNICA.md` — ela é escrita para que
alguém consiga compreender, manter e evoluir o sistema sem depender de explicações
adicionais.

---

## 🚀 Execução Rápida (desenvolvimento)

```bash
# Backend — requer PostgreSQL em localhost:5432
cd regulacao-backend
./mvnw spring-boot:run          # http://localhost:8080

# Frontend — proxy /api/** aponta para o backend automaticamente
cd regulacao-frontend
npm install && npm run dev      # http://localhost:5173
```

Detalhes de pré-requisitos, criação do banco e execução dos testes:
[DOCUMENTACAO_TECNICA.md §12](DOCUMENTACAO_TECNICA.md#12-guia-de-execução).
