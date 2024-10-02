package com.kharagedition.tibetandictionary.ui

import android.animation.*
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textview.MaterialTextView
import com.kharagedition.tibetandictionary.R
import com.kharagedition.tibetandictionary.util.BottomSheetDialog
import com.kharagedition.tibetandictionary.util.Constant
import com.kharagedition.tibetandictionary.viewmodel.WordsViewModel


class HomeFragment : Fragment() {
    lateinit var topAnimation : Animation
    lateinit var wodLinearLayout: LinearLayout
    lateinit var settingCardView: MaterialCardView
    lateinit var aboutCardView: MaterialCardView
    private lateinit var favouriteCardView: MaterialCardView
    lateinit var dictionrayCardView: MaterialCardView
    lateinit var mlCardView: MaterialCardView
    private lateinit var settingIcon: ImageView
    private lateinit var aboutIcon: ImageView
    lateinit var favouriteIcon: ImageView
    lateinit var dictionaryIcon: ImageView
    lateinit var mlIcon: ImageView
    private lateinit var rotation: Animation
    private lateinit var shake: Animation
    private lateinit var pulseAnimation: ObjectAnimator
    private lateinit var flipFromAnimation: ObjectAnimator
    private lateinit var flipToAnimation: ObjectAnimator
    lateinit var wodTibetan:MaterialTextView
    lateinit var wodEnglish:MaterialTextView
    lateinit var wodGenerateBtn:MaterialButton
    lateinit var exitAppIcon: ImageView
    lateinit var ownerDictionaryName: MaterialTextView
    private val wordsViewModel: WordsViewModel by activityViewModels()
    var count: Int = 0;



    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        var view =  inflater.inflate(R.layout.fragment_home, container, false)

        initView(view)
        setDefaultValue()
        initAnimation()
        val layoutTransition: LayoutTransition = wodLinearLayout.layoutTransition
        layoutTransition.enableTransitionType(LayoutTransition.CHANGING)
        initListener()
        generateWOD()



        topAnimation = AnimationUtils.loadAnimation(context, R.anim.layout_top_anim)
        wodLinearLayout.startAnimation(topAnimation)
        //rotate anmation
         rotation = AnimationUtils.loadAnimation(context, R.anim.button_rotate)
        //shake animation
        shake = AnimationUtils.loadAnimation(context, R.anim.shake_anim)

        return view
    }

    private fun setDefaultValue() {
        val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val username = prefs.getString("signature", "");
        username.apply {
            ownerDictionaryName.text = " ${username} དབྱིན་བོད་ཚིག་མཛོད་"
        }
    }

    private fun generateWOD() {
        wordsViewModel.generateWordOfTheDay()
        wordsViewModel.wordOfDay.observe(viewLifecycleOwner, {
            wodEnglish.text = it.english
            wodTibetan.text = it.defination
        })
    }

    private fun initAnimation() {
        //pulse animation
        pulseAnimation = ObjectAnimator.ofPropertyValuesHolder(
                favouriteIcon,
                PropertyValuesHolder.ofFloat("scaleX", 1.2f),
                PropertyValuesHolder.ofFloat("scaleY", 1.2f))
        pulseAnimation.duration = 300
        pulseAnimation.interpolator = FastOutSlowInInterpolator()
        pulseAnimation.repeatMode = ObjectAnimator.REVERSE

        //favourite animation
         flipFromAnimation = ObjectAnimator.ofFloat(dictionaryIcon, "scaleX", 1f, 0f)
         flipToAnimation = ObjectAnimator.ofFloat(dictionaryIcon, "scaleX", 0f, 1f)
        flipFromAnimation.duration = 150
        flipToAnimation.duration = 150
        flipFromAnimation.interpolator = DecelerateInterpolator()
         flipToAnimation.interpolator = AccelerateDecelerateInterpolator()
    }

    private fun showAlertDialog(layout: Int){
        var adRequest = AdRequest.Builder().build()

        var dialogBuilder = AlertDialog.Builder(context)
        val layoutView = layoutInflater.inflate(layout, null)
        val dialogButton: MaterialButton = layoutView.findViewById(R.id.btnDialog)
        val sadImage: ImageView = layoutView.findViewById(R.id.sad_image)
        var mAdView:AdView = layoutView.findViewById(R.id.bannerAd2)
        dialogBuilder.setView(layoutView)
        var alertDialog = dialogBuilder.create()
        //alertDialog.window?.setWindowAnimations(R.style.DialogAnimation)
        loadAdsIfNotPurchased(mAdView,adRequest)
        alertDialog.window?.attributes?.windowAnimations = R.style.DialogAnimation
        alertDialog.show()
        dialogButton.setOnClickListener {
            alertDialog.dismiss()
            requireActivity().finishAffinity()
        }
        sadImage.setOnClickListener{
            count++
            if(count==5){
                val prefs = activity?.getSharedPreferences(
                        "com.kharagedition.dictionary", Context.MODE_PRIVATE)?.edit()
                prefs?.putBoolean(Constant.PURCHASED,true)
                prefs?.apply();
                Log.e("TAG", "showAlertDialog: DISABLED ADS", )
            }

        }
    }
    private fun loadAdsIfNotPurchased(mAdView:AdView, adRequest: AdRequest) {
        val prefs: SharedPreferences? = activity?.getSharedPreferences("com.kharagedition.dictionary",Context.MODE_PRIVATE)
        val isPurchased = prefs?.getBoolean(Constant.PURCHASED, false)

        if(isPurchased==null || !isPurchased){
            mAdView.loadAd(adRequest)
        }else{
            mAdView.visibility=GONE
        }
    }
    private fun initListener() {

        wodGenerateBtn.setOnClickListener {
            generateWOD()
        }

        exitAppIcon.setOnClickListener {
            showAlertDialog(R.layout.dialog_negative_layout)
        }
        // DICTIONARY CARD  ONCLICK LISTENER
        dictionrayCardView.setOnClickListener {

            flipFromAnimation.start()

            flipFromAnimation.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    super.onAnimationEnd(animation)
                    dictionaryIcon.setImageResource(R.drawable.ic_baseline_menu_book_24)
                    flipToAnimation.start()
                }
            })
            flipToAnimation.addListener(object : AnimatorListenerAdapter() {

                override fun onAnimationEnd(animation: Animator) {
                    super.onAnimationEnd(animation)
                    val bundle = bundleOf("favourite" to false)
                    findNavController().navigate(R.id.listFragment, bundle)
                }
            })
            //
        }
        // SETTING CARD ONCLICK LISTENER
        settingCardView.setOnClickListener {
            settingIcon.startAnimation(rotation)
            rotation.setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation?) {
                    Log.d("TAG", "onAnimationStart: ")
                }

                override fun onAnimationEnd(animation: Animation?) {
                    startActivity(Intent(activity,SettingActivity::class.java))
                    //activity?.overridePendingTransition(android.R.anim.slide_out_right,android.R.anim.slide_in_left,)
                }

                override fun onAnimationRepeat(animation: Animation?) {
                    Log.d("TAG", "onAnimationRepeat: ")
                }
            })
            //findNavController().navigate(R.id.listFragment)

        }
        // ABOUT CARD ONCLICK LISTENER
        aboutCardView.setOnClickListener {
            aboutIcon.startAnimation(rotation)
            rotation.setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation?) {
                    Log.d("TAG", "onAnimationStart: ")
                }

                override fun onAnimationEnd(animation: Animation?) {
                    val sheet = BottomSheetDialog()
                    sheet.show(requireActivity().supportFragmentManager, "ModalBottomSheet")
                }

                override fun onAnimationRepeat(animation: Animation?) {
                    Log.d("TAG", "onAnimationRepeat: ")
                }
            })
            //findNavController().navigate(R.id.listFragment)

        }

        mlCardView.setOnClickListener {
            mlIcon.startAnimation(shake)
            shake.setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation?) {
                    Log.d("TAG", "onAnimationStart: ")
                }

                override fun onAnimationEnd(animation: Animation?) {
                    findNavController().navigate(R.id.MLSearchFragment)
                }

                override fun onAnimationRepeat(animation: Animation?) {
                    Log.d("TAG", "onAnimationRepeat: ")
                }
            })
        }

        // FAVOURITE CARD ONCLICK LISTENER
        favouriteCardView.setOnClickListener {
            pulseAnimation.start()
        }
        pulseAnimation.addListener(object : Animator.AnimatorListener {
            override fun onAnimationStart(animator: Animator) {}
            override fun onAnimationEnd(animator: Animator) {
                val bundle = bundleOf("favourite" to true)
                findNavController().navigate(R.id.listFragment,bundle)
            }

            override fun onAnimationCancel(animator: Animator) {}
            override fun onAnimationRepeat(animator: Animator) {}
        })
    }

    private fun initView(view: View) {
        dictionrayCardView = view.findViewById(R.id.dictionary_card_view)
        favouriteCardView = view.findViewById(R.id.favourite_card_view)
        settingCardView = view.findViewById(R.id.setting_card_view)
        aboutCardView = view.findViewById(R.id.about_card_view)
        dictionaryIcon = view.findViewById(R.id.dictionary_icon)
        favouriteIcon = view.findViewById(R.id.fav_icon)
        settingIcon = view.findViewById(R.id.icon_setting)
        aboutIcon = view.findViewById(R.id.icon_about)
        exitAppIcon = view.findViewById(R.id.exit_app)
        wodEnglish = view.findViewById(R.id.wod_en)
        wodTibetan = view.findViewById(R.id.wod_tb)
        wodGenerateBtn = view.findViewById(R.id.wod_generate_btn)
        wodLinearLayout = view.findViewById(R.id.wodLinearLayout)
        ownerDictionaryName = view.findViewById(R.id.owner_dictionary)
        mlIcon = view.findViewById(R.id.machine_icon)
        mlCardView = view.findViewById(R.id.ml_card_view)




    }


}