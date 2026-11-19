package ar.edu.unrc.cursos.listeners;

import org.flowable.engine.IdentityService;
import org.flowable.engine.TaskService;
import org.flowable.engine.delegate.TaskListener;
import org.flowable.engine.impl.context.Context;
import org.flowable.idm.api.User;
import org.flowable.task.service.delegate.DelegateTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.List;

public class AsignarListaDefinitivaTaskListener implements TaskListener {
    private static final Logger log = LoggerFactory.getLogger(AsignarListaDefinitivaTaskListener.class);

    @Override
    public void notify(DelegateTask delegateTask) {
        IdentityService identityService = Context.getProcessEngineConfiguration().getIdentityService();
        TaskService taskService = Context.getProcessEngineConfiguration().getTaskService();

        List<User> administrativos = identityService.createUserQuery()
                .memberOfGroup("Administracion")
                .list();

        if (administrativos.isEmpty()) {
            log.warn("No se encontraron usuarios en el grupo Administracion; se asigna por defecto a jefe_admin.");
            delegateTask.setAssignee("jefe_admin");
            return;
        }

        // Ordenar por cantidad de tareas activas ASIGNADAS (excluye tareas candidatas sin reclamar)
        User seleccionado = administrativos.stream()
                .min(Comparator.comparingLong((User u) ->
                        taskService.createTaskQuery().taskAssignee(u.getId()).active().count()
                ).thenComparing(User::getId))
                .orElse(administrativos.get(0));

        long cargaActual = taskService.createTaskQuery().taskAssignee(seleccionado.getId()).active().count();
        delegateTask.setAssignee(seleccionado.getId());
        log.info("Tarea 'Confeccionar lista definitiva' asignada a '{}' (tareas pendientes asignadas: {})",
                seleccionado.getId(), cargaActual);
    }
}
