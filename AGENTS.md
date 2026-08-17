# Backend Codex instructions

Read `.claude/system.md` in full before changing backend code, running backend
commands, or making an implementation decision.  It is the canonical backend
engineering constitution; this file is only its Codex-compatible entry point.
Also read `../Smart-WorkFlow-Knowledge/system.md` when the task uses the shared
role, receipt, knowledge, or workflow rules.

This directory is **backend executor scope**.  Work only on this repository;
do not read, edit, build, test, or analyse `../Smart-WorkFlow-Web/`, and do not
run frontend commands.  Do not create or alter product direction: execute an
already-issued direction and report any infeasibility through the prescribed
receipt path.

For Maven compilation or tests, use `MAVEN_OPTS="-Xmx512m"`.  Before any
compile/test/build operation, check that no frontend compile/test/build process
is running; wait rather than run the two stacks concurrently.  Use the
constitution's module boundaries, schema/security constraints, verification
gate, and structured receipts without exception.

Claude-only settings under `.claude/settings*.json` do not replace Codex's
approval and sandbox policies.

