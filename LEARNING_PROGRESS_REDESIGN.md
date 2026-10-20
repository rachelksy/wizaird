# Wizaird: Learning Progress redesign (parked)

Status: **parked, not started.** Filed 2026-10-20 to revisit after practice mode v1. Nothing here is decided. It is a proposal from a design conversation, and the owner has not approved it.

## The problem
The owner wants Learning Progress to update automatically. Today it is manual (GENERATE in Project Settings), so only 1 of 7 projects has one. In the 2026-10-20 backup, AI Product Design had 10 insights and 3 long chats but an empty progress field. That means the insight and chat prompts mostly can't "build on what's covered".

The owner's concerns about automating it:
- It must not forget topics covered earlier.
- It must not write the progress wrongly (summarize things that weren't covered, or distort them).
- It digests a lot of context, so it may need a more powerful model. The app has one global model, currently a Gemini Flash model.

## How it works today
`LearningProgressRepository.kt` makes one call with the existing progress, the background, the last `progressInsightCount` (20) insights and the last `progressChatCount` (10) chats. It **rewrites the whole record**. The prompt asks it to keep the existing progress as the base, but nothing in code enforces that. Every run is a chance to drop or distort topics, and the input grows with the project. There are 2 undo snapshots (`learningProgressHistory`).

## Proposed design
Core idea: **never let the AI rewrite what it isn't changing.** A stronger model alone doesn't fix forgetting. The structure does.

1. **Per-item log (the record of truth).** When an insight is created, or a chat is finished, one small call writes 2–4 lines on what that item covered and stores them on the item. The call sees only that one item, so a cheap model should be accurate. Entries are never rewritten.
   - Open: what counts as "a chat is finished" (leaving the screen, X minutes idle, every N messages?).
2. **Progress = a list of topics, not one text block.** Each topic has an id, a name, a short summary, and links to the insights and chats it came from. An update only:
   - assigns new log entries to existing topics, or creates new topics, and
   - rewrites the summaries of the topics it touched.

   Code leaves every other topic exactly as it was, and checks that every topic from before still exists. Forgetting becomes impossible by design.
3. **Checkable accuracy.** Every topic links to its sources (like concept pages do). A wrong summary is fixed by editing that one topic.
4. **Rare, manual cleanup.** Merging duplicate or overlapping topics is a button, with a before/after preview and undo.
5. **"Model for heavy tasks" setting.** An optional second model for consolidation (and maybe concept pages), while everyday calls stay on the main model.

## How prompts would use it
The insight, chat and progress prompts get `project.learningProgress` as one text block today. With topics, they could get the topic list plus the summaries, rendered as text, so the prompts change little. Later, prompts could get only the relevant topics if the list gets long.

## Connections
- **Practice mode.** Topics are the best unit for a project-wide "practice me" (better than concepts, which are created selectively and are sparse). A topic could also store practice results (shaky / solid + date).
- **Background field.** Still separate: what the user knew coming in, never listed as progress.
- **Migration.** Existing free-text progress (AI Conceptual Literacy, ~4,000 chars) would need a one-time split into topics. Keep the old text until the owner checks the result.

## Open questions
- When does the per-item log call run, and does it run for old insights and chats (backfill)?
- Does the user see topics as their own screen (e.g. in Project Settings), or only through prompts?
- Is consolidation ever automatic, or always a button?
- Is the second model setting worth the extra settings complexity?
