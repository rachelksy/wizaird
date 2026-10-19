# Insight to Chat Feature Implementation

## Overview
Implemented functionality to allow users to start a new chat from an insight. When the user taps the chat button on an insight, it opens a new screen (`InsightChatScreen`) with the insight displayed as a normal AI message bubble. The chat is only created in the database once the user sends their first message.

## Changes Made

### 1. StoredInsightRepository.kt
**Added:**
- `storedInsightFlow()` - Flow function to fetch a single insight by ID

### 2. ProjectScreen.kt
**Modified:**
- Added `onInsightChatClick` parameter to `ProjectScreen` composable
- Added `onChatClick` parameter to `InsightListItem` composable
- Added `onChatClick` parameter to `InsightPreviewOverlay` composable
- Updated chat button in `InsightPreviewOverlay` to call `onChatClick()` instead of showing "CHAT COMING SOON" toast
- Wired up the callback chain: InsightListItem → InsightPreviewOverlay → ProjectScreen → MainActivity

### 3. MainActivity.kt
**Modified:**
- Added `onInsightChatClick` callback to both `project/{id}` and `project/{id}/notes` routes
- Added new route: `chat/{projectId}/insight/{insightId}` that uses `InsightChatScreen`
- Navigation properly handles back stack management for both tabs

### 4. InsightChatScreen.kt (NEW FILE)
**Created:**
- New composable screen specifically for chat-from-insight flow
- Displays insight as a normal AI message bubble (not the welcome bubble)
- Shows only the insight message initially
- Creates chat only when user sends their first message
- Chat includes both insight (AI message) and user's message
- Navigates to `ExistingChatScreen` after chat creation

## User Flow

1. User taps on an insight in the Insights tab
2. Preview overlay opens showing the full insight
3. User taps the chat icon (message icon) at the bottom
4. Preview closes and navigates to `InsightChatScreen`
5. `InsightChatScreen` displays the insight in a normal AI message bubble (with markdown formatting)
6. User types their question/message
7. When user sends the message:
   - Chat is created with insight as first AI message
   - User's message is added as second message
   - Navigation moves to `ExistingChatScreen` to continue the conversation

## Technical Details

### Data Flow
```
Insight ID → storedInsightFlow() → StoredInsight → InsightChatScreen
                                                   ↓
                                    Display as normal AI message bubble
                                                   ↓
                                    User sends message
                                                   ↓
                        Create ChatData with [AI message, User message]
                                                   ↓
                                    Navigate to ExistingChatScreen
```

### Navigation Routes
- `chat/{projectId}/insight/{insightId}` - New chat with insight pre-populated (uses `InsightChatScreen`)
- Existing routes remain unchanged

### Database Schema
No schema changes required. The existing `ChatData.insightId` field is used to link chats to their originating insights.

## Files Modified
- `app/src/main/java/com/wizaird/app/data/StoredInsightRepository.kt` - Added `storedInsightFlow()`
- `app/src/main/java/com/wizaird/app/ui/ProjectScreen.kt` - Added callbacks for insight chat
- `app/src/main/java/com/wizaird/app/MainActivity.kt` - Added navigation route

## Files Created
- `app/src/main/java/com/wizaird/app/ui/InsightChatScreen.kt` - New screen for insight-to-chat flow

## Testing Checklist
- [ ] Tap chat button on insight opens InsightChatScreen with insight as AI bubble
- [ ] Insight text displays correctly in normal AI bubble with markdown formatting
- [ ] Sending a message creates chat with both insight and user message
- [ ] Navigation to ExistingChatScreen works correctly
- [ ] Back button returns to project screen
- [ ] Chat appears in Chats tab after creation
- [ ] Chat title is generated correctly
- [ ] Works from both Chats tab and Insights tab views
