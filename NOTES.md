# Notes

## Summary of Changes
- **SQL / Database (`TaskRepository.java`, `search_tasks.sql`, `task_search_package.sql`)**: Fixed operator precedence by wrapping title/description OR conditions in parentheses, preventing archived task leakage and status filter bypass on title matches. Added `id DESC` tie-breaker for deterministic sorting.
- **Backend Controller (`TaskController.java`)**: Added input validation returning HTTP 400 for invalid statuses and non-positive page/pageSize values. Removed artificial `Thread.sleep` delay.
- **Frontend (`useTasks.js`, `App.jsx`)**: Handled loading state in catch block, cleared stale errors, eliminated race conditions via an ignore flag, and reset pagination to page 1 on filter changes.
- **Tests (`pom.xml`, `TaskControllerTest.java`)**: Added `spring-boot-starter-test` and 8 automated regression tests covering search, status filtering, archived exclusion, pagination, and validation.

## What I Deliberately Left Unchanged and Why
- **In-memory pagination**: Maintained existing repository contract (`List<Task>` with `subList`) instead of rewriting to Spring Data `Pageable`, preserving minimal diff scope.
- **Database schema and seed data**: Kept H2 schema and `data.sql` intact to preserve environment compatibility.
- **Frontend styling**: Retained existing Vanilla CSS and UI component structure.

## Biggest Remaining Risk
The backend loads all matching rows into JVM memory before slicing with `subList`. For large production datasets, this will cause memory pressure and high query latency. It should be replaced with database-level pagination (`LIMIT`/`OFFSET` or JPA `Pageable`).

## Tools and AI Used
Used AI assistance for initial exploration, drafting test assertions, and structuring notes. Manually inspected root causes, reproduced bugs via curl, implemented targeted fixes, and verified results via Maven tests and Vite build.
