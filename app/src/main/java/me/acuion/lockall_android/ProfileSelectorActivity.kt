package me.acuion.lockall_android

import android.os.Bundle
import android.app.Activity
import android.view.View.*

import kotlinx.android.synthetic.main.activity_profile_selector.*
import android.widget.ArrayAdapter
import android.content.Intent
import android.widget.AdapterView.OnItemClickListener
import android.app.AlertDialog
import android.content.DialogInterface
import android.text.InputType
import android.widget.EditText
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ProfileSelectorActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile_selector)

        val allowProfileCreation = intent.getBooleanExtra("allowProfileCreation",
                false)
        val resourceName = intent.getStringExtra("resourceName")!!
        val accountsList = intent.getStringArrayExtra("accountsList")!!

        val arrayAdapter = ArrayAdapter<String>(
                this,
                android.R.layout.simple_list_item_1,
                accountsList)
        accountsListView.adapter = arrayAdapter

        textView.text = resources.getString(R.string.accs_available_header, resourceName)
        addProfileButton.visibility = if (allowProfileCreation) VISIBLE else GONE

        accountsListView.onItemClickListener = OnItemClickListener { parent, view, position, id ->
            val resultIntent = Intent()
            resultIntent.putExtra("profile", accountsList[id.toInt()])
            setResult(RESULT_OK, resultIntent)
            finish()
        }

        addProfileButton.setOnClickListener {
            val builder = AlertDialog.Builder(this)
            builder.setTitle("New profile")

            val input = EditText(this)
            input.inputType = InputType.TYPE_CLASS_TEXT
            builder.setView(input)

            builder.setPositiveButton("Add", DialogInterface.OnClickListener { dialog, which ->
                run {
                    val resultIntent = Intent()
                    resultIntent.putExtra("profile", input.text.toString())
                    setResult(RESULT_OK, resultIntent)
                    finish()
                }
            })
            builder.setNegativeButton("Cancel", DialogInterface.OnClickListener { dialog, which -> dialog.cancel() })

            builder.show()
        }

        GlobalScope.launch {
            for (i in 1..60) {
                runOnUiThread {
                    progressBar.incrementProgressBy(-1)
                }
                delay(500)
            }
            setResult(RESULT_CANCELED)
            finish()
        }
    }

}
