package ar.edu.unrc.cursos.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseHelper {
    private static final String URL = System.getenv().getOrDefault(
            "INSTITUCION_DB_URL", "jdbc:postgresql://postgres:5432/institucion_cursos");
    private static final String USER = System.getenv().getOrDefault("POSTGRES_USER", "flowable");
    private static final String PASS = System.getenv().getOrDefault("POSTGRES_PASSWORD", "flowablepassword");

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASS);
    }
}
