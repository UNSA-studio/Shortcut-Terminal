# GUI and inventory logic audit

Version, archive name and release tags are unchanged: **1.0.1(Beta)**.

## Fixed defects

- A terminal panel could install hardware but had no path to open its screen. Normal use now opens the terminal; hardware installation predicts the same action on both sides and mutates inventory only on the server.
- New sessions were inserted twice. Removing an inactive tab could select/write the wrong session. Resize reset selection. Boot tasks could print into another tab. Sessions now have stable live state, one insertion, atomic persistence, per-tab drafts and main-thread boot progression.
- Delete closed a tab and Ctrl+C switched tabs instead of editing text. Only explicit close controls/Ctrl+W close a tab now.
- Input, RAM status and prompt overlapped. Long output escaped the panel. The scrollbar used the pre-tab layout origin. Thirty-two tabs overflowed the fixed panel. Output is wrapped/clipped and tabs are paged; input and status have separate rows.
- Package operations and external programs could block the render thread. Their blocking I/O now runs off-thread and results return to the originating session.
- RAM pressure omitted the 10 KiB idle cost and treated 80% as exceeding 80%. Accounting now aggregates all tabs, including idle cost, history and drafts. This is a **STOS logical budget**, not host-JVM memory measurement. Existing RAM tier values are retained.
- Menus depended on finding a client block entity, so an unavailable chunk could remove the machine slots and shift all inventory indices. Client constructors now always allocate matching containers.
- Lithography progress was unsynchronized. ContainerData now synchronizes progress, target, remaining coil energy, formation and running state. Missing multiblock parts are explained in the screen instead of preventing inspection.
- The coil was never consumed because its condition could not become true. One coil now supplies two batches. Three output slots retain all three possible CPU grades without ejecting overflow. Inputs are locked while processing; break drops retain inventory.
- The assembly bench produced an extractable unpaid preview, did not preserve installed hardware and could duplicate output on quick-move/break. A server-validated sealing button consumes seven inputs atomically and creates one paid output with CPU/RAM/SSD data. Old unpaid previews are migrated by retaining their inputs.
- Masks ending in `_l9` could be accepted as processors. Processor detection now checks exact registered item identity.
- The assembly background was not rendered, labels/slots overlapped and new block-item models were missing. Screens now share consistent slot frames, localized labels, contextual empty-slot tooltips and state indicators.
- Client file operations used incompatible player-name/UUID keys, accepted files as directories, mishandled absolute/trailing paths and could recursively copy a directory into itself. These paths and SSD quota checks have been corrected.

## Automated verification

`./gradlew runGameTestServer` runs four real NeoForge server GameTests:

1. Ordered assembly, atomic consumption, preserved hardware, repeated button packets and Shift-click extraction.
2. Both client menu constructors without any block entity at the supplied position.
3. Two 400-tick batches per coil, exactly ten CPUs per batch, permanent mask, working-slot locks, output backpressure and no third batch without fuel.
4. CPU hard limits, aggregate RAM threshold (including the exact 80% boundary) and reachability of all 32 tabs at small width.

CI requires the literal `All 4 required tests passed` log before publishing. A filtered report is published alongside the jar in `dist/GUI_REGRESSION_REPORT.txt`. JSON parsing/pattern-key checks are additional static checks, not substitutes for runtime tests.

## Remaining visual validation

A headless server cannot prove OpenGL/font rendering or FCL touch behavior. Check these in the actual client:

- Use a terminal in either hand, install/replace each hardware type, resize and change GUI scale.
- Edit with Delete/Ctrl+C/Ctrl+X; create/switch/close tabs with drafts and long output; open an L9 panel with many tabs.
- Open both machines holding an item; hover empty slots, Shift-click, drag/swap stacks and inspect progress in multiplayer.
- Assemble, save/reload, break the bench/machine and verify the returned inventory.

Do not claim a visual FCL pass without actually running that client.
