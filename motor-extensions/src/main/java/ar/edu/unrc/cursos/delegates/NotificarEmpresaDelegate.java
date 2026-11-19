package ar.edu.unrc.cursos.delegates;

import ar.edu.unrc.cursos.db.DatabaseHelper;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.common.engine.api.delegate.Expression;
import org.flowable.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;

public class NotificarEmpresaDelegate implements JavaDelegate {
    private static final Logger log = LoggerFactory.getLogger(NotificarEmpresaDelegate.class);
    private Expression tipoNotificacion;

    @Override
    public void execute(DelegateExecution execution) {
        String tipo = (tipoNotificacion != null) ? tipoNotificacion.getValue(execution).toString() : "GENERAL";
        String piId = execution.getProcessInstanceId();

        if ("RECORDATORIO".equals(tipo)) {
            int rec = execution.getVariable("recordatorios") != null
                    ? Integer.parseInt(execution.getVariable("recordatorios").toString()) : 0;
            execution.setVariable("recordatorios", rec + 1);
            log.info("[NOTIFICACIÓN EMPRESA] Recordatorio #{} enviado para instancia {}", rec + 1, piId);
            return;
        }

        if ("NO_VIABLE".equals(tipo)) {
            execution.setVariable("resultadoFinal", "no_viable");
        } else if ("CANCELACION".equals(tipo)) {
            execution.setVariable("resultadoFinal", "cancelado");
        } else if ("CONFIRMACION_CURSO".equals(tipo)) {
            actualizarEstadoIntermedio(piId, "confirmado");
        }

        log.info("[NOTIFICACIÓN EMPRESA] Tipo='{}' enviada para instancia {}", tipo, piId);
    }

    private void actualizarEstadoIntermedio(String piId, String nuevoEstado) {
        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement("UPDATE cursos SET estado = ? WHERE process_instance_id = ?")) {
            ps.setString(1, nuevoEstado);
            ps.setString(2, piId);
            ps.executeUpdate();
        } catch (Exception e) {
            log.warn("No se pudo actualizar estado intermedio a '{}': {}", nuevoEstado, e.getMessage());
        }
    }
}
