import pandas as pd
import pm4py

# Leer el CSV. Mantener los identificadores como texto.
df = pd.read_csv("eventos.csv", dtype=str)

# Conservar tareas de usuario, tareas automáticas y eventos intermedios de negociación
tipos_validos = ["userTask", "serviceTask", "intermediateCatchEvent"]
df = df[df["tipo"].isin(tipos_validos)].copy()


# Convertir fechas y conservar el orden del CSV ante empates.
df["fecha_hora"] = pd.to_datetime(df["fecha_hora"], utc=True)
df = df.sort_values(
    ["instancia", "fecha_hora"], kind="stable"
)

# Adaptar las columnas al formato utilizado por PM4Py.
log = pm4py.format_dataframe(
    df,
    case_id="instancia",
    activity_key="actividad",
    timestamp_key="fecha_hora",
)

# 1. Recorridos o variantes mÃ¡s frecuentes.
variantes = pm4py.get_variants_as_tuples(log)
total = log["case:concept:name"].nunique()

print(f"\nInstancias analizadas: {total}")
print(f"Recorridos diferentes: {len(variantes)}")

print("\nLos tres recorridos mÃ¡s frecuentes:")
for recorrido, cantidad in sorted(
    variantes.items(),
    key=lambda elemento: elemento[1],
    reverse=True,
)[:3]:
    print(f"\n{cantidad} casos ({100 * cantidad / total:.1f} %)")
    print(" â†’ ".join(recorrido))

# 2. Grafo de sucesiÃ³n directa:
# cada flecha indica cuÃ¡ntas veces una tarea siguiÃ³ a otra.
dfg, inicios, finales = pm4py.discover_dfg(log)
pm4py.save_vis_dfg(
    dfg, inicios, finales, "recorridos.png"
)

# 3. Modelo BPMN descubierto a partir de las ejecuciones.
modelo = pm4py.discover_bpmn_inductive(log)
pm4py.save_vis_bpmn(modelo, "modelo_descubierto.png")
pm4py.write_bpmn(modelo, "modelo_descubierto.bpmn")

print("\nArchivos generados:")
print("- recorridos.png")
print("- modelo_descubierto.png")
print("- modelo_descubierto.bpmn")
