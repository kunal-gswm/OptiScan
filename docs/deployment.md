# OptiScan Backend Production Deployment Guide

This document provides instructions for deploying the OptiScan FastAPI backend service to production environments and connecting production Android clients.

---

## 1. System Architecture & Prerequisites

The OptiScan backend provides computer vision and scene understanding services for the OptiScan Android application.

- **Framework**: FastAPI (Python 3.11+)
- **ASGI Server**: Uvicorn
- **AI Vision Engine**: Google Gemini 2.5 Flash via REST API
- **On-Device Capabilities**: Google ML Kit OCR (runs on Android without backend connectivity)
- **Security Rule**: The Gemini API key must **never** be embedded into the Android client APK. All AI inference is proxied through the authenticated backend service.
- **Protocol**: Production Android apps require HTTPS (`https://`) due to Android's network security configuration blocking unencrypted cleartext HTTP traffic in release builds.

---

## 2. Environment Variables & Secrets

The service is configured using standard environment variables:

| Variable | Type | Default | Description |
|---|---|---|---|
| `VISION_PROVIDER` | `str` | `gemini` | Vision provider: `gemini` (production), `mock` (offline testing), or `openai` |
| `VISION_API_KEY` | `str` | `""` | Google Gemini API key (Required when `VISION_PROVIDER=gemini`) |
| `VISION_MODEL_NAME` | `str` | `gemini-2.5-flash` | Gemini model variant to utilize |
| `ENVIRONMENT` | `str` | `production` | Deployment environment name (`production`, `staging`, `development`) |
| `DEBUG` | `bool` | `false` | Enable/disable debug logging and auto-reload |
| `HOST` | `str` | `0.0.0.0` | Server host binding |
| `PORT` | `int` | `8000` | Server port binding (Cloud Run uses `$PORT`) |
| `CORS_ORIGINS` | `list` | `["*"]` | Allowed CORS origins for web clients |

---

## 3. Docker Containerization

The repository provides a multi-stage production Docker image:

- Python 3.11-slim base image.
- Dedicated non-root service account (`optiscan`).
- Integrated container health check on `/health`.

### Building the Docker Image

```bash
docker build -t optiscan-backend:latest ./backend
```

### Running with Docker Compose

From the repository root:

```bash
# Set your Gemini API key
export VISION_API_KEY="your-gemini-api-key-here"

# Start service in detached mode
docker compose up -d

# Verify container health
docker compose ps
curl http://localhost:8000/health
```

Expected health check response:
```json
{"status":"ok","service":"optiscan-backend"}
```

---

## 4. Production Hosting Options

### Option A: Google Cloud Run (Recommended)

Google Cloud Run is the recommended platform: it provides native HTTPS, auto-scaling to zero, and managed container runtime.

1. Install and authenticate the Google Cloud SDK:
   ```bash
   gcloud auth login
   gcloud config set project YOUR_PROJECT_ID
   ```

2. Build and submit the container to Google Artifact Registry:
   ```bash
   gcloud builds submit backend --tag gcr.io/YOUR_PROJECT_ID/optiscan-backend:latest
   ```

3. Deploy the service to Cloud Run:
   ```bash
   gcloud run deploy optiscan-backend \
     --image gcr.io/YOUR_PROJECT_ID/optiscan-backend:latest \
     --platform managed \
     --region us-central1 \
     --allow-unauthenticated \
     --set-env-vars VISION_PROVIDER=gemini,ENVIRONMENT=production \
     --set-secrets VISION_API_KEY=GEMINI_API_KEY:latest \
     --memory 1Gi \
     --cpu 1 \
     --min-instances 0 \
     --max-instances 10
   ```

4. Retrieve the deployed HTTPS URL:
   ```bash
   gcloud run services describe optiscan-backend --format='value(status.url)'
   ```
   Example: `https://optiscan-backend-xyz-uc.a.run.app`

---

### Option B: VPS with Docker & Caddy (Automatic HTTPS)

For hosting on an Ubuntu/Debian VPS with automated Let's Encrypt SSL certificates:

1. Install Docker and Caddy on the VPS:
   ```bash
   sudo apt-get update
   sudo apt-get install -y docker.io docker-compose caddy
   ```

2. Clone repository and configure `.env`:
   ```bash
   git clone https://github.com/amvbl/OptiScan.git /opt/optiscan
   cd /opt/optiscan
   cat <<EOF > backend/.env
   VISION_PROVIDER=gemini
   VISION_API_KEY=your_gemini_api_key_here
   ENVIRONMENT=production
   DEBUG=false
   EOF
   ```

3. Configure Caddy (`/etc/caddy/Caddyfile`):
   ```caddy
   api.yourdomain.com {
       reverse_proxy 127.0.0.1:8000
   }
   ```

4. Start Docker container and reload Caddy:
   ```bash
   docker compose up -d
   sudo systemctl reload caddy
   ```

---

### Option C: Render / Railway / PaaS

1. Connect your Git repository to Render or Railway.
2. Select **Docker** environment or Python 3.11 runtime.
3. If using standard Python build:
   - Build Command: `pip install -r backend/requirements.txt`
   - Start Command: `cd backend && uvicorn app.main:app --host 0.0.0.0 --port $PORT`
4. In the service settings, add Environment Variables:
   - `VISION_PROVIDER` = `gemini`
   - `VISION_API_KEY` = `your_gemini_api_key`
   - `ENVIRONMENT` = `production`
5. Copy the generated HTTPS service URL.

---

## 5. Android Client Configuration

### Connecting the Android App to Your Backend

1. **In-App Configuration (Recommended for live demos & physical devices)**:
   - Open the OptiScan app.
   - Tap the **Settings** gear icon in the top right.
   - Scroll to the **Backend Server Connection** section.
   - Enter your hosted HTTPS URL (e.g., `https://api.yourdomain.com/` or `https://optiscan-backend-xyz-uc.a.run.app/`).
   - Tap **Save Server URL**.
   - Tap **Test Connection** to verify live connectivity. A spoken announcement and status pill will confirm connectivity.

2. **Build-Time Default Configuration**:
   - For custom enterprise or branded builds, edit `android/app/build.gradle.kts`:
     ```kotlin
     release {
         buildConfigField("String", "DEFAULT_BACKEND_URL", "\"https://api.yourdomain.com/\"")
     }
     ```

### Cleartext Traffic & Scoped Security Rules

- **Debug Builds**: Permit cleartext `http://` traffic scoped via `src/debug/res/xml/network_security_config.xml` strictly for local development testing (`http://10.0.2.2:8000/`, USB reverse `http://127.0.0.1:8000/`, and Wi-Fi LAN HTTP).
- **Release Builds**: Strictly enforce secure HTTPS traffic (`src/main/res/xml/network_security_config.xml` with `cleartextTrafficPermitted="false"`). Loopback and insecure HTTP endpoints are blocked.
- **Unconfigured Backend Handling**: If no hosted URL is configured in Release, the Settings screen displays *"No hosted backend configured"*. When a user attempts visual scene description without a backend, the app cleanly announces:
  > *"AI object description is unavailable because no backend is connected. Connect to a local backend to use live AI analysis."*
- **On-Device OCR Independence**: Google ML Kit OCR functions 100% on-device without requiring any network connection or configured backend.

---

## 6. Offline vs. Online Capabilities

OptiScan provides graceful degradation when network connectivity is lost:

| Feature | Online (Connected Backend) | Offline / Unconfigured Backend |
|---|---|---|
| **Text Reading (Read Text)** | High-accuracy scene text with AI context | **100% On-Device:** Google ML Kit OCR; reads text aloud via TTS independently |
| **Object & Scene Description** | Gemini 2.5 Flash spatial scene analysis | Clean honest announcement: *"AI object description is unavailable because no backend is connected. Connect to a local backend to use live AI analysis."* |
| **Follow-up Questions** | Interactive multi-turn voice and text Q&A | Alerts user that connected backend is required |
| **Guided Task Mode** | Step-by-step assistance for physical tasks | Requires connected backend or Offline Mock Mode |
| **Voice Navigation & TTS** | Full English & Hindi TTS | On-device Android TextToSpeech engine |
| **Offline Mock Mode** | N/A | Full offline simulation clearly marked with `[Simulated Description - Mock Mode]`, `[Simulated Label - Mock Mode]`, etc. |
