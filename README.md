# Sistema de Gestión y Organización de Cursos de Capacitación (Flowable BPMN 2.0)

Proyecto desarrollado para la asignatura **Gestión y Tecnologías de Procesos de Negocio** (UNRC). Implementa el proceso integral de solicitud, evaluación de viabilidad, negociación de propuestas, preparación, ejecución y cierre de cursos de capacitación empresarial sobre el motor **Flowable**.

---

## 1. Arquitectura y Estructura del Proyecto

```text
proyecto-flowable-cursos/
├── docker-compose.yml          # Orquestación de PostgreSQL y Flowable UI/REST
├── .env                        # Variables de entorno para Docker Compose
├── flowable-lib/               # JAR compilado con Delegates/Listeners (inyectado al motor)
│   └── motor-extensions-1.0.0.jar
├── infra/
│   ├── db/
│   │   └── init/
│   │       └── 01_init.sql     # Esquema y datos de prueba de la BD institucional
│   ├── scripts/
│   │   ├── deploy_models.sh    # Despliega BPMN y DMN en Flowable via REST
│   │   └── iniciar_solicitud.sh # Inicia una instancia del proceso via REST
│   └── setup_idm.sh            # Crea usuarios y grupos en Flowable IDM
├── data/
│   └── informes/               # Almacenamiento de documentos generados
├── motor-extensions/           # Proyecto Maven con Delegates, Listeners, BPMN y DMN
│   └── src/main/
│       ├── java/ar/edu/unrc/cursos/
│       │   ├── delegates/      # JavaDelegates de tareas de servicio
│       │   ├── listeners/      # ExecutionListeners y TaskListeners
│       │   └── db/             # DatabaseHelper (conexión a BD institucional)
│       └── resources/
│           ├── processes/      # BPMN del proceso principal
│           └── dmn/            # Tabla de decisión de viabilidad
├── web-app/                    # Sitio web institucional
├── java-materials-app/         # App de escritorio Java (Flowable Java API)
└── rest-simulator/             # Simulador REST de comunicaciones con empresa
```

---

## 2. Requisitos

| Herramienta | Versión mínima |
|---|---|
| Docker + Docker Compose | Docker 24+ |
| Java (JDK) | **11** (⚠️ ver nota abajo) |
| Maven | 3.8+ |

> [!IMPORTANT]
> **El JAR de extensiones DEBE compilarse con Java 11 (`--release 11`).**
> La imagen `flowable/flowable-ui:6.8.0` corre con Java 11 internamente.
> Compilar con Java 17 o superior genera bytecode (major version ≥ 61) que Flowable
> no puede cargar, lanzando `couldn't instantiate class` al ejecutar cualquier delegate.
> El `pom.xml` ya está configurado con `<maven.compiler.release>11</maven.compiler.release>`.

---

## 3. Puesta en marcha (desde cero)

### 3.1 Compilar las extensiones

```bash
cd motor-extensions
mvn clean package
cp target/motor-extensions-1.0.0.jar ../flowable-lib/
cd ..
```

### 3.2 Levantar los contenedores

```bash
docker compose up -d postgres flowable
```

Verificar que ambos estén sanos:

```bash
docker compose ps
```

### 3.3 Crear usuarios y grupos en Flowable IDM

Solo es necesario la primera vez (el script es idempotente):

```bash
bash infra/setup_idm.sh
```

Usuarios creados (contraseña `test` para todos):

| Usuario | Rol | Grupo |
|---|---|---|
| `admin` | Administrador Flowable | — |
| `jefe_admin` | Jefe Administrativo | Administracion |
| `admin_2` | Personal Administrativo | Administracion |
| `jefe_docente` | Jefe de Docentes | Docentes |
| `docente_2` | Docente | Docentes |
| `docente_3` | Docente | Docentes |

### 3.4 Desplegar BPMN y DMN

```bash
bash infra/scripts/deploy_models.sh
```

### 3.5 Iniciar una instancia del proceso

```bash
bash infra/scripts/iniciar_solicitud.sh
```

También se puede pasar empresa ID y temática como argumentos:

```bash
bash infra/scripts/iniciar_solicitud.sh 2 "Docker y Kubernetes"
```

---

## 4. Configuración de Docker (detalles importantes)

### Cómo se inyecta el JAR de extensiones

El JAR `motor-extensions-1.0.0.jar` se monta directamente en `/app/WEB-INF/lib/` del contenedor:

```yaml
volumes:
  - ./flowable-lib/motor-extensions-1.0.0.jar:/app/WEB-INF/lib/motor-extensions-1.0.0.jar:ro
```

Este es el directorio que el `LaunchedURLClassLoader` de Spring Boot escanea para cargar clases externas. **No usar** `entrypoint` personalizado con `-cp` — eso no es compatible con la forma en que Flowable resuelve delegates internamente.

### Reiniciar Flowable después de cambios en el JAR

```bash
# Recompilar
cd motor-extensions && mvn clean package && cp target/motor-extensions-1.0.0.jar ../flowable-lib/ && cd ..

# Reiniciar el contenedor para que tome el nuevo JAR
docker compose up -d --force-recreate flowable

# Redesplegar modelos si también cambió el BPMN o DMN
bash infra/scripts/deploy_models.sh
```

---

## 5. Delegates y Listeners implementados

| Clase | Tipo | Descripción |
|---|---|---|
| `ContarCursosEnDesarrolloDelegate` | JavaDelegate | Cuenta instancias activas con `cursoEnDesarrollo=true` |
| `HabilitarAccesoEmpresaDelegate` | JavaDelegate | Marca hito de desarrollo y habilita cuenta en BD |
| `NotificarEmpresaDelegate` | JavaDelegate | Registra notificaciones (propuesta, recordatorio, cierre, etc.) |
| `ReservarAulaDelegate` | JavaDelegate | Simula reserva de aula física (lanza BpmnError si `simularSinAulas=true`) |
| `LiberarAulaDelegate` | JavaDelegate | Compensación: libera aula física reservada |
| `LiberarAulaVirtualDelegate` | JavaDelegate | Compensación: libera aula virtual |
| `ActualizarEstadoCursoExecutionListener` | ExecutionListener | Al finalizar el proceso, actualiza estado en BD institucional |
| `AsignarListaDefinitivaTaskListener` | TaskListener | Asigna la tarea al miembro de Administración con menos carga |

---

## 6. Regla de negocio — Viabilidad de solicitud

Una solicitud es **viable** si hay menos de 5 "cursos en desarrollo" al momento de evaluarse.

**Definición de "curso en desarrollo":** toda instancia del proceso `Process_Curso` que:
1. Ya pasó por la tarea de servicio **"Habilitar acceso de la empresa"** (tiene variable `cursoEnDesarrollo = true`), **Y**
2. Todavía **no finalizó**.

La evaluación se hace en dos pasos automáticos al iniciar cada instancia:
1. `ContarCursosEnDesarrolloDelegate` → cuenta y guarda `cantidadCursosEnDesarrollo`
2. DMN `Decision_Viabilidad` → evalúa el conteo y devuelve `esViable` (booleano)

> [!NOTE]
> Puede haber momentalmente más de 5 cursos simultáneos: la regla solo se verifica
> al evaluar **cada nueva solicitud**. Si ya pasaron por "Habilitar acceso" antes de
> que se evaluara la restricción, siguen su curso normalmente.

---

## 7. Flujo del proceso (resumen)

```
Solicitud recibida
  → Contar cursos en desarrollo
  → Evaluar viabilidad (DMN)
  → [No viable] → Notificar empresa → FIN
  → [Viable]
      → Registrar y Elaborar propuesta (jefe_admin)
      → Enviar propuesta a empresa
      → Esperar respuesta (con recordatorios periódicos)
        ├─ Aceptación → Habilitar acceso empresa
        │                → Preparar curso (sub-proceso)
        │                    → Reservar aula (con compensación si falla)
        │                → Confirmar fecha
        │                → Definir docente (jefe_docente)
        │                → Esperar fecha programada
        │                → Desarrollar curso (docente)
        │                → Elaborar informe (jefe_docente)
        │                → Enviar informe y archivar (jefe_admin) → FIN
        ├─ Rechazo → Archivar expediente → FIN
        └─ Sin respuesta (2 recordatorios) → Archivar expediente → FIN
```

---

## 8. Acceso a Flowable UI

URL: [http://localhost:8080/flowable-ui](http://localhost:8080/flowable-ui)

| Usuario | Contraseña | Ver tareas de |
|---|---|---|
| `admin` | `test` | Todo (administrador) |
| `jefe_admin` | `test` | Propuesta, Revisar propuesta, Enviar informe |
| `jefe_docente` | `test` | Informe de resultados, Definir docente |
| `docente_2` / `docente_3` | `test` | Desarrollar curso |

---

## 9. Troubleshooting

### "couldn't instantiate class ar.edu.unrc.cursos.delegates.XxxDelegate"

**Causa:** El JAR fue compilado con Java 17+ pero Flowable UI 6.8.0 corre con Java 11.

**Solución:** Verificar que `pom.xml` tenga `<maven.compiler.release>11</maven.compiler.release>` y recompilar:

```bash
cd motor-extensions && mvn clean package
javap -verbose target/classes/ar/edu/unrc/cursos/delegates/ContarCursosEnDesarrolloDelegate.class | grep "major version"
# Debe decir: major version: 55  (= Java 11)
cp target/motor-extensions-1.0.0.jar ../flowable-lib/
docker compose up -d --force-recreate flowable
```

### El proceso no aparece en "Start process" de la UI

El proceso se despliega vía REST, no como App de Flowable. Iniciarlo desde:
- El script `bash infra/scripts/iniciar_solicitud.sh`, o
- Directamente desde la UI de **Tasks → Processes** (no desde el Modeler).

### Cambios en el BPMN no se reflejan

```bash
bash infra/scripts/deploy_models.sh
```

Cada ejecución crea una nueva versión del deployment. Las instancias existentes siguen
corriendo en la versión anterior.


---

## 10. Aplicaciones Externas

El proyecto cuenta con las tres aplicaciones requeridas por el enunciado:

### 10.1 Simulador REST de Comunicaciones de la Empresa (`rest-simulator/`)

Permite seleccionar cualquier instancia activa del proceso y disparar las comunicaciones pertinentes de la empresa a través de la API REST de Flowable:
- **Aceptar propuesta** (ingresando fecha acordada `fechaCurso`).
- **Rechazar propuesta**.
- **Solicitar modificaciones** (con observaciones).
- **Enviar nómina de participantes**.
- **Cancelar durante la preparación** (interrumpe el subproceso y ejecuta compensaciones).

Ejecución interactiva:
```bash
bash rest-simulator/simular.sh
```

---

### 10.2 Aplicación de Materiales - API Java (`java-materials-app/`)

Aplicación de escritorio conectada directamente a la base de datos de Flowable mediante la **API Java del motor** (`TaskService` y `IdentityService`):
- Permite autenticar a un administrativo (`jefe_admin`, `admin_2`).
- Consulta tareas "Preparar materiales didácticos" asignadas o del grupo.
- Reclama tareas disponibles.
- Completa la preparación de materiales didácticos.

Ejecución (con GUI Swing o modo consola si no hay servidor gráfico):
```bash
bash java-materials-app/run.sh
# O explícitamente en modo texto:
bash java-materials-app/run.sh --cli
```

---

### 10.3 Sitio Web Institucional (`web-app/`)

Desarrollado en Spring Boot + Thymeleaf con Spring Security autenticando contra Flowable IDM:
- **Público (`/solicitud/nueva`)**: Formulario para que las empresas soliciten capacitaciones, registrando en BD institucional e iniciando la instancia en Flowable.
- **Administración (`/admin/tareas`)**: Gestión de tareas asignadas y candidatas (reclamar, registrar propuesta, revisar, confeccionar lista definitiva, evaluar recursos y archivar).
- **Jefe Docente (`/docente/tareas`)**: Consulta y asignación del docente para dictar el curso (`Definir docente`).

Ejecución:
```bash
cd web-app
mvn spring-boot:run
```
Acceso en navegador: `http://localhost:8081`

---

## 11. Alcance de Implementación (Exclusiones / Marcado en Azul)

Conforme a las especificaciones de la cátedra (texto en azul no requerido):
1. **Sección 4.1.2 ("Acceso y seguimiento para las empresas")**: No implementado.
2. **Sección 4.1.3 puntos 3 y 4**: No se generan documentos PDF para elaboración ni revisión de propuestas (las tareas se completan mediante formularios web o Flowable UI).
3. **Sección 4.1.4**: El docente asignado completa la tarea *"Desarrollar curso"* directamente desde la UI de Flowable tras la fecha programada.
