package ar.edu.unrc.cursos.webapp.controller;

import ar.edu.unrc.cursos.webapp.service.FlowableService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.*;

/**
 * Funciones para el Jefe del Equipo Docente (sección 4.1.4):
 * - Ver tareas "Definir docente"
 * - Seleccionar docente y asignar a tarea "Desarrollar curso"
 * - Completar tarea
 *
 * NO implementado (azul): que el docente consulte y complete "Desarrollar curso"
 * → esa tarea se completa desde la UI de Flowable.
 */
@Controller
@RequestMapping("/docente")
public class DocenteController {

    private final FlowableService flowableService;

    public DocenteController(FlowableService flowableService) {
        this.flowableService = flowableService;
    }

    @GetMapping("/tareas")
    public String listaTareas(Model model, Authentication auth) {
        String userId = auth.getName();

        // Jefe docente: solo ve sus tareas asignadas + candidatas del grupo
        List<Map<?,?>> asignadas = flowableService.tareasAsignadas(userId);
        List<Map<?,?>> candidatas = flowableService.tareasCandidatas("Docentes");
        candidatas = candidatas.stream()
                .filter(t -> t.get("assignee") == null)
                .collect(java.util.stream.Collectors.toList());

        model.addAttribute("asignadas", asignadas);
        model.addAttribute("candidatas", candidatas);
        model.addAttribute("userId", userId);
        return "docente/tareas";
    }

    // ── Definir docente ───────────────────────────────────────────────────────

    @GetMapping("/tareas/{taskId}/definir-docente")
    public String formDefinirDocente(@PathVariable String taskId, Model model) {
        Map<?,?> tarea = flowableService.getTarea(taskId);
        List<Map<?,?>> docentes = flowableService.getDocentes();

        model.addAttribute("tarea", tarea);
        model.addAttribute("docentes", docentes);
        return "docente/definir_docente";
    }

    @PostMapping("/tareas/{taskId}/definir-docente")
    public String completarDefinirDocente(@PathVariable String taskId,
                                           @RequestParam String docenteSeleccionado,
                                           RedirectAttributes redirectAttrs) {
        // La variable "docenteSeleccionado" será usada por el BPMN
        // como assignee de la tarea "Desarrollar curso"
        flowableService.completarTarea(taskId, Map.of("docenteSeleccionado", docenteSeleccionado));
        redirectAttrs.addFlashAttribute("exito",
                "Docente '" + docenteSeleccionado + "' asignado al curso con éxito.");
        return "redirect:/docente/tareas";
    }
}
