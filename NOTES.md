# Notes

## Summary of Changes
- **Root Cause & Query Correctness (`TaskRepository`, SQL, Oracle)**: Operator precedence caused `AND` to bind tighter than `OR`, evaluating `(archived=0 AND title) OR (desc AND status)`. This leaked archived tasks when descriptions matched and bypassed status filtering on title matches. I added parentheses to group the title/description `OR` predicates and appended `id DESC` for deterministic pagination.
- **Backend Validation & Throttling (`TaskController`)**: Removed artificial `Thread.sleep` that stalled short queries for 1s. Validated `page < 1`, `pageSize < 1`, and `TaskStatus.valueOf`, returning HTTP 400 instead of crashing with 500 errors.
- **Frontend State Integrity (`useTasks`, `App`)**: Handled loading state in `catch`, cleared errors on new fetches, added an `ignore` flag to discard stale out-of-order responses, and reset pagination to page 1 on filter changes.

## What I Deliberately Left Unchanged and Why
- **Trade-offs**: I kept in-memory `subList` pagination rather than rewriting the data layer to Spring Data `Pageable`. A focused, high-precision patch avoids breaking API contracts or introducing unnecessary ORM churn within the timebox.
- Maintained existing Vanilla CSS, component structure, and H2 database configuration.

## Biggest Remaining Risk
In-memory pagination loads all matching entities into JVM memory before slicing. Under high row volume, this will induce heap pressure and database I/O bottlenecks. Future work should implement SQL `LIMIT`/`OFFSET` via Spring Data `Pageable`.

## Tools and AI Used
I used AI as a conversational rubber-duck to brainstorm edge cases and quickly draft boilerplate test assertions. I personally analyzed the AST operator precedence bug, deduced the negative `subList` index failure, authored the fixes, and verified everything locally via 8 passing JUnit tests, curl benchmarks, and Vite builds.
