# Smart Village Complaint Management System

A complete Java 17 web application built with JSP, Jakarta Servlets, JDBC and MySQL. Villagers report and track public issues; Panchayat administrators verify, assign and resolve them. The responsive interface uses local CSS and JavaScript without a frontend framework or external font/CDN dependencies.

## Features

- Villager registration, login and logout; separate administrator permissions.
- Complaint title, category, description, landmark, optional GPS coordinates and JPEG/PNG photo evidence.
- Unique `SV-<UUID>` complaint IDs, title/ID search and status filters.
- Dashboard totals, complaint details and timestamped progress history.
- Administrator verification, team/officer assignment and public update notes.
- Enforced Pending → Under Process → Resolved workflow. Resolved complaints are closed.
- One feedback submission per resolved complaint, restricted to its owner, with a 1–5 rating.
- Photos persist in MySQL alongside the complaint and are accessible only to its owner and administrators.

## Quick start with Docker

Requires Docker Desktop with Compose. From this folder:

```powershell
Copy-Item .env.example .env
# Edit .env and replace all example passwords. ADMIN_PASSWORD needs 12+ characters.
docker compose up --build -d
docker compose logs -f app
```

Open **http://localhost:8080**. Choose **Create an account** to register a villager. Sign in using `ADMIN_EMAIL` and `ADMIN_PASSWORD` from `.env` for the Panchayat dashboard. Tables and the first administrator are initialized automatically. MySQL data persists in the `mysql-data` Docker volume. `docker compose down` stops the services without removing that data.

The administrator is created only if the configured email does not exist. Changing `ADMIN_PASSWORD` after the account exists does not reset its password. A configured email belonging to a villager causes startup to fail rather than granting it administrator privileges.

## Local preview without MySQL

To try the interface immediately on this Windows machine, run `./Start-Demo.ps1` in PowerShell, then open **http://localhost:8080**. It uses the downloaded project-local Maven and the installed Android Studio JDK when available. On another machine, install JDK 17+ and Maven first.

This preview runs the same Servlets and JSP pages on embedded Tomcat, with a separate persistent H2 database under `.tools/demo/`. It is a **local demonstration**, not the MySQL deployment. Sign in as `admin@village.local` with `LocalPreview123!` for the demo administrator, or register a villager account. The server binds to the loopback interface only. Demo credentials and dependencies are not included in the production WAR. Stop it with Ctrl+C before starting Docker on the same port.

## Run with Java, Maven, MySQL and Tomcat

1. Install JDK 17+, Maven 3.9+, MySQL 8.0+ and Apache Tomcat 10.1. Use Tomcat 10.1 rather than Tomcat 9 because the application uses Jakarta Servlet 6.0 packages.
2. As a MySQL administrator, create a database and a dedicated application account:

```sql
CREATE DATABASE smart_village CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'village'@'localhost' IDENTIFIED BY 'replace-with-a-long-password';
GRANT SELECT, INSERT, UPDATE, CREATE, REFERENCES ON smart_village.* TO 'village'@'localhost';
```

3. Set these environment variables in the shell that starts Tomcat:

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk-17'
$env:DB_URL = 'jdbc:mysql://localhost:3306/smart_village?serverTimezone=UTC'
$env:DB_USER = 'village'
$env:DB_PASSWORD = 'replace-with-a-long-password'
$env:ADMIN_EMAIL = 'admin@village.local'
$env:ADMIN_PASSWORD = 'replace-with-at-least-12-characters'
mvn clean verify
Copy-Item target\smart-village.war C:\path\to\tomcat\webapps\
& C:\path\to\tomcat\bin\catalina.bat run
```

4. Open **http://localhost:8080/smart-village/**. On first deployment, `src/main/resources/schema.sql` initializes the tables. The schema is idempotent for an empty or existing installation of this version; it is not a versioned migration system.

When running Tomcat as a Windows service, configure these variables for the service, not only your terminal. Keep the JVM/database timezone in UTC for consistent timeline dates.

## Project structure

```text
src/main/java/org/smartvillage/
  model/User.java                 Encapsulated, immutable session identity
  db/Database.java                Environment configuration and JDBC connections
  db/UserRepository.java          Accounts, authentication and administrator bootstrap
  db/ComplaintRepository.java     Complaint queries, transactions, history and feedback
  security/Passwords.java         Salted PBKDF2-HMAC-SHA256 password hashing
  web/BaseServlet.java            Abstract controller helpers
  web/AuthServlet.java            Register, login and logout routes
  web/ComplaintServlet.java       Complaint and administrator workflows
  web/SecurityFilter.java         CSRF and security headers
  web/Startup.java                Database initialization
src/main/resources/schema.sql    MySQL table definitions
src/main/webapp/WEB-INF/views/    Protected JSP pages and shared fragments
src/main/webapp/assets/           Responsive stylesheet and browser interactions
src/test/java/org/smartvillage/   Workflow and security boundary tests
```

## OOP concepts demonstrated

| Concept | Implementation |
| --- | --- |
| Encapsulation | Private final identity fields in `User`; persistence hidden behind repositories |
| Abstraction | `BaseServlet` centralizes rendering, redirects, authentication and input helpers |
| Inheritance | `AuthServlet` and `ComplaintServlet` extend `BaseServlet`, which extends `HttpServlet` |
| Polymorphism | Controllers override `doGet` and `doPost`; Servlet container dispatches requests |
| Exception handling | Validation errors become actionable messages; SQL errors are logged; transactions roll back on failure |
| Database connectivity | JDBC prepared statements, try-with-resources and atomic complaint/history updates |

## Validation

`mvn clean verify` compiles the application, runs the JUnit suite, and produces `target/smart-village.war`. Tests use H2 in MySQL compatibility mode, exercising the actual repositories and schema. They cover the complete lifecycle, ownership checks, restricted photos, feedback rules, invalid transitions, search and password verification. An embedded Tomcat HTTP test also compiles and renders the JSP pages and exercises registration, CSRF, multipart submission, administration, feedback and logout. H2 does not replace testing against a real MySQL server.

Manual acceptance walkthrough:

1. Register two villagers in separate browser sessions.
2. Submit a complaint with a photo and a landmark. Confirm Pending status and save its ID.
3. Confirm the other villager cannot open the complaint or its photo URL.
4. Sign in as administrator, verify it, assign an officer and move it to Under Process with a note.
5. Resolve it with a completion note; confirm all three events appear in the timeline.
6. Return as the submitting villager and submit feedback. Confirm it appears for both villager and administrator, and cannot be submitted twice.
7. Try search, status filters, logout, a mobile viewport and geolocation denial.

## Deployment notes

This is a runnable project foundation. Use HTTPS before exposing it publicly; configure secure session cookies and SameSite=Lax at Tomcat or the reverse proxy. Geolocation requires HTTPS or localhost. The Docker configuration intentionally binds only to localhost and uses unencrypted MySQL communication inside the private Compose network. Use TLS for remote databases.

Passwords are salted and hashed, SQL uses bound parameters, HTML output is escaped, POST forms require CSRF tokens, and session IDs rotate at sign-in. Photo uploads are validated as JPEG/PNG, limited to 5 MB and 24 megapixels, and served through authorized endpoints. Login throttling, email verification, password recovery, notifications, a managed staff directory, pagination beyond the latest 200 matching results and production monitoring are future extensions. Assignment currently records a team/officer name, rather than creating a separate staff login. Back up MySQL, which also contains uploaded photos.

Platform references: [Tomcat 10.1 / Jakarta compatibility](https://tomcat.apache.org/migration-10.1.html) and [MySQL JDBC Maven coordinates](https://dev.mysql.com/doc/connector-j/en/connector-j-installing-maven.html).
