# Project Agent Instructions

These instructions merge Rizum Guidelines with the Karpathy Guidelines for this
repository. Follow them for the entire project/thread until the user says
otherwise.

## Working Agreement

Rizum Guidelines are active for this project/thread until the user says
otherwise.

- Spend time on thinking; you do not need to use the commentary channel to report progress to me.

## Version Control Backups

- After each conversation that changes files, commit and push the completed
  changes so the work is backed up remotely.
- Split commits by coherent change area. Do not mix unrelated UI, architecture,
  documentation, release, or tooling edits into the same commit.
- Before committing, inspect the worktree and stage only the files that belong
  to that commit. Leave unrelated dirty files untouched.
- If pushing is blocked by authentication, network, conflicts, or unfinished
  work that should not be committed yet, report the blocker and the exact
  pending files instead of silently leaving the backup undone.

## Project Context

This app is a modified Fcitx5 for Android input method for physical T9-key
Android smart/feature phones. The primary workflow is Chinese T9 input through
Rime, English multi-tap input, numeric mode, and a compact always-available
screen keyboard for auxiliary controls.

## Documentation Hygiene

Read `CONTEXT.md` and only the relevant files under `docs/` before changing a
subsystem. Keep all project files, code comments, and developer documentation
in English; user-facing Chinese manuals may remain Chinese. Chat summaries must
be written in Chinese.

- `CONTEXT.md` is the concise, current architecture and domain map. Update
  existing sections instead of appending a chronological work log.
- `docs/adr/` stores durable product or architecture decisions that explain a
  non-obvious tradeoff.
- Runbooks and debugging notes store repeatable procedures, not implementation
  history or completed task narratives.
- Use the conversation plan tool for active work. Do not create persistent root
  `analysis.md`, `design.md`, or `plan.md` journals unless the user explicitly
  requests an artifact.
- Delete completed checklists and superseded reasoning once their durable
  decision is represented in code, `CONTEXT.md`, an ADR, or a runbook. Git
  history is the archive.

## Engineering Style

- State assumptions when requirements have multiple interpretations.
- Prefer the smallest code change that solves the requested behavior.
- Touch only files that are needed for the task.
- Match existing style, even if a different style might be preferable.
- Do not add speculative abstractions or configuration.
- Remove only unused code introduced by the current change.
- Mention unrelated issues instead of fixing them silently.
- Add comments when they preserve an important product or architecture decision.
  Prefer explaining why this approach exists, especially when it records a user
  requirement or tradeoff. Avoid comments that merely narrate what the code is
  already doing.

## Verification

Define success criteria before implementation. For fixes, use the narrowest
available syntax or static checks. Do not run full project compilations,
instrumented tests, or comprehensive suites unless the user explicitly asks.
Functional and end-to-end testing is delegated to the user.

When user testing is needed, provide a small checklist with:

- What changed.
- How to test in 2-5 concrete steps.
- Expected result.
- What details to report if it fails.
