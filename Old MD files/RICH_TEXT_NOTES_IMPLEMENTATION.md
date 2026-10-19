# Rich Text Notes Implementation

## Summary
Added rich text formatting capabilities to the notes screen using the Compose Rich Editor library with a custom toolbar.

## Changes Made

### 1. Dependencies
- Added `com.mohamedrejeb.richeditor:richeditor-compose:1.0.0-rc07` to `gradle/libs.versions.toml` and `app/build.gradle.kts`

### 2. NoteScreen.kt Updates
- Replaced `BasicTextField` with `RichTextEditor` from the library
- Changed state management from plain `String` to `RichTextState`
- Storage format changed from plain text to HTML (backward compatible - old plain text notes will load as plain text)
- **Padding**: Text editor uses 16dp horizontal padding (applied to the outer Box, not the editor itself to avoid double padding)
- **Toolbar**: Custom toolbar at the bottom with secondary button color background, 4dp vertical padding
- **Save button**: Now shows Coral color when there are unsaved changes, disabled (non-clickable) when no changes
- **Undo button**: Added to the left of the save button (currently non-functional as the library version doesn't expose undo API)

### 3. Toolbar Features
The toolbar includes:
- **Bold** (B)
- **Italic** (I)
- **Underline** (U)
- **Strikethrough** (S)
- **Heading 1** (H1) - 24sp
- **Heading 2** (H2) - 20sp
- **Bullet List** (•)
- **Numbered List** (1.)

### 4. ProjectScreen.kt Updates
- Added `stripHtml()` helper function to convert HTML to plain text for note previews
- Updated `NoteListItem` to display plain text previews (no HTML tags or formatting visible in the list)

## How It Works

1. **Editing**: The rich text editor allows tapping to move cursor and selecting text. The toolbar at the bottom provides formatting buttons.

2. **Storage**: Notes are saved as HTML in the `body` field of `NoteData`. This is transparent to the user.

3. **Previews**: Note previews in the project's Notes tab show plain text only (HTML is stripped for display).

4. **Migration**: Existing plain text notes will load correctly. When saved, they'll be converted to HTML format automatically.

5. **No Mode Switching**: Unlike markdown editors, there's no separate view/edit mode. The formatted text is visible while editing.

## Known Limitations

- **Undo button**: Currently non-functional as the library version (1.0.0-rc07) doesn't expose a public undo API. The button is present but disabled.
- **Cursor positioning**: Works with standard `RichTextEditor` component behavior

## Testing Recommendations

1. Create a new note and test all formatting options
2. Open an existing plain text note to verify backward compatibility
3. Test that note previews show plain text (no HTML tags)
4. Verify the toolbar appears at the bottom with correct styling
5. Verify save button turns Coral when changes are made and is disabled when no changes
6. Test cursor positioning by tapping in the text
7. Verify save/load cycle preserves formatting

## Future Enhancements (Optional)

- Implement undo/redo when library exposes the API
- Add more formatting options (code blocks, quotes, links)
- Add keyboard shortcuts for common formatting
