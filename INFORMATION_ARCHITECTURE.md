# Wizaird — Information Architecture

## What the app is

Wizaird is a pixel-art-themed AI microlearning app. Users define "projects" — topics they want to learn (e.g. Python, Stoicism, Sketching) — and the app's wizard AI generates bite-sized insights on a 30-minute session timer. Everything hangs off the **Project** as the central organizing unit.

---

## Navigation Map

```
home  ──────────────────────────────────────────────────────────────────────┐
  ├─► settings                                                               │
  │     ├─► account_settings                                                 │
  │     ├─► app_settings                                                     │
  │     ├─► api_settings                                                     │
  │     └─► backup_settings                                                  │
  ├─► projects                                                               │
  │     └─► new_project                                                      │
  ├─► new_project                                                            │
  ├─► project/{id}          [tab: CHATS]                                     │
  │     ├─► project/{id}/notes      [tab: NOTES]                             │
  │     │     └─► note/{id}/new | note/{id}/{noteId}                         │
  │     ├─► project/{id}/concepts   [tab: CONCEPTS]                          │
  │     │     ├─► concept/{id}/new                                           │
  │     │     └─► concept/{id}/{conceptId}          (concept page)           │
  │     │           └─► concept/{id}/{conceptId}/edit                        │
  │     ├─► project_settings/{id}                                            │
  │     ├─► chat/{projectId}             (new chat)                          │
  │     │     └─► chat/{projectId}/{chatId}  (existing chat)                 │
  │     │           └─► chat_settings/{projectId}/{chatId}                   │
  │     └─► chat/{projectId}/insight/{insightId}   (insight-seeded chat)    │
  │           └─► chat/{projectId}/{chatId}                                  │
  └─► chat/{projectId}/{chatId}  (created from home input bar)              ─┘
```

> Tab order: Concepts, Chats, Insights, Notes (opening a project lands on Concepts). Tab state in `ProjectScreen` is encoded as three routes (`project/{id}` for Chats and Insights, `project/{id}/concepts`, `project/{id}/notes`) so Back navigation returns to the correct tab. Opening something from another tab first swaps the route to that tab's route.

---

## Screen Inventory

| Screen | Route | Purpose |
|--------|-------|---------|
| **HomeScreen** | `home` | Root. Wizard character, active-project insight bubble, project switcher, quick-ask input bar |
| **ProjectsScreen** | `projects` | Full list of all projects. Drag-to-reorder. |
| **NewProjectScreen** | `new_project` | Form to create a project (name + picture) |
| **ProjectScreen** | `project/{id}` | Per-project hub with 4 tabs: Chats, Concepts, Insights, Notes |
| **ProjectSettingsScreen** | `project_settings/{id}` | Edit project name, picture, AI instructions, background, learning progress |
| **ChatScreen** | `chat/{projectId}` | New-chat entry point — user sends first message, chat is created |
| **ExistingChatScreen** | `chat/{projectId}/{chatId}` | Full conversation UI with markdown rendering |
| **ChatSettingsScreen** | `chat_settings/{projectId}/{chatId}` | Chat title, context-window size, delete |
| **InsightChatScreen** | `chat/{projectId}/insight/{insightId}` | Conversation pre-seeded with a stored insight |
| **NoteScreen** | `note/{projectId}/{noteId}` | WYSIWYG rich-text note editor |
| **ConceptPageScreen** | `concept/{projectId}/{conceptId}` | Read-only concept info page: name, aliases, summary, article, connected chats/insights/notes, related concepts. Ghosts get Save concept / Generate with AI / Edit manually |
| **ConceptEditScreen** | `concept/{projectId}/new`, `concept/{projectId}/{conceptId}/edit` | Create / edit a concept (name, summary, details, aliases) with Generate/Regenerate with AI |
| **SettingsScreen** | `settings` | Hub for the four settings areas |
| **AccountSettingsScreen** | `account_settings` | Placeholder (no active functionality) |
| **AppSettingsScreen** | `app_settings` | Dark mode toggle |
| **ApiSettingsScreen** | `api_settings` | AI provider, API key, model, temperature |
| **BackupScreen** | `backup_settings` | Export / import full ZIP backup |

---

## Data Model

```
AiSettings          (global, one record)
  ├─ provider       openai | gemini | claude | custom
  ├─ apiKey
  ├─ model
  ├─ baseUrl        (custom only)
  ├─ temperature
  ├─ darkMode
  └─ detectedFormat (custom: openai | gemini | claude)

Project             (many)
  ├─ id
  ├─ name
  ├─ picturePath    (internal storage JPG)
  ├─ order          (display order)
  │
  ├─ [AI context]
  │   ├─ instructions          custom AI system prompt
  │   ├─ background            what the user already knows
  │   ├─ prioritizeInstructions
  │   └─ learningProgress      rolling AI-generated summary
  │         └─ learningProgressHistory  [2 undo snapshots]
  │
  ├─ [Insight state]
  │   ├─ lastInsightText
  │   ├─ lastInsightTimestamp  (30-min regeneration timer)
  │   ├─ pinnedInsight         (pauses auto-generation)
  │   ├─ insightHistory        List<InsightHistoryEntry> (last 20, IDs + text)
  │   ├─ progressInsightCount  (default 20)
  │   └─ progressChatCount     (default 10)
  │
  ├─► StoredInsight  (many per project)
  │     ├─ id
  │     ├─ text
  │     ├─ thinkingContent  (reasoning chain, thinking models only)
  │     └─ createdAt
  │
  ├─► ChatData       (many per project)
  │     ├─ id
  │     ├─ title              (auto-generated after first AI response)
  │     ├─ insightId?         (FK → StoredInsight, if insight-seeded)
  │     ├─ contextWindowSize  (default 50)
  │     ├─ createdAt
  │     └─► ChatMessage  (many)
  │           ├─ id
  │           ├─ sender         USER | AI
  │           ├─ text
  │           ├─ thinkingContent?
  │           └─ timestamp
  │
  ├─► NoteData       (many per project)
  │     ├─ id
  │     ├─ body          (HTML from rich-text editor)
  │     ├─ createdAtMs   (epoch ms for sorting)
  │     ├─ createdAt / updatedAt (display strings)
  │     └─ conceptIds    (concepts this note is tagged with)
  │
  └─► Concept        (many per project)
        ├─ id            (stable; migrated glossary words keep their old ID)
        ├─ name
        ├─ intro         (1–2 sentence summary, shown on cards)
        ├─ body          (markdown article, shown on the concept page)
        ├─ aliases       (comma-separated, used for search + duplicate check)
        ├─ status        FULL | GHOST (ghosts shown in the muted color)
        ├─ createdAt / updatedAt
        ├─ firstMetAt / lastTouchedAt
        ├─ sources       List<ConceptSource>: type (INSIGHT | CHAT_MESSAGE | NOTE),
        │                chatId / messageId / insightId / noteId, snippet, createdAt
        ├─ relatedConceptIds  (empty for now)
        └─ copiedFromId  (set when copied from another project)

  GlossaryWord (legacy, still stored under "glossary_words" until the migration is verified;
  migrated into concepts once at app start and when importing an older backup)
```

**Storage:** Everything is in a single Jetpack DataStore Preferences file, serialized as JSON via Gson. No SQL database. Project pictures live in `filesDir/project_pictures/`.

---

## AI Architecture

All AI calls route through `AiRepository.askAi()` / `askAiWithThinking()`.

### Providers

| Provider | API Format |
|----------|-----------|
| OpenAI | Chat Completions (`/v1/chat/completions`) |
| Gemini | Google Generative Language REST |
| Claude | Anthropic Messages API |
| Custom | Auto-detected via `detectCustomApiFormat()` |

### AI Use Cases

| Feature | Prompt builder | When triggered |
|---------|---------------|----------------|
| Microlearning insight | `InsightRepository.buildInsightPrompt()` | 30-min session timer or manual regenerate |
| Chat response | `ChatPromptBuilder.buildChatSystemPrompt()` | User sends a message |
| Learning progress summary | `LearningProgressRepository` | Manual "GENERATE" in Project Settings |
| Concept page | `ConceptGenerator.generateConcept()` | Generate / Regenerate on the concept page or edit screen; automatically after "Add to concepts" |
| Chat title | `ChatTitleGenerator` | After first AI response in a new chat |

---

## Feature Areas Summary

| Area | Entry point | Content types |
|------|-------------|---------------|
| **Home / Learning feed** | `home` | AI insight bubble, project switcher, quick chat |
| **Projects** | `projects` | Project cards, reorder, create new |
| **Chat** | `project/{id}` → Chats tab | Markdown conversations, insight-seeded chats |
| **Notes** | `project/{id}` → Notes tab | WYSIWYG rich-text notes (HTML) |
| **Insights** | `project/{id}` → Insights tab | Stored AI insights, link to chat |
| **Concepts** | `project/{id}` → Concepts tab | Concept list (search, sort, ghost filter chips), concept pages, AI-written articles, note tags |
| **Project config** | `project_settings/{id}` | AI instructions, background, learning progress |
| **Settings** | `settings` | Account (stub), appearance, AI API config, backup |

---

## Notable Architecture Observations

- **Navigation tab encoding:** `ProjectScreen` uses three separate routes (`project/{id}`, `project/{id}/concepts`, `project/{id}/notes`) purely to control which tab is active on Back navigation. The Insights tab has no route of its own.
- **"Add to concepts":** the text-selection menu in chats, insight chats, the insight preview and the home bubble opens the New concept screen with the selected text as the name. If the name/alias already exists, a dialog offers to open the existing concept (and records where it was met). Otherwise the page is written with AI.
- **Single DataStore for everything:** No SQL — all entities are JSON-serialized into one Preferences store. Simple, but the entire collection is deserialized on every read, which may become a performance concern as data grows.
- **AccountSettingsScreen is a stub:** It exists in the nav graph but has no active functionality.
- **Thinking content support:** Both `StoredInsight` and `ChatMessage` have a `thinkingContent` field to store reasoning chains from thinking models (Gemini 2.5 Pro, OpenAI o-series). This is surfaced as an expandable section in the UI.
- **Cover screen mode:** A `rememberIsCoverScreen()` utility detects foldable cover screens and adjusts layout/keyboard behavior accordingly.
