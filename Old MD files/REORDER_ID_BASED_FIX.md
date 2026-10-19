# Critical Fix: ID-Based Drag Tracking

## Problem

**Wrong card being dragged after reorder**: After reordering projects, attempting to drag a card would drag the wrong one. This happened because:

1. User drags card at index 3 to index 2
2. Reorder completes, list updates
3. Card that was at index 2 is now at index 3
4. User tries to drag the card now at index 3
5. **BUG**: The old index 3 (now at index 2) gets dragged instead!

### Root Cause

The code was using **index-based tracking** (`draggedItemIndex`):
- Callbacks captured the index when the card was rendered
- After reorder, the list recomposed with new order
- But the captured index still pointed to the old position
- Result: Wrong card was dragged

## Solution

Changed from **index-based** to **ID-based** tracking:

### Before (Index-Based):
```kotlin
var draggedItemIndex by remember { mutableStateOf<Int?>(null) }

// Captured index in closure
val currentIndex = index
onDragStart = { draggedItemIndex = currentIndex }
```

### After (ID-Based):
```kotlin
var draggedProjectId by remember { mutableStateOf<String?>(null) }

// Use project ID directly
onDragStart = { draggedProjectId = project.id }

// Look up current index when needed
val draggedItemIndex = draggedProjectId?.let { id ->
    projects.indexOfFirst { it.id == id }
}?.takeIf { it >= 0 }
```

## Key Changes

### 1. State Variable
- Changed from `draggedItemIndex: Int?` to `draggedProjectId: String?`
- Project ID is stable across reorders, index is not

### 2. Drag Detection
```kotlin
isDragged = draggedProjectId == project.id  // Not index-based
```

### 3. Index Lookup
```kotlin
// Calculate index from ID when needed
val draggedItemIndex = draggedProjectId?.let { id ->
    projects.indexOfFirst { it.id == id }
}?.takeIf { it >= 0 }
```

### 4. Drag Callbacks
```kotlin
onDragStart = {
    draggedProjectId = project.id  // Store ID, not index
    dragOffsetPx = 0f
}

onDrag = { delta ->
    if (draggedProjectId == project.id) {  // Compare by ID
        dragOffsetPx += delta
    }
}
```

## Benefits

1. **Correct card tracking**: Always drags the right card, even after reorders
2. **Stable identity**: Project ID doesn't change when list reorders
3. **No stale closures**: ID is looked up fresh on each render
4. **Robust**: Works correctly with any number of reorders

## Testing

Test these scenarios:
- [x] Drag card from position 0 to 3
- [x] Immediately drag the card now at position 0 (was at 1)
- [x] Drag card from position 3 to 0
- [x] Immediately drag the card now at position 3 (was at 2)
- [x] Multiple consecutive reorders
- [x] Verify correct card is always dragged

## Visual Jump Issue

The visual jump on drop is a separate issue that still needs addressing. It happens because:
1. User releases drag with large offset (e.g., 694px)
2. Reorder completes, list updates
3. Drag state resets to 0
4. Card "jumps" from offset position to final position

This will be addressed separately with proper animation coordination.
