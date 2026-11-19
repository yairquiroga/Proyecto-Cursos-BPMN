# Simulador de Comunicaciones de Empresa — REST API

Aplicación de línea de comandos que simula las comunicaciones que la empresa envía
a la institución a través de la API REST de Flowable.

## Uso

```bash
bash rest-simulator/simular.sh
```

El simulador:
1. Lista todas las instancias activas del proceso `Process_Curso`
2. Pedís el ID de la instancia con la que querés interactuar
3. Muestra un menú con las comunicaciones disponibles

## Comunicaciones disponibles

| Opción | Comunicación | Cuándo usarla |
|---|---|---|
| 1 | **Aceptar propuesta** | Cuando el proceso está esperando respuesta (`Esperar respuesta`) |
| 2 | **Rechazar propuesta** | Ídem anterior |
| 3 | **Solicitar modificaciones** | Ídem anterior |
| 4 | **Enviar nómina de participantes** | Durante la preparación del curso |
| 5 | **Cancelar durante preparación** | Mientras el subproceso `Preparar curso` está activo |

## Variables de entorno

```bash
FLOWABLE_HOST=localhost:8080   # por defecto
FLOWABLE_USER=admin            # por defecto
FLOWABLE_PASS=test             # por defecto
```

Ejemplo con variables personalizadas:

```bash
FLOWABLE_HOST=192.168.1.10:8080 bash rest-simulator/simular.sh
```

## Dependencias

- `bash`
- `curl`
- `python3` (para parsear JSON)
