# P0 message navigation

Status: DONE

Establish one shared semantics boundary in the chat message wrapper so Cursor and Bubble styles use
the same localized user-message and AI-reply roles. Mark that boundary as a heading while retaining
the existing click and long-press implementation.

Remove only redundant presentation semantics inside each style:

- Keep Prompt and Response visible, but exclude them from accessibility output
- Keep proxy sender names available because they identify the actual speaker
- Remove decorative Bubble avatar descriptions and semantics without changing pointer handling
- Hide fully transparent placeholder messages from accessibility services

This step does not merge structured Markdown blocks, expose the message menu as an accessibility
action, or add live-region announcements. Those changes belong to later stages of issue #735.

## Validation

- Reviewed every Cursor and Bubble message renderer call site, including floating and fullscreen UI
- Confirmed both role labels exist in all seven locale resource files
- Checked the complete diff for whitespace errors
- Per project and task constraints, did not run local builds, compilation, or tests

[DONE]
