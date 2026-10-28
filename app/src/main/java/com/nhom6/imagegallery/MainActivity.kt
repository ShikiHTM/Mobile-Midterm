package com.nhom6.imagegallery

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.google.android.material.appbar.MaterialToolbar

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, GalleryFragment())
                .commit()
        }

        supportFragmentManager.addOnBackStackChangedListener {
            val isDetail: Boolean = supportFragmentManager.backStackEntryCount > 0
            val toolbar: MaterialToolbar = findViewById(R.id.tb_gallery)

            if (isDetail) {
                toolbar.title = getString(R.string.detail_title)
                toolbar.setNavigationIcon(android.R.drawable.ic_menu_revert)
                toolbar.setNavigationOnClickListener {
                    supportFragmentManager.popBackStack()
                }
            } else {
                toolbar.title = getString(R.string.gallery_title)
                toolbar.navigationIcon = null
            }
        }
    }

    fun navigateToDetail(imageUrl: String) {
        val detailFragment: Fragment = DetailFragment.newInstance(imageUrl)

        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, detailFragment)
            .addToBackStack(null)
            .commit()
    }
}