#!/usr/bin/env bash

# configuration
DIR=./pki/CA
NAME=rootserver

SSL_CNF=${DIR}/openssl.cnf
PRIVATE=${DIR}/private
CERTS=${DIR}/certs
NEWCERTS=${DIR}/newcerts
CRL=${DIR}/crl

CA_CRT=${CERTS}/ca.crt
CA_KEY=${PRIVATE}/ca.key

SERVER_CRT=${CERTS}/${NAME}.crt
SERVER_KEY=${PRIVATE}/${NAME}.key
SERVER_PEM=${PRIVATE}/${NAME}.pem
SERVER_P12=${PRIVATE}/${NAME}.p12
SERVER_JKS=${PRIVATE}/${NAME}.jks

echo "Generate Certificate Request"
openssl req -config ${SSL_CNF} -new -nodes -keyout ${SERVER_KEY} -out ${NAME}.csr -days 365
chmod 0400 ${SERVER_KEY}

echo "Sign Certificate Request"
openssl ca -config ${SSL_CNF} -policy policy_anything -out ${SERVER_CRT} -infiles ${NAME}.csr
rm ${NAME}.csr

echo "Create PEM"
cat ${SERVER_CRT} ${SERVER_KEY} > ${SERVER_PEM}
chmod 0400 ${SERVER_PEM}

echo "Create PKCS12"
openssl pkcs12 -export -in ${SERVER_PEM} -out ${SERVER_P12}
chmod 0400 ${SERVER_P12}

echo "Create JKS"
keytool -importkeystore -srckeystore ${SERVER_P12} -srcstoretype PKCS12 -destkeystore ${SERVER_JKS} -deststoretype JKS
chmod 0400 ${SERVER_JKS}
