package ar.edu.unrc.cursos.delegates;

import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LiberarAulaDelegate implements JavaDelegate {
    private static final Logger log = LoggerFactory.getLogger(LiberarAulaDelegate.class);

    @Override
    public void execute(DelegateExecution execution) {
        Object aula = execution.getVariable("aulaAsignada");
        execution.setVariable("aulaReservada", false);
        log.info("Compensación Web Service: Aula '{}' liberada para instancia {}", aula, execution.getProcessInstanceId());
    }
}
