#!/usr/bin/env bash
# =============================================================================
# Simulador de comunicaciones de la empresa — API REST de Flowable
# Proyecto: Gestión y Tecnologías de Procesos de Negocio (UNRC)
# =============================================================================
set -e

FLOWABLE_HOST="${FLOWABLE_HOST:-localhost:8080}"
FLOWABLE_USER="${FLOWABLE_USER:-admin}"
FLOWABLE_PASS="${FLOWABLE_PASS:-test}"
BASE_URL="http://$FLOWABLE_HOST/flowable-ui/process-api"

# ─────────────────── Colores ───────────────────
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'
CYAN='\033[0;36m'; BOLD='\033[1m'; RESET='\033[0m'

# ─────────────────── Helpers ───────────────────
flowable_get()  { curl -sf -u "$FLOWABLE_USER:$FLOWABLE_PASS" "$BASE_URL$1"; }
flowable_post() { curl -sf -u "$FLOWABLE_USER:$FLOWABLE_PASS" -X POST -H "Content-Type: application/json" -d "$2" "$BASE_URL$1"; }
flowable_put()  { curl -sf -u "$FLOWABLE_USER:$FLOWABLE_PASS" -X PUT  -H "Content-Type: application/json" -d "$2" "$BASE_URL$1"; }

# Obtiene todas las instancias activas del proceso Process_Curso
listar_instancias() {
    flowable_get "/runtime/process-instances?processDefinitionKey=Process_Curso&size=50" \
        | python3 -c "
import sys, json
data = json.load(sys.stdin)
instances = data.get('data', [])
if not instances:
    print('  (sin instancias activas)')
else:
    for i, pi in enumerate(instances, 1):
        pid = pi['id']
        started = pi.get('startTime','?')[:19].replace('T',' ')
        print(f'  [{i}] ID: {pid}  |  Iniciado: {started}')
print(f'  Total: {len(instances)}')
" 2>/dev/null
}

# Obtiene la variable de una instancia
get_variable() {
    local pi_id="$1" varname="$2"
    flowable_get "/runtime/process-instances/$pi_id/variables/$varname" \
        | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('value',''))" 2>/dev/null || echo ""
}

# Busca la tarea activa de una instancia por nombre parcial
get_task_id() {
    local pi_id="$1" task_name_fragment="$2"
    flowable_get "/runtime/tasks?processInstanceId=$pi_id&size=20" \
        | python3 -c "
import sys, json
data = json.load(sys.stdin)
tasks = data.get('data', [])
frag = '$task_name_fragment'.lower()
for t in tasks:
    if frag in t.get('name','').lower():
        print(t['id'])
        break
" 2>/dev/null
}

# Muestra todas las tareas activas de una instancia
mostrar_tareas() {
    local pi_id="$1"
    flowable_get "/runtime/tasks?processInstanceId=$pi_id&size=20" \
        | python3 -c "
import sys, json
data = json.load(sys.stdin)
tasks = data.get('data', [])
if not tasks:
    print('  (sin tareas activas — puede estar en un evento de espera o en paralelo interno)')
else:
    for t in tasks:
        ass = t.get('assignee') or '(sin asignar — grupo candidato)'
        print(f'  • {t[\"name\"]}  [assignee: {ass}]  id: {t[\"id\"]}')
" 2>/dev/null
}

# Envía un mensaje Flowable a la instancia
enviar_mensaje() {
    local pi_id="$1" msg_name="$2" extra_vars="$3"
    local vars_json="[]"
    [ -n "$extra_vars" ] && vars_json="$extra_vars"
    flowable_post "/runtime/executions" "{
        \"action\": \"messageEventReceived\",
        \"messageName\": \"$msg_name\",
        \"processInstanceId\": \"$pi_id\",
        \"variables\": $vars_json
    }" >/dev/null 2>&1 && return 0

    # Alternativa: buscar execution con correlación
    local exec_id
    exec_id=$(flowable_get "/runtime/executions?processInstanceId=$pi_id&messageEventSubscriptionName=$msg_name" \
        | python3 -c "import sys,json; d=json.load(sys.stdin); execs=d.get('data',[]); print(execs[0]['id'] if execs else '')" 2>/dev/null)

    if [ -z "$exec_id" ]; then
        echo -e "${RED}  ✗ No se encontró una ejecución esperando el mensaje '$msg_name' en esta instancia.${RESET}"
        echo -e "${YELLOW}  Verificá que el proceso esté en el paso 'Esperar respuesta'.${RESET}"
        return 1
    fi

    flowable_put "/runtime/executions/$exec_id" "{
        \"action\": \"messageEventReceived\",
        \"messageName\": \"$msg_name\",
        \"variables\": $vars_json
    }" >/dev/null 2>&1
}

# ─────────────────── Selección de instancia ───────────────────
seleccionar_instancia() {
    echo -e "\n${CYAN}${BOLD}Instancias activas del proceso:${RESET}"
    listar_instancias

    echo -e "\n${BOLD}Ingresá el ID completo de la instancia (o 'q' para salir):${RESET}"
    read -r PI_ID
    [ "$PI_ID" = "q" ] && exit 0

    # Verificar que existe
    local check
    check=$(flowable_get "/runtime/process-instances/$PI_ID" 2>/dev/null | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('id',''))" 2>/dev/null)
    if [ -z "$check" ]; then
        echo -e "${RED}Instancia no encontrada. Verificá el ID.${RESET}"
        exit 1
    fi
    echo -e "${GREEN}✓ Instancia seleccionada: $PI_ID${RESET}"

    echo -e "\n${CYAN}Tareas activas en esta instancia:${RESET}"
    mostrar_tareas "$PI_ID"
}

# ─────────────────── Acciones ───────────────────

accion_aceptar() {
    echo -e "\n${BOLD}Fecha acordada para el curso (ISO 8601, ej: 2026-12-15T09:00:00):${RESET}"
    read -r FECHA
    [ -z "$FECHA" ] && FECHA="2026-12-20T09:00:00"

    echo -e "${YELLOW}  Enviando aceptación con fecha: $FECHA ...${RESET}"
    enviar_mensaje "$PI_ID" "Aceptacion" "[{\"name\":\"fechaCurso\",\"value\":\"$FECHA\"}]" \
        && echo -e "${GREEN}  ✓ Aceptación enviada. El proceso avanzará a 'Habilitar acceso de la empresa'.${RESET}" \
        || true
}

accion_rechazar() {
    echo -e "${YELLOW}  Enviando rechazo ...${RESET}"
    enviar_mensaje "$PI_ID" "Rechazo" "[]" \
        && echo -e "${GREEN}  ✓ Rechazo enviado. El proceso archivará la solicitud.${RESET}" \
        || true
}

accion_modificaciones() {
    echo -e "\n${BOLD}Observaciones de la empresa:${RESET}"
    read -r OBS
    [ -z "$OBS" ] && OBS="Por favor ajustar el presupuesto y las fechas propuestas."

    local obs_escaped
    obs_escaped=$(echo "$OBS" | python3 -c "import sys,json; print(json.dumps(sys.stdin.read().strip()))")

    echo -e "${YELLOW}  Enviando solicitud de modificaciones ...${RESET}"
    enviar_mensaje "$PI_ID" "Solicitud de modificaciones" "[{\"name\":\"observacionesEmpresa\",\"value\":$obs_escaped}]" \
        && echo -e "${GREEN}  ✓ Modificaciones enviadas. El jefe admin deberá revisar la propuesta.${RESET}" \
        || true
}

accion_nomina() {
    echo -e "\n${BOLD}Lista de participantes (separados por comas, ej: Juan Pérez,Ana López,...):${RESET}"
    read -r NOMINA
    [ -z "$NOMINA" ] && NOMINA="Participante 1,Participante 2,Participante 3"

    # Buscar tarea "Confeccionar lista definitiva" o variable de proceso
    # La nómina llega como mensaje Msg_nom (variable nominaParticipantes)
    local nom_escaped
    nom_escaped=$(echo "$NOMINA" | python3 -c "import sys,json; print(json.dumps(sys.stdin.read().strip()))")

    echo -e "${YELLOW}  Enviando nómina de participantes ...${RESET}"
    enviar_mensaje "$PI_ID" "Nomina de participantes" "[{\"name\":\"nominaParticipantes\",\"value\":$nom_escaped}]" \
        && echo -e "${GREEN}  ✓ Nómina enviada.${RESET}" \
        || true
}

accion_cancelar() {
    echo -e "${RED}${BOLD}  ¿Confirmar cancelación durante preparación? (s/N):${RESET}"
    read -r CONFIRMAR
    [ "$CONFIRMAR" != "s" ] && [ "$CONFIRMAR" != "S" ] && echo "  Cancelado." && return

    echo -e "${YELLOW}  Enviando cancelación ...${RESET}"
    enviar_mensaje "$PI_ID" "Cancelacion" "[]" \
        && echo -e "${GREEN}  ✓ Cancelación enviada. Se interrumpirá la preparación y se ejecutarán compensaciones.${RESET}" \
        || true
}

# ─────────────────── Menú principal ───────────────────
menu_comunicaciones() {
    local esViable
    esViable=$(get_variable "$PI_ID" "esViable" 2>/dev/null)

    echo -e "\n${CYAN}${BOLD}╔══════════════════════════════════════════════╗${RESET}"
    echo -e "${CYAN}${BOLD}║   Simulador de comunicaciones de empresa     ║${RESET}"
    echo -e "${CYAN}${BOLD}╚══════════════════════════════════════════════╝${RESET}"
    echo -e "  Instancia: ${BOLD}$PI_ID${RESET}"
    echo ""
    echo -e "  ${BOLD}1)${RESET} Aceptar propuesta        (con fecha acordada)"
    echo -e "  ${BOLD}2)${RESET} Rechazar propuesta"
    echo -e "  ${BOLD}3)${RESET} Solicitar modificaciones  (con observaciones)"
    echo -e "  ${BOLD}4)${RESET} Enviar nómina de participantes"
    echo -e "  ${BOLD}5)${RESET} Cancelar durante preparación"
    echo -e "  ${BOLD}6)${RESET} Ver tareas activas de esta instancia"
    echo -e "  ${BOLD}7)${RESET} Ver variables de proceso"
    echo -e "  ${BOLD}0)${RESET} Salir"
    echo ""
    echo -e "${BOLD}Seleccioná una opción:${RESET}"
    read -r OPCION

    case "$OPCION" in
        1) accion_aceptar ;;
        2) accion_rechazar ;;
        3) accion_modificaciones ;;
        4) accion_nomina ;;
        5) accion_cancelar ;;
        6)
            echo -e "\n${CYAN}Tareas activas:${RESET}"
            mostrar_tareas "$PI_ID"
            ;;
        7)
            echo -e "\n${CYAN}Variables de proceso:${RESET}"
            flowable_get "/runtime/process-instances/$PI_ID/variables" \
                | python3 -c "
import sys, json
vars = json.load(sys.stdin)
for v in vars:
    print(f'  {v[\"name\"]:35s} = {v[\"value\"]}')
" 2>/dev/null
            ;;
        0) exit 0 ;;
        *) echo -e "${RED}Opción inválida.${RESET}" ;;
    esac

    # Volver al menú
    menu_comunicaciones
}

# ─────────────────── Entry point ───────────────────
echo -e "\n${CYAN}${BOLD}═══════════════════════════════════════════════════════${RESET}"
echo -e "${CYAN}${BOLD}  Simulador REST — Comunicaciones de Empresa           ${RESET}"
echo -e "${CYAN}${BOLD}  Proyecto BPMN — GTPN — UNRC                         ${RESET}"
echo -e "${CYAN}${BOLD}═══════════════════════════════════════════════════════${RESET}"

seleccionar_instancia
menu_comunicaciones
