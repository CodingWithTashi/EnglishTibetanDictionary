package com.kharagedition.tibetandictionary.ui

import android.Manifest
import android.app.Activity
import android.app.Activity.RESULT_OK
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textview.MaterialTextView
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.kharagedition.tibetandictionary.R
import com.kharagedition.tibetandictionary.util.Constant
import com.kharagedition.tibetandictionary.viewmodel.WordsViewModel


class MLSearchFragment : Fragment() {
    companion object{
        const val REQUEST_IMAGE_CAPTURE = 1

    }
    lateinit var backBtn:ImageView;
    lateinit var selectImageView:ImageView;
    lateinit var uploadIconImageVew:LinearLayout;
    lateinit var openCameraButton:MaterialButton;
    lateinit var searchWordBtn:MaterialButton;
    lateinit var englishWordText:MaterialTextView;
    lateinit var meaningTextView:MaterialTextView;
    private var imageUri: Uri? = null;
     private var cameraBitMap: Bitmap? = null;
    lateinit  var mAdView: AdView

    private val wordsViewModel: WordsViewModel by activityViewModels()


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view =  inflater.inflate(R.layout.fragment_m_l_search, container, false)
        backBtn = view.findViewById(R.id.ml_backbtn);
        initVew(view);
        initListener()
        var adRequest = AdRequest.Builder().build()
        loadAdsIfNotPurchased(mAdView, adRequest)
        return  view;
    }

    private fun loadAdsIfNotPurchased(mAdView: AdView, adRequest: AdRequest) {
        val prefs: SharedPreferences? = activity?.getSharedPreferences(
            "com.kharagedition.dictionary",
            Context.MODE_PRIVATE
        )
        val isPurchased = prefs?.getBoolean(Constant.PURCHASED, false)

        if(isPurchased==null || !isPurchased){
            mAdView.loadAd(adRequest)
        }else{
            mAdView.visibility= View.GONE
        }
    }

    private fun initListener() {
        backBtn.setOnClickListener {
            this.findNavController().popBackStack()
        }
        openCameraButton.setOnClickListener {
            val ctx = context;
            if(ctx!=null){
                if (ContextCompat.checkSelfPermission(
                        ctx,
                        Manifest.permission.CAMERA
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    ActivityCompat.requestPermissions(
                        (context as Activity?)!!, arrayOf(Manifest.permission.CAMERA),
                        12
                    )
                }else{
                    dispatchTakePictureIntent();
                }
            }


        }
        uploadIconImageVew.setOnClickListener{
            val photoPickerIntent = Intent(Intent.ACTION_PICK)
            photoPickerIntent.type = "image/*"
            getContent.launch("image/*")
        }
        searchWordBtn.setOnClickListener {
            if(imageUri!=null || cameraBitMap!=null){
                detectTextFromImage();
            }else
            {
                Toast.makeText(context, "Image not selected yet", Toast.LENGTH_SHORT).show()
            }

        }
    }

    private fun detectTextFromImage() {
        val image:InputImage = if(imageUri!=null && context!=null){
            InputImage.fromFilePath(requireContext(), imageUri!!)
        }else{
            InputImage.fromBitmap(cameraBitMap!!, 0)
        }


        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val result = recognizer.process(image).addOnSuccessListener {
            var resultText = it.text
            resultText = resultText.replace("[\\t\\n\\r]+", " ");
            val words = resultText.split(" ")
            if(words.isNotEmpty() && words.size>1){
                val items = words.toTypedArray();
                val ctx = context;
                if(ctx!=null){
                    val builder: AlertDialog.Builder = AlertDialog.Builder(ctx)
                    builder.setTitle("Too many word found, Select one and edit the word")
                    builder.setItems(
                        items
                    ) { dialog, item -> // Do something with the selection
                        //searchForWordMeaning(items[item])
                        dialog.dismiss()
                        showDialogWithInput(items[item])
                    }
                    val alert: AlertDialog = builder.create()
                    alert.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, 400);
                    alert.show()
                }

            }else{
                searchForWordMeaning(words[0])
            }

            Log.e("TAG", "detectTextFromImage: ::: $resultText");
        }.addOnFailureListener{
            Log.e("TAG", "detectTextFromImage: " + it.printStackTrace())
            Toast.makeText(context, "Error:" + it.localizedMessage, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showDialogWithInput(word: String) {
        context.apply {
            val builder = context?.let { AlertDialog.Builder(it) }
            builder?.setTitle("Edit Word")

            val input = EditText(this)
            builder?.setView(input)
            input.setText(word);
            builder?.setPositiveButton("Confirm") { dialog, _ -> searchForWordMeaning(
                input.text.trim().toString()
            );dialog.dismiss() }
            builder?.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }

            builder?.show()
        }

    }

    private fun searchForWordMeaning(word: String) {
        wordsViewModel.getMeaningForMLSearchWord(word);
        wordsViewModel.mlQueryWordList.observe(viewLifecycleOwner, {
            if (it != null && it.isNotEmpty()) {
                val word = it.first()
                englishWordText.text = word.english
                meaningTextView.text = word.defination
            } else {
                Toast.makeText(context, "Oopse, Word not found!", Toast.LENGTH_SHORT).show()
            }

        })
    }

    private val getContent = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if(uri!=null){
            imageUri = uri;
            cameraBitMap = null;
            selectImageView.setImageURI(uri)
            searchWordBtn.isEnabled = true;
        }else{
            Toast.makeText(context, "Image not selected", Toast.LENGTH_SHORT).show()
        }
        
    }

    private fun dispatchTakePictureIntent() {
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        try {
            //startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE)
            startForResult.launch(takePictureIntent)
        } catch (e: ActivityNotFoundException) {
            Log.e("TAG", "dispatchTakePictureIntent: " + e.localizedMessage)
        }
    }
    private val startForResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data
            val imageBitmap = data?.extras?.get("data") as Bitmap
            cameraBitMap = imageBitmap;
            selectImageView.setImageBitmap(imageBitmap)
            imageUri = null;
            searchWordBtn.isEnabled = true;
        }
    }


    private fun initVew(view: View) {
        backBtn = view.findViewById(R.id.ml_backbtn);
        selectImageView = view.findViewById(R.id.select_img);
        uploadIconImageVew = view.findViewById(R.id.upload_icon);
        openCameraButton = view.findViewById(R.id.open_camera);
        searchWordBtn = view.findViewById(R.id.translate);
        englishWordText = view.findViewById(R.id.ml_word_title);
        meaningTextView = view.findViewById(R.id.ml_word_meaning);
        mAdView = view.findViewById(R.id.ml_banner)

    }



}