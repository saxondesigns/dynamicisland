Island, a floating live-activity overlay for Android

Build: open in Android Studio (Ladybug or newer), let it sync, run on a physical device running Android 11 or newer.

Setup on the device
1. Allow "Display over other apps".
2. Allow notification access for Island (feeds media, navigation, delivery, downloads, notifications).
3. Allow phone state and call controls (calls).
4. On Android 13+, allow notifications so the service notification is visible.
5. Turn on "Show island".
Some manufacturers (Xiaomi, Oppo, Samsung, Huawei) kill background services aggressively. Exclude Island from battery optimisation and lock it in recents.

Adding an activity type
Create a class implementing IslandActivity (Leading, Trailing, Expanded, expandedHeight, priority, category), add a Category entry, and publish instances to ActivityRepository from any source.

Changes in this revision (not yet compiled or run on a device, please build and test)
- Fixed a crash in IslandService.onDestroy when overlay permission is missing (lifecycle was destroyed before it was created).
- Settings are loaded before the overlay is attached, so it no longer appears with defaults and then jumps.
- Cutout detection falls back to Display.getCutout(), and the island re-places itself on rotation or fold changes.
- The pill can no longer spring below its resting size, which exposed the camera edge in camera-aware mode.
- Press feedback now matches iOS (compact grows slightly, expanded shrinks slightly) and never fades the black body.
- Removed the transparent padding and shadows around the pill that blocked touches to the app underneath.
- Compact width now fits the active content, so timers over an hour, "Incoming" and long nav titles don't wrap or hit the camera.
- Charging and low battery are momentary (6s / 8s) instead of permanently hiding media. The first battery broadcast no longer flashes "charging".
- A pinned activity can no longer hide an incoming call, and stale pins are cleared.
- Download notifications that complete or change kind no longer leave a stuck progress ring behind.
- Expiring activities (delivery, notifications) no longer get removed by an old timer after being replaced by a persistent one.
- Long press opens the source app on Android 14+ (PendingIntent background-start opt-in).
