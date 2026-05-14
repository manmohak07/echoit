# Echoit

> Secure, ephemeral peer-to-peer file sharing. no accounts, no cloud storage, no permanent copies.

A sender uploads a file and gets a **port-based invite code** plus a **one-time PIN**. The recipient enters both to download the file. The file is automatically destroyed after **60 seconds** or after **2 failed PIN attempts**, whichever comes first.

---

## Features

| Feature | Details |
|---|---|
| Drag & drop upload | Click or drop any file to start sharing |
| Invite code | A random ephemeral port (49152–65535) acts as the share link |
| PIN authentication | A random 4-digit PIN is generated per session; recipient needs both code + PIN |
| Session timer | 60-second countdown starts on upload; file auto-deletes at zero |
| Colour-coded timer | Green > 30 s → Amber 10–30 s → Red < 10 s |
| PIN retry limiting | Max 2 wrong attempts before the session is permanently terminated |
| Copy to clipboard | One-click copy for both the invite code and PIN |
| Auto file cleanup | Temp file + TCP socket closed on expiry or lockout |
| Docker support | Single `docker-compose up` spins up both services |
| One-command startup | `start.sh` / `start.bat` builds and launches everything locally |

---

## Architecture

```
  ┌───────────────────────────────────────────────────────────────────────┐
  │                        Browser  :3000                                 │
  │                                                                       │
  │   ┌──────────────────┐    ┌───────────────────┐   ┌───────────────┐   │
  │   │  FileUpload.tsx  │    │ FileDownload.tsx  │   │InviteCode.tsx │   │
  │   │  (drag & drop)   │    │  (port + PIN form)│   │(timer + copy) │   │
  │   └────────┬─────────┘    └────────┬──────────┘   └───────────────┘   │
  └────────────┼───────────────────────┼──────────────────────────────────┘
               │ POST /api/upload      │ GET /api/download/:port?pin=
               ▼                       ▼
  ┌──────────────────────────────────────────────────────────────────────┐
  │               Next.js API Proxy  (next.config.js)                    │
  │          /api/*  ──────────────►  http://localhost:8080              │
  └─────────────────────────────────────┬────────────────────────────────┘
                                        │ HTTP  :8080
                                        ▼
  ┌──────────────────────────────────────────────────────────────────────┐
  │                    Java Backend  :8080                               │
  │                                                                      │
  │   ┌────────────────────┐      ┌─────────────────────────────────┐    │
  │   │   UploadHandler    │      │        DownloadHandler          │    │
  │   │  POST /upload      │      │   GET /download/:port?pin=      │    │
  │   └─────────┬──────────┘      └───────────────┬─────────────────┘    │
  │             │                                 │                      │
  │             └──────────────┬──────────────────┘                      │
  │                            ▼                                         │
  │             ┌──────────────────────────────┐                         │
  │             │          FileSharer          │                         │
  │             │  offerFile()  validatePin()  │                         │
  │             │  startFileServer()           │                         │
  │             │  terminateSession()          │                         │
  │             └──────────────┬───────────────┘                         │
  │                            │                                         │
  │          ┌─────────────────┴──────────────────┐                      │
  │          ▼                                    ▼                      │
  │  ┌───────────────────────┐      ┌───────────────────────────────┐    │
  │  │      FileSession      │      │  TCP ServerSocket :49152–65535│    │
  │  │  fileName             │      │  (one per active session)     │    │
  │  │  port                 │      │  FileSenderHandler (thread)   │    │
  │  │  pin                  │      └───────────────────────────────┘    │
  │  │  retryCount (max 2)   │                                           │
  │  └───────────────────────┘                                           │
  │                                                                      │
  │         ScheduledExecutorService  ──►  cleanUpSession() @ +60 s      │
  └──────────────────────────────────────────────────────────────────────┘
```

> **Key design choice:** The HTTP server is purely the control plane.
> Actual file bytes travel over a **raw TCP socket** on an ephemeral port.
> The Java backend opens a `Socket("127.0.0.1", port)` to its own TCP server and
> pipes the bytes through the HTTP response and the browser never touches the TCP layer.

---

## Data Flow

### Upload Flow

```
  User selects / drops a file
          │
          ▼
  FileUpload.tsx
          │
          │  POST /api/upload  (multipart/form-data)
          ▼
  Next.js proxy  ──────────────────────────►  Java UploadHandler :8080
                                                      │
                                          ┌───────────▼────────────┐
                                          │  Parse multipart body  │
                                          │  (custom MultiParser)  │
                                          └───────────┬────────────┘
                                                      │
                                          ┌───────────▼────────────┐
                                          │  Save to OS temp dir   │
                                          │  /tmp/echoit-uploads/  │
                                          └───────────┬────────────┘
                                                      │
                                          ┌───────────▼────────────┐
                                          │  FileSharer            │
                                          │  · pick random port    │
                                          │    (49152–65535)       │
                                          │  · generate 4-digit PIN│
                                          │  · create FileSession  │
                                          └───────────┬────────────┘
                                                      │
                                       ┌──────────────┴──────────────┐
                                       ▼                             ▼
                              Start TCP ServerSocket       Schedule 60 s cleanup
                              on chosen port (thread)      (ScheduledExecutorService)
                                       │
                                       ▼
                              Wait for one connection
                              (ServerSocket.accept())
                                        │
                                        ▼
                                JSON { port, pin }
                                        │
                                        ▼
                                InviteCode.tsx
                                · Display invite code + PIN
                                · Start 60 s countdown
                                · Copy-to-clipboard buttons
```

---

### Download Flow

```
  Receiver enters invite code (port) + PIN
          │
          ▼
  FileDownload.tsx
          │
          │  GET /api/download/:port?pin=XXXX
          ▼
  Next.js proxy  ──────────────────────────►  Java DownloadHandler :8080
                                                      │
                                          ┌───────────▼────────────┐
                                          │  Parse port from path  │
                                          │  Parse pin from query  │
                                          └───────────┬────────────┘
                                                      │
                                          ┌───────────▼────────────┐
                                          │  FileSharer            │
                                          │  .validatePin()        │
                                          └───────────┬────────────┘
                                                      │
                    ┌─────────────────────────────────┤
                    │                                 │
          ┌─────────▼──────────┐           ┌─────────▼──────────────┐
          │   NOT_FOUND        │           │   WRONG_PIN            │
          │   → 404            │           │   retryCount--         │
          └────────────────────┘           │   → 401 + retriesLeft  │
                                           │                        │
                    ┌──────────────────────┤   retryCount == 0?     │
                    │                      │   → terminateSession() │
                    │                      │   → 403 LOCKED_OUT     │
                    │                      └────────────────────────┘
                    │
          ┌─────────▼──────────┐
          │   CORRECT          │
          └─────────┬──────────┘
                    │
                    │  open TCP Socket → 127.0.0.1:<port>
                    ▼
          FileSenderHandler (Java thread)
          · write  "Filename: <name>\n"
          · stream file bytes
                    │
                    ▼
          DownloadHandler reads TCP stream
          · parse filename from header line
          · pipe bytes to HTTP response
            Content-Disposition: attachment; filename="<name>"
                    │
                    ▼
          Browser saves file to disk
```

---

### Session Lifecycle

```
  File uploaded
       │
       ▼
  FileSession created
  ┌─────────────────────────┐
  │ fileName  → /tmp/...    │
  │ port      → 54321       │
  │ pin       → 7382        │
  │ retryCount → 2          │
  └────────────┬────────────┘
               │
       ┌───────┴────────────────────────┐
       │                                │
       ▼                                ▼
  Wrong PIN entered               60 s timer fires
  retryCount = 1                  ScheduledExecutor
       │                                │
       ▼                                │
  Wrong PIN again                       │
  retryCount = 0                        │
       │                                │
       ▼                                ▼
  terminateSession()  ◄─────────  cleanUpSession()
       │
       ▼
  ┌─────────────────────────┐
  │ · ServerSocket.close()  │
  │ · File.delete()         │
  │ · Remove from HashMap   │
  └─────────────────────────┘
```

---

## Prerequisites

| Requirement | Version |
|---|---|
| Node.js + npm | 18+ |
| Java JDK | 17+ |
| Maven | 3.6+ |
| Docker + Compose | (optional, for containerised deployment) |

---

## Getting Started

### Option 1 — On a VPS (recommended)

```bash
1. git clone https://github.com/manmohak07/echoit.git
2. cd echoit
3. chmod +x vps-setup.sh  
4. ./vps-setup.sh   
```

The vps-setup.sh script will:

- Install Java 17, Node.js, Maven, Nginx, and PM2

- Build both frontend and backend

- Configure Nginx reverse proxy

- Start services with PM2

- Set up auto-restart on boot
---

### Option 2 — Local
```bash
1. git clone https://github.com/manmohak07/echoit.git
2. cd echoit
3. mvn clean package
4. java -jar target/echoit-1.0-SNAPSHOT.jar
5. cd ui
6. npm install
7. npm run dev
8. Access the application
9. Frontend: http://localhost:3000
10. Backend: http://localhost:8080
```
---

### Option 3 — Docker

```bash
docker-compose up --build
```

- Backend on `:8080`, frontend on `:3000`
- The `BACKEND_URL` env var in the compose file routes the Next.js proxy to the backend container automatically.

---

## How to Use

### Sharing a file

1. Open the **Share a File** tab.
2. Drag & drop a file or click to browse.
3. Once the backend responds, you'll see:
   - an **invite code** (the ephemeral port number)
   - a **4-digit PIN**
4. Share **both** values with your recipient — they need each one to download.
5. The session is live for **60 seconds**. The file is gone when the timer hits zero.

### Receiving a file

1. Open the **Receive a File** tab.
2. Enter the **invite code** and the **PIN**.
3. Click **Download File** — the file arrives in your browser with its original name.
4. You get **2 attempts** to enter the correct PIN. On the third failure the session is permanently destroyed.

---

## Project Structure

```
echoit/
├── src/main/java/echoit/
│   ├── App.java                        ← entry point, starts HTTP server on :8080
│   ├── controller/
│   │   ├── FileController.java         ← HTTP server, UploadHandler, MultiParser, CORS
│   │   └── DownloadHandler.java        ← PIN validation, TCP→HTTP byte bridging
│   ├── service/
│   │   └── FileSharer.java             ← session registry, TCP file server, PIN logic
│   └── util/
│       ├── FileSession.java            ← session model (fileName, port, pin, retryCount)
│       └── UploadUtils.java            ← random port + PIN generators
│
├── ui/                                 ← Next.js frontend
│   ├── src/
│   │   ├── app/                        ← App Router pages & global styles
│   │   └── components/
│   │       ├── FileUpload.tsx          ← drag-and-drop upload zone
│   │       ├── FileDownload.tsx        ← invite code + PIN download form
│   │       └── InviteCode.tsx          ← countdown timer, code & PIN display
│   └── next.config.js                  ← API proxy rewrites + BACKEND_URL config
│
├── Dockerfile.backend
├── Dockerfile.frontend
├── docker-compose.yml
├── start.sh                            ← one-command launcher (Linux/macOS)
├── start.bat                           ← one-command launcher (Windows)
└── pom.xml
```

---

## Tech Stack

| Layer | Technology |
|---|---|
| Frontend | Next.js 14, React 18, TypeScript, Tailwind CSS |
| HTTP backend | Java 17, `com.sun.net.httpserver.HttpServer` |
| File transfer | Raw TCP sockets (`java.net.ServerSocket`) |
| Build | Maven (backend), npm (frontend) |
| Containerisation | Docker, Docker Compose, Nginx |
