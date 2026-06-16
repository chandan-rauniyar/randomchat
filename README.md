# RandomChat Backend

Spring Boot backend for RandomChat with PostgreSQL, Redis, Flyway, Docker, and pgAdmin.

---

# Tech Stack

* Java 21
* Spring Boot
* Spring Security
* Spring Data JPA
* Flyway
* PostgreSQL
* Redis
* Docker
* pgAdmin

---

# Project Structure

```text
randomchat/
├── src/
├── docker-compose.yml
├── Dockerfile
├── pom.xml
└── README.md
```

---

# Start Docker Services

Start PostgreSQL + Redis + pgAdmin:

```bash
docker compose --profile tools up -d
```

Check running containers:

```bash
docker ps
```

Stop containers:

```bash
docker compose down
```

Remove containers and volumes:

```bash
docker compose down -v
```

---

# Ports

| Service     | Host Port |
| ----------- | --------- |
| Spring Boot | 8080      |
| PostgreSQL  | 5434      |
| Redis       | 6380      |
| pgAdmin     | 5051      |

---

# PostgreSQL Credentials

Database:

```text
randomchat_dev
```

Username:

```text
randomchat
```

Password:

```text
randomchat_dev_password
```

Connection URL:

```text
jdbc:postgresql://localhost:5434/randomchat_dev
```

---

# Redis

Host:

```text
localhost
```

Port:

```text
6380
```

---

# Run Spring Boot

Using IntelliJ:

* Click Run ▶

Using terminal:

```bash
mvn spring-boot:run
```

Application URL:

```text
http://localhost:8080
```

Health Endpoint:

```text
http://localhost:8080/actuator/health
```

---

# pgAdmin Login

Open:

```text
http://localhost:5051
```

Login:

Email:

```text
admin@admin.com
```

Password:

```text
admin
```

---

# Connect PostgreSQL in pgAdmin

General:

Name:

```text
RandomChat DB
```

Connection:

Host:

```text
postgres
```

Port:

```text
5432
```

Database:

```text
randomchat_dev
```

Username:

```text
randomchat
```

Password:

```text
randomchat_dev_password
```

Save and connect.

---

# Flyway Migrations

Migration files:

```text
src/main/resources/db/migration/
```

Examples:

```text
V1__create_users.sql
V2__create_sessions.sql
V3__create_reports.sql
```

Flyway automatically executes new migrations during application startup.

---

# Development Workflow

1. Start Docker:

```bash
docker compose --profile tools up -d
```

2. Run Spring Boot:

```bash
mvn spring-boot:run
```

3. Make code changes.

4. DevTools automatically restarts the application.

5. Stop Spring Boot with:

```text
Ctrl + C
```

6. Stop Docker:

```bash
docker compose down
```

---

# Package Name

```text
com.chandan.randomchat
```
