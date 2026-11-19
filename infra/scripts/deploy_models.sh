#!/usr/bin/env bash
set -e

FLOWABLE_HOST="${FLOWABLE_HOST:-localhost:8080}"
FLOWABLE_USER="${FLOWABLE_USER:-admin}"
FLOWABLE_PASS="${FLOWABLE_PASS:-test}"

BPMN_FILE="motor-extensions/src/main/resources/processes/Proceso_Organizacion_Cursos.bpmn"
DMN_FILE="motor-extensions/src/main/resources/dmn/viabilidad.dmn"

echo "=== Despliegue de Modelos a Flowable ($FLOWABLE_HOST) ==="

echo "1. Verificando disponibilidad de Flowable Engine..."
until curl -s -u "$FLOWABLE_USER:$FLOWABLE_PASS" "http://$FLOWABLE_HOST/flowable-ui/process-api/repository/deployments" > /dev/null 2>&1; do
    echo "   Esperando a Flowable..."
    sleep 3
done
echo "   -> Flowable está activo."

echo "2. Desplegando Tabla de Decisión DMN ($DMN_FILE)..."
DMN_RES=$(curl -s -u "$FLOWABLE_USER:$FLOWABLE_PASS" -X POST \
    "http://$FLOWABLE_HOST/flowable-ui/dmn-api/dmn-repository/deployments" \
    -F "file=@$DMN_FILE")

echo "   Respuesta DMN: $DMN_RES"

echo "3. Desplegando Proceso BPMN ($BPMN_FILE)..."
BPMN_RES=$(curl -s -u "$FLOWABLE_USER:$FLOWABLE_PASS" -X POST \
    "http://$FLOWABLE_HOST/flowable-ui/process-api/repository/deployments" \
    -F "file=@$BPMN_FILE")

echo "   Respuesta BPMN: $BPMN_RES"
echo "=== Despliegue completado con éxito ==="
