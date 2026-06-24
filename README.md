# Malling Bio – IMS3000 Orchestrator (Quarkus + Gradle multiproject)

This is a lightweight Quarkus-based control plane for two IMS3000 servers (Sal 1 & Sal 2).

## Key goals
- Separate **app mode**: `AUTO`, `MANUAL` (read-only), `MAINTENANCE` (global pause)
- Per-screen **connectivity**: `NO_CONNECTION` / `CONNECTING` / `CONNECTED`
- Per-screen **operational state** (when connected): `IDLE`, `INTRO_LOOP`, `STARTING`, `REKLAMER`, `TRAILERS`, `FEATURE`, `ENDING`, `PREPARE_NEXT`
- Support **two stub/test-driver screens** for development without cinema access

## Modules
- `apps/orchestrator` – Quarkus REST API + scheduler/state supervision
- `libs/domain` – enums + domain models (states, snapshots)
- `libs/ims-soap` – IMS client abstraction + (placeholder) SOAP implementation
- `libs/stub-ims` – deterministic simulator for two screens (dev/testing)
- `libs/spl-parser` – SPL Base64 decode + XML parsing placeholders

## Running (dev)
```bash
./gradlew :apps:orchestrator:quarkusDev
```

## Configuration
See `apps/orchestrator/src/main/resources/application.yaml`.

### Profiles
- `dev` uses the stub IMS by default.
- `prod` is intended for real SOAP (to be implemented in `libs/ims-soap`).

## Gradle/Cache isolation (work vs hobby)
Recommended: run builds using a dedicated `GRADLE_USER_HOME`.
Example Powershell:
```powershell
$env:GRADLE_USER_HOME='C:\hobby\.gradle\malling-bio-ims'
./gradlew --version
```

Quarkus versions used: 3.35.3
Gradle wrapper version: 9.3.1
