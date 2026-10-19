# Queued Insight Implementation

## Overview
Implemented a queued insight system to improve user experience by reducing wait times when generating insights. Instead of showing a loading GIF every time, the app now pre-generates insights and shows them instantly.

## How It Works

### First Generation (No Queue)
1. User taps "Generate Insight" for the first time
2. Shows loading GIF (coffee.gif) and "THINKING..." text
3. AI generates **2 insights**:
   - First insight: Displayed to user immediately
   - Second insight: Stored as "queued insight" for next time
4. Both insights are saved

### Subsequent Generations (With Queue)
1. User taps "Generate Insight" again
2. **No loading GIF** - instantly shows the queued insight with typewriter effect
3. In the background, AI generates a new queued insight for next time
4. User sees instant results while next insight is prepared

### Auto-Generation
- When user opens a project after 30 minutes, auto-generation still works
- If no queued insight exists, shows loading GIF as before
- If queued insight exists, shows it instantly

## Technical Changes

### 1. Project Data Model (`ProjectRepository.kt`)
Added new field to `Project` data class:
```kotlin
val queuedInsight: String = ""  // Pre-generated insight ready to show instantly
```

Updated deserializer to handle the new field for backward compatibility.

### 2. Insight Generation (`InsightRepository.kt`)

#### Modified `generateInsight()` function:
- Added `generateQueued: Boolean = true` parameter
- When true, generates 2 insights sequentially
- First insight becomes current, second becomes queued
- Handles errors gracefully - if queued generation fails, continues without it

#### New `getNextInsight()` function:
- Checks if project has a queued insight
- **If queued insight exists:**
  - Uses it immediately (no loading)
  - Promotes it to current insight
  - Generates new queued insight in background (non-blocking coroutine)
- **If no queued insight:**
  - Falls back to normal generation with loading
  - Generates 2 insights (current + queued)

### 3. UI Updates (`HomeScreen.kt`)

#### Updated `generateInsightForProject()`:
- Checks if project has queued insight
- Only shows loading state if no queued insight available
- Calls `getNextInsight()` instead of `generateInsight()`
- Provides instant feedback when queued insight exists

## Benefits

1. **Faster UX**: Second and subsequent generations appear instant
2. **Reduced Wait Time**: Users only wait once per session
3. **Seamless Experience**: Loading only shown when necessary
4. **Background Processing**: Next insight prepared while user reads current one
5. **Backward Compatible**: Works with existing projects (empty queue = normal flow)

## Edge Cases Handled

1. **First time generation**: No queue exists → shows loading as before
2. **Network failure on queue**: Main insight succeeds, queue generation fails → continues without queue
3. **Invalid queued insight**: Validates queued insight before using it
4. **Concurrent generations**: Cancels previous generation job before starting new one
5. **Project switching**: Each project maintains its own queued insight

## User Experience Flow

```
First Tap:
User taps → Loading GIF → AI generates 2 insights → Shows first → Queues second

Second Tap:
User taps → Instant display of queued insight → Background: generates new queue

Third Tap:
User taps → Instant display of queued insight → Background: generates new queue

(Pattern continues...)
```

## Performance Considerations

- Background generation uses separate coroutine scope (non-blocking)
- Queue generation failures don't affect user experience
- Only one queued insight stored per project (minimal storage overhead)
- Queued insight cleared when promoted to current (prevents stale data)

## Testing Recommendations

1. Test first generation (no queue) - should show loading
2. Test second generation (with queue) - should be instant
3. Test network failure during queue generation
4. Test switching between projects
5. Test auto-generation after 30 minutes
6. Test with pinned insights (should not auto-generate)
