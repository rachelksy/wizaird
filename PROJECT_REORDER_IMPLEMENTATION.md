# Project Reordering Implementation

## Overview
Implemented drag-and-drop reordering for projects in the ProjectsScreen. The reordering is triggered by long-pressing any project card, which enters "reorder mode" and shows drag handles on all cards.

## Changes Made

### 1. Data Model (`ProjectRepository.kt`)
- **Added `order` field** to `Project` data class (Int, default 0)
- **Updated `ProjectDeserializer`** to handle the new `order` field during JSON deserialization
- **Modified `projectsFlow()`** to sort projects by the `order` field
- **Updated `upsertProject()`** to assign proper order values to new projects
- **Added `reorderProjects()`** function to handle reordering logic:
  - Takes `fromIndex` and `toIndex` parameters
  - Moves the project from one position to another
  - Reassigns order values to all projects
  - Persists changes immediately

### 2. UI Implementation (`ProjectsScreen.kt`)

#### ProjectsScreen Composable
- **Added reorder mode state**:
  - `isReorderMode`: Boolean to track if user is in reorder mode
  - `draggedItemIndex`: Tracks which item is being dragged
  - `dragOffset`: Tracks the vertical drag distance

- **Updated header**:
  - Title changes to "REORDER PROJECTS" when in reorder mode
  - Back button exits reorder mode if active, otherwise navigates back
  - "+ NEW" button replaced with "DONE" button in reorder mode

- **Enhanced project list**:
  - Long press on any card enters reorder mode
  - Cards show drag handles (menu icon) when in reorder mode
  - Drag gestures move cards vertically
  - Visual feedback: dragged card becomes semi-transparent with shadow
  - Border highlights in coral color when dragging
  - Changes persist immediately to DataStore

#### ProjectCard Composable
- **Added reorder parameters**:
  - `isReorderMode`: Shows/hides drag handle
  - `isDragged`: Visual state for the dragged card
  - `dragOffsetY`: Vertical offset for smooth dragging
  - `onDragStart`, `onDrag`, `onDragEnd`: Drag gesture callbacks

- **Drag handle**:
  - Uses `menu.svg` icon from pixelarticons
  - Only visible in reorder mode
  - Positioned at the left of each card
  - Tinted with `textXLow` color

- **Gesture handling**:
  - Uses `detectDragGesturesAfterLongPress` for intuitive drag interaction
  - In reorder mode: entire card is draggable
  - In normal mode: card responds to click and long press

### 3. Visual Feedback
- **Dragged card**: 70% opacity, elevated shadow, coral border
- **Smooth animations**: Uses `animateFloatAsState` for smooth transitions
- **Real-time updates**: UI updates immediately as projects are reordered

## User Experience

1. **Enter Reorder Mode**: Long press any project card
2. **Reorder Projects**: Long press and drag any card up or down
3. **Visual Feedback**: 
   - Dragged card becomes semi-transparent with coral border and shadow
   - Card follows your finger precisely
   - Other cards smoothly animate to their new positions in real-time
4. **Exit Reorder Mode**: Tap "DONE" button or back button
5. **Persistence**: Order is saved immediately and persists across app restarts

## Drag Behavior

- **Follows finger**: The dragged card translates exactly with your finger movement
- **Real-time feedback**: Other cards move to their target positions as you drag
- **Smooth animations**: All non-dragged cards animate smoothly using `animateFloatAsState`
- **Visual hierarchy**: Dragged card has elevated shadow and reduced opacity
- **Snap to position**: When released, cards snap to their final positions

## Technical Details

- **Sorting**: Projects are sorted by `order` field in ascending order
- **Migration**: Existing projects without `order` field default to 0
- **New projects**: Automatically assigned the next available order value
- **Reordering algorithm**: Moves item from source to destination, then reassigns all order values sequentially
- **Data persistence**: Uses DataStore with Gson serialization
- **Synchronization**: Both HomeScreen and ProjectsScreen read from the same `projectsFlow()`, so reordering in one place updates both

## Icons Used
- `pixelarticons/menu.svg` - Drag handle icon (three horizontal lines)

## Files Modified
1. `app/src/main/java/com/wizaird/app/data/ProjectRepository.kt`
2. `app/src/main/java/com/wizaird/app/ui/ProjectsScreen.kt`

## Testing Recommendations
1. Test with 0, 1, 2, and many projects
2. Test dragging first item to last position
3. Test dragging last item to first position
4. Test dragging middle items
5. Verify order persists after app restart
6. Verify HomeScreen circles reflect the new order
7. Test entering/exiting reorder mode multiple times
