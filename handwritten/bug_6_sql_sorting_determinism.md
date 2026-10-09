# Handwritten Note Template: Bug 6 - Non-Deterministic Sorting Tie-Breaker

## 1. Location
- **Files**:
  - `backend/src/main/java/com/internal/tasktracker/TaskRepository.java` (line 16)
  - `db/queries/search_tasks.sql` (line 14)
  - `db/oracle/task_search_package.sql` (line 70)
- **Layer**: Database Query / SQL

## 2. Discovery
- Seeded task in `data.sql` stated: "Page 2 shows the last item from page 1 as its first item".
- Code inspection revealed `ORDER BY created_at DESC` with no secondary tie-breaking column.

## 3. Root Cause
- Relational databases (H2, Oracle, PostgreSQL) do not guarantee stable row ordering across queries when multiple rows share identical `created_at` timestamp values.
- During pagination, non-deterministic ordering causes records with identical timestamps to swap positions between page fetches, producing duplicate or missing records across pages.

## 4. Fix
- Added the unique primary key `id DESC` as a deterministic tie-breaker:
  `ORDER BY created_at DESC, id DESC`
- Applied consistently across `TaskRepository.java`, `search_tasks.sql`, and `task_search_package.sql`.

## 5. Verification
- **Automated Tests**:
  - `testPaginationAndPageSize()` in `TaskControllerTest.java` ensures deterministic pagination across page boundaries without duplicate rows.
- Re-running page 1 and page 2 fetches returns distinct, stable result slices.
