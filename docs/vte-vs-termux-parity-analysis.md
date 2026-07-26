# VTE ↔ Termux Rendering Parity + Port Plan

Full analysis saved from Qwen session. See summary below.

## Scorecard

| # | Subsystem | Status | Priority | Effort |
|---|-----------|--------|----------|--------|
| 1 | Drawing pipeline | ⚠️ Partial | 2 | M/H |
| 2 | determine_colors() equivalent | ⚠️ Partial | **1** | M |
| 3 | Font system | ⚠️ Partial | **1** | M |
| 4 | Minifont | ✅ Mostly done | 2 | M |
| 5 | Box drawing | ✅ Needs verification | 3 | M |
| 6 | Decoration drawing | ⚠️ Partial | 2 | M |
| 7 | Cursor | ⚠️ Partial | 2 | M |
| 8 | Text run segmentation | ⚠️ Partial | **1** | M |
| 9 | Color system | ⚠️ Partial | **1** | M |
| 10 | Unicode width | ⚠️ Partial | 2 | M |
| 11 | BiDi | ❌ Not integrated | 3 | H |
| 12 | IME preedit | ⚠️ Partial | 3 | M |

## Phase 1 — Do immediately

### 1. RowLayout-driven drawRowText()
- cluster iteration, fragment skip, codepoint minifont detection

### 2. Paint-specific advance cache
- key by paint ID + font generation, measure with draw paint

### 3. Central VTE-like color resolver
- reverse XOR DECSCNM, bold-bright, dim, selection, cursor, deco

### 4. VS15/VS16 policy
- VS zero-width, minifont overrides VS16, emoji clusters keep VS16

## Phase 2 — Next
1. Decoration run merging + SGR 58 deco color
2. Minifont hardening (cache key, resolved fg, cursor redraw)
3. Cursor block redraw for minifont/emoji
4. Unicode width audit
5. Background merging + run planner

## Phase 3 — Later
1. BiDi integration
2. IME preedit integration
3. Damage/partial redraw/row caching
4. Font polish (italic rejection, locale)
5. Box drawing golden tests
