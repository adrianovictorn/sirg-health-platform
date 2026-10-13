#!/bin/sh
# Gera a configuração do nginx a partir dos templates e sobe o nginx.
#
# A landing é opcional e nunca pode impedir o nginx de subir: se faltar
# LANDING_DOMAIN, certificado ou a configuração não passar em `nginx -t`,
# ela é descartada e o sistema do município sobe como sempre.
set -eu

SRC=/etc/nginx/sirg
OUT=/etc/nginx/conf.d

envsubst '${NGINX_DOMAIN}' < "$SRC/nginx.conf" > "$OUT/default.conf"
rm -f "$OUT/landing.conf"

if [ -n "${LANDING_DOMAIN:-}" ]; then
  envsubst '${LANDING_DOMAIN}' < "$SRC/landing.http.conf" > "$OUT/landing.conf"

  CERT_DIR="/etc/letsencrypt/live/${LANDING_DOMAIN}"
  if [ -f "$CERT_DIR/fullchain.pem" ] && [ -f "$CERT_DIR/privkey.pem" ]; then
    envsubst '${LANDING_DOMAIN}' < "$SRC/landing.https.conf" >> "$OUT/landing.conf"
    echo "[sirg-nginx] landing em ${LANDING_DOMAIN}: HTTP e HTTPS"
  else
    echo "[sirg-nginx] AVISO: sem certificado em ${CERT_DIR}; landing so na porta 80 (emita o certificado e reinicie o nginx)"
  fi

  if ! nginx -t > /dev/null 2>&1; then
    echo "[sirg-nginx] AVISO: configuracao da landing invalida; ignorada. Saida do nginx -t:"
    rm -f "$OUT/landing.conf"
    nginx -t || true
  fi
else
  echo "[sirg-nginx] landing desligada (LANDING_DOMAIN vazio)"
fi

exec nginx -g 'daemon off;'
