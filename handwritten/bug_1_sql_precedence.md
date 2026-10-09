# Handwritten Note Template: Bug 1 - SQL Operator Precedence

## 1. Location
- **Files**:
  - `backend/src/main/java/com/internal/tasktracker/TaskRepository.java` (line 14-16)
  - `db/queries/search_tasks.sql` (line 10-13)
  - `db/oracle/task_search_package.sql` (line 52-55, 66-69)
- **Layer**: Database / SQL & Spring Data JPA

## 2. Discovery
- **Reproduction 1**: Requested `curl "http://localhost:8080/api/tasks?q=migration"`. Task ID 20 (`archived = true`) was returned even though the query specified `WHERE archived = FALSE`.
- **Reproduction 2**: Requested `curl "http://localhost:8080/api/tasks?q=login&status=DONE"`. Task ID 1 (`Fix login redirect bug`, status `OPEN`) was returned despite filtering by `status=DONE`.

## 3. Root Cause
- In SQL, `AND` operator has higher precedence than `OR`.
- The clause:
  `WHERE archived = FALSE AND LOWER(title) LIKE :term OR LOWER(description) LIKE :term AND (:status IS NULL OR status = :status)`
  was evaluated as:
  `(archived = FALSE AND LOWER(title) LIKE :term) OR (LOWER(description) LIKE :term AND (:status IS NULL OR status = :status))`
- This caused two major bugs:
  1. Any archived task whose description matched `:term` and `:status` was returned (leaking archived data).
  2. Any task whose title matched `:term` satisfied the first expression, completely bypassing the `status` filter check.

## 4. Fix
- Grouped the title and description `OR` predicates inside parentheses:
  `WHERE archived = FALSE AND (LOWER(title) LIKE :term OR LOWER(description) LIKE :term) AND (:status IS NULL OR status = :status)`
- Mirrors the exact fix across `TaskRepository.java`, `search_tasks.sql`, and the Oracle reference package `task_search_package.sql`.

## 5. Verification
- **Automated Tests**:
  - `testSearchExcludesArchivedTasks()` in `TaskControllerTest.java` (searching 'migration' returns 1 task, excludes archived task 20).
  - `testStatusFilterEnforcedOnTitleMatches()` in `TaskControllerTest.java` (searching 'login' with `status=DONE` returns 0 tasks).
- **Manual Verification**: Re-ran curl commands; archived tasks are excluded and status filtering strictly applies to all matches.
