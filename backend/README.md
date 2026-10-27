# Nuru Backend

Spring Boot 3.4 + Java 21 + Microsoft SQL Server. AI-assisted financial management
for African savings groups and stokvels. See `../docs/` for the implementation
contract, API contract, and production checklist.

## Prerequisites

- JDK 21, Maven 3.9+
- SQL Server 2022 reachable over TCP (local Docker example below)
- Docker (only for the Testcontainers test suite)

```powershell
# SQL Server used for local development (example)
docker run -e ACCEPT_EULA=Y -e MSSQL_SA_PASSWORD='<strong-password>' `
  -p 7862:1433 --name sqlserver -d mcr.microsoft.com/mssql/server:2022-latest
```

Copy `../.env.example` to `.env` (git-ignored) or export the variables. Never
commit real secrets.

## Run

```powershell
cd backend
$env:MSSQL_HOST="localhost"; $env:MSSQL_PORT="7862"; $env:MSSQL_DB="nuru"
$env:MSSQL_USER="sa"; $env:MSSQL_PASSWORD="<sa-password>"
$env:NURU_JWT_SECRET="<random-32-chars-minimum>"
# Optional local AI: $env:NURU_AI_PROVIDER="DEV"
mvn spring-boot:run
```

Flyway migrates the database automatically (`ddl-auto` is `validate`).
Health: `GET /actuator/health` · API docs: `/swagger-ui.html`.

## Test

```powershell
cd backend
mvn test
```

Integration tests boot the full app against an ephemeral SQL Server 2022
container (Testcontainers, same image as local dev) and apply all migrations.

## Configuration

All secrets and provider settings come from environment variables — see
`.env.example` at the repository root. Defaults are safe: AI, identity, credit,
and live notification gateways report “unconfigured” until explicitly set up.
