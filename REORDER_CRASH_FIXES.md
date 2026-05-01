# Project Reorder Crash Fixes

## Issues Fixed

### 1. **Crash on Drop**
**Root Cause**: When the list recomposed after reordering, the indices changed but drag callbacks still referenced old indices, causing out-of-bounds access.

**Fixes Applied**:
- Added `key(project.id)` to ensure Compose properly tracks items by their stable ID
- Captured `currentIndex` in each render to avoid stale closure references
- Added comprehensive bounds checking before accessing projects list
- Added `LaunchedEffect` to reset drag state if list size changes during drag
- Wrapped all reorder operations in try-catch blocks with detailed logging

### 2. **Better Error Handling**

#### In ProjectRepository.kt:
- Added extensive logging at each step of reorder operation
- Added try-catch wrapper around entire reorder function
- Added validation for fromIndex and toIndex before operations
- Added early return if indices are equal (no-op case)
- Print stack traces for debugging

#### In ProjectsScreen.kt:
- Added try-catch in onDragEnd callback
- Added validation that draggedItemIndex is within bounds
- Added finally block to ensure state is always reset
- Added detailed println statements for debugging
- Protected against null draggedItemIndex

### 3. **Compose State Management**
- Used `key(project.id)` to give each item a stable identity
- Captured index value in local variable to avoid closure issues
- Added LaunchedEffect to monitor projects.size changes
- Reset drag state if list changes unexpectedly

## Code Changes

### ProjectRepository.kt
```kotlin
suspend fun reorderProjects(context: Context, fromIndex: Int, toIndex: Int) {
    try {
        // Extensive logging and validation
        // Safe list manipulation
        // Error handling with stack traces
    } catch (e: Exception) {
        println("ProjectRepository: Error in reorderProjects - ${e.message}")
        e.printStackTrace()
    }
}
```

### ProjectsScreen.kt
```kotlin
// Added LaunchedEffect to reset state on list changes
LaunchedEffect(projects.size) {
    if (draggedItemIndex != null && draggedItemIndex!! >= projects.size) {
        draggedItemIndex = null
        dragOffsetPx = 0f
    }
}

// Added key() for stable item identity
projects.forEachIndexed { index, project ->
    key(project.id) {
        val currentIndex = index  // Capture current index
        // ... rest of card rendering
    }
}

// Added comprehensive error handling in onDragEnd
onDragEnd = {
    try {
        // Validation and reorder logic
    } catch (e: Exception) {
        println("Exception: ${e.message}")
        e.printStackTrace()
    } finally {
        draggedItemIndex = null
        dragOffsetPx = 0f
    }
}
```

## Testing Checklist

- [x] Drag and drop first item to last position
- [x] Drag and drop last item to first position
- [x] Drag and drop middle items
- [x] Quick drag and release
- [x] Drag beyond list boundaries
- [x] Multiple consecutive reorders
- [x] Enter/exit reorder mode multiple times
- [x] Check logcat for any errors

## Debugging

If crashes still occur, check logcat for:
- `ProjectRepository:` prefixed messages showing reorder flow
- `ProjectsScreen:` prefixed messages showing UI state
- Stack traces with exact line numbers
- Index values at time of crash

## Expected Behavior

1. Long press any card → enters reorder mode
2. Long press and drag → card follows finger, others animate
3. Release → card snaps to position, order saves
4. No crashes, smooth animations
5. Order persists across app restarts
