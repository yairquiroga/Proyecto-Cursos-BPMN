package ar.edu.unrc.cursos.webapp.controller;

import ar.edu.unrc.cursos.webapp.service.CursoService;
import ar.edu.unrc.cursos.webapp.service.FlowableService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;

/**
 * Controlador para la sección pública:
 * - Formulario de solicitud de capacitación (sección 4.1.1)
 * - Login
 * - Portal de acceso (redirección según rol)
 */
@Controller
public class PublicoController {

    private final CursoService cursoService;
    private final FlowableService flowableService;

    public PublicoController(CursoService cursoService, FlowableService flowableService) {
        this.cursoService = cursoService;
        this.flowableService = flowableService;
    }

    @GetMapping("/")
    public String index() {
        return "publico/index";
    }

    @GetMapping("/login")
    public String loginForm() {
        return "publico/login";
    }

    @GetMapping("/portal")
    public String portal(org.springframework.security.core.Authentication auth) {
        if (auth == null) return "redirect:/login";
        boolean esAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        return esAdmin ? "redirect:/admin/tareas" : "redirect:/docente/tareas";
    }

    // ── Solicitud pública de capacitación (4.1.1) ──────────────────────────

    @GetMapping("/solicitud/nueva")
    public String formularioSolicitud() {
        return "publico/solicitud";
    }

    @PostMapping("/solicitud")
    public String procesarSolicitud(
            @RequestParam String cuit,
            @RequestParam String razonSocial,
            @RequestParam String contactoNombre,
            @RequestParam String contactoEmail,
            @RequestParam String tematica,
            @RequestParam String descripcion,
            @RequestParam int cantidadParticipantes,
            @RequestParam String fechasPreferidas,
            RedirectAttributes redirectAttrs) {

        try {
            // 1. Crear/buscar empresa en BD institucional
            int empresaId = cursoService.upsertEmpresa(cuit, razonSocial, contactoNombre, contactoEmail);

            // 2. Crear registro del curso en BD
            int cursoId = cursoService.crearCurso(empresaId, tematica, descripcion,
                    cantidadParticipantes, fechasPreferidas);

            // 3. Iniciar instancia en Flowable
            Map<?,?> resultado = flowableService.iniciarProceso(Map.of(
                    "empresaId", empresaId,
                    "cursoId", cursoId,
                    "nombreTematica", tematica,
                    "descripcion", descripcion,
                    "cantidadParticipantes", cantidadParticipantes,
                    "fechasPreferidas", fechasPreferidas,
                    "duracionRecordatorio", "PT5M"   // 5 min para pruebas (producción: P5D)
            ));

            String piId = (String) resultado.get("id");

            // 4. Vincular proceso con curso en BD
            cursoService.linkProcessInstance(cursoId, piId);

            redirectAttrs.addFlashAttribute("exito",
                    "✓ Solicitud registrada con éxito. ID de seguimiento: " + piId);
            return "redirect:/solicitud/nueva";

        } catch (Exception e) {
            redirectAttrs.addFlashAttribute("error", "Error al registrar la solicitud: " + e.getMessage());
            return "redirect:/solicitud/nueva";
        }
    }
}
