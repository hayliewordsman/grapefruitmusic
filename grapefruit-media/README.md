# Grapefruit Media — Grapefruit OS edition

This module builds Blue Grapefruit's existing audio, podcast, audiobook and integrated TV
implementation with four selectable themes and the **Grapefruit Media** name. Blue's original
module and release tasks are unchanged. Media and Blue share
`io.github.hayliewordsman.bluegrapefruit.victory`; they are alternative editions of one app.

The first Music icon is copied byte-for-byte from commit `37addb2`, in all five original
densities. The splash shows that fruit and a live **Grapefruit Media** wordmark. About uses
the same name and fruit. Both wordmarks use the existing static Geist Pixel Triangle font;
all characters in the product name were checked against its character map. Geist's OFL
notice remains in the shared assets. Victory Blue has a distinct style instead of replacing
Grapefruit Dark. Audio and TV receive matching universal-theme support on this branch.

Source generation overlays Media overrides over Blue overrides over Audio's shared source.
It does not copy generated files back into those modules. TV remains available with the
four-theme edition flag; its colors come from the selected theme. The unreleased advance
EP and its decryption key are not supplied or bundled in this ROM edition.

`./gradlew :grapefruit-media:assembleDebug` creates a development APK. Its debug certificate
is **not** a production upgrade identity. `assembleRelease` is unsigned unless the original
Blue key is explicitly supplied through `GRAPEFRUIT_MEDIA_KEYSTORE`,
`GRAPEFRUIT_MEDIA_STORE_PASSWORD`, `GRAPEFRUIT_MEDIA_KEY_ALIAS` and
`GRAPEFRUIT_MEDIA_KEY_PASSWORD`. Keep secrets outside Git and confirm the certificate
against an existing Blue release before vendor app staging. Audio's permanent key must not
be substituted merely because it is available. Version code 9 must also be compared with
the actual installed Blue version before any future data-preserving update.

There is no APK installation or flashing step in this project.
