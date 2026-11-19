package ar.edu.unrc.cursos.listeners;

import ar.edu.unrc.cursos.db.DatabaseHelper;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.ExecutionListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;

public class ActualizarEstadoCursoExecutionListener implements ExecutionListener {
    private static final Logger log = LoggerFactory.getLogger(ActualizarEstadoCursoExecutionListener.class);

    @Override
    public void notify(DelegateExecution execution) {
        String processInstanceId = execution.getProcessInstanceId();
        Object resultadoObj = execution.getVariable("resultadoFinal");
        String estadoFinal = (resultadoObj != null) ? resultadoObj.toString() : "finalizado";

        String sql = "UPDATE cursos SET estado = ?, fecha_actualizacion = CURRENT_TIMESTAMP WHERE process_instance_id = ?";
        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, estadoFinal);
            ps.setString(2, processInstanceId);
            int rows = ps.executeUpdate();
            log.info("Instancia {} finalizada. Estado curso actualizado a '{}' (filas afectadas: {})",
                    processInstanceId, estadoFinal, rows);
        } catch (Exception e) {
            log.error("Error actualizando estado en BD institucional para instancia {}: {}",
                    processInstanceId, e.getMessage(), e);
        }
    }
}
