---

name: swimming-style
description: Apply Swimming's visual design system to existing frontend code without changing application logic or behavior.
----------------------------------------------------------------------------------------------------------------------------

# Swimming Style

This skill applies the Swimming visual design system to frontend code that has already been implemented by the developer.

The developer owns the structure, behavior, and logic.

This skill owns only the visual presentation.

Before making changes:

1. Read `docs/DESIGN.md`.
2. Inspect the existing frontend implementation.
3. Preserve all existing behavior and application logic.
4. Apply the design system using the smallest possible code changes.

## Core Principle

**Do not redesign the product logic. Style what already exists.**

Treat the existing implementation as functionally correct unless the user explicitly says otherwise.

Your job is to translate the existing UI into Swimming's visual language.

## You May Change

You may modify visual presentation such as:

* CSS
* Tailwind classes
* spacing
* margins and padding
* typography
* font weights and sizes
* colors
* borders
* border radius
* backgrounds
* visual hierarchy
* alignment
* flex/grid presentation
* responsive visual behavior
* hover/focus/active styles
* icons when purely decorative
* visual states already represented by existing logic

You may make minor markup adjustments only when they are strictly necessary to support styling.

Examples:

* wrapping existing elements in a layout container
* adding a class name
* adding a purely presentational element
* changing visual ordering through CSS

## You Must Not Change

Never modify:

* business logic
* API calls
* server communication
* state management
* event handlers
* form submission behavior
* routing
* data fetching
* validation logic
* domain models
* data transformations
* conditional rendering logic
* component responsibilities
* application architecture

Do not rename or restructure logic simply because another implementation would be cleaner.

Do not refactor working code during visual design taskEntities.

## Existing UI Semantics

Preserve:

* all existing information
* all existing actions
* all existing controls
* all navigation destinations
* all form fields
* all component behavior
* all accessibility semantics

Do not remove an element merely to simplify the visual design.

Do not add new product functionality in order to improve the design.

## Design System

Use `references/design.md` as the source of truth for:

* brand personality
* typography
* color
* spacing
* shape
* visual hierarchy
* surfaces
* journey-oriented visual language

When the existing UI does not fit the design system, change its visual treatment rather than changing its functional meaning.

## Component Reuse

Prefer existing components and styles.

Before adding a new visual component:

1. Check whether an existing component can be styled instead.
2. Prefer extending an existing visual variant.
3. Avoid creating duplicate UI primitives.

Do not introduce a new abstraction unless it is necessary for the visual implementation.

## Layout

You may adjust layout when the change is presentational.

Allowed:

* changing grid columns
* adjusting flex direction
* changing gaps
* changing alignment
* adjusting widths
* responsive rearrangement

Not allowed:

* changing which data appears
* changing application flow
* changing interaction sequence
* changing conditional UI behavior

## Styling Priority

When applying the design system, prioritize:

1. visual hierarchy
2. layout and spacing
3. typography
4. color and contrast
5. shapes and surfaces
6. interaction polish
7. decorative details

Avoid decorative additions when hierarchy and spacing can solve the problem.

## Avoid AI-Generated UI Patterns

Do not automatically turn every section into:

* cards
* pills
* gradients
* glowing surfaces
* dashboard widgets

Do not add decorative elements without a clear visual purpose.

Keep Swimming calm, intentional, and journey-oriented.

## Conflict Rule

If applying the design system would require changing application behavior or logic:

**Do not make the change.**

Keep the existing behavior and report the design limitation instead.

## Completion Check

Before finishing, verify:

* no business logic changed
* no API behavior changed
* no event handlers changed
* no state logic changed
* no functionality was removed
* design.md was followed
* changes are primarily visual