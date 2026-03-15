# NexHub - Backend

Este es el servicio de backend para NexHub, una plataforma web orientada a la gestion de proyectos y tareas para desarrolladores freelance. El sistema esta desarrollado con Java 17, Spring Boot y Gradle como gestor de dependencias.

## Requisitos Previos

Para ejecutar este proyecto es necesario contar con:
* Docker y Docker Compose instalados.
* Git para la gestion de versiones.
* Opcional: JDK 17 e IntelliJ IDEA para desarrollo local.

## Instalacion y Ejecucion con Docker

La forma recomendada de ejecutar este servicio es a traves de Docker Compose, lo cual levanta automaticamente la base de datos PostgreSQL y el servidor de aplicaciones.

1. Configuracion del entorno:
   Cree un archivo llamado .env en la raiz del proyecto NexHub (donde se encuentra el archivo docker-compose.yml) basandose en el siguiente modelo:

   DB_NAME=nexhub_db
   DB_USER=tu_usuario
   DB_PASSWORD=tu_password
   PORT=8080

2. Construccion y arranque:
   Desde una terminal en la carpeta raiz, ejecute el siguiente comando:

   docker-compose up --build

3. Acceso al sistema:
    * Backend: http://localhost:8080

## Gestion de Base de Datos desde el IDE

Al utilizar Docker, la base de datos corre en un contenedor pero expone el puerto 5432 a la maquina host. Para gestionar los datos directamente desde IntelliJ IDEA:

1. Abra la pestaña Database (margen derecho del IDE).
2. Seleccione New -> Data Source -> PostgreSQL.
3. Configure los siguientes parametros:
    * Host: localhost
    * Port: 5432
    * User/Password/Database: Los valores definidos en su archivo .env.
4. Haga clic en Test Connection para verificar el acceso.

## Desarrollo Local

Si desea ejecutar la aplicacion sin Docker para tareas de depuracion:

1. Asegurese de tener una instancia de PostgreSQL corriendo en el puerto 5432.
2. Cree la base de datos definida en su configuracion.
3. Configure las variables de entorno DB_NAME, DB_USER y DB_PASSWORD en su IDE o sistema operativo.
4. Ejecute el comando:

   ./gradlew bootRun

## Detalles de Implementacion

* Docker Multi-stage: El archivo Dockerfile utiliza una etapa de build basada en JDK 17 para compilar el codigo y una etapa de runtime basada en JRE 17 para minimizar el tamaño de la imagen final.
* Persistencia: Se utiliza Spring Data JPA con Hibernate para el mapeo de objetos a la base de datos relacional PostgreSQL. La propiedad ddl-auto esta configurada como update para sincronizar el esquema automaticamente durante el desarrollo.
* Configuracion Dinamica: Los parametros de conexion a la base de datos y el puerto del servidor se inyectan mediante variables de entorno para evitar el hardcoding de credenciales en el codigo fuente.

## Estructura de Archivos Principal

* src/main/java: Contiene la logica de negocio, entidades y controladores.
* src/main/resources: Contiene el archivo application.properties para la configuracion de Spring.
* Dockerfile: Definicion de la imagen del contenedor del backend.
* build.gradle: Configuracion de dependencias y plugins de Gradle.

---
Proyecto desarrollado para la materia Laboratorio 1 - Universidad Austral.