import csv
from datetime import datetime
from getpass import getpass

import requests


# ConfiguraciÃ³n
BASE_URL = "http://localhost:8080/flowable-ui/process-api"

# ID del elemento <process id="..."> del BPMN.
PROCESS_KEY = "Process_Curso"

# Opcional: copiar aquí los IDs BPMN de las actividades y eventos
# de negociación que se quieran analizar.
# Vacío = exportar todos los elementos históricos finalizados.
ACTIVIDADES = {
    "Propuesta",         # Registrar y Elaborar propuesta (userTask)
    "EnviarPropuesta",   # Enviar propuesta (serviceTask)
    "Aceptar",           # Aceptación (intermediateCatchEvent)
    "Rechazar",          # Rechazo (intermediateCatchEvent)
    "Modificar",         # Solicitud de modificaciones (intermediateCatchEvent)
    "Plazo",             # Plazo respuesta (intermediateCatchEvent - timer)
    "Recordatorio",      # Enviar recordatorio (serviceTask)
    "Revisar",           # Revisar propuesta (userTask)
    "Archivar",          # Archivar solicitud (userTask)
    "HabilitarAcceso",   # Habilitar acceso de la empresa (serviceTask)
}

ARCHIVO_SALIDA = "eventos.csv"


def consultar_todos(session, recurso, filtros):
    """Recupera todas las pÃ¡ginas de una consulta REST."""
    inicio = 0

    while True:
        respuesta = session.get(
            f"{BASE_URL}/{recurso}",
            params={
                **filtros,
                "start": inicio,
                "size": 100,
            },
            timeout=30,
        )
        respuesta.raise_for_status()
        pagina = respuesta.json()
        registros = pagina["data"]

        if not registros:
            break

        yield from registros
        inicio += len(registros)

        if inicio >= pagina["total"]:
            break


def fecha_iso(valor):
    return datetime.fromisoformat(valor.replace("Z", "+00:00"))


def main():
    import sys
    if len(sys.argv) >= 3:
        usuario = sys.argv[1]
        clave = sys.argv[2]
    else:
        usuario = input("Usuario de Flowable: ")
        clave = getpass("Contraseña: ")

    eventos = []

    with requests.Session() as session:
        session.auth = (usuario, clave)

        instancias = consultar_todos(
            session,
            "history/historic-process-instances",
            {
                "processDefinitionKey": PROCESS_KEY,
                "finished": "true",
            },
        )

        for instancia in instancias:
            # Excluir instancias eliminadas administrativamente.
            if instancia.get("deleteReason"):
                continue

            actividades = consultar_todos(
                session,
                "history/historic-activity-instances",
                {"processInstanceId": instancia["id"]},
            )

            for actividad in actividades:
                # No exportar elementos sin fecha de finalizaciÃ³n.
                if not actividad.get("endTime"):
                    continue

                if (
                    ACTIVIDADES
                    and actividad["activityId"] not in ACTIVIDADES
                ):
                    continue

                eventos.append({
                    "instancia": instancia["id"],
                    "definicion": actividad["processDefinitionId"],
                    "evento_id": actividad["id"],
                    "actividad_id": actividad["activityId"],
                    "actividad": (
                        actividad.get("activityName")
                        or actividad["activityId"]
                    ),
                    "tipo": actividad["activityType"],
                    "inicio": actividad["startTime"],
                    "fecha_hora": actividad["endTime"],
                    "recurso": actividad.get("assignee") or "",
                })

    # Ordenar por instancia y fecha de finalizaciÃ³n.
    # El ID da un orden estable si coinciden las marcas temporales.
    eventos.sort(
        key=lambda evento: (
            evento["instancia"],
            fecha_iso(evento["fecha_hora"]),
            fecha_iso(evento["inicio"]),
            evento["evento_id"],
        )
    )

    columnas = [
        "instancia", "definicion", "evento_id",
        "actividad_id", "actividad", "tipo",
        "inicio", "fecha_hora", "recurso",
    ]

    with open(
        ARCHIVO_SALIDA, "w", newline="", encoding="utf-8"
    ) as archivo:
        escritor = csv.DictWriter(archivo, fieldnames=columnas)
        escritor.writeheader()
        escritor.writerows(eventos)

    cantidad_instancias = len({
        evento["instancia"] for evento in eventos
    })

    print(
        f"Generado {ARCHIVO_SALIDA}: "
        f"{len(eventos)} eventos de {cantidad_instancias} instancias."
    )


if __name__ == "__main__":
    main()
