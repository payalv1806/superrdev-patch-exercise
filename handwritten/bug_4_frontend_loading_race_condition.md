# Handwritten Note Template: Bug 4 - Unhandled Frontend Loading State & Race Conditions

## 1. Location
- **File**: `frontend/src/hooks/useTasks.js` (lines 10-22)
- **Layer**: Frontend State Management / React Hooks

## 2. Discovery
- Inspected `useTasks.js` and `TaskTable.jsx`:
  - In `useTasks.js`, `fetchTasks(...).catch((err) => setError(err.message))` did not set `loading` to `false`.
  - In `TaskTable.jsx`, `if (loading) return <div>Loading tasks...</div>;` preceded `if (error)`.
  - If a fetch failed, the UI remained permanently stuck in "Loading tasks...", never displaying the error message.
  - Rapid keystrokes generated unhandled out-of-order promise resolutions.

## 3. Root Cause
- Missing `setLoading(false)` inside the `.catch()` branch.
- No `setError(null)` reset at the start of new requests, causing stale error state to linger.
- Absence of request cleanup/cancellation allowed stale async responses from earlier keystrokes to overwrite newer results.

## 4. Fix
- Introduced an `ignore` flag with cleanup function in `useEffect`:
  - Sets `setLoading(true)` and resets `setError(null)` on trigger.
  - Updates state and sets `setLoading(false)` on both success and error only if `!ignore`.
  - Stale responses from superseding requests are discarded.

## 5. Verification
- Frontend builds cleanly via `npm run build`.
- Simulating API error (e.g. invalid query) correctly transitions out of loading state and displays the error message.
- Tested rapid typing against API proxy; newest search term displays deterministically.
