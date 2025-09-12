package com.kharagedition.tibetandictionary.ui

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.firestore.FirebaseFirestore
import com.kharagedition.tibetandictionary.R


class SettingActivity : AppCompatActivity() {
    lateinit var materialToolbar: ImageView
    lateinit var storeButton: MaterialButton
    lateinit var feedbackButton: MaterialButton
        override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setting)
        materialToolbar = findViewById(R.id.setting_backbtn)
        storeButton = findViewById(R.id.store_btn)
        feedbackButton = findViewById(R.id.feedback_btn)
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(
                        R.id.settings_container,
                        SettingsFragment()
                )
                .commit()
        }
        initListener()

    }

    private fun initListener() {
        materialToolbar.setOnClickListener {
            finish()
        }
        feedbackButton.setOnClickListener{
            val textInputLayout = TextInputLayout(this)
            textInputLayout.setPadding(
                    resources.getDimensionPixelOffset(R.dimen.dp_19), // if you look at android alert_dialog.xml, you will see the message textview have margin 14dp and padding 5dp. This is the reason why I use 19 here
                    0,
                    resources.getDimensionPixelOffset(R.dimen.dp_19),
                    0
            )
            val input = EditText(this)
            textInputLayout.addView(input)

            val alert = AlertDialog.Builder(this)
                    .setTitle("Feedback/Message")
                    .setView(textInputLayout)
                    .setMessage("Please leave message or feedback")
                    .setPositiveButton("Submit") { dialog, _ ->
                        val data = mutableMapOf<String, String>()
                        val message =input.text.toString();
                        if(message.isNotEmpty() && message.length>3 ){
                            data["message"]= input.text.toString();
                            FirebaseFirestore.getInstance().collection("Message").add(data).addOnCompleteListener {
                                if (it.isSuccessful) {
                                    Toast.makeText(this, "Feedback sent!", Toast.LENGTH_SHORT).show()
                                    dialog.dismiss()
                                }
                            }.addOnFailureListener{
                                Toast.makeText(this, "Error:" + it.localizedMessage, Toast.LENGTH_SHORT).show()
                                dialog.dismiss()
                            }
                        }


                    }
                    .setNegativeButton("Cancel") { dialog, _ ->
                        dialog.cancel()
                    }.create()
            alert.window?.attributes?.windowAnimations = R.style.DialogAnimation
            alert.show()
        }

    }

}