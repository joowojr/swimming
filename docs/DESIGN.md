---
name: 'Cinematic Intelligence: Daylight Focus'
colors:
  surface: '#F7F9F8'
  surface-dim: '#D8DBD9'
  surface-bright: '#FBFCFB'
  surface-container-lowest: '#FFFFFF'
  surface-container-low: '#F1F3F2'
  surface-container: '#EBEEEC'
  surface-container-high: '#E5E8E6'
  surface-container-highest: '#DEE2DF'
  on-surface: '#1A1C1B'
  on-surface-variant: '#414845'
  inverse-surface: '#2F312F'
  inverse-on-surface: '#F0F1EF'
  outline: '#727A76'
  outline-variant: '#C1C8C4'
  surface-tint: '#206D80'
  primary: '#206D80'
  on-primary: '#FFFFFF'
  primary-container: '#D0EFF7'
  on-primary-container: '#001F27'
  inverse-primary: '#8CD1E6'
  secondary: '#59605D'
  on-secondary: '#FFFFFF'
  secondary-container: '#DDE4E0'
  on-secondary-container: '#161C19'
  tertiary: '#8F5549'
  on-tertiary: '#FFFFFF'
  tertiary-container: '#FFDAD3'
  on-tertiary-container: '#360E07'
  error: '#BA1A1A'
  on-error: '#FFFFFF'
  error-container: '#FFDAD6'
  on-error-container: '#410002'
  primary-fixed: '#b0ecff'
  primary-fixed-dim: '#8cd1e6'
  on-primary-fixed: '#001f27'
  on-primary-fixed-variant: '#004e5e'
  secondary-fixed: '#e3e2e3'
  secondary-fixed-dim: '#c6c6c7'
  on-secondary-fixed: '#1a1c1d'
  on-secondary-fixed-variant: '#454748'
  tertiary-fixed: '#ffdad3'
  tertiary-fixed-dim: '#ffb4a5'
  on-tertiary-fixed: '#360e07'
  on-tertiary-fixed-variant: '#6c382e'
  background: '#F7F9F8'
  on-background: '#1A1C1B'
  surface-variant: '#DEE2DF'
  oceanic-depth: '#E8F3F5'
  outline-muted: '#D4D9D6'
typography:
  display-lg:
    fontFamily: Geist
    fontSize: 48px
    fontWeight: '600'
    lineHeight: 52px
    letterSpacing: -0.02em
  display-lg-mobile:
    fontFamily: Geist
    fontSize: 32px
    fontWeight: '600'
    lineHeight: 38px
    letterSpacing: -0.02em
  headline-md:
    fontFamily: Geist
    fontSize: 24px
    fontWeight: '500'
    lineHeight: 32px
  body-lg:
    fontFamily: Geist
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 26px
  body-sm:
    fontFamily: Geist
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 21px
  label-mono:
    fontFamily: JetBrains Mono
    fontSize: 12px
    fontWeight: '500'
    lineHeight: 14px
    letterSpacing: 0.05em
rounded:
  sm: 0.125rem
  DEFAULT: 0.25rem
  md: 0.375rem
  lg: 0.5rem
  xl: 0.75rem
  full: 9999px
spacing:
  unit: 4px
  gutter: 16px
  margin-mobile: 16px
  margin-desktop: 32px
  sidebar-width: 280px
---

## Brand & Style

This design system embodies a **Calm Exploratory Minimalist** aesthetic, designed for people planning and progressing toward meaningful personal goals. The brand personality is thoughtful, focused, and quietly optimistic, reflecting the feeling of preparing for a journey and gradually moving toward a destination.

The visual direction draws inspiration from **travel planning, wayfinding, and quiet transit spaces** rather than conventional productivity software. The interface should feel less like a task manager and more like a personal journey in progress — a place where destinations, stops, and next steps naturally unfold.

Negative space and clear hierarchy create a sense of openness and distance. The UI should recede behind the user's journey, allowing their **destination, current progress, and next stop** to remain the focal points.

The emotional response should be one of **anticipation, possibility, and steady progress** — the feeling of looking forward to a journey while calmly preparing for what comes next.

## Colors

The palette is anchored by a quiet, light background that creates openness and distance while allowing destinations, progress, and important actions to stand out.

* **Primary:** A focused Ocean Blue (#206D80) represents movement, direction, and active progress. Use it sparingly for primary actions, route progress, selected destinations, and active states.
* **Neutral:** Warm off-whites and light grays establish hierarchy between the surrounding environment and journey-related content. Surfaces use restrained tonal differences instead of heavy borders or shadows.
* **Accents:** Near-black text is reserved for destinations and primary information, while Ocean Blue identifies high-priority actions and active progress.

Color should primarily communicate **direction and progress**, not decoration.

## Typography

The typography system uses **Geist** for its neutral clarity and contemporary character, keeping information easy to scan without making the experience feel like conventional productivity software.

It is paired with **JetBrains Mono** selectively for journey metadata such as dates, distances, progress values, location codes, or small navigational labels.

* **Scale:** Strong contrast between destination titles and supporting information establishes a clear sense of orientation.
* **Micro-copy:** Monospaced typography may be used for route metadata, dates, coordinates, progress values, and small wayfinding labels.
* **Tracking:** Headlines use slightly negative letter-spacing to create compact, confident destination headings.

Typography should distinguish between **where the user is going** and **the information needed to get there**.

## Layout & Spacing

The design system employs a **Journey-Centered Fluid Layout**.

The primary journey area remains visually dominant, while navigation, planning tools, and contextual information occupy quieter supporting regions.

Rather than presenting everything as equally weighted dashboard modules, layouts should establish a clear relationship between:

**Destination → Current Position → Next Step**

* **Rhythm:** A 4px baseline grid governs internal spacing.
* **Safe Areas:** Maintain a 32px outer margin on desktop to preserve openness and prevent the interface from becoming dense.
* **Journey Focus:** The primary destination or current stage should receive significantly more visual space than secondary planning information.
* **Reflow:** On mobile, supporting panels collapse into bottom sheets or full-screen views so the user's current journey remains the primary focus.

Avoid conventional productivity-dashboard layouts where every piece of information becomes an equally weighted card.

## Elevation & Depth

Depth is communicated through **Tonal Layering** and restrained borders rather than heavy shadows.

Layers should suggest different levels of the journey: the surrounding environment, active planning surfaces, and temporary contextual information.

* **Level 0 (Environment):** Soft off-white (#F7F9F8), providing an open and calm surrounding space.
* **Level 1 (Journey Surfaces):** White (#FFFFFF) with a subtle 1px border (#D4D9D6) when a meaningful boundary is required.
* **Level 2 (Temporary Context):** Slightly elevated white surfaces or translucent overlays for destination details and contextual planning information.
* **Active State:** The current destination, route, or next actionable step may use a restrained Primary-color tint or border.

Elevation should communicate **context and focus**, not simply container hierarchy.

## Shapes

The shape language is **Soft-Structured**.

Low-radius corners (4px to 8px) keep the interface calm and deliberate while avoiding the overly friendly appearance common to lifestyle and productivity applications.

* **Buttons & Inputs:** Use the standard 4px radius.
* **Journey Panels & Large Containers:** Use 8px radius for clearly defined planning surfaces.
* **Destination Media:** Images and visual destination elements should inherit the radius of their surrounding container.
* **Route Elements:** Lines, markers, and progress indicators may use circular endpoints while maintaining restrained geometry elsewhere.

Avoid excessive pill-shaped elements unless the shape carries navigational or status meaning.

## Components

* **Buttons:** Primary buttons represent meaningful movement such as starting a journey, continuing to the next step, or confirming a plan. Use Ocean Blue with white text. Secondary actions remain visually quiet.
* **Inputs:** White field fills (#FFFFFF) with subtle 1px borders (#D4D9D6). Focus transitions to the Primary color. Inputs should feel integrated into the journey rather than isolated form controls.
* **Markers/Labels:** Compact labels may represent locations, stages, dates, or journey states. Use monospaced text selectively for navigational metadata.
* **Journey Panels:** Prefer spatial grouping and hierarchy over excessive card containers. Borders and tonal surfaces should only appear when they clarify meaningful boundaries.
* **Route & Progress:** Use the Primary Blue for completed or active portions of a route. Upcoming portions should remain visually subdued.
* **Destination Markers:** Current and target destinations should be visually distinguishable without relying solely on color.
* **Progress Controls:** Minimal tracks and indicators may visualize distance or progress toward a destination, but should avoid resembling conventional project-management progress bars where possible.

## Visual Metaphor

The interface should consistently reinforce the idea that **a goal is a destination and progress is a journey**.

Prefer visual language inspired by:

* destinations
* routes
* stops
* departures
* arrivals
* wayfinding
* distance
* travel preparation
* movement through space
* places waiting to be reached

Avoid visual language strongly associated with:

* corporate project management
* kanban boards
* admin dashboards
* performance analytics
* gamified habit trackers
* AI assistants
* generic productivity software

The user should feel that they are **preparing for somewhere they want to go**, not managing another list of tasks.
