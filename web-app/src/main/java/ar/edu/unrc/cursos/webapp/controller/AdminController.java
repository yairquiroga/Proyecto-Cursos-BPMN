package ar.edu.unrc.cursos.webapp.controller;

import ar.edu.unrc.cursos.webapp.service.CursoService;
import ar.edu.unrc.cursos.webapp.service.FlowableService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.*;

/**
 * Funciones para Administración (sección 4.1.3):
 * - Ver tareas asignadas y candidatas
 * - Reclamar tareas de grupo
 * - Completar: Archivar solicitud, Confeccionar lista definitiva,
 *              Evaluar recursos, Enviar informe y archivar expediente
 */
@Controller
@RequestMapping("/admin")
public class AdminController {

    private final FlowableService flowableService;
    private final CursoService cursoService;

    public AdminController(FlowableService flowableService, CursoService cursoService) {
        this.flowableService = flowableService;
        this.cursoService = cursoService;
    }

    // ── Panel de tareas ───────────────────────────────────────────────────────

    @GetMapping("/tareas")
    public String listaTareas(Model model, Authentication auth) {
        String userId = auth.getName();

        List<Map<?,?>> asignadas = flowableService.tareasAsignadas(userId);
        List<Map<?,?>> candidatas = flowableService.tareasCandidatas("Administracion");
        // Excluir de candidatas las que ya tienen asignado a alguien
        candidatas = candidatas.stream()
                .filter(t -> t.get("assignee") == null)
                .collect(java.util.stream.Collectors.toList());

        // Enriquecer con datos de BD (nombre temática)
        enriquecerConDatosCurso(asignadas);
        enriquecerConDatosCurso(candidatas);

        model.addAttribute("asignadas", asignadas);
        model.addAttribute("candidatas", candidatas);
        model.addAttribute("userId", userId);
        return "admin/tareas";
    }

    // ── Reclamar tarea ────────────────────────────────────────────────────────

    @PostMapping("/tareas/{taskId}/reclamar")
    public String reclamarTarea(@PathVariable String taskId, Authentication auth,
                                 RedirectAttributes redirectAttrs) {
        flowableService.reclamarTarea(taskId, auth.getName());
        redirectAttrs.addFlashAttribute("exito", "Tarea reclamada con éxito.");
        return "redirect:/admin/tareas";
    }

    // ── Completar: Registrar propuesta (simple, sin PDF) ──────────────────────
    // Nota: generación de PDF no implementada (marcado en azul en el enunciado)

    @GetMapping("/tareas/{taskId}/propuesta")
    public String formPropuesta(@PathVariable String taskId, Model model) {
        Map<?,?> tarea = flowableService.getTarea(taskId);
        String piId = (String) tarea.get("processInstanceId");
        Map<String, Object> curso = cursoService.findByProcessInstanceId(piId);
        List<Map<?,?>> variables = flowableService.getVariables(piId);

        model.addAttribute("tarea", tarea);
        model.addAttribute("curso", curso);
        model.addAttribute("variables", variables);
        return "admin/propuesta";
    }

    @PostMapping("/tareas/{taskId}/propuesta")
    public String completarPropuesta(@PathVariable String taskId,
                                      @RequestParam String programa,
                                      @RequestParam String presupuesto,
                                      @RequestParam String fechasDisponibles,
                                      RedirectAttributes redirectAttrs) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("programa", programa);
        vars.put("presupuesto", presupuesto);
        vars.put("fechasDisponibles", fechasDisponibles);

        flowableService.completarTarea(taskId, vars);
        redirectAttrs.addFlashAttribute("exito", "Propuesta registrada y enviada a la empresa.");
        return "redirect:/admin/tareas";
    }

    // ── Completar: Revisar propuesta (sin PDF) ────────────────────────────────

    @GetMapping("/tareas/{taskId}/revisar")
    public String formRevisar(@PathVariable String taskId, Model model) {
        Map<?,?> tarea = flowableService.getTarea(taskId);
        String piId = (String) tarea.get("processInstanceId");
        List<Map<?,?>> variables = flowableService.getVariables(piId);

        model.addAttribute("tarea", tarea);
        model.addAttribute("piId", piId);
        model.addAttribute("variables", variables);
        return "admin/revisar";
    }

    @PostMapping("/tareas/{taskId}/revisar")
    public String completarRevisar(@PathVariable String taskId,
                                    @RequestParam String programa,
                                    @RequestParam String presupuesto,
                                    @RequestParam String fechasDisponibles,
                                    RedirectAttributes redirectAttrs) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("programa", programa);
        vars.put("presupuesto", presupuesto);
        vars.put("fechasDisponibles", fechasDisponibles);

        flowableService.completarTarea(taskId, vars);
        redirectAttrs.addFlashAttribute("exito", "Propuesta revisada y reenviada.");
        return "redirect:/admin/tareas";
    }

    // ── Completar: Archivar solicitud ─────────────────────────────────────────

    @PostMapping("/tareas/{taskId}/archivar")
    public String archivarSolicitud(@PathVariable String taskId,
                                     RedirectAttributes redirectAttrs) {
        flowableService.completarTarea(taskId);
        redirectAttrs.addFlashAttribute("exito", "Solicitud archivada.");
        return "redirect:/admin/tareas";
    }

    // ── Completar: Confeccionar lista definitiva ──────────────────────────────

    @GetMapping("/tareas/{taskId}/lista")
    public String formListaDefinitiva(@PathVariable String taskId, Model model) {
        Map<?,?> tarea = flowableService.getTarea(taskId);
        String piId = (String) tarea.get("processInstanceId");
        Object nomina = flowableService.getVariable(piId, "nominaParticipantes");

        model.addAttribute("tarea", tarea);
        model.addAttribute("nomina", nomina != null ? nomina.toString() : "");
        return "admin/lista";
    }

    @PostMapping("/tareas/{taskId}/lista")
    public String completarListaDefinitiva(@PathVariable String taskId,
                                            @RequestParam String listaDefinitiva,
                                            RedirectAttributes redirectAttrs) {
        flowableService.completarTarea(taskId, Map.of("listaDefinitiva", listaDefinitiva));
        redirectAttrs.addFlashAttribute("exito", "Lista definitiva confeccionada.");
        return "redirect:/admin/tareas";
    }

    // ── Completar: Evaluar recursos ───────────────────────────────────────────

    @GetMapping("/tareas/{taskId}/recursos")
    public String formRecursos(@PathVariable String taskId, Model model) {
        Map<?,?> tarea = flowableService.getTarea(taskId);
        model.addAttribute("tarea", tarea);
        return "admin/recursos";
    }

    @PostMapping("/tareas/{taskId}/recursos")
    public String completarRecursos(@PathVariable String taskId,
                                     @RequestParam(required = false) Boolean reservarAula,
                                     @RequestParam(required = false) Boolean aulaVirtual,
                                     @RequestParam(required = false) Boolean equipamiento,
                                     RedirectAttributes redirectAttrs) {
        boolean aula = Boolean.TRUE.equals(reservarAula);
        boolean virtual = Boolean.TRUE.equals(aulaVirtual);
        boolean equip = Boolean.TRUE.equals(equipamiento);

        if (!aula && !virtual && !equip) {
            redirectAttrs.addFlashAttribute("error", "Debe seleccionar al menos un recurso.");
            return "redirect:/admin/tareas/" + taskId + "/recursos";
        }

        Map<String, Object> vars = new HashMap<>();
        vars.put("requiereAulaFisica", aula);
        vars.put("requiereAulaVirtual", virtual);
        vars.put("requiereEquipamiento", equip);

        flowableService.completarTarea(taskId, vars);
        redirectAttrs.addFlashAttribute("exito", "Recursos evaluados. Se activarán las ramas correspondientes.");
        return "redirect:/admin/tareas";
    }

    // ── Completar: Enviar informe y archivar expediente ───────────────────────

    @GetMapping("/tareas/{taskId}/informe")
    public String formInforme(@PathVariable String taskId, Model model) {
        Map<?,?> tarea = flowableService.getTarea(taskId);
        String piId = (String) tarea.get("processInstanceId");
        Object informe = flowableService.getVariable(piId, "informeResultados");
        Map<String, Object> curso = cursoService.findByProcessInstanceId(piId);

        model.addAttribute("tarea", tarea);
        model.addAttribute("informe", informe != null ? informe.toString() : "");
        model.addAttribute("curso", curso);
        return "admin/informe";
    }

    @PostMapping("/tareas/{taskId}/informe")
    public String completarInforme(@PathVariable String taskId,
                                    RedirectAttributes redirectAttrs) {
        flowableService.completarTarea(taskId);
        redirectAttrs.addFlashAttribute("exito", "Informe enviado y expediente archivado. Proceso finalizado.");
        return "redirect:/admin/tareas";
    }

    // ── Helper: enriquecer tareas con nombre de temática ─────────────────────

    private void enriquecerConDatosCurso(List<Map<?,?>> tareas) {
        for (Map<?,?> t : tareas) {
            String piId = (String) t.get("processInstanceId");
            if (piId != null) {
                Map<String, Object> curso = cursoService.findByProcessInstanceId(piId);
                if (curso != null) {
                    ((Map<Object,Object>)(Map<?,?>)t).put("nombreTematica",
                            curso.get("nombre_tematica"));
                }
            }
        }
    }
}
