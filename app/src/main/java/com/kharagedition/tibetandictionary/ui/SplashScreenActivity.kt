package com.kharagedition.tibetandictionary.ui

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.kharagedition.tibetandictionary.R

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import com.kharagedition.tibetandictionary.MainActivity

class SplashScreenActivity : AppCompatActivity() {
    lateinit var topAnimation :Animation 
    lateinit var bottomAnimation: Animation
    lateinit var image: ImageView
    lateinit var version: TextView
    lateinit var textView: TextView
    lateinit var progressBar: ProgressBar
    companion object {
        private var  SECOND : Long = 1500
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash_screen)


        topAnimation = AnimationUtils.loadAnimation(this,R.anim.top_animation)
        bottomAnimation = AnimationUtils.loadAnimation(this,R.anim.bottom_animation)
        image = findViewById(R.id.splash_image)
        textView = findViewById(R.id.splash_text)
        version = findViewById(R.id.version)
        progressBar = findViewById(R.id.splash_progress)

        image.animation =topAnimation
        textView.animation = bottomAnimation
        //progressBar.animation = bottomAnimation
        setVersion()
        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        },SECOND)
    }

    private fun setVersion() {
        try {
            val versionName: String? = this.packageManager
                .getPackageInfo(this.packageName, 0).versionName
            version.text = "Version: $versionName"
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }
    }

}