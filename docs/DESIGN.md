# Swimming — Design System

> **Theme:** light
> **Reference:** warm parchment workspace where color marks identity, never urgency

Swimming is a workspace for people who work alone. The interface stays quiet so the work can be loud: warm off-whites carry every surface, charcoal rather than black carries the text, and Camera Plain Variable keeps display and body in one compact voice — the Lovable foundation this system is built on. Two things are ours. First, **surfaces separate by fill, not by border**: white at rest, light gray on hover, ink when active. Second, **color is reserved for identity** — a folder's tone — while state is expressed by how heavily a chip is filled. Progress accumulates rather than runs out, so warning colors never appear on a task, a folder, or a date. The immersive session screen is the single deliberate exception, where a full-bleed backdrop carries translucent widgets in the corners.

Values live in `frontend/src/styles/tokens.css`. That file is authoritative; update this document alongside it.

## Colors

### Surface and ink

| Token | Value | Usage |
| --- | --- | --- |
| `--color-canvas` | `#fcfbf8` | Page canvas |
| `--color-surface` / `--color-surface-card` | `#f7f4ed` | Warm sand cards and secondary surfaces |
| `--color-surface-container-lowest` | `#ffffff` | **Resting background for every interactive surface** |
| `--color-surface-container-high` | `#ebe7e7` | **Hover gray** |
| `--color-surface-container-highest` | `#e5e2e1` | Pressed, and neutral selection |
| `--color-border-subtle` / `--color-hairline` | `#eceae4` | Borders and separators |
| `--color-stone` / `--color-ash` | `#d4d3d0` | Disabled edges |
| `--color-mute` | `#5f5f5d` | Supporting copy, labels, meta |
| `--color-primary` | `#1c1c1c` | Primary text, and the active fill |
| `--color-on-primary` | `#fcfbf8` | Text on ink |
| `--color-ink` | `#030303` | Highest-emphasis text |
| `--color-on-dark` / `--color-on-dark-mute` | `#fcfbf8` / `rgba(252,251,248,.7)` | Text over the session backdrop |
| `--color-primary-container` | `#3451b2` | Inline links and focus rings |

### Project tones

Four tones cycle to tell folders apart. They sit at matched lightness so no folder outranks another, and each carries a tint for backgrounds.

| Token | Value | Tint |
| --- | --- | --- |
| `--color-tone-fountain` | `#64ADB3` | `#e4f1ef` |
| `--color-tone-indigo` | `#6276b6` | `#e6e9f3` |
| `--color-tone-glorious` | `#F1766F` | `#fcefee` |
| `--color-tone-wild-willow` | `#BCC95D` | `#f1f4ea` |
| `--color-tone-bay-leaf` | `#79B089` | `#e9f5eb` |

Pick the tone with `folder.id % 4`; every screen must use the same key, or one folder changes color as the user moves around. Bay leaf stays out of the rotation — it belongs to the in-progress chip. Use the base tone for fills such as dots and bars, the tint for backgrounds, and keep small text on `--color-mute`, which clears 5.2:1 on every tint.

### Task status

State reads as fill weight, not hue. Done is the heaviest because completion is what accumulates.

| Status | Background | Text | Border |
| --- | --- | --- | --- |
| 시작 전 | transparent | mute | `--color-border-subtle` |
| 하는 중 | bay leaf tint | bay leaf | bay leaf tint |
| 완료 | `--color-primary` | `--color-on-primary` | `--color-primary` |
| 잠시 멈춤 | `--color-surface-container-high` | mute | mute, dashed |

Read them through `--chip-todo-*`, `--chip-doing-*`, `--chip-done-*`, `--chip-hold-*` rather than reaching for the underlying colors.

### Error

`--color-error` `#EF4444` and its container are for **form validation only**. Red never marks a task, a folder, a date, or a session.

### Accent

Coral — `--color-tertiary` `#F48067` with its container and on-container pair — belongs to graphics and brand marks, not to controls.

## Typography

Camera Plain Variable is the sole family; `Inter Variable` and `DM Sans` are fallbacks only. Default tracking is `-0.025em`. Weight 480 is the heaviest step in normal use — the system rations weight. `--text-label-strong-weight` (700) exists for section labels alone.

| Role | Size / line-height | Weight | Token prefix |
| --- | --- | --- | --- |
| label-caps | `14px / 1.3125` | 400 | `--text-label-caps-` |
| body-sm | `14px / 1.3125` | 400 | `--text-body-sm-` |
| body-md | `16px / 1.5` | 400 | `--text-body-md-` |
| body-lg | `18px / 1.5525` | 400 | `--text-body-lg-` |
| button | `16px / 1.5` | 480 | `--text-button-` |
| headline-md | `20px / 1.5625` | 480 | `--text-headline-md-` |
| headline-lg | `36px / 2.475` | 480 | `--text-headline-lg-` |
| headline-xl | `60px / 60px` | 480 | `--text-headline-xl-` |

`--text-display`, `--text-heading`, `--text-body`, `--text-label`, and `--text-meta` are the semantic aliases; `--text-display` drops to the mobile headline below 48rem. `--font-family-mono` currently points at the sans stack, so align numbers with `font-variant-numeric: tabular-nums` instead of a second family.

## Layout, spacing and shape

The centered page is `1280px` at most (`--layout-container-max`), with a `24px` gutter and margins of `16px` on mobile and `80px` on desktop. Sections separate by `32px`, cards pad `16px`, and tight groups use `12px`.

| Scale | `1` | `2` | `3` | `4` | `5` | `6` | `8` | `10` | `12` | `16` |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Value | 4px | 8px | 12px | 16px | 20px | 24px | 32px | 40px | 48px | 80px |

| Element | Radius | Token |
| --- | --- | --- |
| Checkboxes, small surfaces | 8px | `--radius-sm` / `--radius-default` |
| Buttons, inputs, choice rows | 12px | `--radius-md` |
| Cards and panels | 16px | `--radius-lg` / `--radius-panel` |
| Modal shell | 24px | `--radius-xl` |
| Chips, circular icon buttons | full | `--radius-full` / `--radius-control` |

Controls are `48px` tall (`--control-height`); buttons are `32px`, growing to `44px` on coarse pointers. The default modal is `28rem` wide and forms cap at `30rem`.

## Elevation and motion

Depth is nearly absent. The scrim separates a modal from the page, so the modal itself takes neither shadow nor border.

| Token | Value | Usage |
| --- | --- | --- |
| `--shadow-control` | `oklch(0 0 0 / 0.25) 0px 0px 0px 0.5px inset` | Inset edge |
| `--shadow-floating` | `oklab(0 0 0 / 0.08) 0px 0px 0px 1px, rgba(0,0,0,.1) 0px 20px 25px -5px, rgba(0,0,0,.1) 0px 8px 10px -6px` | Rare; never on a modal shell |
| `--overlay-scrim` / `--overlay-blur` | `rgb(28 28 28 / 40%)` / `4px` | Modal backdrop |

Transitions run `120ms` for micro states, `220ms` for short moves, `300ms` for modals, and `140ms` for plain color changes. Use `--ease-out` entering, `--ease-in` leaving, `--ease-in-out` between states.

## Interaction states

This is the load-bearing rule of the system.

| State | Background | Text |
| --- | --- | --- |
| Rest | `--color-surface-container-lowest` | `--color-primary` |
| Hover | `--color-surface-container-high` | `--color-primary` |
| Selected | `--color-on-surface` | `--color-on-primary` |
| Neutral selection | `--color-surface-container-highest` | `--color-primary` |
| Disabled | unchanged | opacity `.55`, `cursor: not-allowed` |
| Loading | unchanged | opacity `.55`, `cursor: wait` |
| Focus | unchanged | `2px` outline, offset `2px` |

Gate hover behind `@media (hover: hover) and (pointer: fine)`, and exclude selected items with `:not(:has(input:checked))` — hover gray over an ink fill erases the selection. Icons inside an ink fill invert to `--color-on-primary`.

## Components

### Modal

The shell is white with `24px` radius and `32px` padding, and carries no border and no shadow. Header, body, and footer are one continuous white surface; do not rule them apart. Header and body sit close — roughly `24px` between the title block and the first section — so the modal opens on content rather than air.

### Section label

`14px / 700 / --color-mute` at `0.02em`. Never attach a numbered badge; numbered steps make a form read as a wizard.

### Choice row

White, `12px` radius, `12px` padding, `4px` between rows. The checkbox is `16px` and fills with ink and a white check when selected; the row itself moves to `--color-surface-container-highest`.

### Filter chip

White, fully pill-shaped, `8px 16px` padding, `40px` tall, ink-filled when active. Let chips wrap with `flex-wrap` rather than locking them into a fixed grid — the count is data-driven.

### Tile

White, `12px` radius, `12px` padding, holding an icon with a title and a supporting line. When active, the fill, the title, the supporting line, and the icon all invert together.

### Stepper

A white pill wrapping two `32px` circular buttons around a value. The value uses `tabular-nums` so it does not shift width as it changes.

### Buttons

Primary is `--color-primary` with `--color-on-primary` at `12px` radius. Tertiary is transparent with charcoal text and a gray hover. Icon-only controls are `40px` circles, transparent until hovered.

### Status chip

A pill using the status tokens. Where the status is editable, build it as a `<select>` styled identically to the read-only chip — the same state should not look like two different things.

### Tag chip

`8px` radius on `--folder-tone-tint`, mute text, preceded by an `8px` dot in `--folder-tone` drawn with `::before`.

### Project card

White with a `4px` tone bar across the top (`--progress-height`). Set `--folder-tone` and `--folder-tone-tint` at card level so the bar, the tag, and anything else inherit one identity.

### Session widget

The backdrop image or video runs full-bleed; widgets float in the corners as translucent cards over `backdrop-filter: blur(20px)`, using `--color-on-dark` and `--color-on-dark-mute`.

## Rules

### Do

- Take every color, space, and font value from `tokens.css`.
- Separate surfaces with radius and fill; treat a border as the last resort.
- Express state as fill weight and identity as hue.
- Frame progress as accumulation — "task 12/20 완료", "이번 주 4일 접속".
- Write the target date plainly as 목표일.
- Check contrast before tinting small text: 4.5:1 for body, 3:1 for large.
- Extract a repeated CSS recipe into tokens, not into a premature component.

### Avoid

- Pressure language such as "지연 위험" or "3일 늦음".
- Red or amber on any task, folder, date, or session.
- Deficit framing — "남은 task 8" instead of "task 12/20 완료".
- Softened euphemisms such as "마음속 목표" or "소프트 목표".
- Heavy travel theming; keep the layout of a productivity tool and remove only the pressure signals.
- Numbered step badges in a form.
- A shadow and a border stacked on the same modal.
- Choosing a folder tone from list position rather than folder identity.

## Known gaps

- `--color-ink-soft`, `--color-body`, `--color-charcoal`, and `--color-success-deep` all resolve to `#1c1c1c`; distinct roles are pointing at one value.
- `--color-primary-container`, `--color-surface-tint`, `--color-inverse-primary`, and `--color-accent-purple-deep` all resolve to `#3451b2`.
- `folder.id % 4` still collides for ids four apart. Storing a tone on the folder, or deriving it from the tag, is the real fix.
- `--font-family-mono` is not a monospace stack.
