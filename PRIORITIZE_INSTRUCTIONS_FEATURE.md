# Prioritize Instructions Feature

## Summary
Added a new feature that allows users to give their project-specific AI instructions more weight/importance in the insight generation prompt.

## Problem
The AI was ignoring custom instructions specified in the project's "instructions" field because:
1. The hardcoded rules in the prompt were more explicit and detailed
2. The project instructions were framed as "Their goal" (user context) rather than "Your instructions" (AI directives)
3. The hardcoded rules came after the project instructions and overrode them

## Solution
Added a toggle in Project Settings called "PRIORITIZE INSTRUCTIONS" that, when enabled, adds an emphasis statement in the prompt that tells the AI to give priority to the custom instructions.

## Changes Made

### 1. Data Model (`ProjectRepository.kt`)
- Added `prioritizeInstructions: Boolean = false` field to the `Project` data class
- Updated the `ProjectDeserializer` to handle the new field during JSON deserialization
- The field defaults to `false` to maintain backward compatibility

### 2. Prompt Builder (`InsightRepository.kt`)
- Modified `buildInsightPrompt()` to check the `prioritizeInstructions` flag
- When enabled AND instructions are not blank, adds this emphasis before the RULES section:
  ```
  IMPORTANT: The user has specified custom instructions in "Their goal" above. 
  These instructions should be given priority and followed closely when generating the insight.
  ```
- The emphasis is strategically placed between the context and the rules to give it maximum visibility
- Does NOT modify the existing prompt structure - only adds the emphasis when needed

### 3. UI (`ProjectSettingsScreen.kt`)
- Added a state variable `prioritizeInstructions` that syncs with the project
- Added a checkbox row below the AI Instructions text field
- The checkbox uses SVG icons from the pixelarticons asset folder:
  - Unchecked: `checkbox.svg`
  - Checked: `checkbox-on.svg`
- Icon size: 16dp
- Label: "Prioritize instructions" (same text size as the instructions input - 12sp)
- The entire row is clickable to toggle the checkbox
- Updated the save handler to persist the `prioritizeInstructions` value

## How It Works

1. User goes to Project Settings
2. Enters custom instructions in the "AI INSTRUCTIONS" field
3. Toggles "PRIORITIZE INSTRUCTIONS" to ON
4. Saves the project
5. When insights are generated, the prompt includes the emphasis statement
6. The AI sees both the custom instructions AND the explicit directive to prioritize them
7. The AI follows the custom format/rules specified in the instructions

## Example Use Case

**Project Instructions:**
```
Generate insights as bullet points with exactly 3 items. 
Each item should be one sentence. End with a question.
```

**Without prioritize flag:** AI ignores this and follows the hardcoded rules (prose format, no questions)

**With prioritize flag:** AI sees the emphasis and follows the custom format (3 bullets, ends with question)

## Technical Notes

- The feature is opt-in (defaults to `false`)
- Backward compatible - existing projects work without changes
- The emphasis only appears when BOTH conditions are met:
  - `prioritizeInstructions == true`
  - `instructions.isNotBlank()`
- The original prompt structure remains intact - we only add emphasis, not modify existing rules
- This approach maintains the quality of the default behavior while allowing customization when needed
