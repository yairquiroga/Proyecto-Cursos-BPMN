package ar.edu.unrc.cursos.delegates;

import org.flowable.engine.RuntimeService;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.flowable.engine.impl.context.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ContarCursosEnDesarrolloDelegate implements JavaDelegate {
    private static final Logger log = LoggerFactory.getLogger(ContarCursosEnDesarrolloDelegate.class);

    @Override
    public void execute(DelegateExecution execution) {
        RuntimeService runtimeService = Context.getProcessEngineConfiguration().getRuntimeService();
        String currentInstanceId = execution.getProcessInstanceId();

        // Cuenta instancias activas del proceso (todas las versiones) que ya pasaron por "Habilitar acceso de la empresa"
        long count = runtimeService.createProcessInstanceQuery()
                .processDefinitionKey("Process_Curso")
                .variableValueEquals("cursoEnDesarrollo", true)
                .active()
                .list()
                .stream()
                .filter(pi -> !pi.getId().equals(currentInstanceId))
                .count();

        execution.setVariable("cantidadCursosEnDesarrollo", (int) count);
        log.info("Evaluación de viabilidad para instancia {}: {} cursos en desarrollo activos.", currentInstanceId, count);
    }
}
