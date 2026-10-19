# Insights Delete Implementation

## Summary
Implemented proper deletion of insights that removes them from both the UI storage (`StoredInsight`) and the AI prompt context (`Project.insightHistory`).

## Changes Made

### 1. Data Model Changes (`ProjectRepository.kt`)

**Added new data class:**
```kotlin
data class InsightHistoryEntry(
    val id: String,
    val text: String
)
```

**Updated Project model:**
- Changed `insightHistory` from `List<String>` to `List<InsightHistoryEntry>`
- Added `insightHistoryLegacy: List<String>?` for migration support
- Updated comment to reflect "Last 20 insights" (was "Last 10")

### 2. Migration Logic (`ProjectRepository.kt`)

**Updated `projectsFlow()`:**
- Automatically migrates old string-based history to new ID-based format
- Generates UUIDs for legacy insights during migration
- Clears legacy field after migration

### 3. Insight Generation (`InsightRepository.kt`)

**Updated `generateInsight()`:**
- Generates a UUID for each new insight
- Creates `InsightHistoryEntry` with ID and text
- Passes the ID to `saveInsight()` for consistent tracking

**Updated `buildInsightPrompt()`:**
- Adapted to use `entry.text` from `InsightHistoryEntry` objects

### 4. Storage Operations (`StoredInsightRepository.kt`)

**Updated `saveInsight()`:**
- Added `id` parameter (with default UUID generation)
- Uses provided ID when called from `generateInsight()`

**Updated `deleteInsight()`:**
- Now removes insight from both storage locations:
  1. Removes from `StoredInsight` storage (UI display)
  2. Finds the associated project
  3. Removes matching entry from `Project.insightHistory` (AI context)
- Uses ID matching for reliable deletion

## How It Works

### Insight Creation Flow:
1. User generates insight (auto or manual)
2. System generates UUID for the insight
3. Insight saved to `StoredInsight` with ID
4. Insight added to `Project.insightHistory` as `InsightHistoryEntry(id, text)`
5. Both use the same ID for tracking

### Insight Deletion Flow:
1. User deletes insight from insights panel
2. System finds the `StoredInsight` by ID
3. Removes from `StoredInsight` storage
4. Finds the project that owns the insight
5. Filters out the matching entry from `Project.insightHistory` by ID
6. Saves updated project
7. Insight is now removed from both UI and AI context

## Benefits

✅ **Reliable deletion** - ID-based matching ensures correct insight is removed
✅ **AI context accuracy** - Deleted insights won't appear in future prompts
✅ **Backward compatible** - Automatic migration for existing data
✅ **Consistent tracking** - Same ID used across both storage systems

## Next Steps (Not Yet Implemented)

- [ ] Edit functionality for insights
- [ ] Update `lastInsightText` when editing/deleting the current insight
- [ ] UI for editing insights (in preview overlay or separate screen)
