package ar.edu.unrc.cursos.delegates;

import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LiberarAulaVirtualDelegate implements JavaDelegate {
    private static final Logger log = LoggerFactory.getLogger(LiberarAulaVirtualDelegate.class);

    @Override
    public void execute(DelegateExecution execution) {
        execution.setVariable("aulaVirtualLiberada", true);
        log.info("Compensación: Aula Virtual liberada para instancia {}", execution.getProcessInstanceId());
    }
}
