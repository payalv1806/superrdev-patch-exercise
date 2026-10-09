# Handwritten Note Template: Bug 5 - Search/Filter Without Pagination Reset

## 1. Location
- **File**: `frontend/src/App.jsx` (lines 8-10, 24-25)
- **Layer**: Frontend State Coordination

## 2. Discovery
- Seeded task in `data.sql` stated: "Fix status filter edge case: Selecting a status filter then clearing it does not reset the results".
- Code inspection revealed that navigating to Page 3 and then filtering down to a result set with < 10 total items left `page = 3`.
- The API received `page=3`, returned `items: []`, causing the table to say "No tasks found" and pagination controls to vanish because `totalPages <= 1`, trapping the user.

## 3. Root Cause
- `SearchBar` and `StatusFilter` directly called `setQuery` and `setStatus` without updating `page`.
- The component state `page` persisted across filter queries even when the total number of pages shrank.

## 4. Fix
- Created dedicated change handlers `handleQueryChange` and `handleStatusChange`:
  - `handleQueryChange`: sets new query and calls `setPage(1)`.
  - `handleStatusChange`: sets new status and calls `setPage(1)`.
- Bound `SearchBar onChange={handleQueryChange}` and `StatusFilter onChange={handleStatusChange}`.

## 5. Verification
- Frontend builds cleanly via `npm run build`.
- Manual verification via state flow: navigating to page 2 and changing search or status immediately re-queries from page 1, ensuring matching records are visible.
