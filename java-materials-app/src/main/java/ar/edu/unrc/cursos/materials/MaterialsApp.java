package ar.edu.unrc.cursos.materials;

import org.flowable.engine.IdentityService;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.ProcessEngineConfiguration;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;

/**
 * Aplicación de escritorio Java (Sección 4.2.1)
 * Interactúa directamente con el motor Flowable mediante su API Java.
 * Permite a un administrativo identificarse, consultar, reclamar y completar
 * las tareas "Preparar materiales didácticos" (o "Preparar materiales").
 */
public class MaterialsApp {

    private static final String JDBC_URL = System.getenv().getOrDefault(
            "FLOWABLE_DB_URL", "jdbc:postgresql://localhost:5432/flowable");
    private static final String JDBC_USER = System.getenv().getOrDefault("FLOWABLE_DB_USER", "flowable");
    private static final String JDBC_PASS = System.getenv().getOrDefault("FLOWABLE_DB_PASS", "flowablepassword");

    private static ProcessEngine processEngine;
    private static TaskService taskService;
    private static IdentityService identityService;

    public static void main(String[] args) {
        inicializarMotor();

        boolean forzarCli = args.length > 0 && "--cli".equalsIgnoreCase(args[0]);
        if (forzarCli || GraphicsEnvironment.isHeadless()) {
            ejecutarModoConsola();
        } else {
            SwingUtilities.invokeLater(MaterialsApp::iniciarGui);
        }
    }

    private static void inicializarMotor() {
        System.out.println("Inicializando conexión con motor Flowable vía API Java...");
        try {
            ProcessEngineConfiguration cfg = ProcessEngineConfiguration
                    .createStandaloneProcessEngineConfiguration()
                    .setJdbcDriver("org.postgresql.Driver")
                    .setJdbcUrl(JDBC_URL)
                    .setJdbcUsername(JDBC_USER)
                    .setJdbcPassword(JDBC_PASS)
                    .setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_FALSE);

            processEngine = cfg.buildProcessEngine();
            taskService = processEngine.getTaskService();
            identityService = processEngine.getIdentityService();
            System.out.println("✓ Conexión establecida con éxito a: " + JDBC_URL);
        } catch (Exception e) {
            System.err.println("Error inicializando ProcessEngine: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    // =========================================================================
    // MODO INTERFAZ GRÁFICA (Swing)
    // =========================================================================

    private static void iniciarGui() {
        // Diálogo de login
        String usuario = (String) JOptionPane.showInputDialog(
                null,
                "Seleccione su usuario de Administración:",
                "Acceso - Preparación de Materiales",
                JOptionPane.QUESTION_MESSAGE,
                null,
                new String[]{"jefe_admin", "admin_2", "admin"},
                "jefe_admin"
        );

        if (usuario == null) {
            System.exit(0);
        }

        JFrame frame = new JFrame("Flowable Java API - Preparación de Materiales [" + usuario + "]");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(750, 480);
        frame.setLocationRelativeTo(null);

        JPanel panelPrincipal = new JPanel(new BorderLayout(10, 10));
        panelPrincipal.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // Encabezado
        JLabel lblHeader = new JLabel("<html><h3>Tareas: Preparar materiales didácticos</h3>" +
                "<p style='color: gray;'>Usuario activo: <b>" + usuario + "</b> (Grupo Administración)</p></html>");
        panelPrincipal.add(lblHeader, BorderLayout.NORTH);

        // Tabla de tareas
        String[] columnas = {"ID Tarea", "Nombre", "ID Instancia", "Asignado a", "Estado"};
        DefaultTableModel model = new DefaultTableModel(columnas, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        JTable table = new JTable(model);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        panelPrincipal.add(new JScrollPane(table), BorderLayout.CENTER);

        // Botonera
        JPanel panelBotones = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        JButton btnRefrescar = new JButton("Actualizar Lista");
        JButton btnReclamar = new JButton("Reclamar Tarea");
        JButton btnCompletar = new JButton("Completar Tarea");

        panelBotones.add(btnRefrescar);
        panelBotones.add(btnReclamar);
        panelBotones.add(btnCompletar);
        panelPrincipal.add(panelBotones, BorderLayout.SOUTH);

        // Lógica de actualización
        Runnable cargarTareas = () -> {
            model.setRowCount(0);
            try {
                // Tareas asignadas directamente al usuario
                List<Task> asignadas = taskService.createTaskQuery()
                        .taskAssignee(usuario)
                        .active()
                        .list();

                for (Task t : asignadas) {
                    if (esTareaMateriales(t)) {
                        model.addRow(new Object[]{t.getId(), t.getName(), t.getProcessInstanceId(), t.getAssignee(), "Asignada"});
                    }
                }

                // Tareas candidatas del grupo Administración
                List<Task> candidatas = taskService.createTaskQuery()
                        .taskCandidateGroup("Administracion")
                        .active()
                        .list();

                for (Task t : candidatas) {
                    if (esTareaMateriales(t) && t.getAssignee() == null) {
                        model.addRow(new Object[]{t.getId(), t.getName(), t.getProcessInstanceId(), "(Sin asignar)", "Disponible"});
                    }
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(frame, "Error cargando tareas: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        };

        btnRefrescar.addActionListener(e -> cargarTareas.run());

        btnReclamar.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row < 0) {
                JOptionPane.showMessageDialog(frame, "Seleccioná una tarea de la tabla.", "Atención", JOptionPane.WARNING_MESSAGE);
                return;
            }
            String taskId = (String) model.getValueAt(row, 0);
            String estado = (String) model.getValueAt(row, 4);
            if (!"Disponible".equals(estado)) {
                JOptionPane.showMessageDialog(frame, "La tarea seleccionada ya está asignada.", "Atención", JOptionPane.WARNING_MESSAGE);
                return;
            }
            try {
                taskService.claim(taskId, usuario);
                JOptionPane.showMessageDialog(frame, "✓ Tarea reclamada con éxito por " + usuario);
                cargarTareas.run();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(frame, "Error al reclamar tarea: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        btnCompletar.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row < 0) {
                JOptionPane.showMessageDialog(frame, "Seleccioná una tarea de la tabla.", "Atención", JOptionPane.WARNING_MESSAGE);
                return;
            }
            String taskId = (String) model.getValueAt(row, 0);
            String assignee = (String) model.getValueAt(row, 3);
            if (!usuario.equals(assignee)) {
                JOptionPane.showMessageDialog(frame, "Primero debés reclamar la tarea para completarla.", "Atención", JOptionPane.WARNING_MESSAGE);
                return;
            }

            int confirm = JOptionPane.showConfirmDialog(frame,
                    "¿Confirmar finalización de la preparación de materiales para esta tarea?",
                    "Completar Tarea", JOptionPane.YES_NO_OPTION);

            if (confirm == JOptionPane.YES_OPTION) {
                try {
                    taskService.complete(taskId);
                    JOptionPane.showMessageDialog(frame, "✓ Tarea completada con éxito vía Flowable Java API.");
                    cargarTareas.run();
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(frame, "Error al completar tarea: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        cargarTareas.run();
        frame.setContentPane(panelPrincipal);
        frame.setVisible(true);
    }

    private static boolean esTareaMateriales(Task t) {
        String n = t.getName() != null ? t.getName().toLowerCase() : "";
        return n.contains("material");
    }

    // =========================================================================
    // MODO CONSOLA / TERMINAL INTERACTIVA (CLI)
    // =========================================================================

    private static void ejecutarModoConsola() {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        System.out.println("\n=======================================================");
        System.out.println("  Flowable Java API - Preparación de Materiales (CLI) ");
        System.out.println("=======================================================");

        try {
            System.out.print("Ingresá tu usuario de Administración [jefe_admin/admin_2]: ");
            String usuario = reader.readLine();
            if (usuario == null || usuario.trim().isEmpty()) usuario = "jefe_admin";

            while (true) {
                System.out.println("\n--- Menú Principal (" + usuario + ") ---");
                System.out.println("1) Consultar tareas de materiales (asignadas y grupales)");
                System.out.println("2) Reclamar tarea");
                System.out.println("3) Completar tarea");
                System.out.println("0) Salir");
                System.out.print("Seleccioná opción: ");

                String op = reader.readLine();
                if (op == null || "0".equals(op.trim())) break;

                switch (op.trim()) {
                    case "1":
                        consultarTareasConsola(usuario);
                        break;
                    case "2":
                        System.out.print("ID de la tarea a reclamar: ");
                        String tClaim = reader.readLine();
                        if (tClaim != null && !tClaim.trim().isEmpty()) {
                            taskService.claim(tClaim.trim(), usuario);
                            System.out.println("✓ Tarea reclamada con éxito.");
                        }
                        break;
                    case "3":
                        System.out.print("ID de la tarea a completar: ");
                        String tComp = reader.readLine();
                        if (tComp != null && !tComp.trim().isEmpty()) {
                            taskService.complete(tComp.trim());
                            System.out.println("✓ Tarea completada con éxito vía Flowable Java API.");
                        }
                        break;
                    default:
                        System.out.println("Opción inválida.");
                }
            }
        } catch (Exception e) {
            System.err.println("Error en modo consola: " + e.getMessage());
        }
    }

    private static void consultarTareasConsola(String usuario) {
        System.out.println("\nTareas asignadas a '" + usuario + "':");
        List<Task> asignadas = taskService.createTaskQuery().taskAssignee(usuario).active().list();
        int count = 0;
        for (Task t : asignadas) {
            if (esTareaMateriales(t)) {
                System.out.println("  • [ID: " + t.getId() + "] " + t.getName() + " (Instancia: " + t.getProcessInstanceId() + ")");
                count++;
            }
        }
        if (count == 0) System.out.println("  (ninguna)");

        System.out.println("\nTareas disponibles para reclamar (Grupo Administración):");
        List<Task> candidatas = taskService.createTaskQuery().taskCandidateGroup("Administracion").active().list();
        int countCand = 0;
        for (Task t : candidatas) {
            if (esTareaMateriales(t) && t.getAssignee() == null) {
                System.out.println("  • [ID: " + t.getId() + "] " + t.getName() + " (Instancia: " + t.getProcessInstanceId() + ")");
                countCand++;
            }
        }
        if (countCand == 0) System.out.println("  (ninguna)");
    }
}
