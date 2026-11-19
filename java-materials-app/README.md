# Aplicación de Escritorio - Preparación de Materiales (API Java Flowable)

Implementación requerida por la **Sección 4.2.1** del proyecto.
Permite a los integrantes del grupo **Administración** interactuar con las tareas de preparación de materiales utilizando directamente la **API Java de Flowable** conectada a la base de datos del motor.

## Funcionalidades
1. **Identificación de usuario**: Selección o ingreso del usuario administrativo (`jefe_admin`, `admin_2`).
2. **Consulta de tareas**: Lista las tareas "Preparar materiales didácticos" asignadas al usuario o disponibles para el grupo `Administracion`.
3. **Reclamar tarea**: Asigna una tarea disponible al usuario activo mediante `taskService.claim(taskId, userId)`.
4. **Completar tarea**: Finaliza la preparación del material didáctico mediante `taskService.complete(taskId)`.

## Ejecución

### Modo Interfaz Gráfica (Swing)
Si tu entorno tiene soporte gráfico:
```bash
bash java-materials-app/run.sh
```

### Modo Consola / Terminal (CLI)
Si estás en una terminal sin servidor gráfico (o preferís modo texto):
```bash
bash java-materials-app/run.sh --cli
```

## Variables de Entorno Opcionales
- `FLOWABLE_DB_URL`: URL JDBC a la BD de Flowable (por defecto: `jdbc:postgresql://localhost:5432/flowable`)
- `FLOWABLE_DB_USER`: Usuario de la BD (por defecto: `flowable`)
- `FLOWABLE_DB_PASS`: Contraseña de la BD (por defecto: `flowablepassword`)
