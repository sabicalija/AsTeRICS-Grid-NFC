package com.example.asterics_grid_nfc

import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    private var nfcAdapter: NfcAdapter? = null
    private lateinit var editTextWrite: EditText
    private lateinit var buttonWrite: Button
    private lateinit var textViewRead: TextView
    private lateinit var textViewReadTimestamp: TextView
    private var currentTag: Tag? = null

    private lateinit var pendingIntent: PendingIntent
    private var intentFiltersArray: Array<IntentFilter>? = null
    private var techListsArray: Array<Array<String>>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setupForegroundDispatch()
        findViews()
        initNFCAdapter()
        registerListeners()
    }

    private fun setupForegroundDispatch() {
        val intent = Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_MUTABLE)
    }

    private fun findViews() {
        editTextWrite = findViewById(R.id.edit_text_write)
        buttonWrite = findViewById(R.id.button_write)
        textViewRead = findViewById(R.id.text_view_read)
        textViewReadTimestamp = findViewById(R.id.text_view_read_timestamp)
    }

    private fun initNFCAdapter() {
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        if (nfcAdapter == null) {
            Toast.makeText(this, "This device does not support NFC", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun registerListeners() {
        buttonWrite.isEnabled = false
        buttonWrite.setOnClickListener {
            currentTag?.let { tag ->
                val textToWrite = editTextWrite.text.toString()
                writeNfcTag(tag, textToWrite)
                buttonWrite.isEnabled = false
            } ?: Toast.makeText(this, "No NFC Tag detected!", Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        nfcAdapter?.enableForegroundDispatch(this, pendingIntent, intentFiltersArray, techListsArray)
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableForegroundDispatch(this)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        currentTag = intent?.getParcelableExtra(NfcAdapter.EXTRA_TAG)
        when (intent?.action) {
            NfcAdapter.ACTION_NDEF_DISCOVERED -> {
                readFromNdef(intent)
                buttonWrite.isEnabled = true
            }
            NfcAdapter.ACTION_TECH_DISCOVERED, NfcAdapter.ACTION_TAG_DISCOVERED -> {
                buttonWrite.isEnabled = true
            }
        }
    }

    private fun readFromNdef(intent: Intent) {
        val ndefMessages = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
        if (!ndefMessages.isNullOrEmpty()) {
            val message = ndefMessages[0] as NdefMessage
            val records = message.records
            if (records.isNotEmpty()) {
                val recordPayload = records[0].payload
                val textEncoding = if ((recordPayload[0].toInt() and 128) == 0) "UTF-8" else "UTF-16"
                val languageCodeLength = recordPayload[0].toInt() and 63
                val text = String(recordPayload, languageCodeLength + 1, recordPayload.size - languageCodeLength - 1, charset(textEncoding))

                // Update UI with read data
                runOnUiThread {
                    textViewRead.text = text
                    textViewReadTimestamp.text = "Last read: ${getCurrentTimestamp()}"
                }
            }
        } else {
            Toast.makeText(this, "No NDEF messages found!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun writeNfcTag(tag: Tag, text: String) {
        if (text.isEmpty()) {
            Toast.makeText(this, "Input field is empty", Toast.LENGTH_LONG).show()
            return
        }
        val ndefRecord = NdefRecord.createTextRecord("en", text)
        val ndefMessage = NdefMessage(arrayOf(ndefRecord))
        try {
            val ndef = Ndef.get(tag)
            ndef.connect()
            if (ndef.isWritable) {
                ndef.writeNdefMessage(ndefMessage)
                Toast.makeText(this, "Tag written successfully!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Tag is read-only.", Toast.LENGTH_SHORT).show()
            }
            ndef.close()
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to write tag", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getCurrentTimestamp(): String {
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        return sdf.format(Date())
    }
}