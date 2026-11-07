# AOSP Launcher2 base

Upstream: Android Open Source Project, `platform/packages/apps/Launcher2`, tag
`android-4.2.2_r1`, commit `002c25eede2a33a5378c16c5bb9165179783e729`.

https://android.googlesource.com/platform/packages/apps/Launcher2/+/002c25eede2a33a5378c16c5bb9165179783e729

The `upstream/` directory contains the exact original source files for audit and
comparison. `UPSTREAM.json` records the source commit and tree. These originals
are outside the Android source set and are not compiled into the APK.

The integrated, modified sources are in
`src/main/java/com/grapefruitlauncher/launcher2/`. This is an adaptation of
Launcher2's workspace and drag core, not a build of the entire stock AOSP app:

- `DragController` retains Launcher2's pointer interception, move/enter/exit/drop,
  source completion, target hit testing, cancellation, edge-scroll scheduling and
  listener lifecycle. The first intercepted move is processed before the old
  child receives CANCEL, so a finger held at an edge schedules paging immediately.
  Repeated edge moves retain drag-layer coordinates. Host APIs use a public `DragLayer`/Context; resource constants
  are local. System vibration permissions and package-removal coupling are removed.
- `DragSource`, `DragScroller`, `DropTarget` retain the upstream contracts; adapter
  targets can report completion without being View subclasses. Deferred post-drop
  animation is disabled so removal is deterministic on the release event.
- `DragLayer` retains drag interception/dispatch and descendant coordinate mapping,
  with public view coordinates. It consumes the release even after endDrag has
  cleared the dragging flag. Widget-resize, folder and private platform APIs are
  omitted. `ViewDropTarget` adapts dock/menu views to the same target search.
- `DragView` uses one owned, display-sized icon bitmap and removes/recycles it at
  drag end. AOSP's deferred widget/folder/fling animation paths are omitted.
- `PagedView` adapts Launcher2's horizontal scrolling states, velocity tracking,
  release snapping, Scroller animation and page layout to a public ViewGroup.
- `CellLayout` adapts cell/span geometry, child layout, occupancy and vacant-cell
  search, with two grid units per icon to support optional half-cell placement.

The Grapefruit layer provides app discovery, persistent all-theme icon caching,
Home/dock persistence and migration, sparse coordinates, resident-page recycling,
settings, typography and theme rendering. No upstream wallpaper service, online
component, widgets or private Android framework API is required.

Original Apache 2.0 copyright headers are retained. Changed files identify the
modifications. The complete original `NOTICE` is bundled as
`src/main/assets/Apache-2.0.txt`, accessible in Settings. The combined application
is distributed under GPL-3.0-or-later, retaining these Apache notices and Geist's
SIL OFL notice.
