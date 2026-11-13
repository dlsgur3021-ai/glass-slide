# Unfold Home v0.5 (Fold8)

Real Android HOME launcher prototype, separate from Glass Slide.

## Safe installation

- Installs using a separate package: `com.portalopen.unfoldhome`.
- Does not overwrite the existing Glass Slide app.
- Does not change Samsung One UI Home, themes, existing icon layout, or system wallpapers during installation.
- Existing wallpaper displays behind the new launcher. The Android system's wallpaper is not written or replaced.
- To make it your default Home, choose **Unfold Home** manually in Settings > Apps > Choose default apps > Home app.
- To return, choose **One UI Home** in the same settings. Your original One UI Home layout remains.
- Once chosen as default, Unfold Home shows **its own** icon layout/widgets; it cannot animate the actual One UI home-screen icons.

## Features in prototype

- Android HOME role, manual opt-in
- Launcher app drawer, search, favorite app selection
- Actual installed launchable apps, one-tap app launching
- Cover 4-column and unfolded 6-column icon arrangement
- Position / opacity / scale transition on Android configuration changes

## Known limitations

- Fold event animation is best-effort; Samsung may re-create launcher process across screens.
- No widgets, notification badges, gesture navigation integration, or original Samsung theme icon pack matching yet.
- Not validated on physical Galaxy Z Fold8. Requires device testing.

## APK build

GitHub > Actions > Build Glass Slide APK > latest completed run > Artifacts > UnfoldHome-debug-APK

This repository started as Glass Slide, so legacy wallpaper source files may remain in the tree; they are not registered in the launcher manifest.
