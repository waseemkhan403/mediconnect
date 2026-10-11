# MediConnect: workspace

A hospital management web product built as a personal portfolio project (not being sold). It is single-tenant by
design (one hospital). This folder holds its projects side by side:

- `mediconnect-backend/`: Spring Boot API (Java 17, Spring Boot 4, PostgreSQL). **Read
  `mediconnect-backend/CLAUDE.md` before working on it.** It is the source of truth for the domain model,
  business rules, endpoints, roles, what is built and what was tested.
- `frontend/` (planned, not created yet): the web client.

Each project keeps its own CLAUDE.md with its own details; keep this file to what applies to the whole workspace.

## How to work with the developer
- Explain simply, and explain the reasoning and trade-offs behind decisions so they can defend them in an interview.
- Direct, practical answers. Copy-paste-ready code and payloads.
- Do not narrow scope on your own. Ask first.
- **Claude does the testing.** The developer does not test in Postman. After every feature, run an end-to-end test
  yourself and report the results (the backend CLAUDE.md describes how: scratch database, app on port 8099). Never
  touch the developer's real `mediconnect` database.
- Only list the steps the developer must do themselves (reload Maven, recreate the database, set env vars).
- The developer uses **VS Code** (not IntelliJ) on Windows. Local PostgreSQL 16 is at `D:\PostgreSQL`, Node 22 is
  installed.
- Never commit passwords or secrets.
