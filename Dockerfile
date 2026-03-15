# ETAPA 1: Build (Construcción)
FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /app

# Copiamos los archivos de configuración de Gradle para descargar dependencias
# Esto se hace primero para que Docker cachee las librerías
COPY gradle/ gradle/
COPY build.gradle settings.gradle gradlew ./

# Damos permisos de ejecución y descargamos dependencias sin compilar todo
RUN chmod +x gradlew
RUN ./gradlew dependencies --no-daemon

# Copiamos el código fuente y generamos el archivo ejecutable (.jar)
COPY src ./src
RUN ./gradlew bootJar -x test --no-daemon

# ETAPA 2: Runtime (Ejecución)
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Copiamos el archivo .jar compilado desde la etapa anterior
# En Gradle, el resultado queda en build/libs/
COPY --from=build /app/build/libs/*.jar app.jar

# Exponemos el puerto de Spring Boot
EXPOSE 8080

# Ejecutamos la aplicación
ENTRYPOINT ["java", "-jar", "app.jar"]