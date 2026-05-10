# Moon Spire Workspace Notes

## Highest Priority

- Any gameplay modification, addition, or removal must be recorded in `docs/gameplay.md` in the same change.
- Gameplay documentation must be written in Simplified Chinese.
- Gameplay entries must be categorized by feature area, describe the player-facing rule/behavior, and briefly explain how the code implements it.
- If a code change affects gameplay even indirectly, update or add the matching category entry instead of leaving the document stale.
- When the user says only "回合开始时" or "回合结束时" without further qualification, interpret it as the start or end of the acting side's own turn (玩家方或怪物方自己的回合), not the start or end of the whole combat round.

## Working Rhythm

- Avoid long silent code-reading stretches. After the first quick scan, report what files look relevant and what will be changed.
- Prefer a small complete patch once enough context is known, then compile or run the narrowest useful verification.
- If exploration takes more than a couple of tool calls, give a short status update before continuing.
- Do not wait until every detail is understood before making low-risk layout, documentation, or narrowly scoped changes.
- When investigating battle confirmation or synchronization delays, do not stop at client-side modal visibility or local animations. Check the authoritative server chain and sync cadence first: payload handler thread, `BattleManager` mutation, `BattleState` pending flags, snapshot size/frequency, and stale snapshot ordering. Large `BattleSnapshot` payloads must not be sent every tick while a pending player choice is idle; repeated old pending snapshots can queue ahead of the confirmation result and look like delayed server resolution.

## Lessons From Recent UI Fixes

- When a user says a previous UI fix did not solve the problem, first re-check the actual render/input state chain before adding another visual workaround. Do not assume the last patch was directionally correct.
- Keep follow-up fixes tightly scoped to the newest user request. If the user narrows the task to one issue, do not keep changing adjacent pile, modal, or layout behavior in the same pass.
- Do not hide card layering bugs by drawing opaque or solid-color backing under cards. Card visuals must stay faithful to their textures; fix leaks through layer ownership, batching, clipping, and render order instead.
- For hand-card drag behavior, every hover entry point must respect `dragState`: frame cache, preview hit testing, sticky hover, direct hover, and render-time hover selection should all return no hover while dragging.
- Clearing a drag hover must not accidentally snap cards to their target layout. If only hover should be cleared, reset hover progress only; use a separate helper when an instant position reset is genuinely intended.
- When a dragged hand card is released without being played, use the release/current card position as the animation start and let normal hand target interpolation pull it back. Do not clear `dragState` and then immediately reset the hand animation to the final layout position.
- Make play attempts report whether a card was actually used before starting played-card or return-to-hand animations. Invalid target, invalid play area, insufficient energy, or locked action should all take the smooth return path.
- After card UI animation changes, compile and also reason through the frame order: input release, local state mutation, snapshot sync, animation target update, and render interpolation.

## Video References

- When the user provides a local gameplay/reference video, treat it as workspace context and analyze it before implementing UI animation or interaction changes.
- Use absolute Windows paths when referencing local videos, for example `D:\视频\2026-05-02 14-55-08.mp4`.
- Prefer extracting representative frames into `build/video_frames/` and, if useful, copying the source video to `build/` under a temporary analysis name. These are build artifacts and should not be treated as source changes.
- On Windows, a reliable no-extra-dependency method is a temporary PowerShell script using .NET/WPF media APIs to seek timestamps and render frames to PNG. Put temporary scripts under `build/`, run them from the workspace, then inspect the resulting PNG frames with image viewing tools.
- Summarize observed timestamps, layout, animation timing, hover/drag behavior, and any uncertain points before changing code. Keep the final implementation guided by the video, but do not add new gameplay rules unless the user asks for them.

## UI

- Before any UI-related change, read `docs/ui_layout.md` and use it as the source of truth for Moon Spire PNG resources, layout JSON fields, debug editor behavior, and file paths.
- Any UI-related change that adds, removes, renames, repurposes, or changes rendering behavior for PNG resources, layout elements, UI debug controls, or screen/HUD placement must update `docs/ui_layout.md` in the same change.
- When the user says to copy an icon, treat that as copying the target icon resource into the source resource tree first, then reference the copied source asset from code. Do not only reference the original runtime target asset.
- When adapting an extendable PNG button or similar UI texture, first inspect the source image dimensions and non-transparent pixel bounds, then identify the fixed end caps and stretch only the repeatable/flat middle band. Do not scale the whole texture to fit, and do not use a fixed target cap width that ignores the destination height; cap widths must be derived from the source slice ratio so angled ends and borders stay visually unwarped.
- `BattleScreen` itself must not draw the chest-style dark transparent background; only modal overlays and popups such as deck views, pile views, and the layout editor may use dark translucent backdrops.
- Do not add blur effects to any UI.
- Avoid blurred backgrounds, backdrop filters, blur shaders, and screen-wide blur overlays.
- Keep the chest-style transparent dark background when a screen needs it, and draw it as the bottom layer.
- All Moon Spire modal and blocking overlays must use the chest/inventory-style black translucent dim layer from `MoonSpireModalLayer.drawTopmostOverlay(...)` or `MoonSpireUiTextures.drawOverlay(...)`; do not use gray near-opaque masks to hide layering bugs.
- Any dark translucent background should match the vanilla chest/inventory-open dim background unless the user explicitly asks for a different overlay texture.
- Prefer crisp HUD/screen panels that leave the world view clear behind the interface.
- Button text defaults to horizontal and vertical centering. Unless a specific button is intentionally icon-led or left-aligned, both widget buttons and hand-drawn button-like controls must draw text centered inside the button; long labels should be clipped or scaled while staying centered.
- UI text should not fake bold weight by drawing the same text multiple times with small offsets. Use the font/style bold support when bold text is needed; draw a separate outline only when the design explicitly needs an outline, and keep it visually distinct from the fill.
- Minecraft 1.21.1 `Screen.renderBackground(...)` calls `renderBlurredBackground(...)`, which runs the vanilla menu blur shader.
- Removing `renderTransparentBackground(...)` is not enough; `super.render(...)` will still call the vanilla background path.
- Every Moon Spire `Screen` implementation must extend `NoBlurScreen` instead of `Screen` directly, unless there is a very explicit user request to use vanilla blur.
- `NoBlurScreen` fixes the issue by overriding `renderBackground(...)`: it keeps the chest-style dim background with `renderTransparentBackground(...)`, but does not call the vanilla blur/background path.
- In custom `render(...)` methods, draw order must be: `renderBackground(...)` first, custom UI content second, `renderWidgets(...)` last.
- Do not call `super.render(...)` at the end of a custom Moon Spire screen, because vanilla `Screen.render(...)` draws background before widgets and can put the background in the wrong layer for custom-rendered content.
- Any UI with a scrollbar must only show and allow scrollbar scrolling when the content cannot fit in the visible area.
- Scrollable UI must use smooth/free pixel scrolling rather than page or row jumps.
- Scrollbars must be directly draggable.
- Scrolling must never skip or permanently hide a row/item.
- If scrollable content fits completely, wheel overscroll may move briefly past the edge but must naturally settle back; the same rebound rule applies when overscrolling at the top or bottom.
- For smooth UI/card animations, advance animation state from render-frame real time, using `System.nanoTime()` delta converted to tick units and clamped to a sane range; do not rely on `tick()`/20 TPS progression for visible card motion, hover previews, or fly-in/out interpolation.
- Prefer frame-rate-independent easing such as `1 - pow(1 - perTickAmount, deltaTicks)` plus a small snap threshold so animation speed stays consistent across FPS.
- Any change involving cards must verify card art renders normally. When rendering cards inside scaled/transformed poses, do not use screen-space clipping that can hide item/card art unless the clip rectangle is transformed correctly; if uncertain, use the non-clipped card-art render path.
- Card pile/deck previews should reserve enough top and bottom space for enlarged cards. Keep the enlarged card and its keyword tips anchored to the card's original position; do not pull the preview toward screen center or clamp it back inward just because the enlarged card exceeds a screen edge.
- Card description keywords, including effects such as Bleed and defensive keywords such as Block, must render in the same darker yellow used by the keyword detail title. Enlarged card previews must show those keyword details beside the card, choosing the left or right side based on available screen space.
- Increased card stat numbers must use a muted/darker green rather than a bright green.
- Attack damage is incoming damage that is counted into shields. Card and intent damage values must not be reduced by the target's current Block; Block absorbs that displayed incoming damage during combat resolution.
- Combatant entry targeting colors are consistent: mouse hover/aim uses a blue outline, and only the selected target uses a yellow outline.
- Bleed status icons use `src/main/resources/assets/moonspire/textures/gui/effects/bleeding.png`; always keep the visible Bleed stack number over the icon.
- Modal UI such as delete confirmations and picker popups must render above all base widgets and swallow base input. Render base content first; when a modal covers base widget text, skip drawing those base widgets instead of hiding them with an almost-opaque screen overlay. Draw the normal chest-style translucent modal blocking layer with `MoonSpireModalLayer.drawTopmostOverlay(...)`, then draw the modal panel last. When a modal closes by confirm, cancel, right click, ESC, or outside click, close it through `MoonSpireModalLayer.close(...)` or the same clear-then-rebuild pattern so no underlying button, card, target, drag, or scrollbar keeps a stale highlighted/aimed state.
- Blocking combat modals, especially the hand-selection modal, must not allow base combat UI text, played cards, flying played-card animations, HUD tooltips, layout-editor text, or base widgets to render above the modal overlay. If a base element visually leaks through a modal, fix draw order by skipping that base layer while the modal is active, then redraw only the modal-owned interactive content above `MoonSpireModalLayer.drawTopmostOverlay(...)`.
- All area numbers and base HUD text must stay below modal overlays. This includes energy numbers, pile counts, exhaust counts, combatant HP/speed/intent values, world billboard damage/effect numbers, card-grid title/count text, deck/pile counters, and layout-editor labels. Do not give these base-layer numbers high z values, `SEE_THROUGH` font display modes, or see-through render types that can punch through a modal.
- Overlay leaks must be fixed by layer ownership, draw order, and depth state, never by making the overlay gray or nearly opaque. The allowed order is: base content at low/normal depth, chest-style translucent black overlay through the shared overlay helper, then only the active modal's own title, buttons, cards, previews, and tooltips.
- When a modal is open, hover previews, enlarged cards, tooltips, played cards, flying cards, and animated cards from the base UI must be skipped unless the modal explicitly owns and redraws them above its overlay. Modal-owned enlarged cards must be drawn after modal buttons if they need to appear in front, and their bounds must avoid the screen edge and confirm buttons.
- Battle/card UI performance diagnostics must not run every frame just because developer mode is enabled. Temporary render timing probes are allowed only while actively debugging and must be removed or guarded behind an explicit one-off diagnostic flag before leaving the workspace.

## Language

- Every player-facing string must use a translation key.
- Do not use `Component.literal(...)` or direct English/Chinese UI text for names, descriptions, HUD labels, messages, buttons, phases, card stats, or card text.
- Store card display text as translation keys (`nameKey`, `descriptionKey`) and render them with `Component.translatable(...)`.
- Card effect description lines default to ending punctuation through the shared card description builder; new effect description branches should use the shared helper instead of adding raw lines without punctuation.
- Before adding, moving, or deleting language file entries, read `docs/language_categories.md`; keep language keys in that documented category order, and update that document if a new category or prefix is needed.
- Reuse existing same-category language keys when the player-facing meaning and placeholder structure match; add a new key only when reuse would be misleading or incompatible.
- Whenever language files are modified, remove translation keys that no longer have an effective code path or resource reference in the same change.
- Internal NBT keys, registry IDs, payload IDs, resource paths, and code comments are not language content and should remain stable implementation strings.
