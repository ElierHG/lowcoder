# Lowcoder Development Quick Start Guide

## Prerequisites

-   Java 17 or higher
-   Maven 3.6+ (preferably 3.8+)
-   Node.js 14.18.0 or higher
-   Yarn package manager
-   Docker (for MongoDB and Redis)

## Quick Start

### 1. Start MongoDB (No Authentication for Local Development)

```bash
docker run -d --name lowcoder-mongodb -p 27017:27017 -e MONGO_INITDB_DATABASE=lowcoder mongo
```

**Note**: For local development, we use MongoDB without authentication to avoid connection issues.

### 2. Start Redis

```bash
docker run -d --name lowcoder-redis -p 6379:6379 redis
```

### 3. Build and Start the Backend Server

```bash
cd server/api-service
mvn clean package -DskipTests
```

Then start the server with the required JVM arguments for local development:

**Single command for Windows (PowerShell):**

```powershell
$env:LOWCODER_SUPERUSER_USERNAME="admin@localhost"; $env:LOWCODER_SUPERUSER_PASSWORD="admin"; $env:PF4J_MODE="development"; $env:PF4J_PLUGINS_DIR="lowcoder-plugins"; $env:SPRING_PROFILES_ACTIVE="lowcoder-local-dev"; java -XX:+AllowRedefinitionToAddDeleteMethods --add-opens java.base/java.nio=ALL-UNNAMED -cp "lowcoder-server/target/app-libs/*;lowcoder-server/target/lowcoder-api-service.jar" org.lowcoder.api.ServerApplication
```

### 4. Start the Node Service (Data Source Plugins)

```bash
cd server/node-service
yarn install
yarn dev
```

### 5. Start the Frontend

```bash
cd client
yarn install
yarn start-win
```

## Access Points

-   **Backend API**: http://localhost:8080
-   **Swagger UI**: http://localhost:8080/api/docs/swagger-ui
-   **OpenAPI JSON**: http://localhost:8080/api/docs/openapi.json
-   **Node Service**: Running on default port (check console output)

## Important Credentials

-   **Super Admin Password**: Generated on first run (check console logs)
-   **MongoDB**: No authentication required for local development

## Node Service Commands

-   `yarn dev` - Development mode with auto-restart
-   `yarn debug` - Development mode with debugging enabled
-   `yarn start` - Production mode (requires building first)
-   `yarn build` - Build the TypeScript code for production
-   `yarn test` - Run tests
