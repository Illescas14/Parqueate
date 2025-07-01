package com.example.parqueate

import ImagePagerAdapter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class FullScreenImageFragment : Fragment() {

    private lateinit var viewPager: ViewPager2
    private lateinit var tabLayout: TabLayout
    private var imageUrls: List<String> = emptyList()
    private var initialPosition: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        imageUrls = arguments?.getStringArrayList("images") ?: emptyList()
        initialPosition = arguments?.getInt("position") ?: 0
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_full_screen_image, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewPager = view.findViewById(R.id.fullScreenViewPager)
        tabLayout = view.findViewById(R.id.fullScreenTabDots)
        val buttonClose: View = view.findViewById(R.id.buttonClose)

        val adapter = ImagePagerAdapter(imageUrls)
        viewPager.adapter = adapter
        viewPager.setCurrentItem(initialPosition, false)

        TabLayoutMediator(tabLayout, viewPager) { _, _ -> }.attach()

        buttonClose.setOnClickListener {
            parentFragmentManager.popBackStack() // Esto regresa al fragmento anterior
        }
    }

}
