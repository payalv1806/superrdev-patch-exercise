# Handwritten Note Template: Bug 3 - Missing Parameter Validation (500 Crashes)

## 1. Location
- **File**: `backend/src/main/java/com/internal/tasktracker/TaskController.java` (lines 22-33, 50-54)
- **Layer**: Backend Controller / Input Validation

## 2. Discovery
- Tested invalid parameter edge cases using `curl`:
  - `curl "http://localhost:8080/api/tasks?status=INVALID"` returned HTTP 500 (`IllegalArgumentException`).
  - `curl "http://localhost:8080/api/tasks?page=0"` returned HTTP 500 (`IndexOutOfBoundsException: fromIndex = -10`).

## 3. Root Cause
- `TaskStatus.valueOf(status.toUpperCase())` threw unchecked `IllegalArgumentException` on unrecognized status strings, unhandled by Spring MVC.
- `int start = (page - 1) * pageSize;` produced a negative index when `page <= 0`, causing `allResults.subList(start, end)` to crash with `IndexOutOfBoundsException`.

## 4. Fix
- Validated `page < 1` and `pageSize < 1`, returning `ResponseEntity.badRequest()` (HTTP 400) with a descriptive error message.
- Wrapped `TaskStatus.valueOf` in a try/catch block, returning `ResponseEntity.badRequest()` (HTTP 400) specifying valid values (`OPEN`, `IN_PROGRESS`, `DONE`).

## 5. Verification
- **Automated Tests**:
  - `testInvalidStatusReturnsBadRequest()` in `TaskControllerTest.java` (asserts HTTP 400 and error message).
  - `testInvalidPaginationReturnsBadRequest()` in `TaskControllerTest.java` (asserts HTTP 400 for `page=0` and `pageSize=0`).
- **Manual Verification**: `curl "http://localhost:8080/api/tasks?status=INVALID"` now cleanly returns `400 Bad Request`.
