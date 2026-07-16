---
title: Chat Accessibility
fork: https://github.com/luojiaping/Operit
branch: agent/issue-735-accessibility
status: p0-complete
issue: https://github.com/AAswordman/Operit/issues/735
---

# Chat Accessibility

## Current state

TalkBack users cannot navigate chat messages reliably. Message roles are not exposed as headings,
while visual Prompt and Response labels and decorative avatars add duplicate or irrelevant speech.
The released Cursor and Bubble layouts must keep their current visual and touch behavior.

## Intent

Implement the staged accessibility plan from issue #735. P0 establishes predictable message-level
navigation without changing message rendering, gestures, or the behavior seen by users who do not
use accessibility services.

## Expected result

- User messages and AI replies expose localized role headings in both chat styles
- TalkBack heading navigation can move between messages
- Visual Prompt and Response labels do not repeat the message role
- Decorative avatars do not create redundant accessibility output
- Existing layout, tap, long-press, scrolling, and menu behavior remains unchanged

## Scope

1. [P0 message navigation](1_MessageNavigation.md) - DONE
2. Structured Markdown and tool content remain in the planned second stage
3. Message-menu accessibility and streaming announcements remain in the planned third stage
4. Automated semantics coverage and real-device TalkBack acceptance remain in the planned fourth stage
