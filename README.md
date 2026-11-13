# Release Dashboard Service

A centralized dashboard for viewing release artifacts (SBOMs, release notes) across multiple repositories.

## Architecture

- **Backend**: Spring Boot service that queries JFrog Artifactory directly and serves API endpoints
- **Frontend**: React dashboard for viewing releases
- **Storage**: JFrog Artifactory for artifact files (source of truth)

Mentat lives at `/mentat`. Another team adds its own site by copying the Foobar example: [ADDING_A_TEAM.md](ADDING_A_TEAM.md).

## Mentat Phase 1 — create production PRs

Weekly Mentat release helper (not part of the dashboard runtime). Opens GitHub PRs for all four Mentat apps: branch `release-MM-DD-YYYY` → base `production`.

| | |
|--|--|
| **Script** | [`scripts/create-production-prs.sh`](scripts/create-production-prs.sh) |
| **Runbook** | Mentat hub: `related-work/phase-1-release-pr-creation-runbook-robin.md` §1a-bis |
| **History** | [#34](https://github.com/slingshot-aerospace-inc/release-dash/pull/34) (create), [#36](https://github.com/slingshot-aerospace-inc/release-dash/pull/36) (empty commit when no new commits) |

### GitHub permissions (required)

Active `gh` user must have **`push` (Write)** on **all four**:

- `slingshot-aerospace-inc/dst-virtual-core`
- `slingshot-aerospace-inc/slingshot-apis-simulation`
- `slingshot-aerospace-inc/rad-talos-single-step`
- `slingshot-aerospace-inc/rad-talos-agents`

**Pull alone is not enough.** Creating the release branch is a git ref create; without push, GitHub often returns **HTTP 404** (not a clear 403). The script checks `permissions.push` per repo and fails with an explicit error before create-ref.

Check (repeat for any repo that fails):

```bash
gh auth status
gh api user --jq '.login'
gh api repos/slingshot-aerospace-inc/rad-talos-single-step --jq '.permissions'
```

Expect `"push": true`. If false: `gh auth switch` to an account with Write, or grant Write (Admin account):

```bash
gh api --method PUT \
  repos/slingshot-aerospace-inc/rad-talos-single-step/collaborators/<github-username> \
  -f permission=push
```

### Deploy date

Enter **`MM-DD-YYYY` = Emily’s Mentat release ticket ship day** (e.g. tickets titled `… 10/02/26` → `10-02-2026`), **not** calendar “today.” That becomes branch/title `release-10-02-2026` for Deploy Production and the CUI orchestrator.

### Run

```bash
cd /path/to/release-dash
gh auth status
./scripts/create-production-prs.sh
```

Opens (or reuses) PRs for all four apps even when `main` has nothing new vs `production` (empty commit). Downstream CUI kick: GitLab [`cui-cd-orchestrator`](https://gitlab.slingshot.ai/tvsim/cui-cd-orchestrator) — after merge + titled Deploy Production succeeds.

## Quick Start

### Using Build & Run Scripts (Recommended for Development)

The project includes convenient build and run scripts that handle frontend building, copying static files, and starting the application. This is the easiest way to get started for local development.

#### Prerequisites

1. **Java 21+** - Required for the backend
2. **Maven** - For building the backend
3. **Node.js & npm** - For building the frontend
4. **Environment Variables** - Set up your JFrog credentials:
   ```bash
   export JFROG_URL=https://slingshotaerospace.jfrog.io/artifactory
   export JFROG_USERNAME=your-jfrog-username
   export JFROG_TOKEN=your-jfrog-token
   ```

#### First-Time Setup

Before running the application for the first time, you need to install frontend dependencies:

```bash
cd frontend
npm install
cd ..
```

This installs TypeScript, Vite, and other frontend dependencies required for building the frontend.

#### macOS/Linux

```bash
# First-time setup: Install frontend dependencies
cd frontend
npm install
cd ..

# Make script executable (first time only)
chmod +x run.sh

# Normal run - builds frontend, backend, and starts the application
./run.sh

# Run with debugger enabled (port 5005)
./run.sh debug

# Skip build step (use existing artifacts)
./run.sh --skip-build

# Combine options
./run.sh debug --skip-build
```

#### Windows

```cmd
REM First-time setup: Install frontend dependencies
cd frontend
npm install
cd ..

REM Normal run - builds frontend, backend, and starts the application
run.bat

REM Run with debugger enabled (port 5005)
run.bat debug

REM Skip build step (use existing artifacts)
run.bat --skip-build

REM Combine options
run.bat debug --skip-build
```

#### What the Scripts Do

1. **Build Frontend**: Runs `npm run build` in the `frontend/` directory (requires `npm install` to be run first)
2. **Copy Static Files**: Copies built frontend files to `backend/src/main/resources/static/`
3. **Build Backend**: Runs `mvn clean package -DskipTests`
4. **Clean Up**: Kills any existing processes on ports 8080 (and 5005 in debug mode)
5. **Start Application**: Runs `mvn spring-boot:run` with optional debugger support

**Note**: If you see `sh: tsc: command not found` or similar errors, make sure you've run `npm install` in the `frontend/` directory first (see First-Time Setup above).

#### Debugging

When running with `debug` mode, the application starts with a Java debugger listening on port **5005**. You can attach your IDE's debugger to this port.

**IntelliJ IDEA:**
1. Go to **Run** → **Edit Configurations...**
2. Click **+** → **Remote JVM Debug**
3. Set:
   - Host: `localhost`
   - Port: `5005`
   - Use module classpath: `release-dashboard-backend`
4. Click **OK**, then **Debug** (green bug icon)

**VS Code:**
1. Install the **Java Extension Pack** if not already installed
2. Open the **Run and Debug** panel (Ctrl+Shift+D / Cmd+Shift+D)
3. Select **"Attach to Release Dashboard"** from the dropdown
4. Press F5 or click the green play button

**Command Line (jdb):**
```bash
jdb -attach localhost:5005
```

**Eclipse:**
1. **Run** → **Debug Configurations...**
2. Create new **Remote Java Application**
3. Set:
   - Host: `localhost`
   - Port: `5005`
4. Click **Debug**

#### Accessing the Application

Once started, the application will be available at:
- **Frontend & API**: http://localhost:8080
- **API Endpoints**: http://localhost:8080/api/*
- **Debug Port** (if enabled): localhost:5005

### Using Docker Compose

For containerized deployment:

```bash
# Copy environment file
cp backend/env.example backend/.env
# Edit backend/.env with your JFrog credentials

# Start services
docker-compose up -d

# Access the dashboard
# Frontend & API: http://localhost:8000
# (Docker maps host port 8000 to container port 8080)
```

### Manual Setup

For more control over the build process:

#### Backend

```bash
cd backend

# Install Maven dependencies and build
mvn clean install

# Set up environment variables
export JFROG_URL=https://slingshotaerospace.jfrog.io/artifactory
export JFROG_USERNAME=your-jfrog-username
export JFROG_TOKEN=your-jfrog-token
export CORS_ORIGINS=http://localhost:3000,http://localhost:5173
export WEBHOOK_SECRET=your-webhook-secret  # Optional

# Run the application
mvn spring-boot:run

# Or run the JAR directly
java -jar target/release-dashboard-backend-0.1.0.jar
```

#### Frontend

```bash
cd frontend

# Install dependencies (required before building)
npm install

# Set up environment (optional)
# Create .env file with: VITE_API_BASE_URL=http://localhost:8000

# Build for production (used by run.sh)
npm run build

# Or start dev server (for frontend-only development)
npm run dev
```

## Environment Variables

### Backend

- `JFROG_URL`: JFrog Artifactory base URL (e.g., `https://slingshotaerospace.jfrog.io/artifactory`)
- `JFROG_USERNAME`: JFrog username
- `JFROG_TOKEN`: JFrog API token
- `CORS_ORIGINS`: Comma-separated list of allowed CORS origins
- `WEBHOOK_SECRET`: (Optional) Secret for webhook authentication
- `CMDB_ENABLED`: Enable the CMDB Git catalog. Preview and staging Helm set this to `true`. See [CMDB_UI_PLAN.md](./CMDB_UI_PLAN.md).
- `CMDB_GITHUB_OWNER` / `CMDB_GITHUB_REPO` / `CMDB_GITHUB_BRANCH`: CMDB GitHub repo (`slingshot-aerospace-inc` / `mentatcmdb` / `main` in Helm). Catalog stays off until `CMDB_ENABLED=true` and the GitHub App ExternalSecret exists.
- `CMDB_GITHUB_APP_ID` / `CMDB_GITHUB_APP_INSTALLATION_ID`: GitHub App **CMDB - Release Dashboard** (`5199318` / installation `168195525` on `mentatcmdb`). Set in Helm.
- `CMDB_GITHUB_APP_PRIVATE_KEY`: App PEM from External Secrets (`/release-dash/cmdb-github/app` JSON key `private_key`). When App ID + PEM are set, the backend mints an installation token and does **not** use a personal PAT for mentatcmdb.
- `CMDB_GITHUB_TOKEN`: Optional PAT fallback if App PEM is unset (local/dev only). Do not inject this in cluster.
- `CMDB_WRITE_MODE`: `pr` (default) or `commit`
- `CMDB_INDEX_PATH` / `CMDB_SCHEMA_PATH`: Paths in the CMDB repo (defaults `cmdb/cis.yaml` and `schema/ci-record.schema.json`)

### Frontend (.env)

- `VITE_API_BASE_URL`: Backend API URL (default: http://localhost:8000)

## GitHub Actions Integration

### Deployment Workflows

The repository includes GitHub Actions workflows for automated deployment:

- **Preview Deployments** (`.github/workflows/deploy_preview.yaml`): 
  - Triggers on pull requests
  - Deploys to `platform-preview` namespace
  - Creates preview URLs like `release-dash-preview-{PR_NUMBER}.slingshotstaging.com`

- **Staging Deployments** (`.github/workflows/deploy_staging.yaml`):
  - Triggers on push to `main` branch
  - Deploys to `platform` namespace
  - Accessible at `release-dash.slingshotstaging.com`

### Webhook Integration

1. Add secrets to your GitHub repository:
   - `RELEASE_DASHBOARD_URL`: Dashboard service URL
   - `RELEASE_DASHBOARD_WEBHOOK_SECRET`: (Optional) Webhook secret

2. The workflow will automatically notify the dashboard after uploading artifacts to JFrog.

See [WEBHOOK_INTEGRATION.md](./WEBHOOK_INTEGRATION.md) for detailed integration instructions.

## API Endpoints

- `POST /webhook/artifact` - Receive artifact metadata from GitHub Actions
- `GET /api/releases` - List all releases (supports `repository`, `environment`, `limit`, `offset` query params)
- `GET /api/releases/{repository}` - Get releases for a specific repository
- `GET /api/releases/{repository}/{pr_number}` - Get specific release details
- `GET /api/artifacts/{repository}/{pr_number}/download/{filename}` - Download artifact from JFrog
- `GET /api/stats` - Get dashboard statistics
- `GET /api/cmdb/items` - List CMDB configuration items from GitHub (`cmdb/cis.yaml`). Returns **503** when `CMDB_ENABLED` is false.
- `GET /api/cmdb/items/{ciNumber}` - Get one CI
- `GET /api/cmdb/schema` - CI JSON Schema (bundled snapshot; available even when CMDB is disabled)
- `GET /api/cmdb/next-number` - Suggest next `MENTAT-{CAT}-NN`
- `POST /api/cmdb/items` - Create a CI by opening a GitHub PR with one per-CI YAML file (`CMDB_WRITE_MODE=pr`)
- `PUT /api/cmdb/items/{ciNumber}` - Update a CI (writes Git; not implemented yet)
- `POST /api/cmdb/refresh` - Bypass CMDB index cache

## How It Works

The application queries JFrog Artifactory's REST API directly to discover releases. It expects artifacts to be organized in the following directory structure:

```
generic-release-artifacts/
  {repository}/
    {environment}/
      pr-{prNumber}/
        {artifact files}
```

The application automatically discovers releases by traversing this directory structure and extracts artifact information from the file names.

### Supported Environments

The application supports multiple deployment environments:

- **Preview**: Temporary environments for pull request previews (deployed to `platform-preview` namespace)
- **Staging**: Staging environment for testing (deployed to `platform` namespace, accessible at `release-dash.slingshotstaging.com`)
- **Production**: Production environment (deployed to `platform` namespace)

Each environment has its own Helm values file:
- `helm/release-dash/values.preview.yaml` - Preview deployments
- `helm/release-dash/values.staging.yaml` - Staging environment
- `helm/release-dash/values.yaml` - Default/base configuration

## Deployment

### Kubernetes (Helm)

The application is deployed to Kubernetes using Helm charts. Deployment workflows are configured for:

- **Preview**: Automatically deployed on pull requests via `.github/workflows/deploy_preview.yaml`
- **Staging**: Deployed on push to main branch via `.github/workflows/deploy_staging.yaml`
- **Production**: Deploy manually or via production workflow (when configured)

Each environment uses environment-specific Helm values files:
- Preview: `helm/release-dash/values.preview.yaml`
- Staging: `helm/release-dash/values.staging.yaml`
- Production: Use `values.staging.yaml` as a template or create `values.production.yaml`

### Docker Compose

The included `docker-compose.yml` can be used for local development or production with proper environment configuration.

### Manual Kubernetes Deployment

1. Create ConfigMap and Secrets for environment variables
2. Deploy PostgreSQL (or use managed service)
3. Deploy backend and frontend services
4. Set up ingress for external access

### Standalone Services

Deploy backend and frontend as separate services, ensuring:
- Backend can access JFrog Artifactory
- Frontend can access backend API
- CORS is properly configured

## Features

- ✅ Centralized view of all releases across repositories
- ✅ Filter by repository and environment
- ✅ View release details and artifacts
- ✅ Download SBOMs and release notes
- ✅ Automatic webhook integration
- ✅ Real-time statistics
- ✅ Kubernetes deployment with health check endpoints (`/readyz`, `/livez`, `/healthcheck`)