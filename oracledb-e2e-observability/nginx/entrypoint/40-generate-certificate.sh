#!/bin/sh
set -eu
case "$CERT_DNS_NAME" in
    ''|*[!a-zA-Z0-9.-]*) echo "CERT_DNS_NAME must be a DNS hostname" >&2; exit 1 ;;
esac
umask 077
mkdir -p /etc/nginx/certs /lab-ca
chmod 755 /lab-ca
if [ ! -f /lab-ca/ca.key ]; then
    openssl req -x509 -nodes -newkey rsa:2048 -sha256 -days 3650 \
        -keyout /lab-ca/ca.key -out /lab-ca/ca.crt \
        -subj '/CN=Oracle AI Database Observability Lab CA' \
        -addext 'basicConstraints=critical,CA:TRUE' \
        -addext 'keyUsage=critical,keyCertSign,cRLSign'
    chmod 644 /lab-ca/ca.crt
fi
openssl req -new -nodes -newkey rsa:2048 -sha256 \
    -keyout /etc/nginx/certs/server.key -out /etc/nginx/certs/server.csr \
    -subj "/CN=$CERT_DNS_NAME"
cat > /etc/nginx/certs/extensions.cnf <<EXT
subjectAltName=DNS:$CERT_DNS_NAME,DNS:localhost,DNS:nginx,IP:127.0.0.1,IP:::1
extendedKeyUsage=serverAuth
basicConstraints=critical,CA:FALSE
keyUsage=critical,digitalSignature,keyEncipherment
EXT
openssl x509 -req -sha256 -days 30 -in /etc/nginx/certs/server.csr \
    -CA /lab-ca/ca.crt -CAkey /lab-ca/ca.key -CAcreateserial \
    -extfile /etc/nginx/certs/extensions.cnf -out /etc/nginx/certs/server.crt
