# SIRG — Documentação de Infraestrutura

## Visão Geral

O sistema roda em **duas VPS independentes**, uma por município, compartilhando o mesmo repositório Git. Cada instância tem comportamento específico definido pelo arquivo `.env` local.

| Município | Domínio | VPS |
|-----------|---------|-----|
| Conceição do Almeida | https://sirg.com.br | VPS 1 (`77.37.69.236`) |
| São Felipe | https://sirgsaofelipe.com | VPS 2 |

---

## Arquitetura Multi-município

### Como funciona

Um único codebase serve os dois municípios. O que diferencia cada instância é o `.env` na VPS:

```env
MUNICIPIO_NOME=CONCEICAO_DO_ALMEIDA      # identificador no banco
MUNICIPIO_QUEUE=fila_conceicao_do_almeida
MUNICIPIO_NOME_DISPLAY=Conceição do Almeida  # nome exibido no frontend/relatórios
```

O backend lê essas variáveis via `InstanceContext.java`, que as injeta em qualquer serviço Spring que precisar do contexto do município atual.

### Para features exclusivas de um município

```java
if (instanceContext.getMunicipioLocal().getNome().equals("SAO_FELIPE")) {
    // lógica exclusiva de São Felipe
}
```

---

## Estrutura de arquivos relevantes

```
.
├── .env.example                        # modelo de variáveis para cada VPS
├── docker-compose.prod.yaml            # stack Docker (postgres, rabbitmq, backend, frontend, nginx)
├── nginx/nginx.conf                    # template nginx (usa envsubst com $NGINX_DOMAIN)
├── .github/workflows/deploy.yml        # CI/CD GitHub Actions
├── regulacao-backend/
│   └── src/main/resources/
│       ├── application.properties      # config base (sobrescrita por env vars)
│       ├── application-conceicao.properties
│       └── images/
│           ├── brasao.png              # brasão de Conceição do Almeida
│           └── brasao_saofelipe.png    # brasão de São Felipe
```

---

## Deploy Automático (GitHub Actions)

O arquivo `.github/workflows/deploy.yml` dispara automaticamente a cada push na branch `main`, ou manualmente via `workflow_dispatch`.

### Ambientes e Secrets

**Environment `conceicao`** (Settings → Environments → conceicao):

| Secret | Valor |
|--------|-------|
| `VPS_HOST` | IP da VPS 1 |
| `VPS_SSH_KEY` | Chave privada SSH (`~/.ssh/github_deploy` na VPS 1) |

**Environment `saofelipe`** (Settings → Environments → saofelipe):

| Secret | Valor |
|--------|-------|
| `VPS_HOST` | IP da VPS 2 |
| `VPS_USER` | `root` |
| `VPS_SSH_KEY` | Chave privada SSH (`~/.ssh/github_deploy` na VPS 2) |
| `VPS_PATH` | `/root/sirg-health-platform` |

### O que o deploy faz em cada VPS

```bash
git pull origin main
docker compose -f docker-compose.prod.yaml up -d --build
docker image prune -f
```

### Deploy seletivo

Via `workflow_dispatch` é possível escolher `ambos`, `conceicao` ou `saofelipe`.

---

## Configuração das VPS

### Pré-requisitos em cada VPS

- Docker Engine 29+
- Docker Compose plugin (`~/.docker/cli-plugins/docker-compose`)
- Certbot (`apt-get install -y certbot`)
- SSH key em `~/.ssh/github_deploy` com pública em `~/.ssh/authorized_keys`
- Porta 22 e 80 e 443 abertas no UFW **e** no firewall do painel Hostinger

### Arquivo `.env` em cada VPS

Baseado no `.env.example`. Nunca commitado. Fica em `/root/<repo>/.env`.

```env
DB_NAME=sirg_db
DB_USER=sirg_user
DB_PASSWORD=senha_forte
JWT_SECRET=chave_longa_e_aleatoria_32_chars_minimo
RABBITMQ_USER=sirg_rabbit
RABBITMQ_PASSWORD=senha_rabbit
MUNICIPIO_NOME=CONCEICAO_DO_ALMEIDA
MUNICIPIO_QUEUE=fila_conceicao_do_almeida
MUNICIPIO_NOME_DISPLAY=Conceição do Almeida
APP_ORIGIN=https://sirg.com.br
NGINX_DOMAIN=sirg.com.br
```

---

## SSL / HTTPS

Certificados Let's Encrypt gerenciados pelo Certbot instalado na VPS (fora do Docker). O nginx dentro do Docker monta `/etc/letsencrypt` como volume read-only.

### Obter certificado (primeira vez)

```bash
docker compose -f docker-compose.prod.yaml stop nginx
certbot certonly --standalone -d <dominio> --non-interactive --agree-tos --email <email>
docker compose -f docker-compose.prod.yaml up -d nginx
```

### Renovação automática (cron)

```bash
# Verificar cron instalado:
crontab -l

# Instalar se não tiver:
echo "0 3 * * * certbot renew --quiet && docker compose -f /root/<repo>/docker-compose.prod.yaml restart nginx" | crontab -
```

### Como o nginx usa o domínio

O `nginx/nginx.conf` é um template com `${NGINX_DOMAIN}`. O docker-compose usa `envsubst` para processar o template na inicialização do container, substituindo pelo valor do `.env`.

---

## Banco de Dados

### Flyway

O banco de produção foi criado antes do Flyway ser introduzido. Para corrigir a validação, foram inseridos registros do tipo `BASELINE` na tabela `flyway_schema_history` para as migrações V1–V5 e V15:

```sql
INSERT INTO flyway_schema_history (installed_rank, version, description, type, script, checksum, installed_by, installed_on, execution_time, success)
VALUES
  (62, '1',  'Create Tables',             'BASELINE', 'V1__Create_Tables.sql',             NULL, 'system', NOW(), 0, TRUE),
  (63, '2',  'Inserindo coluna',          'BASELINE', 'V2__Inserindo_coluna.sql',          NULL, 'system', NOW(), 0, TRUE),
  (64, '3',  'Criando Usuarios',          'BASELINE', 'V3__Criando_Usuarios.sql',          NULL, 'system', NOW(), 0, TRUE),
  (65, '4',  'Alterando Os Enums',        'BASELINE', 'V4__Alterando_Os_Enums.sql',        NULL, 'system', NOW(), 0, TRUE),
  (66, '5',  'Resolvendo',               'BASELINE', 'V5__Resolvendo.sql',               NULL, 'system', NOW(), 0, TRUE),
  (67, '15', 'Adicionando Proctologista', 'BASELINE', 'V15__Adicionando_Proctologista.sql', NULL, 'system', NOW(), 0, TRUE);
```

### Backup

```bash
su - postgres -c "pg_dump <banco> > /tmp/backup.sql"
```

---

## Adicionando um novo município

1. Provisionar VPS com Docker
2. Clonar o repositório
3. Criar `.env` baseado no `.env.example` com os valores do novo município
4. Adicionar brasão em `regulacao-backend/src/main/resources/images/brasao_<municipio>.png`
5. Adicionar case em `ExcelService.getBrasaoPath()` para o novo `MUNICIPIO_NOME`
6. Obter certificado SSL com certbot
7. Criar environment no GitHub com secrets `VPS_HOST`, `VPS_USER`, `VPS_SSH_KEY`, `VPS_PATH`
8. Adicionar job no `.github/workflows/deploy.yml` e opção no `workflow_dispatch`

---

## Pendências conhecidas

- [ ] Credenciais fracas em `application.properties` (`dev_password`, `guest/guest`, JWT fraco) — trocar por valores fortes no `.env` de produção
- [ ] `version: "3.8"` obsoleto no `docker-compose.prod.yaml` — remover o atributo
- [ ] Confirmar cron de renovação SSL instalado nas duas VPS
- [ ] `springdoc` (Swagger) exposto em produção — desabilitar com `springdoc.api-docs.enabled=false`
