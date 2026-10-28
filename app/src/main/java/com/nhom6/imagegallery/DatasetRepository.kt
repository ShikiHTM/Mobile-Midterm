package com.nhom6.imagegallery

object DatasetRepository {

    enum class TestScale(val count: Int) {
        SMALL(100),
        MEDIUM(500),
        LARGE(1000)
    }

    fun getUrls(scale: TestScale = TestScale.SMALL): List<String> {
        return (1..scale.count).map { index ->
            "https://picsum.photos/seed/$index/800/800"
        }
    }
}