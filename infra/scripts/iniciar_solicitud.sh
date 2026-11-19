#!/usr/bin/env bash
set -e

FLOWABLE_HOST="${FLOWABLE_HOST:-localhost:8080}"
FLOWABLE_USER="${FLOWABLE_USER:-admin}"
FLOWABLE_PASS="${FLOWABLE_PASS:-test}"

EMPRESA_ID="${1:-1}"
TEMATICA="${2:-Microservicios con Spring Boot}"

echo "=== Iniciando nueva instancia de Solicitud de Curso ==="
echo "Empresa ID: $EMPRESA_ID"
echo "Temática:   $TEMATICA"

RESPONSE=$(curl -s -u "$FLOWABLE_USER:$FLOWABLE_PASS" \
  -X POST "http://$FLOWABLE_HOST/flowable-ui/process-api/runtime/process-instances" \
  -H "Content-Type: application/json" \
  -d "{
    \"processDefinitionKey\": \"Process_Curso\",
    \"variables\": [
      {\"name\": \"empresaId\", \"value\": $EMPRESA_ID},
      {\"name\": \"nombreTematica\", \"value\": \"$TEMATICA\"},
      {\"name\": \"descripcion\", \"value\": \"Capacitación técnica solicitada por la empresa.\"},
      {\"name\": \"cantidadParticipantes\", \"value\": 15},
      {\"name\": \"fechasPreferidas\", \"value\": \"Primera quincena del próximo mes\"},
      {\"name\": \"duracionRecordatorio\", \"value\": \"PT1M\"}
    ]
  }")

echo "Respuesta del motor:"
echo "$RESPONSE"

PI_ID=$(echo "$RESPONSE" | grep -o '"id":"[^"]*' | head -n1 | cut -d'"' -f4)

if [ -n "$PI_ID" ]; then
  echo ""
  echo ">>> ¡Instancia iniciada con éxito! ID: $PI_ID <<<"
  echo "La evaluación de viabilidad (DMN) ya se ejecutó automáticamente."
  echo "Ahora podés ingresar a Flowable UI como 'jefe_admin' (password: test) para ver la tarea 'Registrar y Elaborar propuesta'."
else
  echo ""
  echo "Error al iniciar la instancia. Revisá la respuesta anterior."
fi
