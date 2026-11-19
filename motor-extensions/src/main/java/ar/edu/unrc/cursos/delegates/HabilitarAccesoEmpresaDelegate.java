package ar.edu.unrc.cursos.delegates;

import ar.edu.unrc.cursos.db.DatabaseHelper;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class HabilitarAccesoEmpresaDelegate implements JavaDelegate {
    private static final Logger log = LoggerFactory.getLogger(HabilitarAccesoEmpresaDelegate.class);

    @Override
    public void execute(DelegateExecution execution) {
        // Marca hito de "curso en desarrollo" para la regla de negocio de viabilidad
        execution.setVariable("cursoEnDesarrollo", true);

        String processInstanceId = execution.getProcessInstanceId();
        Object empresaIdObj = execution.getVariable("empresaId");
        if (empresaIdObj == null) {
            log.warn("No se recibió variable 'empresaId' en instancia {}", processInstanceId);
            return;
        }
        int empresaId = Integer.parseInt(empresaIdObj.toString());

        try (Connection conn = DatabaseHelper.getConnection()) {
            // Verificar si la empresa ya tenía cuenta habilitada
            String checkSql = "SELECT cuenta_habilitada, contacto_email FROM empresas WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(checkSql)) {
                ps.setInt(1, empresaId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        boolean habilitada = rs.getBoolean("cuenta_habilitada");
                        String email = rs.getString("contacto_email");
                        if (!habilitada) {
                            String updateEmp = "UPDATE empresas SET cuenta_habilitada = TRUE, password_hash = COALESCE(password_hash, 'empresa123') WHERE id = ?";
                            try (PreparedStatement up = conn.prepareStatement(updateEmp)) {
                                up.setInt(1, empresaId);
                                up.executeUpdate();
                            }
                            log.info("Cuenta creada y habilitada para empresa ID {} ({}) con clave inicial 'empresa123'", empresaId, email);
                        } else {
                            log.info("Cuenta existente reutilizada para empresa ID {} ({})", empresaId, email);
                        }
                    }
                }
            }

            // Actualizar estado del curso a en_preparacion
            String updateCurso = "UPDATE cursos SET estado = 'en_preparacion', fecha_actualizacion = CURRENT_TIMESTAMP WHERE process_instance_id = ?";
            try (PreparedStatement upC = conn.prepareStatement(updateCurso)) {
                upC.setString(1, processInstanceId);
                upC.executeUpdate();
            }
        } catch (Exception e) {
            log.error("Error habilitando acceso de empresa en BD: {}", e.getMessage(), e);
        }
    }
}
