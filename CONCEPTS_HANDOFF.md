# Wizaird: Concepts build handoff

This file carries decisions from a long design conversation in claude.ai, so a new Claude Code session doesn't start from zero. The owner made the decisions below. Anything marked "proposed" or "open" has NOT been decided.

## Read first
1. `INFORMATION_ARCHITECTURE.md` (how the app is organized; the owner says it is close to the real app but may contain small errors)
2. `PROMPTS.md` (every AI prompt in the app)

## What we are building
Wizaird has a Glossary today. We are replacing it with **Concepts**. A concept is a term the user met while learning (e.g. "steerability", "data flywheel") that gets its own page and shows up in a list, instead of staying buried inside chats. Concepts stay inside one project. Later they will feed a visual knowledge map.

## Decisions
- Concepts replace the glossary. "Add to glossary" becomes "Add to concepts". Existing glossary words are migrated into concepts.
- Build step by step: (1) migrate glossary to concepts, (2) build the concept UI, (3) only then think about the AI extraction prompt.
- Adding a concept stays manual for now.
- Concepts live inside a project. Cross-project links are not ruled out later, so give every concept a stable ID.
- A "ghost" concept has a real page and a list entry, but its text and entry are shown in a secondary color. Ghosts will be created automatically later; the UI should support the style now.
- Later, concepts are found by a separate AI call after insights and chats. The insight prompt and chat prompt are NOT changed to do this.
- Concept chips will appear at the end of the insight text (later).
- The insight prompt stays as it is (it ends cleanly, no question).
- The knowledge map should be visual (nodes and links) so a user can spot forgotten concepts and tap in. Later.
- Not building now: "frontier" logic, fading by last-touched, depth checks. Revisit after the map exists.
- Users can both delete and edit individual chat messages today (confirmed in code).
- Add empty fields to the concept from day one so later features need no migration: where it was met (insight / chat / message / note references plus a one-line snippet), first met, last touched, related concept IDs.
- Tag concepts at message level, store the message reference plus a snippet so deleted messages leave no blanks. Chat messages already have a stable ID.
- Ghost = a name-only placeholder (later created by AI extraction). It becomes a full concept automatically as soon as it has content: a description/article/aliases (Generate or manual edit → Save on the edit screen) or a linked chat. There is no "written ghost" and no Save button on the concept page. (Rule lives in `Concept.withGhostRule()`, applied in `upsertConcept`.)
- The concept page is a read-only info page (not a text editor like notes): title, brief description, connected chats, tags, and whatever else a concept needs. Editing happens elsewhere.
- Auto-generate: offered on a ghost concept page and when creating a concept manually. A generated concept can be regenerated.
- Notes: the note editor does not change. A note can be tagged/linked to existing concepts, and the note card shows the tag pill(s).
- No "Move to another project" for concepts (it breaks links). Copying/linking across projects is allowed later, and must not break links.
- Work happens on the git branch `concepts`. `Backups/` is git-ignored (backups contain the API key).
- Design the whole concepts UI from existing pixel components (buttons, popups, dialogs, overlays). Do not invent new UI pieces.
- "Add to concepts" replaces "Add to glossary" everywhere it appears (home bubble, chats, insight chat, insight preview, text-selection menu).
- Duplicates: when a new concept matches an existing name or alias, warn and offer to open the existing concept.
- Concepts list: newest first, with a sort option. Ghosts are mixed into the list, with filter chips: All / Ghosts only / Hide ghosts.
- Tab order: Concepts first, then Chats, Insights, Notes. Opening a project lands on Concepts.

## Proposed:
- "Ask about this" button on a concept page that starts a normal chat seeded with the concept's intro and tagged with the concept.

## Build order

### A. Understand the current code (change nothing)
- [x] A1. Read the two docs, then inspect the glossary code (data model, screens, routes, the tab in the project screen, the definition generator, the "add to glossary" flow from chat) and report any differences from the docs.
- [x] A2. Report: how chat messages are identified, whether messages can be deleted or edited, how stored data written before a new field was added gets loaded, and how backup export and import work.
- [x] A3. Propose a plan for B and C and wait for the owner's approval.

### B. Data and migration
- [x] B1. Define the Concept model: id, name, intro, aliases, status (full by default, ghost later), createdAt, updatedAt. Also add the empty "where met", first met, last touched and related concept fields (owner agreed).
- [x] B2. Store concepts per project in the same storage as everything else. Old saved data must still load.
- [x] B3. One-time migration of glossary words to concepts: word becomes name, explanation becomes intro, aliases and createdAt carry over. Running it twice must not create duplicates. Keep the old glossary data until the owner has checked the result.
- [x] B4. Backup: export includes concepts, and importing an older backup that has glossary words also migrates them.
- [~] B5. Check with real data, then stop so the owner can review.

### C. Concepts UI
- [x] C1. Rename the Glossary tab to Concepts and update routes. Keep the Back behavior that returns to the right tab.
- [x] C2. Concepts list with search by name and aliases. Entries support the ghost (secondary color) style.
- [x] C3. Concept page: name, intro, aliases, related concepts (empty state), "appears in" (empty state), Save concept button, edit, delete.
- [x] C4. Create and edit concept screens replacing the glossary ones. Keep the AI auto-generate option using the existing glossary generator prompt for now.
- [x] C5. In chat, rename "Add to glossary" to "Add to concepts"; it creates a concept.
- [~] C6. Remove old glossary code and text only after the migration is verified. Update `INFORMATION_ARCHITECTURE.md` and `PROMPTS.md` so they match.

### D. Later (not started, do not build yet)
- Separate extraction call after insights and chats: finds concepts, creates ghosts, tags where they were met. Shown as chips at the end of insight text.
- Related concept links and the "appears in" list.
- "Ask about this" seeded chat.
- Visual knowledge map; cross-project links; frontier, fading and depth checks.

## Working with the owner
The owner is a UX designer, not a developer. Explain things in plain language, say when you are unsure instead of guessing, don't invent details about the code, and ask questions when something is unclear. Asking several questions at once is fine.
## Status (branch `concepts`, 2026-10-19)
Built and tested on the emulator with `Backups/backup.json` (20 glossary words → 20 concepts on import).
- Glossary UI and the glossary definition generator are removed. `GlossaryRepository.kt` only keeps the model + a reader so old data and old backups can be migrated. The stored `glossary_words` data is untouched; delete it (and the legacy backup field) once the owner has checked the migration on the phone. This is what's left of C6.
- Migration runs once at app start (flag `concepts_glossary_migrated`) and on backup import when a backup has glossary words but no concepts. Migrated concepts keep the glossary word's ID.
- Concept = summary (`intro`) + markdown article (`body`). New concept prompt: see PROMPTS.md §4.
- "Add to concepts" opens the New concept screen: duplicate check (name or alias) → "Already a concept" dialog (OPEN records where it was met) → otherwise the page is written with AI. Chat messages and insights are stored as sources, so chats show up on the concept page.
- Notes: ⋮ → "Tag concepts" dialog; tagged notes show concept pills (tap → concept page). Moving a note to another project clears its tags. Deleting a concept removes it from note tags.
- Concepts can't be moved; ⋮ → "Copy to" makes a new concept (new ID, `copiedFromId` → original) that keeps its chat/insight links (they open in the original project).
- Not testable on the emulator: real AI output (the provider returned 403 there), and the ghost style (nothing creates ghosts yet).
- Pill style decision: tappable pills with the real concept name (not #hashtags). Pills are only for concepts (note tags, related concepts); aliases are plain text ("Also known as: …") under the article.
- Concept page header follows the chat screen (back, project name centered, ⋮); tapping it scrolls to top. Under the title, jump buttons (CHATS n / NOTES n / RELATED n) scroll to those sections. Regenerate (⋮) rewrites summary + article.
- Concept list: search, filter dropdown (All / Hide ghosts) and sort on one row. "Ghosts only" was dropped.
- Concept page: sections (Chats, Insights, Notes, Related concepts) are App-Settings-style cards with list rows; added/edited date sits under the "Also known as" line; jump pills use the pill style in the secondary button color. Concept pills are light orange (Coral 25%).
- Chats on a concept page: only chats with a stored link (met there via "Add to concepts"). Linking/unlinking from the concept page was removed for now; chats are linked from the chat page.
- Insights link both ways: the concept page lists the insights it was added from, and insight cards + the insight preview show pills for concepts added from that insight. Insights tab now has its own route (`project/{id}/insights`) so Back returns to it.
- Empty concept: muted "No description or article yet." text + inline GENERATE / EDIT MANUALLY buttons (no card).
- Ghost UI: list card uses the normal card fill at 35% alpha, no outline, textXLow text, name not bold ("Ghost — not explored yet"); the page shows name, "GHOST CONCEPT", a centered caps "NO DESCRIPTION OR ARTICLE YET" + GENERATE / EDIT MANUALLY, and no anchors or empty sections (only Insights, if it came from one).
- Section rows: no dividers; chats = small chat icon + name (textLow) with the date in small text below; insights/notes = small date + 2-line preview. Pill text is textLow (ghosts textXLow).
- TEMPORARY: debug builds add, once, "Sample ghost" and "Sample concept with links" (linked to the project's 5 latest chats + 2 latest insights) to each project that has concepts (`addSampleConceptsOnce`, flag `concepts_samples_v2_added`). Remove once ghosts are created for real.
- Concept list: filter button opens a bottom sheet (All / Hide ghosts) like sort (shared `PixelOptionsBottomSheet`). Card preview = brief description only; empty → "No description or article" (secondary text).
- Concept page section order: Related concepts, Chats, Insights, Notes. Empty texts: "No chats/insights/notes linked to this concept yet", "No related concepts yet".
- Chat cards (Chats tab) show pills for concepts linked to that chat, like insight cards. Related concepts on the concept page are pills with no card; empty = small secondary "No related concepts".
- (Superseded: chat cards no longer show concept pills.) Chat cards: if the linked concept pills do not fit on one line, a single "LINKED TO N CONCEPTS ⌄" chip shows instead; tapping it expands the card to show every pill (wrapping onto more lines) with a "Show less" text button to collapse (shared `PixelTextButton` in PixelUi.kt: Coral body text, no fill) (`ConceptPillsOrCount`). Sort/filter sheets share the `PixelBottomSheet` frame.
- TEMPORARY: debug builds also add "Sample linked concept 1–5" linked to each project's newest chat (`addSampleLinkedConceptsOnce`, flag `concepts_samples_v3_added`).
- Card styles: concept cards = caps pixel-font name + soft textLow stroke (ghosts: 35% fill, no stroke); chat cards = body-font bold title, no caps, no border. Concept pills and the count chip use the search bar color (tertiarySurface). Concept/chat/insight/note card padding reduced by 2dp per side (concept/insight/note: 14/10/8/10, chat: 14h/10v).
- Chat cards show no concept pills (too cluttered). Links are managed in Chat settings → LINKED CONCEPTS: concept pills (tap → concept page) plus an "Edit" text button that opens a bottom sheet with every concept in the project as a checkbox row (linked first) and SAVE top right (`LinkConceptsBottomSheet`, `setChatConcepts`, source type CHAT). Linking a ghost makes it a full concept; unlinking removes all of that chat's sources from the concept.
- Linked concepts sheet has a search field (name + aliases). Checkbox rows in the concept pickers (sheet + note-tag dialog) use `PixelCheckRow`: no orange; in the linked concepts sheet: sheet = app background, search = tertiarySurface (as on the Concepts tab), selected row = secondaryButton; dark checked box; unselected = no fill, text-low.
- Chat cards: date only (no time) in text-x-low; title/date have no -2dp nudge and padding is 14/9/14/10 so the measured space above and below the text is equal (42px vs 41px at 1080w).
