Grapefruit Maps 1.0 beta 1 is a separate GPL-3.0-or-later Android app for Android 4.2.2 and later.

- Grapefruit UI and media-app Settings design, with Grapefruit Dark, Grapefruit Light, Pitch Black and Victory Blue.
- 2D OpenStreetMap view and NASA Blue Marble satellite composite (regional detail, zoom 8).
- Preserves Gulf of Mexico and Lake Ontario in app-owned labels, local lookup, identified search results and offline water-body rendering. Names baked into online raster tiles remain provider-controlled.
- 418 downloadable 2D maps grouped by 200 countries and territories, resumable downloads, and local Mapsforge rendering.
- Place search, pins, optional current location, driving directions and a saved route overview. No turn-by-turn navigation yet.
- Victory Blue Easter egg: Settings → About → tap Developer / Haylie five times. Tap the red cap to drop poop faces on the map of America. Back clears them. All artwork is bundled and works without emoji fonts.

Satellite viewing and new directions require internet. Satellite downloads, offline route calculation and non-driving routes are not in beta 1. NASA imagery is a static satellite composite, not street-level aerial imagery.

The APK is non-debuggable, with v1 signing for old Android devices and v2 signing for newer ones. The build targets API 36 with minimum API 17. API lint, download and UI tests pass on API 23/28/35, with native offline rendering checked on API 28. Actual Android 4.2.2 hardware remains untested.

Source and build instructions are included in this release's source archives and the maps/README.md on branch maps-beta-1. Map data credits and dependency license texts are included in the app's About screen.
