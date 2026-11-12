# Visual-reference acceptance workflow

This workflow is deliberately **narrowly scoped**. It applies only when the
user provides a visual reference and asks ExFullGram to reproduce it closely or
identically. It is not a global rule for unrelated engineering tasks.

## Default interpretation

If the user supplies a screenshot, video frame, Figma mockup, presentation
image, or similar visual and says "хочу вот так", "как на скрине", "1:1", or an
equivalent phrase, treat the reference as the acceptance target unless the user
explicitly asks for an approximation.

The visual reference is not merely inspiration.

## What the implementation must derive

The user does not need to provide a technical drawing or know how to code.
Translate the reference into measurable implementation constraints yourself:

- silhouette and geometry;
- relative dimensions and spacing;
- alignment and visual hierarchy;
- transparency, blur, refraction and depth;
- border/highlight behavior;
- animation timing, springiness, stretch and deformation;
- touch/drag semantics and gesture conflicts;
- interaction with the surrounding real ExFullGram screen.

## Completion states

Use the following states for reference-matching work:

1. **Coded / not visually verified** — implementation exists.
2. **Builds successfully** — Android compilation is confirmed.
3. **Visually compared / discrepancies remain** — real build evidence has been
   compared against the reference and differences are still visible.
4. **Visually matched within platform limits** — meaningful discrepancies have
   been resolved or explicitly accepted by the user.

Only state 4 is "complete" for this specific kind of task.

A passing build, static CI checks, or code that looks plausible is never enough
by itself to advance to state 4.

## Required visual review before claiming completion

For a reference-matching task:

1. Capture a screenshot/video from the latest Android build.
2. Place or compare it beside the authoritative reference.
3. Check geometry, scale, spacing, material, optical behavior, motion and touch
   semantics.
4. Fix visible differences that materially change the look or feel.
5. If device evidence is unavailable, report **implemented, not visually
   verified** rather than claiming the result is finished.

## Figma review board

Use this Figma file as the persistent review structure:

https://www.figma.com/design/WhGugsuIwY8estzyP3Biyq

It contains:
- an authoritative **Reference** slot;
- a **Latest implementation** slot;
- a completion gate;
- a self-review checklist;
- explicit status vocabulary.

The user's own visual reference always outranks supporting UI kits.

The official Figma library currently exposes an iOS 26
**Tab Bar - iPhone** component that can be useful as a secondary reference.
Importing that Apple component may require accepting Apple's Figma UI-kit EULA;
lack of library access must never be used as a reason to downgrade the user's
reference into an approximation.
