package me.acuion.lockall_android

import android.app.Activity
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.v4.view.PagerAdapter
import android.support.v4.view.ViewPager
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.ImageView
import kotlinx.android.synthetic.main.activity_onboarding.*


class OnboardingActivity : Activity() {

    private fun pageSelected(num: Int) {
        progressBar.setProgress((((num + 1) / 3.0) * 100).toInt(), true)
        backBtn.visibility = View.VISIBLE
        nextBtn.text = "NEXT"
        if (num == 0) {
            backBtn.visibility = View.INVISIBLE
        }
        if (num == 2) {
            nextBtn.text = "DONE"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.requestFeature(Window.FEATURE_ACTION_BAR)
        actionBar.hide()
        setContentView(R.layout.activity_onboarding)

        pageSelected(0)

        nextBtn.setOnClickListener {
            if (introPager.currentItem == 2) {
                finish()
            }
            introPager.setCurrentItem(introPager.currentItem + 1, true)
        }

        backBtn.setOnClickListener {
            introPager.setCurrentItem(introPager.currentItem - 1, true)
        }

        introPager.addOnPageChangeListener(object: ViewPager.OnPageChangeListener {
            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
            }

            override fun onPageSelected(position: Int) {
                pageSelected(position)
            }

            override fun onPageScrollStateChanged(state: Int) {
            }

        })
        introPager.adapter = object : PagerAdapter() {
            override fun isViewFromObject(p0: View, p1: Any): Boolean {
                return p0 == p1
            }

            override fun getCount(): Int {
                return 3
            }

            override fun instantiateItem(container: ViewGroup, position: Int): View {
                val imageView = ImageView(this@OnboardingActivity)

                when(position) {
                    0 -> {
                        imageView.setImageResource(R.drawable.get)
                    }
                    1 -> {
                        imageView.setImageResource(R.drawable.store)
                    }
                    2 -> {
                        imageView.setImageResource(R.drawable.otp)
                    }
                }

                (container as ViewPager).addView(imageView, 0)
                return imageView
            }

            override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
                (container as ViewPager).removeView(`object` as View)
            }
        }

    }
}