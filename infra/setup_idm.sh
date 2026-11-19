#!/usr/bin/env bash
set -e

BASE_URL="http://localhost:8080/flowable-ui/idm-api"
AUTH="admin:test"

echo "1. Esperando a que Flowable IDM esté disponible..."
until curl -s -u "$AUTH" "$BASE_URL/groups" > /dev/null; do
  sleep 2
done
echo "   -> Flowable IDM activo."

echo "2. Creando grupos..."
curl -s -u "$AUTH" -H "Content-Type: application/json" -X POST "$BASE_URL/groups" \
  -d '{"id":"Administracion","name":"Administración","type":"assignment"}' > /dev/null || true

curl -s -u "$AUTH" -H "Content-Type: application/json" -X POST "$BASE_URL/groups" \
  -d '{"id":"Docentes","name":"Docentes","type":"assignment"}' > /dev/null || true
echo "   -> Grupos 'Administracion' y 'Docentes' creados."

create_user() {
  local id=$1
  local first=$2
  local last=$3
  local email=$4
  local group=$5

  echo "3. Configurando usuario '$id' (grupo: $group)..."
  # Crear usuario
  curl -s -u "$AUTH" -H "Content-Type: application/json" -X POST "$BASE_URL/users" \
    -d "{\"id\":\"$id\",\"firstName\":\"$first\",\"lastName\":\"$last\",\"email\":\"$email\",\"password\":\"test\"}" > /dev/null || true

  # Actualizar/Fijar contraseña explícitamente en Flowable IDM
  curl -s -u "$AUTH" -H "Content-Type: application/json" -X PUT "$BASE_URL/users/$id" \
    -d "{\"firstName\":\"$first\",\"lastName\":\"$last\",\"email\":\"$email\",\"password\":\"test\"}" > /dev/null || true

  # Asignar a grupo
  curl -s -u "$AUTH" -H "Content-Type: application/json" -X POST "$BASE_URL/groups/$group/members" \
    -d "{\"userId\":\"$id\"}" > /dev/null || true
}

# Usuarios de Administración
create_user "jefe_admin" "Carlos" "JefeAdmin" "jefe.admin@institucion.edu.ar" "Administracion"
create_user "admin_2" "Ana" "Administrativa" "ana.admin@institucion.edu.ar" "Administracion"

# Usuarios de Docentes
create_user "jefe_docente" "Roberto" "JefeDocente" "jefe.docente@institucion.edu.ar" "Docentes"
create_user "docente_2" "Lucía" "Docente" "lucia.docente@institucion.edu.ar" "Docentes"
create_user "docente_3" "Diego" "Docente" "diego.docente@institucion.edu.ar" "Docentes"

echo "4. Habilitando privilegios de acceso a aplicaciones (Task / Modeler / IDM)..."
PRIVILEGES=$(curl -s -u "$AUTH" "$BASE_URL/privileges")
for priv in "access-task" "access-modeler" "access-idm"; do
  PRIV_ID=$(echo "$PRIVILEGES" | grep -o "\"id\":\"[^\"]*\",\"name\":\"$priv\"" | cut -d'"' -f4)
  if [ -n "$PRIV_ID" ]; then
    curl -s -u "$AUTH" -H "Content-Type: application/json" -X POST "$BASE_URL/privileges/$PRIV_ID/groups" \
      -d '{"groupId":"Administracion"}' > /dev/null || true
    curl -s -u "$AUTH" -H "Content-Type: application/json" -X POST "$BASE_URL/privileges/$PRIV_ID/groups" \
      -d '{"groupId":"Docentes"}' > /dev/null || true
    curl -s -u "$AUTH" -H "Content-Type: application/json" -X POST "$BASE_URL/privileges/$PRIV_ID/users" \
      -d '{"userId":"jefe_admin"}' > /dev/null || true
  fi
done

echo "¡Usuarios, contraseñas y permisos actualizados con éxito! (Contraseña: test)"
