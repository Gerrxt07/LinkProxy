2026-06-02T11:42:34+02:00 [USER] [PLANS] Rename project branding from Velocity to Link and keep Gradle build working.
2026-06-02T11:42:34+02:00 [CODE] [OUTCOMES] Renamed project IDs, package root, class/resource names, config defaults, native library names, build plugin IDs, and user-facing strings from Velocity to Link.
2026-06-02T11:42:34+02:00 [CODE] [DISCOVERIES] Kept external dependency com.velocitypowered:velocity-brigadier unchanged because it is upstream dependency coordinate needed for build resolution.
2026-06-02T11:42:34+02:00 [TOOL] [OUTCOMES] ./gradlew build succeeded after import-order fixes.
2026-06-02T12:00:00+02:00 [USER] [PLANS] Remove existing Git connection from dev/3.0.0 fork and set project version to 1.0.0.
2026-06-02T12:00:00+02:00 [CODE] [OUTCOMES] Removed .git metadata, set gradle.properties version=1.0.0, set Fill versionFamily to 1.0.0, and made manifest version fallback work without Git.
2026-06-02T12:00:00+02:00 [TOOL] [OUTCOMES] ./gradlew build succeeded without .git present.
2026-06-02T12:00:00+02:00 [CODE] [OUTCOMES] Added executable run-proxy.sh to build missing shadow jar and run link-proxy-1.0.0-all.jar from run/.
2026-06-02T12:00:00+02:00 [CODE] [OUTCOMES] Removed bStats startup code, Metrics.java, bStats dependency, and shadow relocation so no bStats console message appears.
2026-06-02T12:00:00+02:00 [TOOL] [OUTCOMES] ./gradlew build succeeded after bStats removal.
2026-06-02T12:00:00+02:00 [CODE] [OUTCOMES] Added --enable-native-access=ALL-UNNAMED to run-proxy.sh and Gradle proxy run args to silence Java restricted native access warning from Jansi.
2026-06-02T12:00:00+02:00 [TOOL] [OUTCOMES] ./gradlew build succeeded after native-access run arg change.
2026-06-02T13:56:18+02:00 [CODE] [OUTCOMES] Internal stability cleanup: removed dead metrics config, moved HAProxy override before listener bind, added HAProxy wildcard-bind and packet-decode debug warnings, stopped Gradle run tasks from forcing packet decode debug, and made native Java fallback report first native setup failure.
2026-06-02T13:56:18+02:00 [TOOL] [OUTCOMES] ./gradlew build succeeded after internal cleanup.
2026-06-02T14:27:58+02:00 [CODE] [OUTCOMES] Optimized Brigadier suggestion usage by coalescing duplicate in-flight suggestion requests per source/input/command graph version, invalidating on command graph changes, and adding tests for coalescing and invalidation.
2026-06-02T14:27:58+02:00 [TOOL] [OUTCOMES] ./gradlew build succeeded after suggestion coalescing changes.
2026-06-02T14:50:01+02:00 [CODE] [OUTCOMES] Fixed prevent-client-proxy-connections URL building to use numeric remote IPs, added a HAProxy warning when proxy protocol is off, removed player-info-forwarding-mode from generated and loaded config, and made Link always use modern player forwarding.
2026-06-02T14:50:01+02:00 [TOOL] [OUTCOMES] ./gradlew build succeeded after modern-only forwarding changes.
2026-06-02T14:54:46+02:00 [CODE] [OUTCOMES] Removed announce-forge from generated and loaded config, stopped local ping responses from adding fake Forge mod info, and kept API getter returning false for compatibility.
2026-06-02T14:54:46+02:00 [TOOL] [OUTCOMES] ./gradlew build succeeded after announce-forge removal.
2026-06-02T15:20:15+02:00 [CODE] [OUTCOMES] Removed ping-passthrough and sample-players-in-ping from generated and loaded config, deleted ping passthrough code and enum, fixed ping player samples to empty, removed online-mode and force-key-authentication from config, and made both always true.
2026-06-02T15:20:15+02:00 [TOOL] [OUTCOMES] ./gradlew build succeeded after ping and auth config removals.
2026-06-02T16:29:48+02:00 [CODE] [OUTCOMES] Copied live run/link.toml choices for bind and MOTD into proxy/src/main/resources/default-link.toml while keeping removed config keys out.
2026-06-02T16:29:48+02:00 [TOOL] [OUTCOMES] ./gradlew build succeeded after default config value sync.
2026-06-02T16:31:19+02:00 [CODE] [OUTCOMES] Corrected default config sync source to proxy/src/main/resources/default-config.toml and made default-link.toml byte-identical to it.
2026-06-02T16:31:19+02:00 [TOOL] [OUTCOMES] diff between default-link.toml and default-config.toml is empty; ./gradlew build succeeded.
2026-06-03T14:10:22+02:00 [USER] [PLANS] Implement GitHub issues #3, #4, and #5; defer #1, #2, and #6.
2026-06-03T14:10:22+02:00 [CODE] [OUTCOMES] Added .serena/ to .gitignore; replaced Netty HAProxy codec with strict PROXY v2 decoder; added adaptive Netty flush consolidation; moved plugin async executor tasks to virtual threads.
2026-06-03T14:10:22+02:00 [TOOL] [OUTCOMES] ./gradlew build succeeded after issue #3/#4/#5 changes.
2026-06-03T15:19:43+02:00 [CODE] [OUTCOMES] Bumped project version and Fill version family to 1.0.1, pushed branch codex/proxy-performance-1.0.1, and opened draft PR #7 to close GitHub issues #3, #4, and #5 when merged.
2026-06-03T15:31:17+02:00 [USER] [PLANS] Implement remaining GitHub issues #1, #2, and #6 on the existing performance PR branch.
2026-06-03T15:31:17+02:00 [CODE] [OUTCOMES] Added FFM-first OpenSSL/libdeflate variants with JNI/Java fallback, made io_uring auto-preferred when Netty reports it available, and wrapped raw retained packet frames in RawMinecraftPacket before zero-copy forwarding.
2026-06-03T15:31:17+02:00 [TOOL] [OUTCOMES] ./gradlew build succeeded after issue #1/#2/#6 changes.
2026-06-03T15:40:16+02:00 [CODE] [OUTCOMES] Removed Java 21 preview-compiled FFM classes after Java 26 runtime rejected class version 65.65535 preview files; existing JNI native libdeflate/OpenSSL paths remain active.
2026-06-03T15:40:16+02:00 [TOOL] [OUTCOMES] ./gradlew build succeeded and ./run-proxy.sh booted to Done without preview class crash; PR #7 should no longer close issue #1 until FFM is reworked without preview class files.
2026-06-03T15:48:40+02:00 [USER] [DECISIONS] Project should target Java 26, so FFM implementation is acceptable without Java 21 preview class files.
2026-06-03T15:48:40+02:00 [CODE] [OUTCOMES] Switched Gradle Java toolchain to 26, restored FFM-first libdeflate/OpenSSL variants without preview flags, and kept JNI/Java fallback variants after FFM.
2026-06-03T15:48:40+02:00 [TOOL] [OUTCOMES] ./gradlew build succeeded; ./run-proxy.sh booted on Java 26 and logged libdeflate FFM plus OpenSSL FFM loaded before Done.
