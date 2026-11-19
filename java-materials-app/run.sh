#!/usr/bin/env bash
# Script para compilar y ejecutar la app de Materiales (API Java)
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DIR"

echo "=== Aplicación Java: Preparación de Materiales (API Java) ==="

if [ ! -f "target/java-materials-app-1.0.0.jar" ]; then
    echo "Compilando aplicación..."
    mvn clean package -DskipTests
fi

# Si se pasa --cli o no hay display X11/Wayland, corre en modo consola
if [ -z "$DISPLAY" ] && [ -z "$WAYLAND_DISPLAY" ]; then
    echo "No se detectó entorno gráfico. Ejecutando en modo consola (--cli)..."
    java -jar target/java-materials-app-1.0.0.jar --cli
else
    java -jar target/java-materials-app-1.0.0.jar "$@"
fi
