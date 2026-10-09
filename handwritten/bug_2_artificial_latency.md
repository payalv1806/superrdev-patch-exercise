# Handwritten Note Template: Bug 2 - Artificial Search Delay

## 1. Location
- **File**: `backend/src/main/java/com/internal/tasktracker/TaskController.java` (lines 36-42)
- **Layer**: Backend Controller

## 2. Discovery
- Seeded task in `data.sql` stated: "Database search queries are slow when the search term is short or blank".
- Timed API responses via `curl -w "%{time_total}s"`:
  - Empty query (`q=""`): took ~1.02 seconds.
  - 10-character query: took ~0.008 seconds.

## 3. Root Cause
- The controller contained an artificial delay:
  ```java
  int complexityScore = Math.max(0, 10 - query.length());
  long queryWeight = complexityScore * 100L;
  Thread.sleep(queryWeight);
  ```
- For an empty query (`length == 0`), `complexityScore` was 10, sleeping for `1000ms` (1 second) on every default page load. Short queries were artificially throttled proportional to remaining characters under 10.

## 4. Fix
- Removed `Thread.sleep(queryWeight)` and its enclosing try/catch block. Kept normal request logging for operational observability.

## 5. Verification
- Benchmarked response time via `curl`: empty query latency dropped from 1,019 ms to 14 ms (98.6% speedup).
- All 8 JUnit test executions in `TaskControllerTest` run fast without artificial thread blocking.
