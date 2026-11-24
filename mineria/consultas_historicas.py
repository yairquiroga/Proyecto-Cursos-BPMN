#!/usr/bin/env python3
"""
consultas_historicas.py - Análisis de las ejecuciones del proceso (Sección 4.3)
Proyecto: Gestión y Organización de Cursos de Capacitación (Flowable BPMN 2.0)

Implementa las tres consultas requeridas por la Sección 4.3 del enunciado:
  4.3.1 Resultados de las solicitudes (cantidades y porcentajes sobre instancias terminadas)
  4.3.2 Análisis de la preparación paralela (duración promedio de cada rama)
  4.3.3 Distribución de tareas completadas por usuario (administrativos y docentes)
"""

import sys
from datetime import datetime
from collections import Counter
import requests

BASE_URL = "http://localhost:8080/flowable-ui/process-api"
PROCESS_KEY = "Process_Curso"


def parse_dt(s):
    if not s:
        return None
    return datetime.fromisoformat(s.replace("Z", "+00:00"))


def format_segundos(s):
    if s is None:
        return "N/D"
    mins, segs = divmod(int(round(s)), 60)
    horas, mins = divmod(mins, 60)
    if horas > 0:
        return f"{horas}h {mins}m {segs}s ({s:.1f} s)"
    elif mins > 0:
        return f"{mins}m {segs}s ({s:.1f} s)"
    return f"{s:.1f} s"


def consultar_todos(session, recurso, filtros):
    inicio = 0
    while True:
        resp = session.get(
            f"{BASE_URL}/{recurso}",
            params={**filtros, "start": inicio, "size": 100},
            timeout=30
        )
        resp.raise_for_status()
        pagina = resp.json()
        registros = pagina.get("data", [])
        if not registros:
            break
        yield from registros
        inicio += len(registros)
        if inicio >= pagina.get("total", 0):
            break


def main():
    usuario = sys.argv[1] if len(sys.argv) > 1 else "admin"
    clave = sys.argv[2] if len(sys.argv) > 2 else "test"

    session = requests.Session()
    session.auth = (usuario, clave)

    print("=" * 70)
    print(" ANÁLISIS DE LAS EJECUCIONES DEL PROCESO (Sección 4.3 - Flowable)")
    print("=" * 70)

    # 1. Recuperar todas las instancias terminadas del proceso
    instancias_raw = list(consultar_todos(
        session,
        "history/historic-process-instances",
        {"processDefinitionKey": PROCESS_KEY, "finished": "true"}
    ))

    # Excluir eliminadas administrativamente
    instancias = [i for i in instancias_raw if not i.get("deleteReason")]
    total_terminadas = len(instancias)

    if total_terminadas == 0:
        print("No se encontraron instancias terminadas para el proceso.")
        return

    # =========================================================================
    # 4.3.1 Resultados de las solicitudes
    # =========================================================================
    categorias = {
        "Curso finalizado": 0,
        "Cancelación": 0,
        "Rechazo de la propuesta": 0,
        "Solicitud no viable": 0,
        "Cierre por falta de respuesta": 0,
    }

    preparaciones_validas = []

    for inst in instancias:
        iid = inst["id"]
        acts = list(consultar_todos(
            session,
            "history/historic-activity-instances",
            {"processInstanceId": iid}
        ))
        act_ids = [a.get("activityId") for a in acts]

        # Determinar resultado de la solicitud
        if "Fin" in act_ids or inst.get("endActivityId") == "Fin":
            categorias["Curso finalizado"] += 1
            preparaciones_validas.append(acts)
        elif "FinCancelado" in act_ids or inst.get("endActivityId") == "FinCancelado":
            categorias["Cancelación"] += 1
        elif "FinNoViable" in act_ids or inst.get("endActivityId") == "FinNoViable":
            categorias["Solicitud no viable"] += 1
        elif "FinSinCurso" in act_ids or inst.get("endActivityId") == "FinSinCurso":
            if "Rechazar" in act_ids:
                categorias["Rechazo de la propuesta"] += 1
            else:
                categorias["Cierre por falta de respuesta"] += 1

    print("\n--- 4.3.1 Resultados de las solicitudes ---")
    print(f"Total de instancias terminadas evaluadas: {total_terminadas}\n")
    print(f"{'Resultado':<35} | {'Cantidad':<10} | {'Porcentaje':<12}")
    print("-" * 62)
    for res, cant in categorias.items():
        pct = (cant / total_terminadas) * 100.0 if total_terminadas > 0 else 0.0
        print(f"{res:<35} | {cant:<10} | {pct:>6.2f} %")

    # =========================================================================
    # 4.3.2 Análisis de la preparación paralela
    # =========================================================================
    print("\n--- 4.3.2 Análisis de la preparación paralela ---")
    print(f"Instancias con preparación completada normalmente: {len(preparaciones_validas)}")

    duraciones_inscripciones = []
    duraciones_materiales = []
    duraciones_recursos = []

    for acts in preparaciones_validas:
        # Inicio común de las ramas paralelas: ParInicio o SubInicio
        par_inicio_list = [a for a in acts if a.get("activityId") == "ParInicio" and a.get("endTime")]
        if not par_inicio_list:
            continue
        t_inicio_ramas = parse_dt(par_inicio_list[0]["endTime"])

        # Rama 1: Inscripciones (desde ParInicio hasta Lista definitiva completada)
        lista_list = [a for a in acts if a.get("activityId") == "Lista" and a.get("endTime")]
        if lista_list:
            t_fin = parse_dt(lista_list[0]["endTime"])
            dur = (t_fin - t_inicio_ramas).total_seconds()
            if dur >= 0:
                duraciones_inscripciones.append(dur)

        # Rama 2: Materiales (desde ParInicio hasta Material completada)
        material_list = [a for a in acts if a.get("activityId") == "Material" and a.get("endTime")]
        if material_list:
            t_fin = parse_dt(material_list[0]["endTime"])
            dur = (t_fin - t_inicio_ramas).total_seconds()
            if dur >= 0:
                duraciones_materiales.append(dur)

        # Rama 3: Recursos (desde ParInicio hasta OrFin completada)
        or_fin_list = [a for a in acts if a.get("activityId") == "OrFin" and a.get("endTime")]
        if or_fin_list:
            t_fin = parse_dt(or_fin_list[0]["endTime"])
            dur = (t_fin - t_inicio_ramas).total_seconds()
            if dur >= 0:
                duraciones_recursos.append(dur)

    prom_inscrip = (sum(duraciones_inscripciones) / len(duraciones_inscripciones)) if duraciones_inscripciones else None
    prom_mater = (sum(duraciones_materiales) / len(duraciones_materiales)) if duraciones_materiales else None
    prom_recurs = (sum(duraciones_recursos) / len(duraciones_recursos)) if duraciones_recursos else None

    print(f"{'Rama de preparación':<30} | {'Muestras':<10} | {'Duración promedio':<25}")
    print("-" * 70)
    print(f"{'Inscripciones (Nómina y Lista)':<30} | {len(duraciones_inscripciones):<10} | {format_segundos(prom_inscrip):<25}")
    print(f"{'Materiales didácticos':<30} | {len(duraciones_materiales):<10} | {format_segundos(prom_mater):<25}")
    print(f"{'Recursos (Aulas/Equipos)':<30} | {len(duraciones_recursos):<10} | {format_segundos(prom_recurs):<25}")

    # =========================================================================
    # 4.3.3 Distribución de tareas
    # =========================================================================
    print("\n--- 4.3.3 Distribución de tareas completadas ---")
    tareas_raw = list(consultar_todos(
        session,
        "history/historic-task-instances",
        {"processDefinitionKey": PROCESS_KEY, "finished": "true"}
    ))

    # Excluir tareas canceladas/interrumpidas (sin endTime o con deleteReason)
    tareas = [t for t in tareas_raw if t.get("endTime") and not t.get("deleteReason")]

    conteo_usuario = Counter()
    conteo_tarea_usuario = {}

    for t in tareas:
        asignado = t.get("assignee") or "Sin asignar"
        nombre_tarea = t.get("name") or t.get("taskDefinitionKey")
        conteo_usuario[asignado] += 1
        conteo_tarea_usuario.setdefault(asignado, Counter())[nombre_tarea] += 1

    total_tareas = sum(conteo_usuario.values())
    print(f"Total de tareas de usuario completadas: {total_tareas}\n")
    print(f"{'Usuario / Rol':<25} | {'Tareas completadas':<20} | {'Porcentaje':<12}")
    print("-" * 62)
    for usr, cant in conteo_usuario.most_common():
        pct = (cant / total_tareas) * 100.0 if total_tareas > 0 else 0.0
        print(f"{usr:<25} | {cant:<20} | {pct:>6.2f} %")

    print("\nDetalle de tareas por usuario:")
    for usr, subtareas in conteo_tarea_usuario.items():
        print(f"\n  [{usr}] (Total: {sum(subtareas.values())}):")
        for st, c in subtareas.items():
            print(f"    - {st}: {c}")

    print("\n" + "=" * 70)


if __name__ == "__main__":
    main()
