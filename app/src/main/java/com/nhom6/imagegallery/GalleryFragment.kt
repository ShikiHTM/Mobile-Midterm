package com.nhom6.imagegallery

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

class GalleryFragment: Fragment() {

    private lateinit var adapter: PhotoAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_gallery, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val rvGallery: RecyclerView = view.findViewById(R.id.rv_gallery)
        rvGallery.layoutManager = GridLayoutManager(requireContext(), 3)

        val initialUrls: List<String> = DatasetRepository.getUrls(DatasetRepository.TestScale.LARGE)
        android.util.Log.d("DEBUG_GALLERY", "onViewCreated running! Dataset size: $initialUrls")

        val strategy: ImageLoaderStrategy = OptimizedImageLoader(view.context)

        adapter = PhotoAdapter(initialUrls, strategy) { url ->
            (requireActivity() as? MainActivity)?.navigateToDetail(url)
        }
        rvGallery.adapter = adapter
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (::adapter.isInitialized) {
            adapter.imageLoader.cancelAllRequests()
        }
    }

    fun changeTestScale(scale: DatasetRepository.TestScale) {
        adapter.imageLoader.logBenchmarkSummary()
        adapter.imageLoader.resetBenchmark()
        val newUrls: List<String> = DatasetRepository.getUrls(scale)
        adapter.updateData(newUrls)
    }
}