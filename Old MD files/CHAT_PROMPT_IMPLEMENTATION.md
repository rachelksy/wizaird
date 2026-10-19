# Chat Prompt Layer Implementation

## Overview
Implemented a unified chat system that supports two entry points:
1. **HomeScreen**: Start chat from insight discussion
2. **ChatScreen**: Start fresh chat about the project

Both flows create a chat immediately and navigate to `ExistingChatScreen`, where AI responses are generated automatically.

## Changes Made

### 1. Data Model Updates

**ChatRepository.kt**
- Added `insightId: String?` field to `ChatData`
- Links chats to the insight that started them (if applicable)
- Supports future feature: open insight panel → start new chat from that insight

### 2. New Prompt Builder

**ChatPromptBuilder.kt** (new file)
- `buildChatSystemPrompt(project: Project)`: Creates system prompt with project context
- Includes: project name, instructions, background, learning progress
- No special insight handling - insight is just the first AI message in history
- Focuses on answering user questions clearly and substantively

### 3. HomeScreen Changes

**HomeScreen.kt**
- Added `onChatCreated: (projectId: String, chatId: String) -> Unit` callback
- Updated placeholder text:
  - With insight: "Ask the wizaird about the insight above"
  - Without insight: "Ask the wizaird..."
- Submit handler creates chat with:
  - Message 1 (AI): Current insight text (if exists)
  - Message 2 (USER): User's question
  - Links to `insightId` if insight exists
- Navigates immediately to `ExistingChatScreen`
- AI response generated in ExistingChatScreen (not HomeScreen)

**PixelInputBar**
- Added `placeholder: String` parameter (default: "ASK THE WIZAIRD...")
- Allows dynamic placeholder text based on context

### 4. ChatScreen Changes

**ChatScreen.kt**
- Keeps welcome screen behavior: "What can I teach you about {project} today?"
- Creates chat with just user message (no insight)
- Sets `insightId = null`
- Navigates to `ExistingChatScreen` for AI response

### 5. ExistingChatScreen Changes

**ExistingChatScreen.kt**
- Auto-generates AI responses via `LaunchedEffect`
- Triggers when last message is from USER
- Uses `buildChatSystemPrompt(project)` for context
- Sends last 10 messages as conversation history
- **Error handling**:
  - Shows "Thinking..." while generating
  - Shows error message with RETRY button if fails
  - Retry triggers new generation attempt
- Removed old placeholder AI response logic

### 6. Navigation Updates

**MainActivity.kt**
- Added `onChatCreated` callback to HomeScreen route
- Navigates to `chat/{projectId}/{chatId}` when chat created from home

## Flow Diagrams

### HomeScreen → Chat Flow
```
User types in HomeScreen input
    ↓
Check if insight exists
    ↓
Create ChatData:
  - If insight exists: [AI: insight, USER: question]
  - If no insight: [USER: question]
    ↓
Save chat to repository
    ↓
Navigate to ExistingChatScreen
    ↓
LaunchedEffect detects last message is USER
    ↓
Generate AI response with project context
    ↓
Add AI message to chat
```

### ChatScreen → Chat Flow
```
User clicks "New Chat" in ProjectScreen
    ↓
Navigate to ChatScreen (welcome screen)
    ↓
User types question
    ↓
Create ChatData: [USER: question]
    ↓
Save chat to repository
    ↓
Navigate to ExistingChatScreen
    ↓
LaunchedEffect detects last message is USER
    ↓
Generate AI response with project context
    ↓
Add AI message to chat
```

## Key Design Decisions

1. **Insight as first message**: Instead of special prompt handling, insight is just the first AI message in the conversation. This keeps the system simple and allows the AI to reference it naturally through message history.

2. **Immediate navigation**: Both entry points create the chat and navigate immediately. AI response happens in ExistingChatScreen, not in the entry screen. This ensures:
   - Consistent error handling
   - User sees chat being created
   - Failures don't block navigation

3. **Unified prompt builder**: Single `buildChatSystemPrompt()` function for all chats. No special cases for insight vs non-insight chats. The insight context comes from message history, not the prompt.

4. **Auto-response generation**: ExistingChatScreen automatically generates AI responses when it detects the last message is from the user. This works for:
   - New chats from HomeScreen
   - New chats from ChatScreen
   - Continuing existing chats

5. **Error recovery**: Retry button allows users to recover from API failures without leaving the chat or losing context.

## Future Enhancements

1. **Insight panel → chat**: The `insightId` field supports opening an insight from a panel and starting a new chat about it.

2. **Chat title generation**: Currently uses placeholder "Generated Title". Could auto-generate from first user message.

3. **Conversation pruning**: Currently sends last 10 messages. Could implement smarter context window management.

4. **Streaming responses**: Could implement streaming AI responses for better UX.

5. **Message editing**: Allow users to edit their messages and regenerate AI responses.

## Testing Checklist

- [ ] Create chat from HomeScreen with insight
- [ ] Create chat from HomeScreen without insight
- [ ] Create chat from ChatScreen (new chat button)
- [ ] Continue existing chat with new messages
- [ ] Verify AI responses include project context
- [ ] Test error handling and retry functionality
- [ ] Verify insight is properly linked to chat
- [ ] Test placeholder text changes based on insight presence
- [ ] Verify navigation flows correctly
- [ ] Test with different AI providers (OpenAI, Gemini, Claude)
