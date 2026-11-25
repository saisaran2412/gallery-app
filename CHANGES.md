# Folio changes (not compiled here, please run a Gradle build)
- Themes: LIQUID_GLASS_NIGHT, ARCANE_GLASS (ThemeMode, Color, Theme, FolioSurface tint)
- PIN lockout persisted in DataStore with escalating delays (30s, 2m, 10m, 30m)
- Vault temp files written as .part then renamed; temp cache cleared on launch
- FLAG_SECURE on Vault screen
- AirShareForegroundService (connectedDevice) + manifest entries
- Incoming AirShare files must be image/video by MIME and extension, max 4 GB
- Split: AirShareScreen -> AirShareComponents; MediaViewerScreen -> MediaViewerPages, MediaViewerSheets

## Gallery UX
- GalleryGestures.kt: swipe-to-select, pinch day/month/year, peek-on-hold, PeekPreviewOverlay
- MixedMediaTimelineGrid: swipe-select + onPeek; MediaThumbnailTile: peek on 700 ms hold
- HomeGalleryScreen: pinch zoom regroups sections (more columns when zoomed out)

## Drag-to-share
- Long-press a tray app to pin it (pinned first, then most used). Pins in ShareTargetsHelper.
- Strip location: when Settings GPS toggle is off, shared JPEGs get GPS EXIF removed (GalleryRepository.shareUriFor).

## Multi-item drag-to-share (grid)
- Select photos, then press-and-hold an already-selected thumbnail (~0.45 s) and drag onto an app in the dock that appears; the whole selection is shared.
- Drag gesture, dock and badge in GalleryGestures.kt; wiring in HomeGalleryScreen / FolioApp; GalleryRepository.buildTargetedMultiShareIntent.

## Performance
- Release: R8 + resource shrinking on, keep rules (Room, SQLCipher, Nearby), baseline-prof.txt + profileinstaller
- Viewer: open/close/drag values read inside graphicsLayer/drawBehind (no per-frame recomposition); corner clip moved into graphicsLayer
- Atmosphere background: removed full-screen 88px RenderEffect blur; skipped when reduced motion is on

## Fixes: vault thumbnails, launch glitch, albums, settings
- Vault: per-item decrypt de-dup + max 2 parallel (previously tiles raced on the same temp file); unique .part names
- Launch: start destination decided once from stored settings (no permission-page flash / NavHost rebuild)
- Albums: count chips (99+ cap), preview of 8 with "See all N" for albums and device folders
- Settings: grouped sections (Appearance, Privacy & Security, Storage, Offline intelligence), theme picker by group

## Albums: large-library layout
- More than 12 albums: search box, sort (Default / A to Z / Most items), list or grid toggle, "Biggest albums" carousel, A-Z letter headers, compact rows with thumbnails
- 12 or fewer: unchanged grid

## Round: scan control + speed + battery
- NEW data/scanner/ScanControl.kt: ScanControl (pause/resume/stop), PowerGate (battery, power-saver, thermal), runParallel worker pool
- NEW ui/components/ScanControlBar.kt: progress + Pause/Resume + Stop + "waiting: battery/hot" text
- Duplicate scan: SHA-256 only for same-size files, RGB_565 dHash, parallel, Stop keeps partial results
- Face scan: chunked parallel detect, sequential grouping, face-less photos remembered (no rescans), decode 400px
- Auto-hold at <=15% battery unplugged or thermal SEVERE; throttle on power-saver/warm; 1 worker on low battery, up to 4 when charging

## Round: accurate face scan
- ML Kit face detection (bundled) + eye-aligned crops + MobileFaceNet embeddings (assets/mobilefacenet.tflite, 4 MB) + cosine grouping with running centroids
- Old colour-signature groups are auto-wiped on first new scan. MATCH_THRESHOLD = 0.60 in OfflineFaceEngine.
- New: FaceEmbedder.kt; DAO updatePersonGroupCentroid; gradle deps face-detection 16.1.7, litert 1.4.0

## Round: cinematic depth blur
- NEW editor/DepthEstimator.kt (MiDaS v2 via LiteRT + ML Kit selfie segmentation fusion), editor/DepthBlurRenderer.kt (3 blur levels + highlight bloom, depth-masked compositing)
- EditParams: depthBlur, depthFocusX, depthFocusY (saved in JSON). renderEditedBitmap(source, params, depth) transforms depth with the same rotate/flip/crop.
- EditorViewModel computes depth once per photo; export reuses it. Editor > Decor tab: slider, tap photo to focus, "Cinematic look" chip.
- assets/midas.tflite (66 MB). Gradle: com.google.mlkit:segmentation-selfie:16.0.0-beta6

## Round: depth-blur optimisation + magical processing UI
- NEW ui/components/MagicProcessing.kt: MagicProcessingOverlay (aurora edge glow, shimmer sweep, sparkles, cycling status pill, honours reducedMotion) and MagicProgressBar (glowing gradient, travelling shine)
- Editor shows the overlay while depth is analysed and while exporting; scan Pause/Stop bar now uses MagicProgressBar
- Depth: disk cache per photo (filesDir/depth_cache, 80 max), segmentation runs in parallel with MiDaS, blur levels cached + built lazily so slider drag / tap-to-focus only rebuilds masks

## Round: automatic cache cleanup
- NEW core/storage/CacheCleaner.kt, called from MainActivity.onCreate (background thread, max once/12h): temp >1 day, stale AirShare .part/.tmp >2 days, depth maps unused 30 days or >60 MB, thumbnails >200 MB (oldest first). Never touches vault/database/backups.
- Settings > Storage: Cache row (size + Clear button) using CacheCleaner

## Round: share / receive animations with style choice
- NEW ui/components/ShareMagicOverlay.kt: full-screen SEND (card lifts, flies away, light wave) and RECEIVE (wave arrives, photo lands and settles) in a transparent dialog window; 4 looks; haptic; tap to skip; reduced motion -> Minimal
- NEW core/storage/ShareAnimStyle.kt (AURORA, LIQUID, ARCANE, MINIMAL) + UserSettings.shareAnimStyle + PreferencesManager key/setter + GalleryViewModel.setShareAnimStyle
- Settings: "Share animation" card with style chips and Preview send / Preview receive
- AirShareScreen: triggers on first outgoing TRANSFERRING / incoming COMPLETED; a batch plays once, 4 s cooldown
- MagicProcessing: ring() and MagicBase now internal for reuse

## Round: journey share animations (rider / paper plane / rocket / drone)
- NEW ui/components/ShareJourneyOverlay.kt: parallax scene that plays for the whole real transfer; progress = real bytes; arrival finale (check badge, ring, confetti); tap/Back hides, transfer continues
- ShareAnimStyle += SCOOTER ("Delivery rider"), PAPER_PLANE, ROCKET, DRONE (isJourney)
- AirShareScreen: journey tracking (members, progress, done/success); one-shot overlay disabled for journey styles; reduced motion falls back to Minimal
- Settings preview plays a simulated 3.6 s journey for these styles

## Round: particle glow share animations
- ShareAnimStyle: new STREAM ("Particle glow", default) and ORB ("Orb glow"); old styles kept as themes.
- ShareJourneyOverlay.kt: sceneGlow (frosted phones, gradient bloom, additive particles/orb, progress hairline, check finale) driven by real transfer progress.

## Round: live pinch zoom
- GalleryGestures.kt: rememberLivePinchZoom (grid scales live with fingers, steps columns/day-month-year at thresholds, spring back on release, haptic tick, never consumes events).
- HomeGalleryScreen: timeline grid uses it instead of the one-shot pinchToChangeZoom.

## Round: split depth model
- assets/midas.tflite replaced by midas_part0..2.bin (23 MB each, under AI Studio's 30 MB limit); DepthEstimator joins them at load (still accepts a single midas.tflite).

## Round: pinch + viewer drag fixes
- Pinch now moves only photo tiles and section titles (pinchCell per cell about the pinch point); top bar, filters and nav stay still. (GalleryGestures.kt LivePinchState/pinchCell, MediaGridComponents.kt, HomeGalleryScreen.kt)
- Viewer: a `grabbing` flag blocks swipe-up-info / swipe-down-dismiss while a long-press drag-to-share is active, so the info sheet no longer opens and the drag reaches the capsules. (MediaViewerPages.kt, image and video pages)
- Nav: Albums tab icon PhotoAlbum (was Collections, looked identical to Gallery's PhotoLibrary)
- build.gradle.kts: abiFilters arm64-v8a only + META-INF excludes (APK size)

## Edge fix, text depth, birthdays, text search
- Depth blur: guided-filter edge refinement + person mask (depth cache moved to depth_cache2).
- Editor: "Text depth" tool puts text behind the subject (size, move, toggle per text layer).
- Birthdays (Settings > Birthdays): name + date, notifications on the day (once / hourly / 30 / 15 / every minute, time window, day-before, Done button, max 60/day), cake marks on the calendar. DB v2 (manual migration). Needs notification + "Alarms & reminders" permissions.
- Search text in photos (Settings > Offline intelligence): ML Kit Latin OCR with pause/stop, results appear in normal Search. Dependency com.google.mlkit:text-recognition:16.0.1.
- build.gradle.kts: arm64-v8a only.
