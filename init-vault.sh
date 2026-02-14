#!/usr/bin/env bash
set -euo pipefail

# Usage: ./init-vault.sh
# - waits for Vault + Postgres from docker-compose
# - creates a sample table in Postgres
# - enables approle auth and database secrets engine in Vault
# - creates AppRole and prints role_id + secret_id

VAULT_ADDR=http://127.0.0.1:8200
ROOT_TOKEN=root
DB_HOST=postgres
DB_PORT=5432
DB_NAME=demo
DB_USER=vaultdemo
DB_PASS=vaultdemo
APPROLE_NAME=app-role-demo
DB_CONFIG_NAME=my-postgresql-database
DB_ROLE_NAME=demo-role

echo "Waiting for Postgres..."
until docker exec $(docker ps -q -f name=postgres) pg_isready -U ${DB_USER} >/dev/null 2>&1; do
  sleep 1
done

echo "Initializing database schema..."
cat <<SQL | docker exec -i $(docker ps -q -f name=postgres) psql -U ${DB_USER} -d ${DB_NAME}
CREATE TABLE IF NOT EXISTS sample_table (id SERIAL PRIMARY KEY, name text);
INSERT INTO sample_table (name) VALUES ('record-1') ON CONFLICT DO NOTHING;
SQL

# Wait for Vault
echo "Waiting for Vault to be ready..."
until curl -s ${VAULT_ADDR}/v1/sys/health | grep -q "initialized"; do
  sleep 1
done

echo "Enabling AppRole auth method..."
curl -s --header "X-Vault-Token: ${ROOT_TOKEN}" --request POST ${VAULT_ADDR}/v1/sys/auth/approle -d '{"type":"approle"}' >/dev/null

# Create a policy that allows reading the DB role credentials
POLICY="path \"database/creds/${DB_ROLE_NAME}\" {\n  capabilities = [\"read\"]\n}"

echo "Writing policy 'db-role-policy'..."
curl -s --header "X-Vault-Token: ${ROOT_TOKEN}" --request PUT --data "{\"policy\": \"${POLICY}\"}" ${VAULT_ADDR}/v1/sys/policy/db-role-policy >/dev/null

# Create AppRole and attach the policy
echo "Creating AppRole ${APPROLE_NAME}..."
curl -s --header "X-Vault-Token: ${ROOT_TOKEN}" --request POST --data '{"policies":["db-role-policy"]}' ${VAULT_ADDR}/v1/auth/approle/role/${APPROLE_NAME} >/dev/null

# Fetch role_id and secret_id
ROLE_ID=$(curl -s --header "X-Vault-Token: ${ROOT_TOKEN}" ${VAULT_ADDR}/v1/auth/approle/role/${APPROLE_NAME}/role-id | jq -r .data.role_id)
SECRET_ID=$(curl -s --header "X-Vault-Token: ${ROOT_TOKEN}" --request POST ${VAULT_ADDR}/v1/auth/approle/role/${APPROLE_NAME}/secret-id | jq -r .data.secret_id)

# Enable database secrets engine
echo "Enabling database secrets engine..."
curl -s --header "X-Vault-Token: ${ROOT_TOKEN}" --request POST --data '{"type":"database"}' ${VAULT_ADDR}/v1/sys/mounts/database >/dev/null

# Configure Postgres connection for Vault (use Docker service name 'postgres')
cat <<JSON > /tmp/vault-db-config.json
{
  "plugin_name": "postgresql-database-plugin",
  "allowed_roles": "${DB_ROLE_NAME}",
  "connection_url": "postgresql://{{username}}:{{password}}@${DB_HOST}:${DB_PORT}/${DB_NAME}?sslmode=disable",
  "username": "${DB_USER}",
  "password": "${DB_PASS}"
}
JSON

curl -s --header "X-Vault-Token: ${ROOT_TOKEN}" --request POST --data @/tmp/vault-db-config.json ${VAULT_ADDR}/v1/database/config/${DB_CONFIG_NAME} >/dev/null

# Create a role that Vault will use to generate dynamic users
cat <<JSON > /tmp/vault-db-role.json
{
  "db_name": "${DB_CONFIG_NAME}",
  "creation_statements": [
    "CREATE ROLE \"{{name}}\" WITH LOGIN PASSWORD '{{password}}' VALID UNTIL '{{expiration}}';",
    "GRANT SELECT ON ALL TABLES IN SCHEMA public TO \"{{name}}\";"
  ],
  "default_ttl": "1h",
  "max_ttl": "24h"
}
JSON

curl -s --header "X-Vault-Token: ${ROOT_TOKEN}" --request POST --data @/tmp/vault-db-role.json ${VAULT_ADDR}/v1/database/roles/${DB_ROLE_NAME} >/dev/null

cat <<EOF
Initialization complete.
AppRole: ${APPROLE_NAME}
role_id: ${ROLE_ID}
secret_id: ${SECRET_ID}
Vault token (dev root): ${ROOT_TOKEN}
DB role name (to request creds): ${DB_ROLE_NAME}

Export the following environment variables before running the Spring Boot app:

export VAULT_ADDR=${VAULT_ADDR}
export VAULT_ROLE_ID=${ROLE_ID}
export VAULT_SECRET_ID=${SECRET_ID}
export DB_HOST=localhost

Then: mvn spring-boot:run

EOF