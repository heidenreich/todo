# Simple TV

A minimal home screen for an old Fire TV (built for a Toshiba Fire TV Edition set).
One row of big tiles for the apps you use, a clock, and nothing else: no ads, no
network access, about 25 KB.

First run shows Prime Video, YouTube TV, Apple TV and HBO Max (whichever are installed),
then **Add app** and **Settings**.

- **OK** on a tile opens the app.
- **Hold OK** on a tile to move it left/right or remove it.
- **Add app** lists every installed app.
- **Settings** opens Fire TV settings, the Amazon home screen (for the app store and
  updates), or the Home button setup.

## Install on the TV

1. On the TV: Settings → My Fire TV → About → click the device name 7 times to unlock
   Developer Options.
2. Settings → My Fire TV → Developer Options → turn on **ADB debugging** and
   **Apps from unknown sources** (on some versions this is set per app; allow Downloader).
3. Install **Downloader** (by AFTVnews) from the Amazon Appstore, enter the link to
   `simple-tv.apk`, and install it.

Or from a computer on the same Wi-Fi (TV's IP is in Settings → My Fire TV → About → Network):

    adb connect <tv-ip>
    adb install -r simple-tv.apk

## Make the Home button open Simple TV

Fire OS won't let another app be the default home screen, so Simple TV includes an
accessibility service that catches the Home button. Turn it on in
Settings → Accessibility → **Simple TV Home Button** (Simple TV shows a reminder until you do).

If that setting isn't listed on your TV, turn it on from a computer instead:

    adb shell settings put secure enabled_accessibility_services com.simpletv.launcher/com.simpletv.launcher.HomeButtonService
    adb shell settings put secure accessibility_enabled 1

Note: with this on, holding Home no longer opens Amazon's quick menu; use the Settings tile.

## Build

    sudo apt-get install aapt apksigner android-sdk-platform-23 dalvik-exchange zipalign
    ./build.sh

The first build creates `simple-tv.keystore`. Keep it: updates must be signed with the
same key, or the old version has to be uninstalled first.
