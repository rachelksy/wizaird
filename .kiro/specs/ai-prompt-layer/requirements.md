# Requirements Document

## Introduction

The AI Prompt Layer is a system that constructs context-aware prompts for AI interactions in the Wizaird mobile learning app. The system assembles prompts from project-level settings (topic, goal, prior knowledge), AI-maintained memory (knowledge map, nugget summaries), and recent conversation history. It supports two interaction modes: home screen bubbles (microlearning nuggets) and project-level chats (deeper conversations).

## Glossary

- **Prompt_Builder**: The component responsible for assembling prompts from project context and memory
- **Nugget**: A 150-250 word microlearning message displayed on the home screen
- **Knowledge_Map**: An AI-maintained summary of what the user has learned, visible and editable by the user
- **Nugget_Summary**: A compressed summary generated every 10 nuggets
- **Project**: A learning goal with associated settings, memory, and chat history
- **Memory_Manager**: The component responsible for managing nugget storage, summary generation, and knowledge map updates
- **Chat**: A conversation thread within a project, initiated by the user
- **Bubble_Generator**: The component that generates home screen nuggets
- **Chat_Generator**: The component that generates chat responses within a project

## Requirements

### Requirement 1: Prompt Construction for Home Screen Bubbles

**User Story:** As a user, I want the AI to generate relevant microlearning nuggets based on my project context, so that I receive personalized learning content on the home screen.

#### Acceptance Criteria

1. WHEN a nugget is requested, THE Prompt_Builder SHALL assemble a prompt containing the project topic, project goal, user prior knowledge (if provided), current knowledge map (if exists), and the last 10 nuggets (if exist)
2. THE Prompt_Builder SHALL format the assembled prompt with clear section delimiters for topic, goal, prior knowledge, knowledge map, and recent nuggets
3. WHEN prior knowledge is empty, THE Prompt_Builder SHALL omit the prior knowledge section from the prompt
4. WHEN no knowledge map exists, THE Prompt_Builder SHALL omit the knowledge map section from the prompt
5. WHEN fewer than 10 nuggets exist, THE Prompt_Builder SHALL include all available nuggets in the prompt
6. THE Prompt_Builder SHALL include instructions specifying the nugget format: 150-250 words, conversational tone, ending with a thought or question

### Requirement 2: Prompt Construction for Project Chats

**User Story:** As a user, I want the AI to maintain context during conversations within a project, so that I can have coherent learning discussions.

#### Acceptance Criteria

1. WHEN a chat message is sent, THE Prompt_Builder SHALL assemble a prompt containing the project topic, project goal, user prior knowledge (if provided), current knowledge map (if exists), and the last 10 nuggets (if exist)
2. THE Prompt_Builder SHALL include the full chat history for the current conversation in the prompt
3. THE Prompt_Builder SHALL format chat history with clear role indicators for user and assistant messages
4. THE Prompt_Builder SHALL include instructions specifying the chat response format: conversational, educational, and contextually relevant to the project topic

### Requirement 3: Memory Storage and Retrieval

**User Story:** As a user, I want the system to remember my recent learning interactions, so that the AI can build upon previous conversations.

#### Acceptance Criteria

1. WHEN a nugget is generated, THE Memory_Manager SHALL store the complete nugget text with a timestamp
2. THE Memory_Manager SHALL maintain storage of the last 10 nuggets for each project
3. WHEN an 11th nugget is generated, THE Memory_Manager SHALL remove the oldest nugget from storage
4. THE Memory_Manager SHALL retrieve the last 10 nuggets in chronological order when requested by the Prompt_Builder
5. THE Memory_Manager SHALL persist nugget storage across app sessions

### Requirement 4: Nugget Summary Generation

**User Story:** As a user, I want the system to compress my learning history, so that long-term context is preserved without overwhelming the AI prompt.

#### Acceptance Criteria

1. WHEN 10 nuggets have been generated since the last summary, THE Memory_Manager SHALL generate a compressed summary of those 10 nuggets
2. THE Memory_Manager SHALL use the AI provider to generate the summary
3. THE Memory_Manager SHALL store the generated summary with a timestamp
4. THE Memory_Manager SHALL maintain all generated summaries for each project
5. THE Memory_Manager SHALL persist summaries across app sessions

### Requirement 5: Knowledge Map Updates

**User Story:** As a user, I want the system to maintain an up-to-date summary of what I have learned, so that I can track my progress and the AI can avoid repeating content.

#### Acceptance Criteria

1. WHEN 5 nugget summaries have been generated since the last knowledge map update, THE Memory_Manager SHALL condense and rewrite the knowledge map
2. THE Memory_Manager SHALL use the AI provider to generate the updated knowledge map
3. THE Memory_Manager SHALL incorporate all existing summaries and the current knowledge map into the new knowledge map
4. THE Memory_Manager SHALL replace the previous knowledge map with the newly generated one
5. THE Memory_Manager SHALL persist the knowledge map across app sessions

### Requirement 6: Manual Knowledge Map Refresh

**User Story:** As a user, I want to manually refresh my knowledge map at any time, so that I can get an immediate updated summary of my learning progress.

#### Acceptance Criteria

1. WHEN the user triggers a manual refresh, THE Memory_Manager SHALL condense all nuggets and chat messages into an updated knowledge map
2. THE Memory_Manager SHALL use the AI provider to generate the refreshed knowledge map
3. THE Memory_Manager SHALL replace the previous knowledge map with the newly generated one
4. THE Memory_Manager SHALL complete the refresh operation within 30 seconds or return an error
5. WHEN the refresh operation fails, THE Memory_Manager SHALL preserve the existing knowledge map

### Requirement 7: Knowledge Map Visibility and Editing

**User Story:** As a user, I want to view and edit my knowledge map, so that I can correct inaccuracies or add my own notes.

#### Acceptance Criteria

1. THE Project_Settings_Screen SHALL display the current knowledge map as editable text
2. WHEN the user modifies the knowledge map, THE Project_Settings_Screen SHALL save the updated text
3. THE Project_Settings_Screen SHALL provide a manual refresh button that triggers knowledge map regeneration
4. WHEN no knowledge map exists, THE Project_Settings_Screen SHALL display a placeholder message indicating no learning history
5. THE Project_Settings_Screen SHALL persist user edits to the knowledge map across app sessions

### Requirement 8: Chat Title Generation

**User Story:** As a user, I want chat conversations to have descriptive titles, so that I can easily identify and navigate between different discussions.

#### Acceptance Criteria

1. WHEN a user initiates a new chat, THE Chat_Generator SHALL generate a title from the user's first message
2. THE Chat_Generator SHALL use the AI provider to generate the title
3. THE Chat_Generator SHALL limit the generated title to 50 characters or fewer
4. THE Chat_Generator SHALL store the generated title with the chat conversation
5. THE Chat_Generator SHALL persist the chat title across app sessions

### Requirement 9: Project Context Isolation

**User Story:** As a user, I want each project to maintain its own independent context, so that learning about different topics does not interfere with each other.

#### Acceptance Criteria

1. THE Memory_Manager SHALL store nuggets, summaries, and knowledge maps separately for each project
2. THE Prompt_Builder SHALL only include context from the active project when assembling prompts
3. WHEN switching between projects, THE Prompt_Builder SHALL use the context from the newly selected project
4. THE Memory_Manager SHALL prevent cross-contamination of memory between different projects
5. WHEN a project is deleted, THE Memory_Manager SHALL remove all associated nuggets, summaries, and knowledge maps

### Requirement 10: Prompt Layer Architecture Integration

**User Story:** As a developer, I want the prompt layer to integrate cleanly with the existing repository architecture, so that the codebase remains maintainable and testable.

#### Acceptance Criteria

1. THE Prompt_Builder SHALL be implemented as a separate repository class following the existing repository pattern
2. THE Prompt_Builder SHALL accept project data and memory data as input parameters
3. THE Prompt_Builder SHALL return assembled prompts as string output
4. THE Memory_Manager SHALL be implemented as a separate repository class following the existing repository pattern
5. THE Memory_Manager SHALL use DataStore for persistence following the existing pattern in ProjectRepository and AiRepository
6. THE Prompt_Builder SHALL not directly call the AI provider (that responsibility remains with AiRepository)
7. THE AiRepository SHALL be modified to accept assembled prompts from the Prompt_Builder instead of using the hardcoded SYSTEM_PROMPT
