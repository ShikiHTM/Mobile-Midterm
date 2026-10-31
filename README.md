# ImageGallery - Image Loader Benchmark

Simple Android Image Gallery app to benchmark custom image loading strategies.

## Branches

- **loader/optimized**: Coroutines + Memory Cache (LruCache) + Disk Cache (DiskLruCache) + Downsampling + Job Cancellation + Logcat Benchmarking.
- **loader/naive**: Traditional Java Threads (ThreadPoolExecutor) + Direct Network Download (No cache, no job cancellation).

---

## How to Build

```bash
# Switch to desired branch
git checkout loader/optimized

# Build and install on connected device / emulator
./gradlew installDebug
# Windows PowerShell: .\gradlew installDebug
```

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

1. **Filter Logcat Tag**: Open Logcat in Android Studio and filter by:
   ```text
   tag:ImageLoaderBenchmark
   ```
2. **Run Scenarios**:
   - **Cold Start (TC01)**: Clear app cache/data and launch the app.
   - **Warm Start (TC02)**: Re-open the app or scroll back to cached items.
   - **Rapid Scrolling (TC03)**: Fling list quickly to observe active job cancellation (`Cancelled image request`).
   - **Dataset Scaling (TC07)**: Select 100, 500, or 1000 images from the top menu to view memory & load time logs.
   - **Flight Mode (TC05)**: Turn off Wi-Fi/Cellular to test offline cache performance.
