package com.kharagedition.englishtibetandictionary.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.view.size
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.navigation.fragment.findNavController
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textview.MaterialTextView
import com.kharagedition.englishtibetandictionary.R
import com.kharagedition.englishtibetandictionary.util.BottomSheetDialog
import com.kharagedition.englishtibetandictionary.viewmodel.WordsViewModel


class HomeFragment : Fragment() {
    lateinit var topAnimation : Animation;
    lateinit var layout: LinearLayout;
    lateinit var settingCardView: MaterialCardView;
    private lateinit var favouriteCardView: MaterialCardView;
    lateinit var dictionrayCardView: MaterialCardView;
    private lateinit var settingIcon: ImageView;
    lateinit var favouriteIcon: ImageView;
    lateinit var dictionaryIcon: ImageView;
    private lateinit var rotation: Animation;
    private lateinit var pulseAnimation: ObjectAnimator;
    private lateinit var flipFromAnimation: ObjectAnimator;
    private lateinit var flipToAnimation: ObjectAnimator;
    lateinit var wodTibetan:MaterialTextView;
    lateinit var wodEnglish:MaterialTextView;
    lateinit var wodGenerateBtn:MaterialButton;
    lateinit var exitAppIcon: ImageView;
    private val wordsViewModel: WordsViewModel by activityViewModels();



    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        var view =  inflater.inflate(R.layout.fragment_home, container, false)

        initView(view);
        initAnimation();

        initListener();
        generateWOD();



        topAnimation = AnimationUtils.loadAnimation(context, R.anim.layout_top_anim)
        layout.startAnimation(topAnimation);
        //rotate anmation
         rotation = AnimationUtils.loadAnimation(context, R.anim.button_rotate);

        return view;
    }

    private fun generateWOD() {
        wordsViewModel.generateWordOfTheDay();
        wordsViewModel.wordOfDay.observe(viewLifecycleOwner, {
            wodEnglish.text = it.english;
            wodTibetan.text = it.defination
        });
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
        flipFromAnimation.duration = 150;
        flipToAnimation.duration = 150;
        flipFromAnimation.interpolator = DecelerateInterpolator()
         flipToAnimation.interpolator = AccelerateDecelerateInterpolator()
    }

    private fun showAlertDialog(layout: Int){
        var adRequest = AdRequest.Builder().build()

        var dialogBuilder = AlertDialog.Builder(context)
        val layoutView = layoutInflater.inflate(layout, null)
        val dialogButton: MaterialButton = layoutView.findViewById(R.id.btnDialog)
        var mAdView:AdView = layoutView.findViewById(R.id.bannerAd2)
        dialogBuilder.setView(layoutView)
        var alertDialog = dialogBuilder.create()
        //alertDialog.window?.setWindowAnimations(R.style.DialogAnimation)
        mAdView.loadAd(adRequest)
        alertDialog.window?.attributes?.windowAnimations = R.style.DialogAnimation;
        alertDialog.show()
        dialogButton.setOnClickListener {
            alertDialog.dismiss()
            requireActivity().finishAffinity();
        }
    }
    private fun initListener() {

        wodGenerateBtn.setOnClickListener {
            generateWOD();
        }

        exitAppIcon.setOnClickListener {
            showAlertDialog(R.layout.dialog_negative_layout);
        };
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
                override fun onAnimationEnd(animation: Animator?) {
                    super.onAnimationEnd(animation)
                    findNavController().navigate(R.id.listFragment)
                }
            })
            //
        }
        // SETTING CARD ONCLICK LISTENER
        settingCardView.setOnClickListener {
            settingIcon.startAnimation(rotation);
            rotation.setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation?) {
                    Log.d("TAG", "onAnimationStart: ")
                }

                override fun onAnimationEnd(animation: Animation?) {
                    val sheet = BottomSheetDialog();
                    sheet.show(requireActivity().supportFragmentManager, "ModalBottomSheet");
                }

                override fun onAnimationRepeat(animation: Animation?) {
                    Log.d("TAG", "onAnimationRepeat: ")
                }
            })
            //findNavController().navigate(R.id.listFragment)

        };
        // FAVOURITE CARD ONCLICK LISTENER
        favouriteCardView.setOnClickListener {
            pulseAnimation.start();
        }
        pulseAnimation.addListener(object : Animator.AnimatorListener {
            override fun onAnimationStart(animator: Animator) {}
            override fun onAnimationEnd(animator: Animator) {
                findNavController().navigate(R.id.listFragment)
            }

            override fun onAnimationCancel(animator: Animator) {}
            override fun onAnimationRepeat(animator: Animator) {}
        })
    }

    private fun initView(view: View) {
        layout = view.findViewById(R.id.linearLayout)
        dictionrayCardView = view.findViewById(R.id.dictionary_card_view)
        favouriteCardView = view.findViewById(R.id.favourite_card_view)
        settingCardView = view.findViewById(R.id.setting_card_view)
        dictionaryIcon = view.findViewById(R.id.dictionary_icon)
        favouriteIcon = view.findViewById(R.id.fav_icon)
        settingIcon = view.findViewById(R.id.icon_settings)
        exitAppIcon = view.findViewById(R.id.exit_app)
        wodEnglish = view.findViewById(R.id.wod_en)
        wodTibetan = view.findViewById(R.id.wod_tb)
        wodGenerateBtn = view.findViewById(R.id.wod_generate_btn)



    }


}