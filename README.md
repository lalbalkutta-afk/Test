# Lalit Media Player (Android TV prototype)

A starter Android TV media player built with AndroidX Media3 / ExoPlayer. It has a video document picker (including USB devices exposed by Android's document picker), Media3 playback controls, external SRT selection, and offset plus linear drift-rate correction for external SRT files.

## Important limitations

- This source project has not yet been built or tested on a target television. The current execution environment does not have an Android SDK or Gradle installed, so an APK could not be compiled here.
- Embedded MKV subtitles are delegated to Media3 and the platform demuxer/decoder. This prototype cannot rewrite timestamps inside embedded subtitle tracks; correction controls apply only to external `.srt` files.
- A linear SRT drift adjustment can compensate for a subtitle file whose timing error grows at a roughly constant rate. It cannot fix every cause of drift, including media with incorrect or discontinuous timestamps, variable frame rate, demuxer bugs, or device codec issues.
- AAC/HE-AAC decoding depends on Android's available codecs and the media's actual stream format.
- USB access uses Android's Storage Access Framework. The file picker must expose the USB drive on the TV.
- This is source code, not a finished, validated release. A signed APK must be built and tested before everyday use.

## Build in Android Studio

1. Install Android Studio with Android SDK Platform 35 and JDK 17.
2. Open this folder (`lalit-media-player`) as an existing Gradle project.
3. Allow Gradle to download the Android Gradle Plugin and Media3 dependencies.
4. Build > Build APK(s), then locate the debug APK under `app/build/outputs/apk/debug/`.
5. For installation without ADB, copy the APK to a USB drive and open it on the TV with a file manager that supports APK installation. Android may require enabling permission to install unknown apps for that file manager. This is not guaranteed to be available on every TV configuration.

## Controls

- Open video / USB: opens Android's document picker.
- External subtitles: select an external `.srt` file.
- Offset: positive values delay subtitles; negative values bring them earlier.
- Drift: scales subtitle timestamps progressively around the start of the subtitle file. Adjust in small increments and test near both the beginning and end of the video.
- Media3 playback settings: use the player's settings control to choose available audio and embedded subtitle tracks.

## Package details

- App label: Lalit Media Player
- Application ID: `com.lalitbadoga.mediaplayer`
- Version: `0.1.0`
- Minimum Android API: 23

## Build the APK online with GitHub Actions (no Android Studio or ADB)

This repository includes `.github/workflows/build-apk.yml` to build a debug APK on GitHub's hosted runner.

1. Create a GitHub repository named `lalit-media-player`.
2. On GitHub, open the repository and choose **Add file → Upload files**.
3. Upload the *contents inside* this project folder, including the hidden `.github` folder and its workflow file. Do not upload only the ZIP file. If the browser does not show hidden files, make sure `.github/workflows/build-apk.yml` is included when selecting the extracted folder's contents.
4. Commit the upload to the `main` branch.
5. Open the repository's **Actions** tab, select **Build Lalit Media Player APK**, and open the latest successful run.
6. Under **Artifacts**, download `Lalit-Media-Player-debug-APK`. Extract the downloaded artifact ZIP; it contains `app-debug.apk`.
7. Copy `app-debug.apk` to a USB drive and use a file manager on the TV to install it, if the TV permits installation from unknown sources.

The workflow creates a debug-signed APK for testing. It does not guarantee that subtitle drift is fixed; test the same MKV on your target television. This project applies drift adjustment to external SRT subtitles only, not embedded subtitle timestamps.
