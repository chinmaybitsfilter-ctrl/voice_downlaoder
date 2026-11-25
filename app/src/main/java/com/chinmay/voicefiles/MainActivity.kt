package com.chinmay.voicefiles

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.text.format.DateUtils
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.snackbar.Snackbar
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    companion object {
        const val ACTION_VOICE = "com.chinmay.voicefiles.VOICE_OPEN"
        private const val PREF_AUTO_LISTEN = "auto_listen"
    }

    private lateinit var root: View
    private lateinit var toolbar: MaterialToolbar
    private lateinit var search: EditText
    private lateinit var chips: ChipGroup
    private lateinit var statusBox: View
    private lateinit var status: TextView
    private lateinit var grantButton: Button
    private lateinit var heardText: TextView
    private lateinit var micFab: ExtendedFloatingActionButton
    private lateinit var adapter: FileAdapter

    private var allFiles: List<FileItem> = emptyList()
    private var filesLoaded = false
    private var loading = false
    private var listenAfterLoad = false
    private var pendingSpeech: List<String>? = null
    private var permissionDeniedOnce = false

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private val io = Executors.newSingleThreadExecutor()

    private val prefs by lazy { getSharedPreferences("settings", MODE_PRIVATE) }

    // ---------------- launchers ----------------

    private val legacyPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) loadFiles() else {
                permissionDeniedOnce = true
                showNeedAccess()
            }
        }

    private val speechLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
            if (res.resultCode != RESULT_OK) return@registerForActivityResult
            val results = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS).orEmpty()
            if (results.isEmpty()) return@registerForActivityResult
            if (filesLoaded) handleSpeech(results) else {
                pendingSpeech = results           // handled as soon as the scan finishes
                loadFiles()
            }
        }

    // ---------------- lifecycle ----------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        root = findViewById(R.id.root)
        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        search = findViewById(R.id.search)
        chips = findViewById(R.id.chips)
        statusBox = findViewById(R.id.statusBox)
        status = findViewById(R.id.status)
        grantButton = findViewById(R.id.grantButton)
        heardText = findViewById(R.id.heardText)
        micFab = findViewById(R.id.micFab)

        adapter = FileAdapter(
            onOpen = { FileOpener.open(this, it) },
            onLongPress = { FileOpener.share(this, it) },
        )
        findViewById<RecyclerView>(R.id.list).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
        }

        search.doAfterTextChanged { applyFilter() }
        chips.setOnCheckedStateChangeListener { _, _ -> applyFilter() }
        micFab.setOnClickListener { startListening() }
        grantButton.setOnClickListener { requestAccess() }

        tts = TextToSpeech(this, this)

        listenAfterLoad = intent?.action == ACTION_VOICE ||
            (savedInstanceState == null && prefs.getBoolean(PREF_AUTO_LISTEN, false))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == ACTION_VOICE) startListening()
    }

    override fun onResume() {
        super.onResume()
        // Re-scan every time we come back: new downloads, or access just granted in Settings.
        if (hasAccess()) loadFiles() else showNeedAccess()
    }

    override fun onDestroy() {
        tts?.shutdown()
        io.shutdown()
        super.onDestroy()
    }

    // ---------------- menu ----------------

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        menu.findItem(R.id.action_auto_listen).isChecked = prefs.getBoolean(PREF_AUTO_LISTEN, false)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_refresh -> { loadFiles(); true }
        R.id.action_auto_listen -> {
            item.isChecked = !item.isChecked
            prefs.edit().putBoolean(PREF_AUTO_LISTEN, item.isChecked).apply()
            true
        }
        R.id.action_help -> {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.help_title)
                .setMessage(R.string.help_body)
                .setPositiveButton(android.R.string.ok, null)
                .show()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    // ---------------- storage access ----------------

    private fun hasAccess(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager()
        else ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED

    private fun requestAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.access_title)
                .setMessage(R.string.access_body)
                .setPositiveButton(R.string.continue_) { _, _ -> openAllFilesSettings() }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        } else if (permissionDeniedOnce &&
            !shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE)
        ) {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
            )
        } else {
            legacyPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    private fun openAllFilesSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        try {
            startActivity(
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName"))
            )
        } catch (e: ActivityNotFoundException) {
            startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
        }
    }

    private fun showNeedAccess() {
        adapter.submit(emptyList())
        toolbar.subtitle = null
        status.setText(R.string.need_access)
        grantButton.visibility = View.VISIBLE
        statusBox.visibility = View.VISIBLE
    }

    // ---------------- file list ----------------

    private fun loadFiles() {
        if (!hasAccess() || loading) return
        loading = true
        if (!filesLoaded) showStatus(getString(R.string.loading))
        io.execute {
            val list = runCatching { FileRepository.scan() }.getOrElse { emptyList() }
            runOnUiThread {
                loading = false
                if (isFinishing || isDestroyed) return@runOnUiThread
                allFiles = list
                filesLoaded = true
                applyFilter()
                pendingSpeech?.let {
                    pendingSpeech = null
                    handleSpeech(it)
                }
                if (listenAfterLoad) {
                    listenAfterLoad = false
                    startListening()
                }
            }
        }
    }

    private fun selectedKinds(): Set<FileKind>? = when (chips.checkedChipId) {
        R.id.chip_pdf -> setOf(FileKind.PDF)
        R.id.chip_images -> setOf(FileKind.IMAGE)
        R.id.chip_videos -> setOf(FileKind.VIDEO)
        R.id.chip_audio -> setOf(FileKind.AUDIO)
        R.id.chip_docs -> setOf(FileKind.DOC, FileKind.SHEET, FileKind.SLIDES)
        R.id.chip_apk -> setOf(FileKind.APK)
        R.id.chip_other -> setOf(FileKind.ARCHIVE, FileKind.OTHER)
        else -> null
    }

    private fun applyFilter() {
        if (!filesLoaded) return
        val q = search.text?.toString()?.trim().orEmpty()
        val kinds = selectedKinds()
        var list = allFiles
        if (kinds != null) list = list.filter { it.kind in kinds }
        if (q.isNotEmpty()) {
            val terms = TextNorm.tokenize(q)
            list = list.filter { it.name.contains(q, ignoreCase = true) || VoiceCommand.score(terms, it) >= 0.6 }
        }
        adapter.submit(list)
        toolbar.subtitle = resources.getQuantityString(R.plurals.file_count, allFiles.size, allFiles.size)
        grantButton.visibility = View.GONE
        when {
            allFiles.isEmpty() -> showStatus(getString(R.string.no_files))
            list.isEmpty() -> showStatus(getString(R.string.no_matches, q.ifEmpty { "…" }))
            else -> statusBox.visibility = View.GONE
        }
    }

    private fun showStatus(text: String) {
        status.text = text
        grantButton.visibility = View.GONE
        statusBox.visibility = View.VISIBLE
    }

    // ---------------- voice assistant ----------------

    private fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.say_prompt))
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
        }
        try {
            speechLauncher.launch(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.no_recognizer, Toast.LENGTH_LONG).show()
        }
    }

    private fun handleSpeech(results: List<String>) {
        heardText.text = getString(R.string.heard, results.first())
        heardText.visibility = View.VISIBLE

        if (!hasAccess()) {
            speak(getString(R.string.need_access_spoken))
            requestAccess()
            return
        }

        val r = VoiceCommand.resolve(results, allFiles)
        when (val m = r.match) {
            is Match.Open -> act(r.query.action, m.item)
            is Match.Choose -> choose(r.query.action, m.items)
            Match.Empty -> {
                speak(getString(R.string.say_name))
                retrySnackbar(getString(R.string.say_name))
            }
            Match.None -> {
                speak(getString(R.string.not_found_spoken))
                retrySnackbar(getString(R.string.not_found, r.heard))
            }
        }
    }

    private fun act(action: Action, item: FileItem) {
        when (action) {
            Action.OPEN -> {
                speak(getString(R.string.opening_spoken, item.spokenName))
                Snackbar.make(root, getString(R.string.opening_bar, item.name), Snackbar.LENGTH_SHORT)
                    .setAnchorView(micFab).show()
                FileOpener.open(this, item)
            }
            Action.SHARE -> {
                speak(getString(R.string.sharing_spoken, item.spokenName))
                FileOpener.share(this, item)
            }
        }
    }

    private fun choose(action: Action, items: List<FileItem>) {
        speak(getString(R.string.found_n_spoken, items.size))
        val now = System.currentTimeMillis()
        val labels = items.map { f ->
            val age = DateUtils.getRelativeTimeSpanString(
                f.modified, now, DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE
            )
            "${f.name}\n$age"
        }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.which_one)
            .setItems(labels) { _, i -> act(action, items[i]) }
            .setNeutralButton(R.string.try_again) { _, _ -> startListening() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun retrySnackbar(msg: String) {
        Snackbar.make(root, msg, Snackbar.LENGTH_LONG)
            .setAnchorView(micFab)
            .setAction(R.string.try_again) { startListening() }
            .show()
    }

    // ---------------- text-to-speech ----------------

    override fun onInit(statusCode: Int) {
        ttsReady = statusCode == TextToSpeech.SUCCESS
        if (ttsReady) {
            val res = tts?.setLanguage(Locale.getDefault())
            if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.setLanguage(Locale.US)
            }
        }
    }

    private fun speak(text: String) {
        if (ttsReady) tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "voicefiles")
    }
}
