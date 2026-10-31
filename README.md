---  
You:  
rewrite the readme in the codeblock
Agent:  
```markdown
# ImageGallery - Optimized Image Loader (`loader/optimized`)

Android Image Gallery app implementing an optimized custom image loading strategy using Kotlin Coroutines, dual-level caching, downsampling, and job cancellation.

---

## Branch Info

- **Branch**: `loader/optimized`
- **Strategy**: `OptimizedImageLoader`
- **Key Features**:
  - **Concurrency**: Kotlin Coroutines (`Dispatchers.IO` for background IO, `Dispatchers.Main` for UI updates).
  - **Memory Cache**: In-memory `LruCache<String, Bitmap>` (configured to 1/8th max device memory).
  - **Disk Cache**: `DiskLruCache` (100 MB storage capacity).
  - **Downsampling**: Adaptive `inSampleSize` decoding via `BitmapFactory.Options` to reduce bitmap memory footprint.
  - **Job Cancellation**: Active `Job` cancellation when RecyclerView items are recycled or views are destroyed.
  - **Benchmarking**: Real-time Logcat performance and cache hit rate tracking under `ImageLoaderBenchmark` tag.

---

## How to Build

### Option 1: Using Terminal
- **Windows (PowerShell / CMD)**:
  ```powershell
  .\gradlew.bat app:assembleDebug
  ```
- **macOS / Linux**:
  ```bash
  ./gradlew app:assembleDebug
  ```

### Option 2: Using Android Studio GUI
1. Go to top menu: **Build** > **Build Bundle(s) / APK(s)** > **Build APK(s)**.
2. Locate the output APK at `app/build/outputs/apk/debug/app-debug.apk`.

---

## Benchmark Test Scenarios

| ID | Test Scenario | Expected Observations / Metrics |
|---|---|---|
| **TC01** | Initial list load (Cold start) | Display time, load time, and memory usage. |
| **TC02** | Reloading list with valid cache (Warm start) | Display time compared to initial load. |
| **TC03** | Rapid scrolling through multiple images | Number of dropped frames (jank), incorrect image rendering, or redundant reloads. |
| **TC04** | Testing with high-resolution images | Memory usage, decoding time, and overall scroll smoothness. |
| **TC05** | Disconnecting network during list view (Flight mode) | Verification if cached images remain visible; handling of uncached image requests. |
| **TC06** | Testing with invalid image URLs or slow server response | UI thread responsiveness and proper error handling (e.g., error placeholders). |
| **TC07** | Scaling dataset from 100 to 500 and 1,000 images | Growth rate of load time, memory consumption, and UI latency. |
| **TC08** | Comparative analysis (loader/naive vs loader/optimized) | Degree of improvement for each metric and any associated trade-offs. |

---

## How to Perform a Benchmark

1. **Filter Logcat Tag**: Open Logcat in Android Studio and set filter to:
   ```text
   tag:ImageLoaderBenchmark
   ```
2. **Run Test Cases**:
   - **Cold Start (TC01)**: Clear app data/cache and launch the app. Logcat logs `[NETWORK]` fetch duration.
   - **Warm Start (TC02)**: Re-open the app or scroll back. Logcat logs `[DISK_CACHE]` (~20-40ms) and `[MEMORY_CACHE]` (~1-2ms) hits.
   - **Rapid Scrolling (TC03)**: Fling the grid quickly. Observe `Cancelled image request` logs as off-screen jobs are aborted.
   - **High-Res Detail (TC04)**: Tap any image to view full-screen high-resolution detail.
   - **Flight Mode (TC05)**: Turn off network connections. Previously cached images continue to load instantly from disk/memory cache.
   - **Error Handling (TC06)**: Pass invalid URLs or test timeout responses to verify fallback error drawables.
   - **Dataset Scaling (TC07)**: Select 100, 500, or 1000 images from the top menu to view memory & overall cache hit summary log.
   - **Comparative Analysis (TC08)**: Compare latency and RAM usage logs against the `loader/naive` branch output.
```
