# Blue-only Android 4.x text editing (Beta 5)

The reported failure is double-tap/long-press editing on Android 4.2.2, without a crash report.
The exact device exception is unconfirmed. Newer Android test runtimes did not reproduce it.
This compatibility change avoids the legacy platform selection UI instead of claiming to fix a
specific unverified framework exception. Both original app modules remain unchanged.

`BlueEditText` uses this compatibility path on API 17–20. On API 21 and later it delegates to the
normal Android editor. The widget still inherits EditText: the editable buffer, input connection,
IME actions, input filters, input types, hardware keys and password transformation stay in place.
It does not install a custom keyboard or inspect private framework fields.

## Legacy interaction

- Tap to place the caret and type. Double-tap a word or long-press to select and open Blue's
  existing blue **Edit text** sheet: Cut, Copy, Paste and Select all, as applicable.
- Drag the blue selection handles to adjust a range. A long-press can also extend the selection
  while dragging before releasing. Text can scroll inside a long field; page scrolling remains
  available outside a selection drag.
- Touch gestures do not enter `Editor.onTouchEvent`, which can open the system insertion/paste
  popup independently of the selection action bar. Requested action modes/context menus are
  redirected too. No exception swallowing around Android's global event loop is used.
- Clipboard commands use plain text, respect input filters and finish old IME composition before
  replacing text. A URI-only clipboard item is pasted as its address without opening its provider.
- Password inputs offer neither Copy nor Cut, including visible-password and numeric variants.
  Read-only radio diagnostic text offers Copy and Select all, with no editing or keyboard.
- A field's menu closes on detach. Delayed dismiss callbacks cannot take over a new menu or bring
  a keyboard back to a page that has closed.

All Blue creation paths use the widget: `Ui.field`, library and station search, tag editing,
TV search/provider forms, Victory Roll Call search, and selectable radio diagnostics. There are
no editable XML layouts requiring an inflater hook.

## Carrying the change over after tablet testing

The portable core is `blue-victory/src/main/java/com/grapefruitmusic/ui/BlueEditText.java`.
The standard audio app already has the same `Ui` and `GrapefruitDialog` helpers, so a later port
can add the widget to that app and replace its field constructors. A TV port must adapt package
names plus the accent, dimension and custom-menu helpers to that app's equivalents. Do not copy
Blue's unrelated screens or change the original apps as part of this beta.

The constructor's default compatibility policy is `Build.VERSION.SDK_INT < 21`. Its package-only
boolean constructor allows automated tests to exercise this exact code on supported desktop
runtimes, which start at API 23. It is not an end-user setting.

Validation includes deliberately rejecting any platform selection action mode, gesture dispatch,
custom menu actions, filtered/empty/reversed-selection paste, IME typing/composition, password
restrictions, read-only text, Unicode and native-rendered draggable handles. Modern editor
selection/copy/paste is checked separately. The portable editor also compiles against Android
API 16 stubs; this is an API compatibility check, not a device test. Lint checks the API 17 minimum. Android 4.2.2 device
validation remains necessary; this workspace does not have a device or an API 17 runtime.
