package ar.edu.unrc.cursos.webapp.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Acceso a la base de datos institucional (institucion_cursos).
 * Registra empresas, crea cursos y los vincula con instancias de Flowable.
 */
@Service
public class CursoService {

    private final JdbcTemplate jdbc;

    public CursoService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ── Empresas ─────────────────────────────────────────────────────────────

    public Map<String, Object> findEmpresaByCuit(String cuit) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM empresas WHERE cuit = ?", cuit);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * Registra una empresa nueva o devuelve la existente si ya tiene ese CUIT.
     * @return ID de la empresa
     */
    public int upsertEmpresa(String cuit, String razonSocial,
                              String contactoNombre, String contactoEmail) {
        Map<String, Object> existing = findEmpresaByCuit(cuit);
        if (existing != null) {
            return ((Number) existing.get("id")).intValue();
        }
        return jdbc.queryForObject(
                "INSERT INTO empresas (cuit, razon_social, contacto_nombre, contacto_email) " +
                "VALUES (?,?,?,?) RETURNING id",
                Integer.class, cuit, razonSocial, contactoNombre, contactoEmail);
    }

    /**
     * Crea un curso en la BD institucional ANTES de iniciar la instancia en Flowable.
     * El process_instance_id se actualiza luego con linkProcessInstance().
     */
    public int crearCurso(int empresaId, String tematica, String descripcion,
                           int cantParticipantes, String fechasPreferidas) {
        return jdbc.queryForObject(
                "INSERT INTO cursos (empresa_id, nombre_tematica, descripcion, " +
                "cantidad_participantes, fechas_preferidas, estado) " +
                "VALUES (?,?,?,?,?,'en_evaluacion') RETURNING id",
                Integer.class,
                empresaId, tematica, descripcion, cantParticipantes, fechasPreferidas);
    }

    /** Vincula el curso con la instancia de proceso Flowable */
    public void linkProcessInstance(int cursoId, String processInstanceId) {
        jdbc.update(
                "UPDATE cursos SET process_instance_id = ? WHERE id = ?",
                processInstanceId, cursoId);
    }

    /** Lista todos los cursos con datos de empresa */
    public List<Map<String, Object>> listarCursos() {
        return jdbc.queryForList(
                "SELECT c.*, e.razon_social, e.contacto_email " +
                "FROM cursos c JOIN empresas e ON c.empresa_id = e.id " +
                "ORDER BY c.fecha_creacion DESC");
    }

    /** Obtiene un curso por su process_instance_id */
    public Map<String, Object> findByProcessInstanceId(String piId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT c.*, e.razon_social FROM cursos c JOIN empresas e ON c.empresa_id = e.id " +
                "WHERE c.process_instance_id = ?", piId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** Actualiza estado del curso */
    public void actualizarEstado(String piId, String estado) {
        jdbc.update(
                "UPDATE cursos SET estado = ?, fecha_actualizacion = CURRENT_TIMESTAMP " +
                "WHERE process_instance_id = ?", estado, piId);
    }
}
