# RIZUM-AGENT.md

Behavioral guidelines to ensure structured planning, living documentation, and clear communication. Merge with project-specific instructions as needed.

**Tradeoff:** These guidelines bias toward deliberate planning and continuous documentation over immediate, undocumented coding.

## 1. Mandatory Documentation

**Always ground your work in explicit analysis, design, and planning.**

- Create and maintain three specific files in the project directory before executing tasks: `analysis.md`, `design.md`, and `plan.md`.
- `analysis.md`: Focuses on understanding the problem, edge cases, and constraints.
- `design.md`: Focuses on architectural choices, system structure, API contracts, or UI/UX patterns.
- Do not begin writing project code until the foundation in these three documents is established.

## 2. Connected, Testable Planning

**Bridge the gap between theory and execution with actionable steps.**

- Ensure `plan.md` directly references and connects to the context established in both `analysis.md` and `design.md`.
- Break down the plan into sequential steps using To-Do checklists (e.g., `- [ ]`).
- Size your steps appropriately: they must be small enough to be independently testable and verifiable, but not so micro-granular that they clutter the plan (e.g., testable features/functions, not individual lines of code).

## 3. Living Documents

**Keep your documentation perfectly synced with reality.**

- Actively update the To-Do list in `plan.md` (e.g., `- [x]`) every time a step is completed. 
- Treat your documentation as agile. If working feedback, bugs, or new discoveries cause the actual implementation to shift, stop and update `analysis.md`, `design.md`, and `plan.md` to reflect the new direction. Never let the code drift away from the documentation.

## 4. Bilingual Communication Protocol

**Summarize in Chinese, document in English.**

- Write all project files, code, comments, `analysis.md`, `design.md`, `plan.md`, and technical documentation strictly in English.
- When outputting the summary of your changes in the chat interface, you must write the summary in Chinese. Keep all other parts of the chat response in English where appropriate.

---

**These guidelines are working if:** the project directory always contains up-to-date `analysis.md`, `design.md`, and `plan.md` files; execution perfectly matches the checked-off steps; documentation shifts dynamically with implementation changes; and chat change summaries are consistently delivered in Chinese.
