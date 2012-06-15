#!/usr/bin/env bash

# configuration
DIR=./pki/CA
SSL_CNF_BASE=/etc/ssl/openssl.cnf

SSL_CNF=${DIR}/openssl.cnf
PRIVATE=${DIR}/private
CERTS=${DIR}/certs
NEWCERTS=${DIR}/newcerts
CRL=${DIR}/crl

CA_CRT=${CERTS}/ca.crt
CA_KEY=${PRIVATE}/ca.key

### Build a CA (see http://www.g-loaded.eu/2005/11/10/be-your-own-ca/) ###
mkdir -m 0755 -p ${DIR} ${PRIVATE} ${CERTS} ${NEWCERTS} ${CRL}

cp ${SSL_CNF_BASE} ${SSL_CNF}
chmod 0600 ${SSL_CNF}
touch ${DIR}/index.txt
echo '01' > ${DIR}/serial

# update openssl.cnf
sed "s%^dir.*%dir = ${DIR}%" -i ${SSL_CNF}
sed "s%^certificate.*%certificate = \$dir/certs/ca.crt%" -i ${SSL_CNF}
sed "s%^private_key.*%private_key = \$dir/private/ca.key%" -i ${SSL_CNF}
${EDITOR} ${SSL_CNF}
chmod 0600 ${SSL_CNF}

echo "Creating CA Certificate and Key"
openssl req -config ${SSL_CNF} -new -x509 -extensions v3_ca -keyout ${CA_KEY} -out ${CA_CRT} -days 1825
chmod 0400 ${CA_KEY}
