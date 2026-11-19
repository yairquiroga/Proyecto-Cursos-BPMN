#!/usr/bin/env bash
# =============================================================================
# Script de Ayuda para Pruebas y Demostración de Escenarios
# =============================================================================
set -e

FLOWABLE_HOST="${FLOWABLE_HOST:-localhost:8080}"
FLOWABLE_USER="${FLOWABLE_USER:-admin}"
FLOWABLE_PASS="${FLOWABLE_PASS:-test}"

echo "=========================================================="
echo "  Escenarios de Prueba y Demostración (Defensa Proyecto) "
echo "=========================================================="
echo "1) Escenario Normal: Solicitud viable estándar"
echo "2) Escenario Excepción: Simular falta de aulas disponibles (SIN_AULAS_DISPONIBLES)"
echo "3) Escenario Límite: Cargar 5 cursos para probar regla de No Viabilidad"
echo "0) Salir"
echo ""
read -p "Seleccioná el escenario a ejecutar: " OPCION

case "$OPCION" in
    1)
        echo "Iniciando solicitud estándar..."
        bash infra/scripts/iniciar_solicitud.sh 1 "Microservicios con Spring Boot"
        ;;
    2)
        echo "Iniciando solicitud con variable 'simularSinAulas = true'..."
        RESPONSE=$(curl -s -u "$FLOWABLE_USER:$FLOWABLE_PASS" \
          -X POST "http://$FLOWABLE_HOST/flowable-ui/process-api/runtime/process-instances" \
          -H "Content-Type: application/json" \
          -d '{
            "processDefinitionKey": "Process_Curso",
            "variables": [
              {"name": "empresaId", "value": 1},
              {"name": "nombreTematica", "value": "Curso con Falla Simulada de Aula"},
              {"name": "descripcion", "value": "Prueba de captura de Boundary Error"},
              {"name": "cantidadParticipantes", "value": 20},
              {"name": "fechasPreferidas", "value": "Fin de mes"},
              {"name": "duracionRecordatorio", "value": "PT1M"},
              {"name": "simularSinAulas", "value": true}
            ]
          }')
        echo "$RESPONSE"
        echo "✓ Instancia iniciada. Al llegar a 'Reservar aula', disparará el Boundary Error."
        ;;
    3)
        echo "Insertando/actualizando instancias con cursoEnDesarrollo=true..."
        echo "Para que la regla DMN dé NO VIABLE, deben existir 5 o más cursos con cursoEnDesarrollo = true."
        echo "Podés ejecutar 5 veces el flujo pasando por 'Habilitar acceso de la empresa', o simular una nueva solicitud para ver el resultado de viabilidad."
        ;;
    *)
        exit 0
        ;;
esac
