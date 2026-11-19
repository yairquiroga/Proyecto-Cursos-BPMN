-- Base de datos propia de la institución
CREATE DATABASE institucion_cursos;

\c institucion_cursos;

CREATE TABLE IF NOT EXISTS empresas (
    id SERIAL PRIMARY KEY,
    cuit VARCHAR(20) UNIQUE NOT NULL,
    razon_social VARCHAR(150) NOT NULL,
    contacto_nombre VARCHAR(100) NOT NULL,
    contacto_email VARCHAR(120) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NULL,
    cuenta_habilitada BOOLEAN DEFAULT FALSE,
    fecha_registro TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS cursos (
    id SERIAL PRIMARY KEY,
    empresa_id INT NOT NULL REFERENCES empresas(id),
    process_instance_id VARCHAR(64) UNIQUE NULL,
    nombre_tematica VARCHAR(150) NOT NULL,
    descripcion TEXT NOT NULL,
    cantidad_participantes INT NOT NULL,
    fechas_preferidas VARCHAR(150) NOT NULL,
    programa TEXT NULL,
    presupuesto NUMERIC(12, 2) NULL,
    fecha_inicio_acordada TIMESTAMP NULL,
    version_propuesta INT DEFAULT 0,
    ruta_pdf_propuesta VARCHAR(255) NULL,
    ruta_pdf_informe VARCHAR(255) NULL,
    estado VARCHAR(30) DEFAULT 'en_evaluacion',
    fecha_creacion TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    fecha_actualizacion TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO empresas (cuit, razon_social, contacto_nombre, contacto_email, password_hash, cuenta_habilitada) VALUES
('30-71234567-8', 'TechSolutions S.A.', 'Laura Gómez', 'lgomez@techsolutions.com', '1234', TRUE),
('30-79876543-2', 'AgroIndustrias Río Cuarto S.R.L.', 'Martín Pérez', 'mperez@agrorc.com', NULL, FALSE)
ON CONFLICT (cuit) DO NOTHING;

INSERT INTO cursos (empresa_id, process_instance_id, nombre_tematica, descripcion, cantidad_participantes, fechas_preferidas, estado) VALUES
(1, NULL, 'Arquitectura de Microservicios con Spring Boot', 'Capacitación intensiva para equipo backend.', 18, 'Primera quincena del próximo mes', 'finalizado')
ON CONFLICT DO NOTHING;
