package ar.edu.unrc.cursos.webapp.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Servicio que encapsula todas las llamadas a la API REST de Flowable.
 * Usa las credenciales del admin de Flowable para operaciones del motor.
 * Para operaciones de tarea usa las credenciales del usuario logueado.
 */
@Service
public class FlowableService {

    private final WebClient adminClient;
    private final String baseUrl;
    private final String adminUser;
    private final String adminPass;

    public FlowableService(
            @Value("${flowable.rest.base-url}") String baseUrl,
            @Value("${flowable.rest.user}") String adminUser,
            @Value("${flowable.rest.password}") String adminPass) {
        this.baseUrl = baseUrl;
        this.adminUser = adminUser;
        this.adminPass = adminPass;
        this.adminClient = buildClient(adminUser, adminPass);
    }

    private WebClient buildClient(String user, String pass) {
        String credentials = Base64.getEncoder()
                .encodeToString((user + ":" + pass).getBytes());
        return WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + credentials)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    private WebClient clientFor(String user, String pass) {
        return buildClient(user, pass);
    }

    // ── Proceso ────────────────────────────────────────────────────────────

    /** Inicia una nueva instancia del proceso Process_Curso */
    public Map<?,?> iniciarProceso(Map<String, Object> variables) {
        List<Map<String,Object>> vars = variables.entrySet().stream()
                .map(e -> Map.of("name", e.getKey(), "value", e.getValue()))
                .collect(java.util.stream.Collectors.toList());

        return adminClient.post()
                .uri("/process-api/runtime/process-instances")
                .bodyValue(Map.of(
                        "processDefinitionKey", "Process_Curso",
                        "variables", vars
                ))
                .retrieve()
                .bodyToMono(Map.class)
                .block();
    }

    // ── Tareas ────────────────────────────────────────────────────────────

    /** Tareas asignadas a un usuario específico */
    public List<Map<?,?>> tareasAsignadas(String userId) {
        return adminClient.get()
                .uri("/process-api/runtime/tasks?assignee={u}&size=50", userId)
                .retrieve()
                .bodyToMono(Map.class)
                .map(r -> (List<Map<?,?>>) r.get("data"))
                .block();
    }

    /** Tareas candidatas para un grupo (sin asignar) */
    public List<Map<?,?>> tareasCandidatas(String groupId) {
        return adminClient.get()
                .uri("/process-api/runtime/tasks?candidateGroup={g}&size=50", groupId)
                .retrieve()
                .bodyToMono(Map.class)
                .map(r -> (List<Map<?,?>>) r.get("data"))
                .block();
    }

    /** Reclamar una tarea para un usuario */
    public void reclamarTarea(String taskId, String userId) {
        adminClient.post()
                .uri("/process-api/runtime/tasks/{id}", taskId)
                .bodyValue(Map.of("action", "claim", "assignee", userId))
                .retrieve()
                .bodyToMono(Void.class)
                .block();
    }

    /** Completar una tarea con variables */
    public void completarTarea(String taskId, Map<String, Object> variables) {
        List<Map<String,Object>> vars = variables.entrySet().stream()
                .map(e -> Map.of("name", e.getKey(), "value", e.getValue()))
                .collect(java.util.stream.Collectors.toList());

        adminClient.post()
                .uri("/process-api/runtime/tasks/{id}", taskId)
                .bodyValue(Map.of("action", "complete", "variables", vars))
                .retrieve()
                .bodyToMono(Void.class)
                .block();
    }

    /** Completar una tarea sin variables */
    public void completarTarea(String taskId) {
        completarTarea(taskId, Map.of());
    }

    /** Detalle de una tarea */
    public Map<?,?> getTarea(String taskId) {
        return adminClient.get()
                .uri("/process-api/runtime/tasks/{id}", taskId)
                .retrieve()
                .bodyToMono(Map.class)
                .block();
    }

    /** Variables de una instancia de proceso */
    public List<Map<?,?>> getVariables(String processInstanceId) {
        return adminClient.get()
                .uri("/process-api/runtime/process-instances/{id}/variables", processInstanceId)
                .retrieve()
                .bodyToFlux(Map.class)
                .collectList()
                .block();
    }

    /** Obtiene el valor de una variable específica */
    public Object getVariable(String processInstanceId, String varName) {
        try {
            Map<?,?> v = adminClient.get()
                    .uri("/process-api/runtime/process-instances/{id}/variables/{var}",
                            processInstanceId, varName)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
            return v != null ? v.get("value") : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** Lista todas las instancias activas del proceso */
    public List<Map<?,?>> getInstanciasActivas() {
        return adminClient.get()
                .uri("/process-api/runtime/process-instances?processDefinitionKey=Process_Curso&size=100")
                .retrieve()
                .bodyToMono(Map.class)
                .map(r -> (List<Map<?,?>>) r.get("data"))
                .block();
    }

    /** Tareas activas de una instancia (para mostrar estado de preparación) */
    public List<Map<?,?>> getTareasDeProceso(String processInstanceId) {
        return adminClient.get()
                .uri("/process-api/runtime/tasks?processInstanceId={id}&size=20", processInstanceId)
                .retrieve()
                .bodyToMono(Map.class)
                .map(r -> (List<Map<?,?>>) r.get("data"))
                .block();
    }

    /** Usuarios del grupo Docentes para selección de docente */
    public List<Map<?,?>> getDocentes() {
        return adminClient.get()
                .uri("/idm-api/users?memberOfGroup=Docentes&size=20")
                .retrieve()
                .bodyToMono(Map.class)
                .map(r -> (List<Map<?,?>>) r.get("data"))
                .block();
    }
}
