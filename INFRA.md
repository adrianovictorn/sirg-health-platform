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
│           ├── brasao_conceicao.png    # brasão de Conceição do Almeida
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

### Antes de um deploy com migrations novas

Rode o script de **pré-flight** contra o banco da VPS. Ele é somente leitura e
aponta o que quebraria a migração (cargo fora da constraint, duplicidade que
impediria um índice único, migration anterior com `success = false`, etc.):

```bash
docker compose -f docker-compose.prod.yaml exec -T postgres   psql -U "$DB_USER" -d "$DB_NAME" -f -   < regulacao-backend/src/main/resources/db/preflight/preflight_v80_v84.sql
```

Leia a coluna `resultado` de cada bloco:

| Marcador | Significado |
|---|---|
| `OK` | pode prosseguir |
| `ATENCAO` | não impede o deploy, mas muda o comportamento operacional |
| `BLOQUEIO` | corrija antes de aplicar as migrations |

O script fica em `db/preflight/`, **fora** de `db/migration` — portanto o Flyway
não o executa (`spring.flyway.locations=classpath:db/migration`).

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

**Conceição do Almeida** (`MUNICIPIO_BRASAO` pode ser omitido — o docker-compose usa `brasao_conceicao.png` como default):

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

**São Felipe** (`MUNICIPIO_BRASAO` é obrigatório — sem ele o brasão exibido será o de Conceição):

```env
DB_NAME=sirg_db
DB_USER=sirg_user
DB_PASSWORD=senha_forte
JWT_SECRET=chave_longa_e_aleatoria_32_chars_minimo
RABBITMQ_USER=sirg_rabbit
RABBITMQ_PASSWORD=senha_rabbit
MUNICIPIO_NOME=SAO_FELIPE
MUNICIPIO_QUEUE=fila_sao_felipe
MUNICIPIO_NOME_DISPLAY=São Felipe
MUNICIPIO_BRASAO=brasao_saofelipe.png
APP_ORIGIN=https://sirgsaofelipe.com
NGINX_DOMAIN=sirgsaofelipe.com
WHATSAPP_VERIFY_TOKEN=<gere um valor aleatório — não copie este texto>
WHATSAPP_APP_SECRET=<chave secreta do app, copiada do painel da Meta>
WHATSAPP_ACCESS_TOKEN=<token permanente de usuário do sistema>
WHATSAPP_PHONE_NUMBER_ID=<identificação do número de telefone>
WHATSAPP_LIMITE_DIARIO=200
WHATSAPP_NUMEROS_TESTE=<55 + DDD + número, separados por vírgula; vazio = todos recebem>
```

### Webhook do WhatsApp (Meta)

Opcional e por instância. Hoje só **São Felipe** usa; Conceição do Almeida fica sem as duas
variáveis e a rota responde `404`.

| Campo no painel da Meta | Valor |
|---|---|
| URL de callback | `https://sirgsaofelipe.com/api/webhooks/whatsapp` |
| Verificar token | o mesmo valor de `WHATSAPP_VERIFY_TOKEN` |

- `WHATSAPP_VERIFY_TOKEN` — você inventa (string longa e aleatória) e repete no painel.
- `WHATSAPP_APP_SECRET` — "Chave secreta do app", em *Configurações do app → Básico* no painel
  Meta for Developers. É o que assina cada evento; sem ele nenhum POST é aceito.

**Ordem para ligar** (a Meta reenvia por dias os eventos que não recebem `200`, então o
servidor precisa estar pronto antes de salvar no painel):

1. Deploy da versão que contém a rota.
2. Incluir as duas variáveis no `.env` da VPS.
3. `docker compose -f docker-compose.prod.yaml up -d` para recriar o backend.
4. Conferir `docker logs sirg_backend | grep WhatsApp` → `Webhook do WhatsApp ligado.`
5. Só então preencher a URL e o token no painel, clicar em **Verificar e salvar** e assinar o
   campo `messages`.

**Se o painel recusar:** `404` = variáveis não chegaram ao container (precisam estar no `.env`
**e** listadas em `backend.environment` do `docker-compose.prod.yaml`); `403` = verify token
diferente do configurado.

O nginx não precisa de mudança: `location /api/` já encaminha a rota. O access log do nginx
grava a query string do GET de verificação, então o verify token aparece em
`docker logs sirg_nginx` — troque-o depois se isso incomodar.

### Envio de mensagens pelo WhatsApp

Independente do webhook acima. Sem `WHATSAPP_ACCESS_TOKEN` **e** `WHATSAPP_PHONE_NUMBER_ID`, o
envio fica "não configurado" e a instância opera como antes. Mesmo configurado, **o envio nasce
desligado**: quem liga é um ADMIN, em *Painel Admin → WhatsApp*.

| Variável | O que é | Onde pegar |
|---|---|---|
| `WHATSAPP_ACCESS_TOKEN` | Token **permanente** de um usuário do sistema, com `whatsapp_business_messaging` e `whatsapp_business_management` | Configurações do negócio → Usuários do sistema → Gerar token. O token do painel de testes expira em 24h — não serve |
| `WHATSAPP_PHONE_NUMBER_ID` | "Identificação do número de telefone" (não é o número) | Painel do app → WhatsApp → Configuração da API |
| `WHATSAPP_LIMITE_DIARIO` | Teto de mensagens por dia (padrão 200). O que passar fica como "não enviada" e **não** sai sozinha no dia seguinte | Defina abaixo do limite de conversas que o WhatsApp Manager mostra para o número |
| `WHATSAPP_NUMEROS_TESTE` | Enquanto preenchida, **só** esses números recebem | Seu celular e o de quem vai conferir |

A versão da Graph API (`app.whatsapp.envio.api-version`, padrão `v23.0`) fica em
`application.properties`. Confira a versão vigente no painel da Meta ao implantar; versão
descontinuada faz os envios falharem.

#### Templates a criar no WhatsApp Manager

Categoria **Utilidade**, idioma **Português (BR)**, corpo em texto, sem cabeçalho, rodapé ou
botões. Troque `[Município]` pelo nome do município. **Não mude a ordem das variáveis** — o
código as preenche nesta ordem. Sem os três aprovados, as mensagens falham.

**`sirg_confirmacao_agendamento`** (confirmação e remarcação)

```
Olá, {{1}}. A Central de Regulação de [Município] confirma o seu agendamento.

Atendimento: {{2}}
Local: {{3}}
Data: {{4}} ({{5}})
Horário: {{6}}
Turno: {{7}}
Profissional: {{8}}

Observações: confira as orientações no seu comprovante de agendamento ou procure a sua unidade de saúde.

Esta é uma mensagem automática. Em caso de dúvida ou se não puder comparecer, procure a sua unidade de saúde.
```

1 nome · 2 atendimento · 3 local · 4 data · 5 dia da semana · 6 horário · 7 turno · 8 profissional

**`sirg_lembrete_agendamento`**

```
Olá, {{1}}. Lembrete da Central de Regulação de [Município]: você tem um atendimento agendado para {{2}} ({{3}}).

Atendimento: {{4}}
Local: {{5}}
Horário: {{6}}
Turno: {{7}}
Profissional: {{8}}

Leve documento com foto, Cartão do SUS e o comprovante de agendamento. Confira as demais orientações no comprovante.

Esta é uma mensagem automática. Se não puder comparecer, avise a sua unidade de saúde.
```

1 nome · 2 data · 3 dia da semana · 4 atendimento · 5 local · 6 horário · 7 turno · 8 profissional

**`sirg_cancelamento_agendamento`**

```
Olá, {{1}}. A Central de Regulação de [Município] informa que o seu agendamento de {{2}} do dia {{3}} ({{4}}) foi cancelado.

Para saber o motivo ou reagendar, procure a sua unidade de saúde.

Esta é uma mensagem automática.
```

1 nome · 2 tipo ("consulta ou exame") · 3 data · 4 dia da semana

Exemplos para o formulário da Meta: `Maria`, `Cardiologia`, `Policlínica Regional - Santo Antônio
de Jesus`, `12/10/2026`, `segunda-feira`, `07:30`, `Manhã`, `Dra. Ana Lima`.

Se a Meta aprovar com outro nome, ajuste `app.whatsapp.envio.template.*`.

#### Checklist de ativação

Na ordem. Cada passo tem um motivo; não pule.

1. **Templates aprovados** no WhatsApp Manager (os três acima).
2. **Catálogo revisado:** em *Cadastrar → Especialidade*, marcar como **Sensível** tudo cujo
   nome possa revelar condição de saúde (HIV, sífilis, hepatites, psiquiatria, oncologia,
   infectologia, teste de gravidez...). Nenhuma vem marcada; sem isso o nome do exame sai na
   mensagem.
3. **`.env` da VPS** com as quatro variáveis, e `WHATSAPP_NUMEROS_TESTE` **preenchida** com o
   seu número.
4. `docker compose -f docker-compose.prod.yaml up -d backend`.
5. Em *Painel Admin → WhatsApp*, conferir "Modo de teste: Ativo" e **ligar o envio**.
6. Agendar um paciente de teste cujo telefone seja o seu. A mensagem chega em até um minuto e
   a linha aparece como "Enviada", depois "Entregue" e "Lida".
7. Conferir o texto recebido contra o comprovante.
8. Só então **esvaziar `WHATSAPP_NUMEROS_TESTE`** e recriar o backend. A partir daí os pacientes
   recebem.

**Para parar tudo na hora:** *Painel Admin → WhatsApp → Desligar envio*. Vale a partir da
próxima mensagem, sem reiniciar nada. O webhook continua respondendo à Meta.

**Diagnóstico pelo painel**

| O que aparece | Significado |
|---|---|
| "Não configurado nesta instância" | Variáveis não chegaram ao container: precisam estar no `.env` **e** em `backend.environment` do compose |
| Falhou, código `INTERROMPIDO` | A aplicação reiniciou no meio do envio; não foi repetido para não duplicar |
| Falhou, código `TIMEOUT` | A Meta não respondeu a tempo; pode ter saído. Reenvie pelo painel só se o paciente confirmar que não recebeu |
| Falhou, código `SEM_CONEXAO` ou `HTTP_5xx` | A Meta estava inacessível nas 3 tentativas |
| Falhou, código `SEM_RESPOSTA` | A conexão caiu depois de aberta; pode ter saído. Mesma orientação do `TIMEOUT` |
| Não enviada, "Data do atendimento já passou" | Agendamento antigo foi excluído ou lançado com data retroativa; o paciente não é avisado |
| Falhou, código `META_<número>` | Erro devolvido pela Cloud API (token inválido, template inexistente ou não aprovado, quantidade de variáveis diferente da do template, número sem WhatsApp). Procure o número na lista de códigos de erro da documentação da Meta |
| Não enviada, "Fora da lista de teste" | Há números em `WHATSAPP_NUMEROS_TESTE` |

**Custo:** a Meta cobra por mensagem de template entregue, pela categoria e pelo país do
destinatário. O painel mostra quantas mensagens a Meta marcou como cobráveis no período; o
valor está no WhatsApp Manager (Insights) e na fatura da conta do WhatsApp Business.

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

## Notas sobre migrations

- `spring.jpa.hibernate.ddl-auto=validate`: se o schema divergir das entidades, a
  aplicação **não sobe**. Depois de aplicar migrations novas, confirme que o
  backend iniciou antes de considerar o deploy concluído.
- As migrations V80–V84 são **aditivas**: nenhuma coluna é removida e nenhum dado
  existente é alterado. `ADD COLUMN` nullable sem default é operação de metadados
  no PostgreSQL 11+, sem reescrita de tabela.
- Uma migration que falha deixa `success = false` em `flyway_schema_history` e
  **trava o próximo deploy**. Nesse caso, rode `flyway repair` antes de tentar de
  novo (o bloco 1b do pré-flight detecta isso).

## Pendências conhecidas

- [ ] Credenciais fracas em `application.properties` (`dev_password`, `guest/guest`, JWT fraco) — trocar por valores fortes no `.env` de produção
- [ ] `version: "3.8"` obsoleto no `docker-compose.prod.yaml` — remover o atributo
- [ ] Confirmar cron de renovação SSL instalado nas duas VPS
- [ ] `springdoc` (Swagger) exposto em produção — desabilitar com `springdoc.api-docs.enabled=false`
