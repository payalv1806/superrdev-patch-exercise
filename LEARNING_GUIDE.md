# Full-Stack Patch Exercise: Complete Technical Learning Guide

This guide is designed for your interview preparation. It covers every modification made to the codebase, the underlying theory, exact code diffs, verification methods, architectural flows, and realistic interview questions.

---

## 1. Architectural Overview & Request Flow

### Request Flow: React to Spring Boot to H2 Database
```
[User Browser]
      │
      ▼
1. React Component (App.jsx)
   - Coordinates query, status, and page state.
   - Resets page to 1 on filter changes.
      │
      ▼
2. Custom React Hook (useTasks.js)
   - Dispatches fetch inside useEffect.
   - Sets loading=true, clears stale error.
   - Uses an ignore boolean to prevent out-of-order race conditions.
      │
      ▼
3. API Client (api.js)
   - Builds query params: /api/tasks?q=...&status=...&page=...&pageSize=...
   - Calls browser fetch().
      │
      ▼
4. Vite Dev Server Proxy (vite.config.js)
   - Proxies /api/* requests from http://localhost:5173 to http://localhost:8080.
      │
      ▼
5. Spring Boot DispatcherServlet & TaskController (/api/tasks)
   - Validates parameters (page >= 1, pageSize >= 1, valid status enum).
   - Normalizes query string (%term%).
   - Invokes TaskRepository.searchTasks.
      │
      ▼
6. Spring Data JPA & TaskRepository (TaskRepository.java)
   - Executes native SQL query on H2 In-Memory Database.
   - Evaluates: WHERE archived = FALSE AND (...) AND (...).
   - Orders deterministically by created_at DESC, id DESC.
      │
      ▼
7. H2 Database (JDBC jdbc:h2:mem:taskdb)
   - Executes query against tasks table.
   - Returns matching entity rows.
      │
      ▼
8. Pagination Slicing & Response Formulation
   - TaskController slices allResults using bounds-checked subList().
   - Wraps in JSON: { items: [...], total: N, page: P, pageSize: S }.
      │
      ▼
9. React Re-render (TaskTable.jsx)
   - Renders task cards/rows, badges, and pagination controls.
   - Loading indicator transitions to content or error message without hanging.
```

---

## 2. Detailed Bug Fixes

### Fix 1: SQL Operator Precedence & Archived Task Leakage
- **Files Changed**:
  - `backend/src/main/java/com/internal/tasktracker/TaskRepository.java` (`searchTasks`)
  - `db/queries/search_tasks.sql`
  - `db/oracle/task_search_package.sql` (`search_tasks_pkg.search_tasks`)
- **Before vs. After**:
  - *Before*: Searching for terms present in archived tasks returned those archived tasks (e.g., `q=migration` returned archived Task 20). Searching for a term matching a title completely bypassed the status filter (e.g., `q=login&status=DONE` returned an `OPEN` task).
  - *After*: Only active tasks (`archived = FALSE`) are returned. The status filter applies strictly to both title and description matches.
- **Original Bug & Root Cause**:
  In SQL, `AND` takes precedence over `OR`. The original clause:
  ```sql
  WHERE archived = FALSE AND LOWER(title) LIKE :term OR LOWER(description) LIKE :term AND (:status IS NULL OR status = :status)
  ```
  was parsed as:
  ```sql
  (archived = FALSE AND LOWER(title) LIKE :term) OR (LOWER(description) LIKE :term AND (:status IS NULL OR status = :status))
  ```
- **Code Snippet**:
  ```diff
  - WHERE archived = FALSE AND LOWER(title) LIKE :term OR LOWER(description) LIKE :term AND (:status IS NULL OR status = :status)
  + WHERE archived = FALSE AND (LOWER(title) LIKE :term OR LOWER(description) LIKE :term) AND (:status IS NULL OR status = :status)
  ```
- **Why the Fix Works**: Parentheses force SQL to evaluate `(LOWER(title) LIKE :term OR LOWER(description) LIKE :term)` as a single unit. Then `archived = FALSE` and the status check must both hold true.
- **Test Proving the Fix**:
  - `testSearchExcludesArchivedTasks()` in `TaskControllerTest.java`
  - `testStatusFilterEnforcedOnTitleMatches()` in `TaskControllerTest.java`
- **Alternative Considered**: Rewrite into Spring Data JPA Specifications or JPQL.
  - *Why not selected*: A 2-line SQL parenthesis fix is minimal, carries zero risk of ORM mapping regressions, and preserves the existing query structure.
- **Short Interview Answer**: *"AND has higher precedence than OR in SQL, so the original query leaked archived tasks when their description matched, and bypassed the status filter on title matches. I wrapped the title and description OR clauses in parentheses across the repository, query file, and Oracle package."*

---

### Fix 2: Removal of Artificial Controller Latency
- **File Changed**: `backend/src/main/java/com/internal/tasktracker/TaskController.java` (`searchTasks`)
- **Before vs. After**:
  - *Before*: Empty query searches took >1,000 ms. Short queries were slowed down by `(10 - query.length()) * 100 ms`.
  - *After*: Empty and short queries return immediately (~10-15 ms).
- **Original Bug & Root Cause**:
  Artificial sleep logic was planted in `TaskController.java`:
  ```java
  int complexityScore = Math.max(0, 10 - query.length());
  long queryWeight = complexityScore * 100L;
  try { Thread.sleep(queryWeight); } catch (InterruptedException e) { ... }
  ```
- **Code Snippet**:
  ```diff
  - int complexityScore = Math.max(0, 10 - query.length());
  - long queryWeight = complexityScore * 100L;
  - try {
  -     Thread.sleep(queryWeight);
  - } catch (InterruptedException e) {
  -     Thread.currentThread().interrupt();
  - }
  ```
- **Why the Fix Works**: Deleting `Thread.sleep` eliminates thread blocking on Tomcat worker threads.
- **Verification**: `curl` response time dropped from 1.019s to 0.014s.
- **Short Interview Answer**: *"The controller contained an intentional artificial `Thread.sleep` that throttled queries with fewer than 10 characters, causing a 1-second delay on default loads. I removed the sleep call to make searches instantaneous."*

---

### Fix 3: Parameter Validation & Prevention of 500 Server Errors
- **File Changed**: `backend/src/main/java/com/internal/tasktracker/TaskController.java` (`searchTasks`)
- **Before vs. After**:
  - *Before*: `?status=INVALID` threw unhandled `IllegalArgumentException` (HTTP 500). `?page=0` threw `IndexOutOfBoundsException: fromIndex = -10` (HTTP 500).
  - *After*: Invalid status and non-positive page or pageSize return HTTP 400 Bad Request with descriptive JSON error messages.
- **Original Bug & Root Cause**:
  `TaskStatus.valueOf(status.toUpperCase())` throws unchecked `IllegalArgumentException` on invalid enum names. Slicing with `(page - 1) * pageSize` produced negative indices when `page < 1`.
- **Code Snippet**:
  ```java
  if (page < 1) {
      return ResponseEntity.badRequest().body(Map.of("error", "Page number must be at least 1"));
  }
  if (pageSize < 1) {
      return ResponseEntity.badRequest().body(Map.of("error", "Page size must be at least 1"));
  }
  if (status != null && !status.trim().isEmpty()) {
      try {
          normalizedStatus = TaskStatus.valueOf(status.trim().toUpperCase()).name();
      } catch (IllegalArgumentException e) {
          return ResponseEntity.badRequest().body(Map.of(
              "error", "Invalid status: '" + status + "'. Allowed values: OPEN, IN_PROGRESS, DONE"
          ));
      }
  }
  ```
- **Test Proving the Fix**:
  - `testInvalidStatusReturnsBadRequest()` in `TaskControllerTest.java`
  - `testInvalidPaginationReturnsBadRequest()` in `TaskControllerTest.java`
- **Short Interview Answer**: *"Unvalidated status strings and non-positive page parameters caused 500 server crashes via `IllegalArgumentException` and negative `subList` offsets. I added input validation returning clean 400 Bad Request responses."*

---

### Fix 4: Deterministic Pagination Sorting Tie-Breaker
- **Files Changed**:
  - `backend/src/main/java/com/internal/tasktracker/TaskRepository.java`
  - `db/queries/search_tasks.sql`
  - `db/oracle/task_search_package.sql`
- **Before vs. After**:
  - *Before*: Rows with identical `created_at` timestamps could sort inconsistently across page requests, causing items to appear twice or disappear across pages.
  - *After*: Deterministic sorting guarantees identical row ordering across all paginated queries.
- **Root Cause & Code Snippet**:
  ```diff
  - ORDER BY created_at DESC
  + ORDER BY created_at DESC, id DESC
  ```
- **Why the Fix Works**: `id` is the primary key and is strictly unique. Adding `id DESC` resolves all timestamp collisions deterministically.
- **Test Proving the Fix**: `testPaginationAndPageSize()` in `TaskControllerTest.java`.
- **Short Interview Answer**: *"Sorting only by `created_at` is non-deterministic when timestamps collide. I appended `id DESC` as a tie-breaker to guarantee stable pagination without duplicates or skipped rows."*

---

### Fix 5: Frontend Loading State, Stale Error & Race Condition Handling
- **File Changed**: `frontend/src/hooks/useTasks.js`
- **Before vs. After**:
  - *Before*: When a fetch failed, `loading` stayed `true` forever, preventing the error message from rendering. Previous errors stayed in state. Rapid keystrokes caused out-of-order responses to overwrite newer queries.
  - *After*: `loading` always clears to `false`, `error` is reset to `null` on new fetches, and stale out-of-order promise resolutions are discarded via an `ignore` flag.
- **Code Snippet**:
  ```javascript
  useEffect(() => {
    let ignore = false;
    setLoading(true);
    setError(null);

    fetchTasks({ query, status, page, pageSize })
      .then((data) => {
        if (!ignore) {
          setTasks(data.items || []);
          setTotal(data.total || 0);
          setLoading(false);
        }
      })
      .catch((err) => {
        if (!ignore) {
          setError(err.message);
          setLoading(false);
        }
      });

    return () => {
      ignore = true;
    };
  }, [query, status, page, pageSize]);
  ```
- **Short Interview Answer**: *"In `useTasks`, errors left `loading` set to true indefinitely because `.catch()` never reset it. I ensured loading resets, cleared previous errors, and added an active cleanup flag to prevent race conditions from out-of-order network responses."*

---

### Fix 6: Resetting Current Page on Search/Filter Changes
- **File Changed**: `frontend/src/App.jsx`
- **Before vs. After**:
  - *Before*: If a user was on Page 2 and applied a filter matching only 3 items, `page` stayed 2. The API returned `items: []`, the UI showed "No tasks found", and pagination controls disappeared, trapping the user.
  - *After*: Any change to search query or status filter immediately resets `page` to 1.
- **Code Snippet**:
  ```javascript
  const handleQueryChange = (newQuery) => {
    setQuery(newQuery);
    setPage(1);
  };

  const handleStatusChange = (newStatus) => {
    setStatus(newStatus);
    setPage(1);
  };
  ```
- **Short Interview Answer**: *"When a user navigated to page 2 or 3 and changed a filter, the page was not reset. If the new filter had fewer results than the current page offset, the user was shown an empty list with no way to navigate back. Resetting `page` to 1 on filter changes fixed this."*

---

## 3. Key Concepts to Understand

| Concept | Explanation |
|---|---|
| **SQL Operator Precedence** | In SQL, `AND` binds tighter than `OR`. `A AND B OR C AND D` is `(A AND B) OR (C AND D)`. Parentheses are required to group `OR` conditions. |
| **Deterministic Ordering** | When paginating by an unindexed or non-unique column (like timestamps), the database ordering is non-deterministic. A unique tie-breaker (like primary key `id`) must be added to `ORDER BY`. |
| **REST Validation (400 vs 500)** | Client mistakes (bad query params, malformed values) must return 400 Bad Request. 500 Internal Server Error represents unhandled server-side failures and degrades system reliability. |
| **React Effect Cleanup** | Asynchronous operations inside `useEffect` must account for component re-renders or parameter changes before the promise resolves. An `ignore` boolean prevents setting state on superseded requests. |
| **Vite Dev Proxy** | In development, Vite runs on port 5173 and forwards `/api/*` requests to port 8080 to prevent CORS issues without requiring cross-origin headers in production. |
| **H2 In-Memory DB** | Uses JDBC URL `jdbc:h2:mem:taskdb`. State is wiped on application shutdown and reinitialized via `schema.sql` and `data.sql`. |

---

## 4. Ten Realistic Interview Questions & Answers

1. **Q: Why were archived tasks leaking into the search results?**
   *A:* In `TaskRepository.java`, `AND` has higher precedence than `OR`. The clause `WHERE archived = FALSE AND LOWER(title) LIKE :term OR LOWER(description) LIKE :term AND ...` was treated as two disjunctive branches. If a task was archived but matched in the description, the second branch evaluated to true, leaking the record. Grouping the title and description checks in parentheses fixed this.

2. **Q: Why did searching for a title ignore the status filter?**
   *A:* Due to the same operator precedence issue, matching the title satisfied the first half of the `OR` expression. Because `(archived = FALSE AND LOWER(title) LIKE :term)` was already true, the second branch containing the status filter was never evaluated for title matches.

3. **Q: Why was the search API so slow when the query was empty?**
   *A:* The backend controller contained an artificial throttling calculation: `Math.max(0, 10 - query.length()) * 100L`, followed by `Thread.sleep()`. An empty query slept for a full 1,000 ms. Removing the sleep reduced latency to under 15 ms.

4. **Q: How did you fix the 500 Internal Server Error on invalid parameters?**
   *A:* For invalid status strings, `TaskStatus.valueOf()` threw an unhandled `IllegalArgumentException`. For `page <= 0`, `(page - 1) * pageSize` produced a negative index causing `IndexOutOfBoundsException` in `subList`. I added checks that return HTTP 400 Bad Request with explanatory JSON messages.

5. **Q: Why did you keep in-memory `subList` pagination instead of SQL `LIMIT`/`OFFSET`?**
   *A:* Under the 90-minute timebox, keeping `subList` preserved the existing `TaskRepository` interface contract without risking breaking changes across the data layer. I flagged in-memory pagination in `NOTES.md` as the biggest remaining architectural risk to address in future work.

6. **Q: Why was an `id DESC` added to the `ORDER BY` clause?**
   *A:* If multiple tasks share the same `created_at` timestamp, the database does not guarantee consistent row ordering across separate queries. Paginating without a tie-breaker can cause duplicate or skipped records across page boundaries. Adding `id DESC` ensures deterministic sorting.

7. **Q: What was the bug in the frontend `useTasks` hook when an API call failed?**
   *A:* In the promise `.catch()` block, `loading` was never set back to `false`. Because `TaskTable` checked `if (loading)` before `if (error)`, the UI stayed stuck on "Loading tasks..." and the error message was never shown.

8. **Q: How does your change in `useTasks` handle network race conditions?**
   *A:* A local `ignore` boolean is declared inside `useEffect` and set to `true` in the cleanup function. When the user types quickly and multiple requests are in flight, responses from older requests are ignored once a new effect run begins.

9. **Q: What happened when a user changed search filters while on page 2?**
   *A:* If the new filter matched fewer items than the page offset, the API returned zero items. Because `totalPages <= 1`, the pagination controls disappeared and the user was trapped seeing "No tasks found". Resetting `page` to 1 on filter changes resolved this.

10. **Q: What regression tests did you write to verify your changes?**
    *A:* I added `spring-boot-starter-test` and 8 JUnit 5 / MockMvc tests in `TaskControllerTest.java` covering default loading, archived task exclusion, status filtering on title matches, filter combinations, pagination non-overlap, empty search results, and 400 responses for invalid status and page bounds. All 8 tests pass.

---

## 5. Final Revision Checklist

- [x] Backend compiles and passes all 8 regression tests: `./mvnw test`
- [x] Backend runs and serves API at port 8080: `./mvnw spring-boot:run`
- [x] Frontend builds cleanly: `npm run build`
- [x] Frontend dev server runs at port 5173: `npm run dev`
- [x] `NOTES.md` exists at project root and is under 300 words (245 words).
- [x] `handwritten/` folder contains preparation markdown guides for each bug fixed.
- [x] Oracle PL/SQL reference package updated with matching SQL logic.
- [x] Git diff is clean and contains no build artifacts, node_modules, or target directories.
