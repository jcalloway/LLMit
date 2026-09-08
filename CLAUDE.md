# Claude Usage Widget

Native Android home-screen widget showing Claude subscription usage (5H / 1W / optional Fable
weekly). Kotlin + Jetpack Glance + WorkManager + OkHttp. **Plain Gradle, not Expo/RN.**

## Build (debug APK, ~25MB)

`JAVA_HOME` = Android Studio's jbr. Avast breaks the Gradle wrapper download, so reuse the
bundled truststore:

    $env:JAVA_OPTS = "-Djavax.net.ssl.trustStore=$PWD\gradle-cacerts.jks -Djavax.net.ssl.trustStorePassword=changeit"
    .\gradlew.bat assembleDebug

Output at `app\build\outputs\apk\debug\app-debug.apk`. Debug key, so `adb install -r` needs no
uninstall. **Ask before running a build.** The APK is too big for chat upload (10MB cap): hand
it over with SendUserFile.

## The Glance landmine, cause of two "separate" bugs

`update()` / `updateAll()` do **NOT** restart `provideGlance()` if it is already running. Load
initial data before `provideContent`, then OBSERVE the source from inside the composition.

The old code did `provideContent { UsageWidgetContent(readState(context)) }`. `readState` is a
plain SharedPreferences read, invisible to Compose, so with a live session there was nothing to
invalidate and the widget kept drawing its first snapshot until the host tore the session down.
That is why taps "did nothing for ages" AND why appearance settings only applied after
remove-and-re-add. One cause, two symptoms.

Current shape: `readState` first, then
`val state by remember { widgetStateFlow(context) }.collectAsState(initial = initial)`, where
`widgetStateFlow` is a `callbackFlow` over `OnSharedPreferenceChangeListener`, conflated.
`WidgetState` is a data class so equality skips no-op recompositions.

## Do not revisit these

Burned a full session, all wrong: raw-vs-resource background color, the tap cooldown,
UA/cf_clearance mismatch (twice, and one attempt actively broke Google login), WorkManager
scheduling delay.

> When one path is instant and a sibling path is slow, instrument BOTH and compare numbers
> before theorizing. The diagnostic that settled it was per-stage timings recorded into Storage
> and shown in-app: `storage 0ms | spinner 68ms | fetch 597ms | render 7ms | total 672ms`. The
> tap did all its work in 672ms while the display stayed stale, so the bug was rendering, not
> fetching. `BUILD_TAG` is shown in-app so a screenshot proves which APK is installed.

## Other constraints

- **Strip `; wv` from the main login WebView UA.** Google's Sign-In JS runs in the main claude.ai
  frame and refuses to init under Android WebView's marker, hanging on "Loading…". The popup
  inherits the stripped UA. Don't overthink the fetch UA. A UA change only takes effect after
  re-signing in.
- Background opacity must snap to the 11 real color resources `widget_bg_00..100`. A
  runtime-computed Color does not repaint live for pre-A12 corner-radius backgrounds.
- targetSdk 36 drops `windowOptOutEdgeToEdgeEnforcement` (honored only at 35). Wrap the WebView
  in a FrameLayout and pad THAT for system-bar insets; padding the WebView directly does not take.
- Live widget updates from settings need a process-lifetime CoroutineScope, NOT
  `rememberCoroutineScope`, which is cancelled the moment the user backgrounds the app to go
  look at the widget.
- Tap-refresh cooldown is 45s and must exceed CloudflareResolver's 30s timeout, or an impatient
  repeat tap REPLACE-cancels the in-flight worker before it clears the spinner.
- Near-limit notifications dedupe per reset cycle with a 1h tolerance: `resets_at` drifts
  sub-second between polls, so exact matching re-fired on every tap at 100%.
- The Fable weekly row is parsed from `/usage` `limits[]` where
  `scope.model.display_name == "Fable"`, kind `weekly_scoped`. It is not a top-level field.

## Testing

MuMu on Android 15 (API 35); the older API 32 instance cannot reproduce the edge-to-edge bug.
`adb connect 127.0.0.1:16384`, it flaps offline, retry or `adb -s emulator-5556 wait-for-device`.
`LoginActivity` is not exported: drive it by tapping "Sign in" in MainActivity, don't `am start`
it. Real login is blocked headlessly by Cloudflare plus Google creds; the user tests sign-in.
