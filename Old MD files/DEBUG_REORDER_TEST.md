# Debug Reorder Test

## What to Check in Logcat

When you drag and drop a card, you should see these log messages:

### During Drag (Visual Feedback):
```
ProjectsScreen: Visual - draggedIdx=X, offsetPx=Y, offsetItems=Z, targetIdx=W
```
- `draggedIdx`: Which card you're dragging (0-based index)
- `offsetPx`: How far you've dragged in pixels
- `offsetItems`: How many positions to move
- `targetIdx`: Where the card will land

### On Drop:
```
ProjectsScreen: onDragEnd - draggedItemIndex=X, projects.size=Y, dragOffsetPx=Z
ProjectsScreen: threshold=A, offsetItems=B, newIndex=C
```

### If Reordering:
```
ProjectsScreen: Reordering from X to Y
ProjectRepository: reorderProjects called - fromIndex=X, toIndex=Y
ProjectRepository: Current projects count=Z
ProjectRepository: Moving project from X to Y
ProjectRepository: Saving reordered projects
ProjectRepository: Reorder complete
ProjectsScreen: Reorder completed successfully
```

### If Not Reordering:
```
ProjectsScreen: No reorder needed, same position (newIndex=X == draggedIndex=X)
```

## Expected Values

For a list with 3 projects (indices 0, 1, 2):
- Item height + spacing ≈ 88dp ≈ 264px (on typical device)
- Threshold = 132px (half of item height)

**Example 1: Drag first item down to second position**
- draggedIdx = 0
- Drag down ~150px
- offsetPx = 150
- threshold = 132
- offsetItems = 1 (because 150 > 132)
- newIndex = 1
- Should reorder from 0 to 1

**Example 2: Drag first item down slightly (< 132px)**
- draggedIdx = 0
- Drag down ~100px
- offsetPx = 100
- threshold = 132
- offsetItems = 0 (because 100 < 132)
- newIndex = 0
- Should NOT reorder (returns to original position)

## Troubleshooting

### If you see "No reorder needed" every time:
- Check if `offsetPx` value is being captured
- Check if `threshold` calculation is correct
- Check if `offsetItems` is always 0

### If you see reorder messages but list doesn't change:
- Check `ProjectRepository:` messages
- Look for any error messages
- Check if `saveProjects` is being called

### If no log messages appear:
- Check if `onDragEnd` is being called at all
- Try adding a log at the very start of `onDragEnd`

## Quick Test

To test if the basic mechanism works, you can temporarily change the threshold to 0:

```kotlin
val threshold = 0f  // Temporarily set to 0 for testing
```

This will make ANY drag movement trigger a reorder, which helps isolate whether the issue is:
1. The threshold calculation (if it works with threshold=0)
2. The reorder mechanism itself (if it doesn't work even with threshold=0)
