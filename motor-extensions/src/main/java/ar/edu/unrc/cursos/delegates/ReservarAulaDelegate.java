package ar.edu.unrc.cursos.delegates;

import org.flowable.engine.delegate.BpmnError;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReservarAulaDelegate implements JavaDelegate {
    private static final Logger log = LoggerFactory.getLogger(ReservarAulaDelegate.class);

    @Override
    public void execute(DelegateExecution execution) {
        Object simularFallo = execution.getVariable("simularSinAulas");
        if (simularFallo != null && Boolean.parseBoolean(simularFallo.toString())) {
            log.warn("Simulando falta de aulas disponibles para instancia {}", execution.getProcessInstanceId());
            throw new BpmnError("SIN_AULAS_DISPONIBLES", "No hay aulas físicas disponibles para la fecha solicitada.");
        }

        String aulaAsignada = "Aula Magna - Pabellón 2";
        execution.setVariable("aulaAsignada", aulaAsignada);
        execution.setVariable("aulaReservada", true);
        log.info("Web Service simulado: Aula '{}' reservada con éxito para instancia {}", aulaAsignada, execution.getProcessInstanceId());
    }
}
