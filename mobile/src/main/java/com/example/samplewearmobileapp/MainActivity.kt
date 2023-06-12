package com.example.samplewearmobileapp

import android.Manifest
import android.bluetooth.BluetoothAdapter.*
import android.bluetooth.BluetoothManager
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.*
import android.preference.PreferenceManager
import android.provider.DocumentsContract
import android.text.InputType
import android.util.Log
import android.view.*
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.androidplot.xy.XYPlot
import com.example.samplewearmobileapp.BluetoothService.REQUEST_CODE_ENABLE_BLUETOOTH
import com.example.samplewearmobileapp.Constants.ECG_SAMPLE_RATE
import com.example.samplewearmobileapp.Constants.MS_TO_SEC
import com.example.samplewearmobileapp.Constants.PPG_GREEN_SAMPLE_RATE
import com.example.samplewearmobileapp.Constants.PPG_IR_RED_SAMPLE_RATE
import com.example.samplewearmobileapp.Constants.PREF_ANALYSIS_VISIBILITY
import com.example.samplewearmobileapp.Constants.PREF_DEVICE_ID
import com.example.samplewearmobileapp.Constants.PREF_ECG_VISIBILITY
import com.example.samplewearmobileapp.Constants.PREF_PATIENT_NAME
import com.example.samplewearmobileapp.Constants.PREF_PPG_GREEN_VISIBILITY
import com.example.samplewearmobileapp.Constants.PREF_PPG_IR_VISIBILITY
import com.example.samplewearmobileapp.Constants.PREF_PPG_RED_VISIBILITY
import com.example.samplewearmobileapp.Constants.PREF_TREE_URI
import com.example.samplewearmobileapp.EcgImager.createImage
import com.example.samplewearmobileapp.constants.Entity.PHONE_APP
import com.example.samplewearmobileapp.constants.MessagePath
import com.example.samplewearmobileapp.constants.codes.ActivityCode
import com.example.samplewearmobileapp.constants.codes.ExtraCode.TOGGLE_ACTIVITY
import com.example.samplewearmobileapp.databinding.ActivityMainBinding
import com.example.samplewearmobileapp.models.*
import com.example.samplewearmobileapp.models.Message
import com.example.samplewearmobileapp.utils.AppUtils
import com.example.samplewearmobileapp.utils.UriUtils
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import com.google.gson.Gson
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.PolarBleApi.DeviceStreamingFeature
import com.polar.sdk.api.PolarBleApiCallback
import com.polar.sdk.api.PolarBleApiDefaultImpl.defaultImplementation
import com.polar.sdk.api.PolarBleApiDefaultImpl.versionInfo
import com.polar.sdk.api.errors.PolarInvalidArgument
import com.polar.sdk.api.model.PolarDeviceInfo
import com.polar.sdk.api.model.PolarEcgData
import com.polar.sdk.api.model.PolarHrData
import com.polar.sdk.api.model.PolarSensorSetting
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
import java.io.FileOutputStream
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToLong
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity(), GoogleApiClient.ConnectionCallbacks,
    SharedPreferences.OnSharedPreferenceChangeListener, SectionsAdapter.SectionEvents {
    private lateinit var binding: ActivityMainBinding
    private lateinit var client: GoogleApiClient
    private lateinit var menu: Menu

    private lateinit var viewModel: MainViewModel
    private lateinit var sectionsAdapter: SectionsAdapter

    var ppgGreenPlotter: PpgPlotter? = null
    var ppgIrPlotter: PpgPlotter? = null
    var ppgRedPlotter: PpgPlotter? = null

//    private lateinit var acquaintedDevices: MutableList<DeviceInfo>
    private var polarApi: PolarBleApi? = null
    private var ecgDisposable: Disposable? = null
    var ecgPlotter: EcgPlotter? = null
    private var qrsDetector: QrsDetector? = null
    var qrsPlotter: QrsPlotter? = null
    var hrPlotter: HrPlotter? = null

    private var connectedNode: List<Node>? = null
//    private var message: Message = Message(PHONE_APP)
    private var wearMessage: Message? = null
    private var appState = 0
    private var bluetoothState = STATE_OFF

    private var ppgGreenValueNumber = 0
    private var ppgIrValueNumber = 0
    private var ppgRedValueNumber = 0

    /**
     * Whether to save as CSV, Plot, or both.
     */
    private enum class SaveType {
        ECG_DATA, PPG_GREEN_DATA, PPG_IR_DATA, PPG_RED_DATA, ALL_PPG, ALL
//        , PLOT, BOTH
//        , DEVICE_HR, QRS_HR, ALL
    }

    private var isRecording = false
    private var isPolarDeviceConnected = false
    private var isEcgRunning = false
    private var isPpgGreenRunning = false
    private var isPpgIrRunning = false
    private var isPpgRedRunning = false

    private var activeSection: Section? = null

    private var isPpgGreenVisible = true
    private var isPpgIrVisible = true
    private var isPpgRedVisible = true
    private var isEcgVisible = true
    private var isUsingAnalysis = false

    private var sharedPreferences: SharedPreferences? = null

    private var deviceId = ""
    private var deviceFirmware = "NA"
    private var deviceName = "NA"
    private var deviceAddress = "NA"
    private var deviceBatteryLevel = "NA"

    //* Date when stopped playing. Updated whenever playing started or
    // stopped. */
    private var stopTime: Date? = null
    //* Date when stopped playing. Updated whenever playing started */
    private var startTime: Date? = null

    private var deviceStopHr = "NA"
    private var calculatedStopHr = "NA"

    private lateinit var textStatusContainerTitle: TextView
    private lateinit var ppgGreenStatusContainer: ViewGroup
    private lateinit var textPpgGreenStatus: TextView
    private lateinit var textPpgGreenDataCount: TextView
    private lateinit var ppgIrStatusContainer: ViewGroup
    private lateinit var textPpgIrStatus: TextView
    private lateinit var textPpgIrDataCount: TextView
    private lateinit var ppgRedStatusContainer: ViewGroup
    private lateinit var textPpgRedStatus: TextView
    private lateinit var textPpgRedDataCount: TextView
    private lateinit var ecgStatusContainer: ViewGroup
    private lateinit var textEcgStatus: TextView
    private lateinit var textEcgDataCount: TextView
    private lateinit var ppgContainer: ViewGroup
    private lateinit var ppgGreenPlot: XYPlot
    private lateinit var ppgIrPlot: XYPlot
    private lateinit var ppgRedPlot: XYPlot
//    private lateinit var buttonPpgGreen: Button
//    private lateinit var buttonPpgIr: Button
//    private lateinit var buttonPpgRed: Button
    private lateinit var ecgContainer: ViewGroup
    private lateinit var textEcgHr: TextView
    private lateinit var textEcgInfo: TextView
    private lateinit var textEcgTime: TextView
    private lateinit var ecgPlot: XYPlot
    private lateinit var analysisContainer: ViewGroup
    private lateinit var qrsPlot: XYPlot
    private lateinit var hrPlot: XYPlot
    private lateinit var sectionsContainer: ViewGroup
    private lateinit var sectionsList: RecyclerView
    private lateinit var buttonAddSection: ViewGroup

//    private lateinit var textWearStatus: TextView
//    private lateinit var textWearHr: TextView
//    private lateinit var textWearIbi: TextView
//    private lateinit var textWearTimestamp: TextView
//    private lateinit var textHrmStatus: TextView
//    private lateinit var textHrmHr: TextView
//    private lateinit var textHrmIbi: TextView
//    private lateinit var textHrmTimestamp: TextView
//    private lateinit var textTooltip: TextView
//    private lateinit var buttonMain: Button
//    private lateinit var buttonView: TextView
//    private lateinit var buttonConnectHrm: TextView
//    private lateinit var containerWear: LinearLayout
//    private lateinit var containerHrm: LinearLayout

    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            bluetoothState = intent.getIntExtra(EXTRA_STATE, STATE_OFF)
            when (bluetoothState) {
                STATE_TURNING_OFF -> {
                    Toast.makeText(context,
                        "Bluetooth turning off",
                        Toast.LENGTH_SHORT).show()
                }
                STATE_TURNING_ON -> {
                    Toast.makeText(context,
                        "Bluetooth turning on",
                        Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
     * Launcher for opening document tree.
     *
     * Successful launch provides PREF_TREE_URI.
     */
    private val openDocumentTreeLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        Log.d(
            TAG, "openDocumentTreeLauncher: result" +
                    ".getResultCode()=" + result.resultCode
        )
        // Find the UID for this application
        Log.d(TAG, "URI=" + UriUtils.getApplicationUid(this))
        Log.d(
            TAG, "Current permissions (initial): "
                    + UriUtils.getNPersistedPermissions(this)
        )
        try {
            if (result.resultCode == RESULT_OK) {
                // Get Uri from Storage Access Framework.
                val treeUri = result.data!!.data
                val editor =
                    getPreferences(MODE_PRIVATE)
                        .edit()
                if (treeUri == null) {
                    editor.putString(PREF_TREE_URI, null)
                    editor.apply()
                    AppUtils.errMsg(
                        this, ("Failed to get " +
                                "persistent " +
                                "access permissions")
                    )
                    return@registerForActivityResult
                }
                // Persist access permissions.
                try {
                    this.contentResolver.takePersistableUriPermission(
                        treeUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                    // Save the current treeUri as PREF_TREE_URI
                    editor.putString(
                        PREF_TREE_URI,
                        treeUri.toString()
                    )
                    editor.apply()
                    // Trim the persisted permissions
                    UriUtils.trimPermissions(this, 1)
                } catch (ex: java.lang.Exception) {
                    val msg = ("Failed to " +
                            "takePersistableUriPermission for "
                            + treeUri.path)
                    AppUtils.excMsg(this, msg, ex)
                }
                Log.d(TAG, ("Current permissions (final): "
                        + UriUtils.getNPersistedPermissions(this))
                )
            }
        } catch (ex: java.lang.Exception) {
            Log.e(
                TAG, "Error in openDocumentTreeLauncher: " +
                        "startActivity for result", ex
            )
        }
    }

    // Launcher for Settings
    private val settingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        var code = "Unknown"
        if (result.resultCode == RESULT_OK) {
            code = "RESULT_OK"
        } else if (result.resultCode == RESULT_CANCELED) {
            code = "RESULT_CANCELED"
        }
        // PREF_DEVICE_ID
        val oldDeviceId: String = deviceId
        deviceId = sharedPreferences!!.getString(
            PREF_DEVICE_ID,
            ""
        ).toString()
        Log.d(
            TAG, "settingsLauncher: resultCode=" + code
                    + " oldDeviceId=" + oldDeviceId
                    + " DeviceId=" + deviceId
        )
        if (oldDeviceId != deviceId) {
            resetDeviceId(oldDeviceId)
        }
        // setup plot visibility preferences
        isPpgGreenVisible = sharedPreferences!!.getBoolean(
            PREF_PPG_GREEN_VISIBILITY, true
        )
        isPpgIrVisible = sharedPreferences!!.getBoolean(
            PREF_PPG_IR_VISIBILITY, true
        )
        isPpgRedVisible = sharedPreferences!!.getBoolean(
            PREF_PPG_RED_VISIBILITY, true
        )
        isEcgVisible = sharedPreferences!!.getBoolean(
            PREF_ECG_VISIBILITY, true
        )
        setPlotVisibility()

        // PREF_ANALYSIS_VISIBILITY
        isUsingAnalysis = sharedPreferences!!.getBoolean(
            PREF_ANALYSIS_VISIBILITY, true
        )
        setAnalysisVisibility()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, this.javaClass.simpleName + " onCreate")
        super.onCreate(savedInstanceState)

        // Capture global exceptions
        Thread.setDefaultUncaughtExceptionHandler { _, paramThrowable ->
            Log.e(TAG, "Unexpected exception :", paramThrowable)
            // Any non-zero exit code
            exitProcess(2)
        }

        // Keep the screen on
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // request for permissions
        if (allPermissionsGranted()) {
            // start Bluetooth
            setupBluetooth()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }

//        message.sender = Entity.PHONE_APP

        // set UI vars
        textStatusContainerTitle = binding.statusContainerTitle

        ppgGreenStatusContainer = binding.statusPpgGreenContainer
        textPpgGreenStatus = binding.statusPpgGreen
        textPpgGreenDataCount = binding.statusPpgGreenDataCount

        ppgIrStatusContainer = binding.statusPpgIrContainer
        textPpgIrStatus = binding.statusPpgIr
        textPpgIrDataCount = binding.statusPpgIrDataCount

        ppgRedStatusContainer = binding.statusPpgRedContainer
        textPpgRedStatus = binding.statusPpgRed
        textPpgRedDataCount = binding.statusPpgRedDataCount

        ecgStatusContainer = binding.statusEcgContainer
        textEcgStatus = binding.statusEcg
        textEcgDataCount = binding.statusEcgDataCount

        ppgContainer = binding.ppgContainer
        ppgGreenPlot = binding.ppgGreenPlot
        ppgIrPlot = binding.ppgIrPlot
        ppgRedPlot = binding.ppgRedPlot
//        buttonPpgGreen = binding.buttonPpgGreen
//        buttonPpgIr = binding.buttonPpgIr
//        buttonPpgRed = binding.buttonPpgRed
        ecgContainer = binding.ecgContainer
        textEcgHr = binding.ecgHr
        textEcgInfo = binding.ecgInfo
        textEcgTime = binding.ecgTime
        ecgPlot = binding.ecgPlot
        analysisContainer = binding.analysisContainer
        qrsPlot = binding.qrsPlot
        hrPlot = binding.hrPlot

        sectionsContainer = binding.sectioningContainer
        sectionsList = binding.sectionListFrame.findViewById(R.id.section_list)
        buttonAddSection = binding.buttonAddSection

//        textWearStatus = binding.wearStatus
//        textWearHr = binding.wearHr
//        textWearIbi = binding.wearIbi
//        textWearTimestamp = binding.wearTime
//        textHrmStatus = binding.hrmStatus
//        textHrmHr = binding.hrmHr
//        textHrmIbi = binding.hrmIbi
//        textHrmTimestamp = binding.hrmTime
//        textTooltip = binding.buttonTooltip
//        buttonMain = binding.buttonMain
//        buttonView = binding.buttonView
//        buttonConnectHrm = binding.hrmConnect
//        containerWear = binding.wearContainer
//        containerHrm = binding.hrmContainer

//        runOnUiThread {
//            textTooltip.text = getString(R.string.click_me_text)
//            buttonMain.text = getString(R.string.button_start)
//            buttonView.visibility = View.GONE
//        }

        // setup recycler view
        sectionsList.layoutManager = LinearLayoutManager(this)
        sectionsAdapter = SectionsAdapter(this)
        sectionsList.adapter = sectionsAdapter

        // setup view model & live data
        viewModel = ViewModelProviders.of(this).get(MainViewModel::class.java)
        viewModel.getSectionsList().observe(this) {
            sectionsAdapter.setSections(it)
        }

        // debug only
        viewModel.initiateSectioning()

        // TODO update values
        runOnUiThread {
//            sectionsContainer.visibility = View.GONE

            textPpgGreenStatus.text = getString(R.string.ppg_green_status,
                getString(R.string.status_default))
            textPpgIrStatus.text = getString(R.string.ppg_ir_status,
                getString(R.string.status_default))
            textPpgRedStatus.text = getString(R.string.ppg_red_status,
                getString(R.string.status_default))
            textEcgStatus.text = getString(R.string.ecg_status,
                getString(R.string.status_default))
        }

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        // register preference listener
        sharedPreferences!!.registerOnSharedPreferenceChangeListener(this)

        // get visibility preferences
        isPpgGreenVisible = sharedPreferences!!.getBoolean(
            PREF_PPG_GREEN_VISIBILITY, true)
        isPpgIrVisible = sharedPreferences!!.getBoolean(
            PREF_PPG_IR_VISIBILITY, true)
        isPpgRedVisible = sharedPreferences!!.getBoolean(
            PREF_PPG_RED_VISIBILITY, true)
        isEcgVisible = sharedPreferences!!.getBoolean(
            PREF_ECG_VISIBILITY, true)
        setPlotVisibility()
        isUsingAnalysis = sharedPreferences!!.getBoolean(
            PREF_ANALYSIS_VISIBILITY, false)
        if (!isUsingAnalysis) {
            analysisContainer.visibility = View.GONE
        }

        setLastHr()
//        stopTime = Date()

        // get device ID from preferences if there is any
        deviceId = sharedPreferences!!.getString(PREF_DEVICE_ID, "").toString()
        Log.d(TAG, "DeviceId=$deviceId")

        // register bluetooth state broadcast receiver
        registerReceiver(bluetoothStateReceiver, BluetoothService.BLUETOOTH_STATE_FILTER)

        // build Google API Client with access to Wearable API
        client = GoogleApiClient.Builder(this)
            .addApi(Wearable.API)
            .addConnectionCallbacks(this)
            .build()
        client.connect()

        // set click listener
        ppgGreenStatusContainer.setOnClickListener {
            togglePpgTracker(PpgType.PPG_GREEN)
        }
        ppgIrStatusContainer.setOnClickListener {
            togglePpgTracker(PpgType.PPG_IR)
        }
        ppgRedStatusContainer.setOnClickListener {
            togglePpgTracker(PpgType.PPG_RED)
        }
        ecgStatusContainer.setOnClickListener {
            if (!isPolarDeviceConnected) {
                connectPolarDevice()
            }
        }
        buttonAddSection.setOnClickListener {
            viewModel.addNewSection()
            Log.d(TAG,"Add New Section!")
            Log.d(TAG,"${viewModel.getSectionsList().value}")
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
//        Log.d(TAG, this.getClass().getSimpleName() + " onCreateOptionsMenu");
//        Log.d(TAG, "    mPlaying=" + mPlaying);
        this@MainActivity.menu = menu
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.main_menu, menu)
        if (polarApi == null) {
            menu.findItem(R.id.pause).title = "Start"
            menu.findItem(R.id.save).isVisible = false
        } else if (isRecording) {
            menu.findItem(R.id.pause).icon = ResourcesCompat.getDrawable(
                resources,
                R.drawable.ic_stop_white_36dp, null
            )
            menu.findItem(R.id.pause).title = "Pause"
            menu.findItem(R.id.save).isVisible = false
        } else {
            menu.findItem(R.id.pause).icon = ResourcesCompat.getDrawable(
                resources,
                R.drawable.ic_play_arrow_white_36dp, null
            )
            menu.findItem(R.id.pause).title = "Start"
            menu.findItem(R.id.save).isVisible = true
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        if (id == R.id.pause) {
            if (polarApi == null) {
                return true
            }
            if (isRecording) {
                // Turn recording off
                // stop foreground service
                MobileService.stopService(this)
                setLastHr()
                stopTime = Date()
                isRecording = false
                setPanBehavior()
                if (ecgDisposable != null) {
                    // Turns ecg stream off
                    toggleEcgStream()
                    isEcgRunning = false
                }
                // turn off PPG stream
                togglePpgTracker()
                menu.findItem(R.id.pause).icon = ResourcesCompat.getDrawable(
                    resources,
                    R.drawable.ic_play_arrow_white_36dp, null
                )
                menu.findItem(R.id.pause).title = "Start"
                menu.findItem(R.id.save).isVisible = true
//                // hide section container
//                runOnUiThread {
//                    sectionsContainer.visibility = View.GONE
//                }
            } else {
                // Turn recording on
                // start foreground service
                MobileService.startService(this,"Start recording...")
                setLastHr()
                startTime = Date()
                stopTime = Date()
                isRecording = true
                setPanBehavior()
                textStatusContainerTitle.text = getString(
                    R.string.elapsed_time,
                    0.0
                )
                textEcgTime.text = getString(
                    R.string.elapsed_time,
                    0.0
                )
                // Clear the plot
                ppgGreenValueNumber = 0
                ppgIrValueNumber = 0
                ppgRedValueNumber = 0
                ppgGreenPlotter?.clear()
                ppgIrPlotter?.clear()
                ppgRedPlotter?.clear()
                ecgPlotter?.clear()
                qrsPlotter?.clear()
                hrPlotter?.clear()
                if (ecgDisposable == null) {
                    // Turns ecg stream on
                    toggleEcgStream()
                    isEcgRunning = true
                }
                // turn on PPG stream
                togglePpgTracker()
                menu.findItem(R.id.pause).icon = ResourcesCompat.getDrawable(
                    resources,
                    R.drawable.ic_stop_white_36dp, null
                )
                menu.findItem(R.id.pause).title = "Pause"
                menu.findItem(R.id.save).isVisible = false
                // re-initiate sectioning
                viewModel.initiateSectioning()
//                // show section container
//                runOnUiThread {
//                    sectionsContainer.visibility = View.VISIBLE
//                }
            }
            return true
        } else if (id == R.id.save_all) {
            saveDataWithNote(SaveType.ALL)
            return true
        }
        else if (id == R.id.save_all_ppg_data) {
            saveDataWithNote(SaveType.ALL_PPG)
            return true
        }
        else if (id == R.id.save_ecg_data) {
            saveDataWithNote(SaveType.ECG_DATA)
            return true
        }
        else if (id == R.id.save_ppg_green_data) {
            saveDataWithNote(SaveType.PPG_GREEN_DATA)
            return true
        }
        else if (id == R.id.save_ppg_ir_data) {
            saveDataWithNote(SaveType.PPG_IR_DATA)
            return true
        }
        else if (id == R.id.save_ppg_red_data) {
            saveDataWithNote(SaveType.PPG_RED_DATA)
            return true
        }
//        else if (id == R.id.save_plot) {
//            saveDataWithNote(SaveType.PLOT)
//            return true
//        } else if (id == R.id.save_data) {
//            saveDataWithNote(SaveType.DATA)
//            return true
//        } else if (id == R.id.save_both) {
//            saveDataWithNote(SaveType.BOTH)
//            return true
//        }
        else if (id == R.id.info) {
            displayPolarInfo()
            return true
        } else if (id == R.id.restart_polar_api) {
            restartPolarApi()
            return true
        } else if (id == R.id.redo_plot_setup) {
            redoPlotSetup()
            return true
        } else if (id == R.id.device_id) {
            selectDeviceId()
            return true
        } else if (id == R.id.choose_data_directory) {
            chooseDataDirectory()
            return true
        } else if (id == R.id.help) {
            showHelp()
            return true
        } else if (item.itemId == R.id.menu_settings) {
            showSettings()
            return true
        }
        return false
    }

    override fun onSectionClicked(section: Section) {
        if (!isRecording) {
            showSectionNameDialog(null, section)
        }
        else {
            Toast.makeText(
                this@MainActivity,
                getString(R.string.cannot_change_section_name),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    /**
     * Show Section Name dialog input.
     */
    private fun showSectionNameDialog(view: View?, section: Section) {
        val dialog = AlertDialog.Builder(
            this,
            R.style.InverseTheme
        )
        dialog.setTitle(R.string.section_name_dialog_title)

        val viewInflated: View = LayoutInflater.from(applicationContext).inflate(
            R.layout.section_name_dialog,
            if (view == null) null else view.rootView as ViewGroup,
            false
        )

        val input = viewInflated.findViewById<EditText>(R.id.section_name_input)
        input.inputType = InputType.TYPE_CLASS_TEXT
//        deviceId = sharedPreferences?.getString(PREF_DEVICE_ID, "").toString()
//        input.setText(deviceId)
        input.setText(section.name)
        dialog.setView(viewInflated)

        dialog.setPositiveButton(
            R.string.ok
        ) { _, _ ->
            val newName =
                if (input.text == null || input.text.isEmpty()) {
                    "Section"
                } else {
                    input.text.toString()
                }
            val oldName: String = section.name
            // mvvm architecture violation
//            section.name = input.text.toString()
            if (oldName != newName) {
                viewModel.editSectionName(section, input.text.toString())
                Log.d(
                    TAG, "showSectionNameDialog: OK:  oldName="
                            + oldName + " newName="
                            + section.name
                )
            }
            else {
                Log.d(
                    TAG,
                    "showSectionNameDialog: OK:  No Changes  Name=${section.name}"
                )
            }

        }
        dialog.setNegativeButton(
            R.string.cancel
        ) { dialog1, _ ->
            Log.d(
                TAG,
                "showSectionNameDialog: Cancel:  Name=${section.name}"
            )
            dialog1.cancel()
        }
        dialog.show()
    }

    private fun invalidatePpgState() {
        if (!isPpgGreenRunning
            && !isPpgIrRunning
            && !isPpgRedRunning
        ) {
            runOnUiThread {
                textPpgGreenStatus.text = getString(R.string.ppg_green_status,
                    getString(R.string.status_stopped))
                textPpgIrStatus.text = getString(R.string.ppg_ir_status,
                    getString(R.string.status_stopped))
                textPpgRedStatus.text = getString(R.string.ppg_red_status,
                    getString(R.string.status_stopped))
            }
        } else {
            runOnUiThread {
                textPpgGreenStatus.text = getString(R.string.ppg_green_status,
                    getString(R.string.status_running))
                textPpgIrStatus.text = getString(R.string.ppg_ir_status,
                    getString(R.string.status_running))
                textPpgRedStatus.text = getString(R.string.ppg_red_status,
                    getString(R.string.status_running))
            }
        }
    }

    override fun onConnected(p0: Bundle?) {
        Wearable.NodeApi.getConnectedNodes(client).setResultCallback {
            connectedNode = it.nodes
            Log.d(TAG,"Node connected")

//            runOnUiThread {
//                textWearStatus.text = getString(R.string.status_connected)
//            }

            runOnUiThread {
                textPpgGreenStatus.text = getString(R.string.ppg_green_status,
                    getString(R.string.status_connected))
                textPpgIrStatus.text = getString(R.string.ppg_ir_status,
                    getString(R.string.status_connected))
                textPpgRedStatus.text = getString(R.string.ppg_red_status,
                    getString(R.string.status_connected))
            }

            // request Wear App current state
            // request on connection to ensure message is sent
            val message = Message(NAME, ActivityCode.START_ACTIVITY)
            message.content = "Requesting Wear App current state"
//            message.code = ActivityCode.START_ACTIVITY
            sendMessage(message, MessagePath.REQUEST)
        }
        Wearable.MessageApi.addListener(client) { messageEvent ->
            wearMessage = Gson().fromJson(String(messageEvent.data), Message::class.java)
            onMessageArrived(messageEvent.path)
        }
        Wearable.DataApi.addListener(client) { data ->
//            Log.d(TAG, "Data count arrived : ${data.count}")
            for (dataEvent in data) {
                Log.d(TAG, "Data Event\n" +
                        "URI Last Path Segment: ${dataEvent.dataItem.uri.lastPathSegment}\n" +
                        "URI Path: ${dataEvent.dataItem.uri.path}\n" +
                        "URI encoded path: ${dataEvent.dataItem.uri.encodedPath}\n" +
                        "URI Host: ${dataEvent.dataItem.uri.host}")
                onDataArrived(dataEvent)
            }
        }
    }

    override fun onConnectionSuspended(p0: Int) {
        connectedNode = null
//        runOnUiThread {
//            textWearStatus.text = getString(R.string.status_disconnected)
//        }
        runOnUiThread {
            textPpgGreenStatus.text = getString(R.string.ppg_green_status,
                getString(R.string.status_disconnected))
            textPpgIrStatus.text = getString(R.string.ppg_ir_status,
                getString(R.string.status_disconnected))
            textPpgRedStatus.text = getString(R.string.ppg_red_status,
                getString(R.string.status_disconnected))
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_CODE_ENABLE_BLUETOOTH) {
            when (resultCode) {
                RESULT_OK -> {
                    Toast.makeText(this,
                        "Bluetooth enabled.",
                        Toast.LENGTH_SHORT).show()
                }
                RESULT_CANCELED -> {
                    Toast.makeText(this,
                        "Bluetooth has not been enabled.",
                        Toast.LENGTH_SHORT).show()
                }
            }
        }
        else super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults:
        IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                setupBluetooth()
            } else {
                Toast.makeText(this,
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    override fun onSharedPreferenceChanged(
        sharedPreferences: SharedPreferences?,
        key: String
    ) {
        Log.d(TAG, "onSharedPreferenceChanged: key=$key")
    }

    /**
     * Tries to disconnect deviceId from the current polarAPI and restarts the polarAPI.
     */
    private fun restartPolarApi() {
        Log.d(TAG, this.javaClass.simpleName + " restartApi:")
        resetDeviceId(deviceId)
    }

    /**
     * Setup Polar ECG Part of the application.
     * Use for first time initialization and reinitializing
     * the Polar API, connection, and plot setup.
     */
    private fun setupPolar() {
        Log.d(
            TAG, this.javaClass.simpleName + " restart:"
                    + " PolarApi=" + polarApi
                    + " DeviceId=" + deviceId
        )
        if (polarApi != null || deviceId == null || deviceId.isEmpty()) {
            return
        }
        if (ecgDisposable != null) {
            // Turns it off
            toggleEcgStream()
        }
        isRecording = false
        isEcgRunning = false
        ecgPlotter?.clear()
        textEcgHr.text = ""
        textEcgInfo.text = ""
        textEcgTime.text = ""
        invalidateOptionsMenu()

//        // Don't use SDK if BT is not enabled or permissions are not granted.
//        if (!mBleSupported) return
        if (!allPermissionsGranted()) {
//            if (!mAllPermissionsAsked) {
//                mAllPermissionsAsked = true
//                Utils.warnMsg(this, getString(R.string.permission_not_granted))
//            }
//            return
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }

        // setup Polar API
        polarApi = defaultImplementation(
            this,
            PolarBleApi.FEATURE_POLAR_SENSOR_STREAMING or
                    PolarBleApi.FEATURE_BATTERY_INFO or
                    PolarBleApi.FEATURE_DEVICE_INFO or
                    PolarBleApi.FEATURE_POLAR_FILE_TRANSFER or
                    PolarBleApi.FEATURE_HR
        )

        // Post a Runnable to have plots to be setup again in 1 sec

        // setup Polar API Callback
        polarApi!!.setApiCallback(object: PolarBleApiCallback() {
            override fun blePowerStateChanged(powered: Boolean) {
                Log.d(TAG, "BluetoothStateChanged $powered")
            }

            override fun deviceConnected(polarDeviceInfo: PolarDeviceInfo) {
                Log.d(TAG, "*Device connected " + polarDeviceInfo.deviceId)
                deviceAddress = polarDeviceInfo.address
                deviceName = polarDeviceInfo.name
                isPolarDeviceConnected = true
                runOnUiThread {
                    textEcgStatus.text = getString(R.string.ecg_status,
                        getString(R.string.status_connected))
                }
//                // Set the MRU preference here after we know the name
//                setDevicePreferences(DeviceInfo(deviceName, deviceId))
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.connected_string, polarDeviceInfo.name),
                    Toast.LENGTH_SHORT
                ).show()
            }

            override fun deviceDisconnected(polarDeviceInfo: PolarDeviceInfo) {
                Log.d(TAG, "*Device disconnected $polarDeviceInfo")
                isPolarDeviceConnected = false
                runOnUiThread {
                    textEcgStatus.text = getString(R.string.ecg_status,
                        getString(R.string.status_disconnected))
                }
            }

            override fun streamingFeaturesReady(
                identifier: String,
                features: Set<DeviceStreamingFeature>
            ) {
                for (feature in features) {
                    Log.d(TAG, "Streaming feature is ready for 1: $feature")
                    when (feature) {
                        DeviceStreamingFeature.ECG -> {

//                            toggleEcgStream()
                        }
//                        DeviceStreamingFeature.PPI,
//                        DeviceStreamingFeature.ACC,
//                        DeviceStreamingFeature.MAGNETOMETER,
//                        DeviceStreamingFeature.GYRO,
//                        DeviceStreamingFeature.PPG -> {}
                        else -> {}
                    }
                }
            }

            override fun hrFeatureReady(identifier: String) {
                Log.d(TAG, "*HR Feature ready $identifier")
            }

            override fun disInformationReceived(
                identifier: String,
                uuid: UUID,
                value: String
            ) {
                if (uuid == UUID.fromString(
                        "00002a28-0000-1000-8000" +
                                "-00805f9b34fb"
                    )
                ) {
                    deviceFirmware = value.trim { it <= ' ' }
                    Log.d(TAG, "*Firmware: $identifier $deviceFirmware")
                    textEcgInfo.text = getString(
                        R.string.info_string,
                        deviceName, deviceBatteryLevel, deviceFirmware, deviceId
                    )
                }
            }

            override fun batteryLevelReceived(identifier: String, level: Int) {
                deviceBatteryLevel = level.toString()
                Log.d(TAG, "*Battery level $identifier $level")
                textEcgInfo.text = getString(
                    R.string.info_string,
                    deviceName, deviceBatteryLevel, deviceFirmware, deviceId
                )
            }

            override fun hrNotificationReceived(
                identifier: String,
                data: PolarHrData
            ) {
                if (isEcgRunning) {
//                    Log.d(TAG,
//                            "*HR " + polarHrData.hr + " mPlaying=" +
//                            mPlaying);
                    textEcgHr.text = data.hr.toString()
//                    // Add to HR plot
                    val time = Date().time
                    hrPlotter?.addValues1(
                        time.toDouble(),
                        data.hr.toDouble(),
                        data.rrsMs
                    )
                    hrPlotter?.fullUpdate()
                }
            }
        })
//        // try connect to device
//        try {
//            polarApi!!.connectToDevice(deviceId)
//            isPlaying = true
//            setLastHr()
//            stopTime = Date()
//        } catch (ex: PolarInvalidArgument) {
//            val msg = """
//                DeviceId=$deviceId
//                ConnectToDevice: Bad argument:
//                """.trimIndent()
//            AppUtils.excMsg(this, msg, ex)
//            Log.d(TAG, "restart: $msg")
//            isPlaying = false
//            setLastHr()
//            stopTime = Date()
//        }
        invalidateOptionsMenu()
    }

    private fun connectPolarDevice() {
        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed({
            if (!isPolarDeviceConnected) {
                AppUtils.warnMsg(
                    this@MainActivity, "No connection to " + deviceId
                            + " after 1 minute"
                )
                runOnUiThread {
                    textEcgStatus.text = getString(R.string.ecg_status,
                        getString(R.string.status_disconnected))
                }
            }
        }, 60000)

        // try connect to device
        try {
            polarApi!!.connectToDevice(deviceId)
            Log.d(TAG, "Connecting to Polar device...\n" +
                    "DeviceId: $deviceId")
            runOnUiThread {
                textEcgStatus.text = getString(R.string.ecg_status,
                    getString(R.string.status_connecting))
                Toast.makeText(
                    this,
                    getString(R.string.connecting) + " " + deviceId,
                    Toast.LENGTH_SHORT
                ).show()
            }
//            isPlaying = true
            setLastHr()
            stopTime = Date()
        } catch (ex: PolarInvalidArgument) {
            val msg = """
                DeviceId=$deviceId
                ConnectToDevice: Bad argument:
                """.trimIndent()
            AppUtils.excMsg(this, msg, ex)
            Log.d(TAG, "connectPolarDevice: $msg")
//            isPlaying = false
            setLastHr()
            stopTime = Date()
        }
        invalidateOptionsMenu()
    }

    override fun onResume() {
        Log.d(TAG, this.javaClass.simpleName + " onResume:")
        super.onResume()

        // Check if PREF_TREE_URI is valid and remove it if not
        if (UriUtils.getNPersistedPermissions(this) <= 0) {
            val editor = getPreferences(MODE_PRIVATE)
                .edit()
            editor.putString(PREF_TREE_URI, null)
            editor.apply()
        }

        polarApi?.foregroundEntered()
        invalidateOptionsMenu()

        // Setup the plots if not done
        if (ppgGreenPlotter == null) {
            ppgGreenPlot.post {
                ppgGreenPlotter = PpgPlotter(
                    this, ppgGreenPlot, PpgType.PPG_GREEN,
                    "PPG Green", Color.GREEN, false
                )
            }
        }
        if (ppgIrPlotter == null) {
            ppgIrPlot.post {
                ppgIrPlotter = PpgPlotter(
                    this, ppgIrPlot, PpgType.PPG_IR,
                    "PPG Ir", Color.MAGENTA, false
                )
            }
        }
        if (ppgRedPlotter == null) {
            ppgRedPlot.post {
                ppgRedPlotter = PpgPlotter(
                    this, ppgRedPlot, PpgType.PPG_RED,
                    "PPG Red", Color.RED, false
                )
            }
        }
        if (ecgPlotter == null) {
            ecgPlot.post {
                ecgPlotter = EcgPlotter(
                    this, ecgPlot,
                    "ECG", Color.RED, false
                )
            }
        }
        if (hrPlotter == null) {
            hrPlot.post { hrPlotter = HrPlotter(this, hrPlot) }
        }
        if (qrsPlotter == null) {
            qrsPlot.post { qrsPlotter = QrsPlotter(this, qrsPlot) }
        }

        // Set the visibility of the Analysis plot
        isUsingAnalysis = sharedPreferences!!.getBoolean(PREF_ANALYSIS_VISIBILITY, false)
        setAnalysisVisibility()

        // Start the connection to the device
        Log.d(TAG, "DeviceId=$deviceId")
        Log.d(TAG, "polarApi=$polarApi")
        deviceId = sharedPreferences?.getString(PREF_DEVICE_ID, "").toString()
        if (deviceId == null || deviceId.isEmpty()) {
            Toast.makeText(
                this,
                getString(R.string.no_device),
                Toast.LENGTH_SHORT
            ).show()
        } else {
            setupPolar()
        }
    }

    override fun onPause() {
        Log.v(TAG, this.javaClass.simpleName + " onPause")
        super.onPause()

        polarApi?.backgroundEntered()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // This seems to be necessary with Android 12
        // Otherwise onDestroy is not called
        Log.d(TAG, this.javaClass.simpleName + ": onBackPressed")
        finish()
        super.onBackPressed()
    }

    override fun onRestart() {
        super.onRestart()
        Log.i(TAG,"Lifecycle: onRestart()")
        // re-register bluetooth state receiver
        registerReceiver(bluetoothStateReceiver, BluetoothService.BLUETOOTH_STATE_FILTER)
    }

    override fun onStop() {
        super.onStop()
        Log.i(TAG, "Lifecycle: onStop()")
        // unregister receiver
        unregisterReceiver(bluetoothStateReceiver)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG,"Lifecycle: onDestroy()")
        polarApi?.shutDown()
        // unregister receiver
        unregisterReceiver(bluetoothStateReceiver)
        sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
    }

    /**
     * Sets the analysis plots visibility using the current value of isUsingAnalysis.
     */
    private fun setAnalysisVisibility() {
        Log.d(TAG, this.javaClass.simpleName + " setAnalysisVisibility: " +
                "$isUsingAnalysis")
        if (isUsingAnalysis) {
            analysisContainer.visibility = View.VISIBLE
        } else {
            analysisContainer.visibility = View.GONE
        }
    }

    /**
     * Show the help.
     */
    private fun showHelp() {
        Log.d(TAG, "showHelp")
//        try {
//            // Start theInfoActivity
//            val intent = Intent()
//            intent.setClass(this, InfoActivity::class.java)
//            intent.addFlags(
//                Intent.FLAG_ACTIVITY_NEW_TASK
//                        or Intent.FLAG_ACTIVITY_SINGLE_TOP
//            )
//            intent.putExtra(INFO_URL, "file:///android_asset/kedotnetecg.html")
//            startActivity(intent)
//        } catch (ex: java.lang.Exception) {
//            Utils.excMsg(this, getString(R.string.help_show_error), ex)
//        }
    }

    /**
     * Calls the settings activity.
     */
    private fun showSettings() {
        Log.d(TAG, "showSettings")
        val intent = Intent(
            this@MainActivity,
            SettingsActivity::class.java
        )
        settingsLauncher.launch(intent)
    }

    /**
     * Checks whether all permissions required has been granted.
     * @return Boolean value of whether all permissions required has been granted
     */
    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) ==
                PackageManager.PERMISSION_GRANTED
    }

    /**
     * Setting up Bluetooth Manager and Adapter for use.
     */
    private fun setupBluetooth() {
        // setup bluetooth adapter
        BluetoothService.manager = getSystemService(BluetoothManager::class.java)
        BluetoothService.adapter = BluetoothService.manager.adapter

        // check bluetooth availability
//        if (BluetoothService.adapter == null) {
//            runOnUiThread {
//                textHrmStatus.text = getString(R.string.status_no_support)
//            }
//        }

        BluetoothService.enableBluetooth(this, this)
    }

    /***
     * Sets the last HR from the series in the HR plotter.
     */
    private fun setLastHr() {
        calculatedStopHr = "NA"
        deviceStopHr = "NA"

        if (hrPlotter == null) return
        var lastVal: Long
        if (hrPlotter!!.hrSeries1 != null && hrPlotter!!.hrSeries1!!.size() > 0) {
            lastVal = hrPlotter!!.hrSeries1!!.getyVals().last.toDouble().roundToLong()
            deviceStopHr = String.format(Locale.US, "%d", lastVal)
        }
        if (hrPlotter!!.hrSeries2 != null && hrPlotter!!.hrSeries2!!.size() > 0) {
            lastVal = hrPlotter!!.hrSeries2!!.getyVals().last.toDouble().roundToLong()
            calculatedStopHr = String.format(Locale.US, "%d", lastVal)
        }
    }

    /**
     * Panning while collecting data causes problems. Turn it off when
     * playing and turn it on with stopped. Zooming is not enabled.
     */
    private fun setPanBehavior() {
        ppgGreenPlotter?.setPanning(!isPpgGreenRunning)
        ppgIrPlotter?.setPanning(!isPpgIrRunning)
        ppgRedPlotter?.setPanning(!isPpgRedRunning)
        ecgPlotter?.setPanning(!isEcgRunning)
        qrsPlotter?.setPanning(!isEcgRunning)
        hrPlotter?.setPanning(!isEcgRunning)
    }

    private fun redoPlotSetup() {
        ppgGreenPlotter?.setupPlot()
        ppgIrPlotter?.setupPlot()
        ppgRedPlotter?.setupPlot()
        ecgPlotter?.setupPlot()
        qrsPlotter?.setupPlot()
        hrPlotter?.setupPlot()
    }

    private fun setPlotVisibility() {
        runOnUiThread {
            ppgGreenPlot.visibility = when (isPpgGreenVisible) {
                true -> View.VISIBLE
                false -> View.GONE
            }
            ppgIrPlot.visibility = when (isPpgIrVisible) {
                true -> View.VISIBLE
                false -> View.GONE
            }
            ppgRedPlot.visibility = when (isPpgRedVisible) {
                true -> View.VISIBLE
                false -> View.GONE
            }
            ecgPlot.visibility = when (isEcgVisible) {
                true -> View.VISIBLE
                false -> View.GONE
            }
        }
    }

//    /**
//     * Setup a new device into the shared preferences.
//     * This will remove the earliest device added to the list
//     * if the list is on max capacity.
//     * @param deviceInfo info of the new device
//     */
//    private fun setDevicePreferences(deviceInfo: DeviceInfo) {
//        val editor: SharedPreferences.Editor = sharedPreferences!!.edit()
//        // Remove any found so the new one will be added at the beginning
//        val removeList: MutableList<DeviceInfo> = ArrayList()
//        for (acquaintedDeviceInfo in acquaintedDevices) {
//            if (deviceInfo.name == acquaintedDeviceInfo.name &&
//                deviceInfo.id == acquaintedDeviceInfo.id) {
//                removeList.add(acquaintedDeviceInfo)
//            }
//        }
//        for (device in removeList) {
//            acquaintedDevices.remove(device)
//        }
//        // Remove at end if size exceed max
//        if (acquaintedDevices.size != 0 && acquaintedDevices.size == MAX_DEVICES) {
//            acquaintedDevices.removeAt(acquaintedDevices.size - 1)
//        }
//        // Add at the beginning
//        acquaintedDevices.add(0, deviceInfo)
//        val gson = Gson()
//        val json = gson.toJson(acquaintedDevices)
//        editor.putString(PREF_ACQ_DEVICE_IDS, json)
//        deviceId = deviceInfo.id
//        editor.putString(PREF_DEVICE_ID, deviceInfo.id)
//        editor.apply()
//    }

    /**
     * Commence selecting device ID.
     */
    private fun selectDeviceId() {
//        if (acquaintedDevices.size == 0) {
//            showDeviceIdDialog(null)
//            return
//        }
//        val dialog = arrayOf(AlertDialog.Builder(
//                this@MainActivity,
//                R.style.InverseTheme)
//        )
//        dialog[0].setTitle(R.string.device_id_item)
//        val items = arrayOfNulls<String>(acquaintedDevices.size + 1)
//        var deviceInfo: DeviceInfo
//        for (i in acquaintedDevices.indices) {
//            deviceInfo = acquaintedDevices[i]
//            items[i] = deviceInfo.name
//        }
//        items[acquaintedDevices.size] = "New"
//        val checkedItem = 0
//        dialog[0].setSingleChoiceItems(
//            items, checkedItem
//        ) { dialogInterface: DialogInterface, which: Int ->
//            if (which < acquaintedDevices.size) {
//                val deviceInfo1: DeviceInfo = acquaintedDevices[which]
//                val oldDeviceId: String = deviceId
//                setDevicePreferences(deviceInfo1)
//                Log.d(
//                    TAG, "which=" + which
//                            + " name=" + deviceInfo1.name + " id="
//                            + deviceInfo1.id
//                )
//                Log.d(
//                    TAG,
//                    "selectDeviceId: oldDeviceId=" + oldDeviceId
//                            + " mDeviceId=" + deviceId
//                )
//                if (oldDeviceId != deviceId) {
//                    resetDeviceId(oldDeviceId)
//                }
//            } else {
//                showDeviceIdDialog(null)
//            }
//            dialogInterface.dismiss()
//        }
//        dialog[0].setNegativeButton(
//            R.string.cancel
//        ) { dialog1, _ -> dialog1.dismiss() }
//        val alert = dialog[0].create()
//        alert.setCanceledOnTouchOutside(false)
//        alert.show()

        showDeviceIdDialog(null)
    }

    /**
     * Show Device ID dialog input.
     */
    private fun showDeviceIdDialog(view: View?) {
        val dialog = AlertDialog.Builder(
            this,
            R.style.InverseTheme
        )
        dialog.setTitle(R.string.device_id_item)

        val viewInflated: View = LayoutInflater.from(applicationContext).inflate(
            R.layout.device_id_dialog,
            if (view == null) null else view.rootView as ViewGroup,
            false
        )

        val input = viewInflated.findViewById<EditText>(R.id.input)
        input.inputType = InputType.TYPE_CLASS_TEXT
        deviceId = sharedPreferences?.getString(PREF_DEVICE_ID, "").toString()
        input.setText(deviceId)
        dialog.setView(viewInflated)

        dialog.setPositiveButton(
            R.string.ok
        ) { _, _ ->
            val oldDeviceId: String = deviceId
            deviceId = input.text.toString()
            Log.d(
                TAG, "showDeviceIdDialog: OK:  oldDeviceId="
                        + oldDeviceId + " newDeviceId="
                        + deviceId
            )
            val editor: SharedPreferences.Editor = sharedPreferences!!.edit()
            editor.putString(PREF_DEVICE_ID, deviceId)
            editor.apply()
            if (deviceId == null || deviceId.isEmpty()) {
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.no_device),
                    Toast.LENGTH_SHORT
                ).show()
            } else if (oldDeviceId != deviceId) {
                resetDeviceId(oldDeviceId)
            }
        }
        dialog.setNegativeButton(
            R.string.cancel
        ) { dialog1, _ ->
            Log.d(
                TAG,
                "showDeviceIdDialog: Cancel:  mDeviceId=$deviceId"
            )
            dialog1.cancel()
            if (deviceId == null || deviceId.isEmpty()) {
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.no_device),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        dialog.show()
    }

    /**
     * Tries to disconnect the device from the current API and restarts the API,
     * which will use the current mDeviceId.
     *
     * @param oldDeviceId The previous deviceId.
     */
    private fun resetDeviceId(oldDeviceId: String) {
        Log.d(TAG, this.javaClass.simpleName + " resetDeviceId:")
        if (polarApi != null) {
            if (ecgDisposable != null) {
                ecgDisposable!!.dispose()
                ecgDisposable = null
            }
            try {
                polarApi!!.disconnectFromDevice(oldDeviceId)
            } catch (ex: PolarInvalidArgument) {
                val msg = """
                oldDeviceId=$oldDeviceId
                DisconnectFromDevice: Bad argument:
                """.trimIndent()
                AppUtils.excMsg(this@MainActivity, msg, ex)
                Log.d(
                    TAG, this.javaClass.simpleName
                            + " resetDeviceId: " + msg
                )
            }
            polarApi!!.shutDown()
            polarApi = null
        }
        qrsDetector = null
        setupPolar()
    }

    /**
     * Commence saving the current samples as a file.
     * Prompts for a note, then calls
     * the appropriate save method.
     *
     * @param saveType The SaveType.
     */
    private fun saveDataWithNote(saveType: SaveType) {
        val msg: String
        val state = Environment.getExternalStorageState()
        if (Environment.MEDIA_MOUNTED != state) {
            msg = "External Storage is not available"
            Log.e(TAG, msg)
            AppUtils.errMsg(this, msg)
            return
        }
        // Get a note
        val dialog = AlertDialog.Builder(
            this,
            R.style.InverseTheme
        )
        dialog.setTitle(R.string.note_dialog_title)
        val viewInflated: View = LayoutInflater.from(applicationContext)
            .inflate(R.layout.device_id_dialog, null, false)
        val input = viewInflated.findViewById<EditText>(R.id.input)
        input.inputType = (InputType.TYPE_CLASS_TEXT
                or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                or InputType.TYPE_TEXT_FLAG_AUTO_CORRECT)
        dialog.setView(viewInflated)
        dialog.setPositiveButton(
            R.string.ok
        ) { _, _ ->
            when (saveType) {
                SaveType.ALL -> {
                    saveEcgData(input.text.toString())
                    savePpgData(input.text.toString(), ppgGreenPlotter!!)
                    savePpgData(input.text.toString(), ppgIrPlotter!!)
                    savePpgData(input.text.toString(), ppgRedPlotter!!)
                }
                SaveType.ECG_DATA -> saveEcgData(input.text.toString())
                SaveType.PPG_GREEN_DATA -> savePpgData(input.text.toString(), ppgGreenPlotter!!)
                SaveType.PPG_IR_DATA -> savePpgData(input.text.toString(), ppgIrPlotter!!)
                SaveType.PPG_RED_DATA -> savePpgData(input.text.toString(), ppgRedPlotter!!)
                SaveType.ALL_PPG -> {
                    savePpgData(input.text.toString(), ppgGreenPlotter!!)
                    savePpgData(input.text.toString(), ppgIrPlotter!!)
                    savePpgData(input.text.toString(), ppgRedPlotter!!)
                }
//                SaveType.DATA -> saveData(input.text.toString())
//                SaveType.PLOT -> savePlot(input.text.toString())
//                SaveType.BOTH -> {
//                    saveData(input.text.toString())
//                    savePlot(input.text.toString())
//                }
//                SaveType.ALL -> {
//                    saveData(input.text.toString())
//                    savePlot(input.text.toString())
//                    saveSessionData(SaveType.DEVICE_HR)
//                    saveSessionData(SaveType.QRS_HR)
//                }
//                SaveType.DEVICE_HR, SaveType.QRS_HR -> saveSessionData(saveType)
            }
        }
        dialog.setNegativeButton(
            R.string.cancel
        ) { _, _ -> }
        dialog.show()
    }

    /**
     * Finishes the savePlot after getting the note.
     * This only saves plot of the last 30 seconds of ECG recording.
     *
     * @param note The note.
     */
    private fun savePlot(note: String) {
        val prefs = getPreferences(MODE_PRIVATE)
        val treeUriStr = prefs.getString(PREF_TREE_URI, null)
        if (treeUriStr == null) {
            AppUtils.errMsg(this, "There is no data directory set")
            return
        }
        val patientName = prefs.getString(PREF_PATIENT_NAME, "")
        val duration = (stopTime!!.time - startTime!!.time) * MS_TO_SEC
        var msg: String
        val format = "yyyy-MM-dd_HH-mm"
        val df = SimpleDateFormat(format, Locale.US)
        val fileName = "ECG-" + df.format(stopTime!!) + ".png"
        try {
            val treeUri = Uri.parse(treeUriStr)
            val treeDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
            val docTreeUri = DocumentsContract.buildDocumentUriUsingTree(
                treeUri,
                treeDocumentId
            )
            val resolver = this.contentResolver
            val pfd: ParcelFileDescriptor?
            val docUri = DocumentsContract.createDocument(
                resolver, docTreeUri,
                "image/png", fileName
            )
            pfd = contentResolver.openFileDescriptor(docUri!!, "w")
            FileOutputStream(pfd!!.fileDescriptor).use { strm ->
//                val logo = BitmapFactory.decodeResource(
//                    this.resources,
//                    R.drawable.polar_ecg
//                )
                val arrays: EcgPlotArrays = getEcgPlotArrays()
                val ecgValues: DoubleArray = arrays.ecg
                val peakValues: BooleanArray = arrays.peaks
//                ecgPlotter!!.getVisibleSeries().getyVals()
                val sampleCount = ecgValues.size
                val bm: Bitmap = createImage(
                    ECG_SAMPLE_RATE,
//                    logo,
                    patientName,
                    stopTime.toString(),
                    deviceId,
                    deviceFirmware,
                    deviceBatteryLevel,
                    note,
                    deviceStopHr,
                    calculatedStopHr,
                    java.lang.String.format(
                        Locale.US, "%d",
                        qrsPlotter!!.seriesDataPeaks.size()
                    ),
                    java.lang.String.format(
                        Locale.US,
                        "%.1f sec",
//                        sampleCount / ECG_SAMPLE_RATE
                        duration
                    ),
                    ecgValues,
                    peakValues
                )
                bm.compress(Bitmap.CompressFormat.PNG, 80, strm)
                strm.close()
                msg = "Wrote " + docUri.lastPathSegment
                Log.d(TAG, msg)
                AppUtils.infoMsg(this, msg)
            }
            pfd.close()
        } catch (ex: Exception) {
            msg = "Error saving plot"
            Log.e(TAG, msg)
            Log.e(TAG, Log.getStackTraceString(ex))
            AppUtils.excMsg(this, msg, ex)
        }
    }

    /**
     * Finishes the saveData after getting the note.
     * Only saves the ECG recording data.
     * @param note The note.
     */
    private fun saveEcgData(note: String) {
        val prefs = getPreferences(MODE_PRIVATE)
        val treeUriStr = prefs.getString(PREF_TREE_URI, null)
        if (treeUriStr == null) {
            AppUtils.errMsg(this, "There is no data directory set")
            return
        }
        val duration = (stopTime!!.time - startTime!!.time) * MS_TO_SEC
        var msg: String
        val format = "yyyy-MM-dd_HH-mm"
        val df = SimpleDateFormat(format, Locale.US)
        val fileName = "ECG-" + df.format(stopTime!!) + ".csv"
        try {
            val treeUri = Uri.parse(treeUriStr)
            val treeDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
            val docTreeUri = DocumentsContract.buildDocumentUriUsingTree(
                treeUri,
                treeDocumentId
            )
            val resolver = this.contentResolver
            val pfd: ParcelFileDescriptor?
            val docUri = DocumentsContract.createDocument(
                resolver, docTreeUri,
                "text/csv", fileName
            )
            pfd = contentResolver.openFileDescriptor(docUri!!, "w")
            FileWriter(pfd!!.fileDescriptor).use { writer ->
                PrintWriter(writer).use { out ->
                    // Write header
                    val arrays: EcgPlotArrays = getEcgPlotArrays()
                    val ecgValues: DoubleArray = arrays.ecg
                    val peakValues: BooleanArray = arrays.peaks
                    val timestamps: LongArray = arrays.timestamp
                    val peakCount: Int = qrsPlotter!!.seriesDataPeaks.size()
                    val sampleCount = ecgValues.size
                    val durationString = java.lang.String.format(
                        Locale.US, "%.1f sec",
                        duration
//                        sampleCount / ECG_SAMPLE_RATE
                    )
                    out.write(
                        ("application=" + "SamplingApp Version: "
                                + AppUtils.getVersion(this)) + "\n"
                    )
                    out.write("datatype=ECG")
                    out.write(
                        """
                        stoptime=${stopTime.toString()}
                        
                        """.trimIndent()
                    )
                    out.write("duration=$durationString\n")
                    out.write("samplescount=$sampleCount\n")
                    out.write(
                        """
                        ${"samplingrate=$ECG_SAMPLE_RATE"}
                        
                        """.trimIndent()
                    )
                    out.write("stopdevicehr=$deviceStopHr\n")
                    out.write("stopcalculatedhr=$calculatedStopHr\n")
                    out.write("peakscount=$peakCount\n")
                    out.write("devicename=$deviceName\n")
                    out.write("deviceid=$deviceId\n")
                    out.write("battery=$deviceBatteryLevel\n")
                    out.write("firmware=$deviceFirmware\n")
                    out.write("note=$note\n")

                    // Write samples
                    for (i in 0 until sampleCount) {
                        out.write(
                            String.format(
                                Locale.US, "%.3f,%d,%d,%s\n",
                                ecgValues[i],
                                if (peakValues[i]) 1 else 0,
                                timestamps[i],
                                timestampFormat.format(Date(timestamps[i]))
                            )
                        )
                    }
                    out.flush()
                    msg = "Wrote " + docUri.lastPathSegment
                    Log.d(TAG, msg)
                    AppUtils.infoMsg(this, msg)
                }
            }
            pfd.close()
        } catch (ex: java.lang.Exception) {
            msg = "Error writing CSV file"
            Log.e(TAG, msg)
            Log.e(TAG, Log.getStackTraceString(ex))
            AppUtils.excMsg(this, msg, ex)
        }
    }

    /**
     * Finishes the saveData after getting the note.
     * Only saves the PPG recording data that corresponds
     * to the given PPG Plotter.
     * @param note The note.
     * @param ppgPlotter A PPG Plotter.
     */
    private fun savePpgData(note: String, ppgPlotter: PpgPlotter) {
        val prefs = getPreferences(MODE_PRIVATE)
        val treeUriStr = prefs.getString(PREF_TREE_URI, null)
        if (treeUriStr == null) {
            AppUtils.errMsg(this, "There is no data directory set")
            return
        }
        // TODO: DURATION CAN BE INCORRECT. CHECK STOP TIME START TIME USAGE
        val duration = (stopTime!!.time - startTime!!.time) * MS_TO_SEC
        var msg: String
        val format = "yyyy-MM-dd_HH-mm"
        val df = SimpleDateFormat(format, Locale.US)
        val ppgTypeString = when (ppgPlotter.getPpgType()) {
            PpgType.PPG_GREEN -> "PPG Green"
            PpgType.PPG_IR -> "PPG IR"
            PpgType.PPG_RED -> "PPG Red"
        }
        val fileName = ppgTypeString + "-" + df.format(stopTime!!) + ".csv"
        try {
            val treeUri = Uri.parse(treeUriStr)
            val treeDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
            val docTreeUri = DocumentsContract.buildDocumentUriUsingTree(
                treeUri,
                treeDocumentId
            )
            val resolver = this.contentResolver
            val pfd: ParcelFileDescriptor?
            val docUri = DocumentsContract.createDocument(
                resolver, docTreeUri,
                "text/csv", fileName
            )
            pfd = contentResolver.openFileDescriptor(docUri!!, "w")
            FileWriter(pfd!!.fileDescriptor).use { writer ->
                PrintWriter(writer).use { out ->
                    // Write header
                    val arrays: PpgPlotArrays = getPpgPlotArrays(ppgPlotter)
                    val ppgValues: IntArray = arrays.ppg
                    val timestamps: LongArray = arrays.timestamp
                    val sampleCount = ppgValues.size
                    val sampleRate = when (ppgPlotter.getPpgType()) {
                        PpgType.PPG_GREEN -> PPG_GREEN_SAMPLE_RATE
                        PpgType.PPG_IR, PpgType.PPG_RED -> PPG_IR_RED_SAMPLE_RATE
                    }
                    val durationString = java.lang.String.format(
                        Locale.US, "%.1f sec",
                        duration
//                        sampleCount / sampleRate
                    )
                    out.write(
                        ("application=" + "SamplingApp Version: "
                                + AppUtils.getVersion(this)) + "\n"
                    )
                    out.write("datatype=$ppgTypeString")
                    out.write(
                        """
                        stoptime=${stopTime.toString()}
                        
                        """.trimIndent()
                    )
                    out.write("duration=$durationString\n")
                    out.write("samplescount=$sampleCount\n")
                    out.write(
                        """
                        ${"samplingrate=$sampleRate"}
                        
                        """.trimIndent()
                    )
                    out.write("stopcalculatedhr=$calculatedStopHr\n")
                    out.write("note=$note\n")

                    // Write samples
                    for (i in 0 until sampleCount) {
                        out.write(
                            String.format(
                                Locale.US, "%d,%d,%s\n",
                                ppgValues[i],
                                timestamps[i],
                                timestampFormat.format(Date(timestamps[i]))
                            )
                        )
                    }
                    out.flush()
                    msg = "Wrote " + docUri.lastPathSegment
                    Log.d(TAG, msg)
                    AppUtils.infoMsg(this, msg)
                }
            }
            pfd.close()
        } catch (ex: java.lang.Exception) {
            msg = "Error writing CSV file"
            Log.e(TAG, msg)
            Log.e(TAG, Log.getStackTraceString(ex))
            AppUtils.excMsg(this, msg, ex)
        }
    }

//    /**
//     * Finishes the saveData.
//     *
//     * @param saveType The saveType (either DEVICE_HR or QRS_HR).
//     */
//    private fun saveSessionData(saveType: SaveType) {
//        val prefs = getPreferences(MODE_PRIVATE)
//        val treeUriStr = prefs.getString(PREF_TREE_URI, null)
//        if (treeUriStr == null) {
//            AppUtils.errMsg(this, "There is no data directory set")
//            return
//        }
//        var msg: String
//        val format = "yyyy-MM-dd_HH-mm"
//        val df = SimpleDateFormat(format, Locale.US)
//        val fileName: String
//        val dataList: List<HrPlotter.HrRrSessionData>
//        try {
////            if (saveType == SaveType.DEVICE_HR) {
////                fileName = "PolarECG-DeviceHR-" + df.format(stopTime) + ".csv"
////                dataList = hrPlotter!!.hrRrList1
////            } else if (saveType == SaveType.QRS_HR) {
////                fileName = "PolarECG-QRSHR-" + df.format(stopTime) + ".csv"
////                dataList = hrPlotter!!.hrRrList2
////            } else {
////                AppUtils.errMsg(
////                    this,
////                    "Invalid saveType (" + saveType + "0 in " +
////                            "doSaveSessionData"
////                )
////                return
////            }
//            when (saveType) {
//                SaveType.DEVICE_HR -> {
//                    fileName = "PolarECG-DeviceHR-" + df.format(stopTime!!) + ".csv"
//                    dataList = hrPlotter!!.hrRrList1
//                }
//                SaveType.QRS_HR -> {
//                    fileName = "PolarECG-QRSHR-" + df.format(stopTime!!) + ".csv"
//                    dataList = hrPlotter!!.hrRrList2
//                }
//                else -> {
//                    AppUtils.errMsg(
//                        this,
//                        "Invalid saveType (" + saveType + "0 in " +
//                                "doSaveSessionData"
//                    )
//                    return
//                }
//            }
//            val treeUri = Uri.parse(treeUriStr)
//            val treeDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
//            val docTreeUri = DocumentsContract.buildDocumentUriUsingTree(
//                treeUri,
//                treeDocumentId
//            )
//            val resolver = this.contentResolver
//            val pfd: ParcelFileDescriptor?
//            val docUri = DocumentsContract.createDocument(
//                resolver, docTreeUri,
//                "text/csv", fileName
//            )
//            pfd = contentResolver.openFileDescriptor((docUri)!!, "w")
//            FileWriter(pfd!!.fileDescriptor).use { writer ->
//                PrintWriter((writer)).use { out ->
//                    // Write header (None)
//                    // Write samples
//                    for (data: HrPlotter.HrRrSessionData in dataList) {
//                        out.write(data.csvString + "\n")
//                    }
//                    out.flush()
//                    msg = "Wrote " + docUri.lastPathSegment
//                    AppUtils.infoMsg(this, msg)
//                    Log.d(TAG, "    Wrote " + dataList.size + " items")
//                }
//            }
//            pfd.close()
//        } catch (ex: java.lang.Exception) {
//            msg = "Error writing $saveType CSV file"
//            Log.e(TAG, msg)
//            Log.e(TAG, Log.getStackTraceString(ex))
//            AppUtils.excMsg(this, msg, ex)
//        }
//    }

    /**
     * Brings up a system file chooser to get the data directory
     */
    private fun chooseDataDirectory() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        intent.addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION and
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        openDocumentTreeLauncher.launch(intent)
    }

    /**
     * Gets ECG and Peaks and converts them into doubles. This relies on the
     * ecg arrays in EcgPlotter and QRSPlotter being the same and also the
     * respective DataIndex's. The ones in QRSPlotter are used. It also has
     * the peak values in form of binary value of `0` or `1`,
     * and also has timestamp taken from Polar device in `Long` type.
     *
     * @return PlotArrays with the ECG values, binary values that shows
     * whether it is a peak or not, and the timestamp.
     */
    private fun getEcgPlotArrays(): EcgPlotArrays {
        val arrays: EcgPlotArrays
        // Remove any out-of-range values peak values
        qrsPlotter!!.removeOutOfRangePlotPeakValues()
        val ecgValues: LinkedList<Number> = qrsPlotter!!.seriesDataEcg.getyVals()
        val peakValues: LinkedList<Number> = qrsPlotter!!.seriesDataPeaks.getyVals()
        val peakXValues: LinkedList<Number> = qrsPlotter!!.seriesDataPeaks.getxVals()
        val timestampValues: LinkedList<Number> = qrsPlotter!!.seriesTimestamp.getyVals()
        val ecgLength = ecgValues.size
        val peaksLength = peakValues.size
        val ecg = DoubleArray(ecgLength)
        val peaks = BooleanArray(ecgLength)
        val timestamp = LongArray(ecgLength)
        for ((i, value) in ecgValues.withIndex()) {
            ecg[i] = value.toDouble()
            peaks[i] = false
            timestamp[i] = timestampValues[i].toLong()
        }
//        // The peak values correspond to an index related to when they came in.
//        // This is different from the index in the arrays, which goes from 0
//        // to no more than N_TOTAL_VISIBLE_POINTS.
//        var offset = 0
//        if (qrsPlotter!!.dataIndex > N_TOTAL_VISIBLE_POINTS) {
//            offset = (-(qrsPlotter!!.dataIndex - N_TOTAL_VISIBLE_POINTS)).toInt()
//        }

        var index: Int
        for (j in 0 until peaksLength) {
            index = peakXValues[j].toInt()
//            + offset
//            Log.d(TAG, String.format("j=%d indx=%d xval=%d",
//            j, indx, peakxvals.get(j).intValue()));
            peaks[index] = true
        }
        arrays = EcgPlotArrays(ecg
            , peaks
            , timestamp)
        return arrays
    }

    /**
     * Gets PPG and timestamp of a given PpgPlotter as plot arrays.
     * This relies on the 'data' series and timestamp series
     * in the given PpgPlotter having DataIndex
     * that correspond with each other.
     *
     * @param ppgPlotter A PpgPlotter object
     * @return PlotArrays with the Ppg values and the timestamp.
     */
    private fun getPpgPlotArrays(ppgPlotter: PpgPlotter): PpgPlotArrays {
        val arrays: PpgPlotArrays
        val ppgValues: LinkedList<Number> = ppgPlotter.getDataSeries().getyVals()
        val timestampValues: LinkedList<Number> = ppgPlotter.getTimestampSeries().getyVals()
        val length = ppgValues.size
        val ppg = IntArray(length)
        val timestamp = LongArray(length)
        for ((i, value) in ppgValues.withIndex()) {
            ppg[i] = value.toInt()
            timestamp[i] = timestampValues[i].toLong()
        }
        arrays = PpgPlotArrays(ppg, timestamp)
        return arrays
    }

    /**
     * Toggles the tracker for *all* PPG types.
     * If a PPG type is given, toggles the tracker
     * for *only* the given type.
     *
     * This function reads current `isRecording` status
     * when toggling *all* PPG tracker.
     * Turns streaming off when not recording.
     * Turns streaming on when it is currently recording.
     *
     * When a PPG type is given, toggling uses that PPG
     * specific status to determine whether to on or off.
     *
     * @param ppgType Optional. The PPG type to toggle the tracker.
     * @see PpgType
     */
    private fun togglePpgTracker(ppgType: PpgType? = null) {
        Log.d(
            TAG, this.javaClass.simpleName + " togglePpgTracker:"
                    + " connectedNode=" + connectedNode
        )
        if (connectedNode.isNullOrEmpty()) {
            AppUtils.errMsg(this,
                "togglePpgTracker: Wear Device is not connected yet")
            return
        }

        // conditional toggling when ppg type is given
        // return early if condition met
        when (ppgType) {
            PpgType.PPG_GREEN -> {
                val message = Message(NAME, ActivityCode.START_ACTIVITY, TOGGLE_ACTIVITY)
                sendMessage(message, MessagePath.DATA_PPG_GREEN)
                toggleState(PpgType.PPG_GREEN)
                return
            }
            PpgType.PPG_IR -> {
                val message = Message(NAME, ActivityCode.START_ACTIVITY, TOGGLE_ACTIVITY)
                sendMessage(message, MessagePath.DATA_PPG_IR)
                toggleState(PpgType.PPG_IR)
                return
            }
            PpgType.PPG_RED -> {
                val message = Message(NAME, ActivityCode.START_ACTIVITY, TOGGLE_ACTIVITY)
                sendMessage(message, MessagePath.DATA_PPG_RED)
                toggleState(PpgType.PPG_RED)
                return
            }
            else -> {}
        }

        if (isRecording) {
            // if recording is on, turn on all ppg tracker
            val message = Message(NAME, ActivityCode.START_ACTIVITY)
            sendMessage(message, MessagePath.COMMAND)
            toggleState(true)
        } else {
            // if not recording, turn off all ppg tracker
            val message = Message(NAME, ActivityCode.STOP_ACTIVITY)
            sendMessage(message, MessagePath.COMMAND)
            toggleState(false)
        }
    }

    /**
     * Toggles streaming for ECG.
     *
     * Turns streaming on when not running.
     *
     * Turns streaming off when it is currently running.
     */
    private fun toggleEcgStream() {
        Log.d(
            TAG, this.javaClass.simpleName + " streamECG:"
                    + " EcgDisposable=" + ecgDisposable
                    + " isConnected=" + isPolarDeviceConnected
        )
        if (!isPolarDeviceConnected) {
            AppUtils.errMsg(this, "streamECG: Device is not connected yet")
            return
        }
        logEpochInfo("UTC")
        if (ecgDisposable == null) {
            // Set the local time to get correct timestamps. Polar H10 apparently
            // resets its time to 01:01:2019 00:00:00 when connected to strap
            val timeZone = TimeZone.getTimeZone("UTC")
            val calNow = Calendar.getInstance(timeZone)
            Log.d(TAG, "setLocalTime to " + calNow.time)
            polarApi!!.setLocalTime(deviceId, calNow)
            ecgDisposable = polarApi!!.setLocalTime(deviceId, calNow)
                .andThen(
                    polarApi!!.requestStreamSettings(
                        deviceId,
                        DeviceStreamingFeature.ECG
                    )
                )
                .toFlowable()
                .flatMap { sensorSetting: PolarSensorSetting ->
                    polarApi!!.startEcgStreaming(
                        deviceId,
                        sensorSetting.maxSettings()
                    )
                }.observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { polarEcgData: PolarEcgData ->
//                                        logTimestampInfo(polarEcgData);
                        if (qrsDetector == null) {
                            qrsDetector = QrsDetector(this@MainActivity)
                        }
                        //                                        logEcgDataInfo(polarEcgData);
                        qrsDetector!!.process(polarEcgData)
                        // Update the elapsed time
                        val elapsed: Double = ecgPlotter!!.getDataIndex() / ECG_SAMPLE_RATE
                        textEcgTime.text = getString(R.string.elapsed_time, elapsed)
                    },
                    { throwable: Throwable ->
                        Log.e(
                            TAG, "ECG Error: "
                                    + throwable.localizedMessage,
                            throwable
                        )
                        AppUtils.excMsg(
                            this@MainActivity, "ECG " +
                                    "Error: ",
                            throwable
                        )
                        ecgDisposable = null
                    },
                    {
                        Log.d(
                            TAG,
                            "ECG streaming complete"
                        )
                    }
                )
        } else {
            // NOTE stops streaming if it is "running"
            ecgDisposable?.dispose()
            ecgDisposable = null
            if (qrsDetector != null) qrsDetector = null
        }
    }

    private fun onDataArrived(dataEvent: DataEvent) {
        when (dataEvent.dataItem.uri.path) {
            MessagePath.DATA_HR -> {
                val heartData = Gson().fromJson(String(dataEvent.dataItem.data),
                    HeartData::class.java)
                Log.d(TAG, "Heart Rate data received\n" +
                        "HR: ${heartData.hr}\n" +
                        "IBI: ${heartData.ibi}\n" +
                        "Timestamp: ${heartData.timestamp}")
//                    runOnUiThread {
//                        textWearHr.text = data.hr.toString()
//                        textWearIbi.text = data.ibi.toString()
//                        textWearTimestamp.text = data.timestamp
//                    }
            }
            MessagePath.DATA_PPG_GREEN -> {
                val ppgGreenData = Gson().fromJson(String(dataEvent.dataItem.data),
                    PpgData::class.java)
                Log.d(TAG, "PPG Green data batch received\n" +
                        "Data count: ${ppgGreenData.size}\n" +
                        "PPG Green Value: ${ppgGreenData.ppgValues}\n" +
                        "Timestamp: ${ppgGreenData.timestamps}")
                for (i in 0 until ppgGreenData.size) {
                    ppgGreenPlotter?.addValues(
                        ppgGreenData.ppgValues[i],
                        ppgGreenData.timestamps[i])
                    Log.d(TAG, "Data #$i\n" +
                            "PPG Value: ${ppgGreenData.ppgValues[i]}\n" +
                            "Timestamp: ${ppgGreenData.timestamps[i]}")
                }
                ppgGreenValueNumber += ppgGreenData.size

                // Update the view
                val elapsed: Double = ppgGreenPlotter!!.getDataIndex() / PPG_GREEN_SAMPLE_RATE
                runOnUiThread {
                    textPpgGreenStatus.text = getString(R.string.ppg_green_status,
                        ppgGreenValueNumber.toString())
                    textStatusContainerTitle.text = getString(R.string.elapsed_time, elapsed)
                }
            }
            MessagePath.DATA_PPG_IR -> {
                val ppgIrData = Gson().fromJson(String(dataEvent.dataItem.data),
                    PpgData::class.java)
                Log.d(TAG, "PPG IR data batch received\n" +
                        "Data count: ${ppgIrData.size}")
                for (i in 0 until ppgIrData.size) {
                    ppgIrPlotter?.addValues(
                        ppgIrData.ppgValues[i],
                        ppgIrData.timestamps[i])
                    Log.d(TAG, "Data #$i\n" +
                            "PPG Value: ${ppgIrData.ppgValues[i]}\n" +
                            "Timestamp: ${ppgIrData.timestamps[i]}")
                }
                ppgIrValueNumber += ppgIrData.size

                // Update the view
                val elapsed: Double = ppgIrPlotter!!.getDataIndex() / PPG_IR_RED_SAMPLE_RATE
                runOnUiThread {
                    textPpgIrStatus.text = getString(R.string.ppg_ir_status,
                        ppgIrValueNumber.toString())
                    textStatusContainerTitle.text = getString(R.string.elapsed_time, elapsed)
                }
            }
            MessagePath.DATA_PPG_RED -> {
                val ppgRedData = Gson().fromJson(String(dataEvent.dataItem.data),
                    PpgData::class.java)
                Log.d(TAG, "PPG Red data batch received\n" +
                        "Data count: ${ppgRedData.size}\n" +
                        "PPG Red Value: ${ppgRedData.ppgValues}\n" +
                        "Timestamp: ${ppgRedData.timestamps}")
                for (i in 0 until ppgRedData.size) {
                    ppgRedPlotter?.addValues(
                        ppgRedData.ppgValues[i],
                        ppgRedData.timestamps[i])
                    Log.d(TAG, "Data #$i\n" +
                            "PPG Value: ${ppgRedData.ppgValues[i]}\n" +
                            "Timestamp: ${ppgRedData.timestamps[i]}")
                }
                ppgRedValueNumber += ppgRedData.size

                // Update the view
                val elapsed: Double = ppgRedPlotter!!.getDataIndex() / PPG_IR_RED_SAMPLE_RATE
                runOnUiThread {
                    textPpgRedStatus.text = getString(R.string.ppg_red_status,
                        ppgRedValueNumber.toString())
                    textStatusContainerTitle.text = getString(R.string.elapsed_time, elapsed)
                }
            }
        }
//            val receivedData = Gson().fromJson(String(data[0].dataItem.data), HeartData::class.java)
//            runOnUiThread {
//                textWearHr.text = receivedData.hr.toString()
//                textWearIbi.text = receivedData.ibi.toString()
//                textWearTimestamp.text = receivedData.timestamp
//            }
    }

    private fun onMessageArrived(messagePath: String) {
        wearMessage?.let {
            when (messagePath) {
                MessagePath.COMMAND -> {
                    if (it.code == ActivityCode.STOP_ACTIVITY) { // reset this module's state
                        toggleState(0)
                    }
                }
                MessagePath.REQUEST -> {
                    TODO("Not yet implemented")
                }
                MessagePath.INFO -> {
                    when (it.code) {
                        ActivityCode.START_ACTIVITY -> { // get Wear's current state
                            toggleState(1)
                        }
                        ActivityCode.DO_NOTHING -> {
                            toggleState(0)
                        }
                    }
                }
            }
        }
    }

    private fun sendMessage(message: Message, path: String) {
        val gson = Gson()
        connectedNode?.forEach { node ->
            val bytes = gson.toJson(message).toByteArray()
            Wearable.MessageApi.sendMessage(client, node.id, path, bytes)
//            Toast.makeText(applicationContext, "Message sent!", Toast.LENGTH_LONG).show()
        }
    }

    private fun setMessageCode(message: Message, forceCode: Int = 99) {
        if (forceCode != 99) {
            message.code = forceCode
        }
        else if (appState == 0) {
            message.code = ActivityCode.START_ACTIVITY
        }
        else if (appState == 1) {
            message.code = ActivityCode.STOP_ACTIVITY
        }
    }

    /**
     * Toggles the running state of *all* PPG Tracker
     *
     * Pass a parameter to force the state
     * to a certain value.
     *
     * @param forceState Optional. A value to be used as the state
     * if present.
     */
    private fun toggleState(forceState: Boolean? = null) {
        if (forceState != null) {
            isPpgGreenRunning = forceState
            isPpgIrRunning = forceState
            isPpgRedRunning = forceState
            invalidatePpgState()
            return
        }
        isPpgGreenRunning = !isPpgGreenRunning
        isPpgIrRunning = !isPpgIrRunning
        isPpgRedRunning = !isPpgRedRunning
        invalidatePpgState()
    }

    /**
     * Toggles the running state of PPG Tracker of
     * the given PPG type.
     *
     * Pass a second parameter to force the state
     * to a certain value.
     *
     * @param ppgType The type of PPG to toggle the state
     * @param forceState Optional. A value to be used as the state
     * if present.
     * @see PpgType
     */
    private fun toggleState(ppgType: PpgType, forceState: Boolean? = null) {
        when (ppgType) {
            PpgType.PPG_GREEN -> {
                if (forceState != null) {
                    isPpgGreenRunning = forceState
                    return
                }
                isPpgGreenRunning = !isPpgGreenRunning

                if (isPpgGreenRunning) {
                    runOnUiThread {
                        textPpgGreenStatus.text = getString(R.string.ppg_green_status,
                            getString(R.string.status_running))
                    }
                } else {
                    runOnUiThread {
                        textPpgGreenStatus.text = getString(R.string.ppg_green_status,
                            getString(R.string.status_stopped))
                    }
                }
            }
            PpgType.PPG_IR -> {
                if (forceState != null) {
                    isPpgIrRunning = forceState
                    return
                }
                isPpgIrRunning = !isPpgIrRunning

                if (isPpgIrRunning) {
                    runOnUiThread {
                        textPpgIrStatus.text = getString(R.string.ppg_ir_status,
                            getString(R.string.status_running))
                    }
                } else {
                    runOnUiThread {
                        textPpgIrStatus.text = getString(R.string.ppg_ir_status,
                            getString(R.string.status_stopped))
                    }
                }
            }
            PpgType.PPG_RED -> {
                if (forceState != null) {
                    isPpgRedRunning = forceState
                    return
                }
                isPpgRedRunning = !isPpgRedRunning

                if (isPpgRedRunning) {
                    runOnUiThread {
                        textPpgRedStatus.text = getString(R.string.ppg_red_status,
                            getString(R.string.status_running))
                    }
                } else {
                    runOnUiThread {
                        textPpgRedStatus.text = getString(R.string.ppg_red_status,
                            getString(R.string.status_stopped))
                    }
                }
            }
        }
    }

    private fun toggleState(forceCode: Int = 99) {
        if (forceCode != 99) {
            appState = forceCode
        }
        else if (appState == 0) {
            appState = 1
        }
        else if (appState == 1) {
            appState = 0
        }
//        runOnUiThread {
//            if (appState == 0) {
//                buttonMain.text = getString(R.string.button_start)
//                textWearStatus.text = getString(R.string.status_stopped)
//            }
//            if (appState == 1) {
//                buttonMain.text = getString(R.string.button_stop)
//                textWearStatus.text = getString(R.string.status_running)
//            }
//            textTooltip.text = appState.toString()
//        }
        Log.d(TAG,"stateNum changed to: $appState")
    }

    private fun displayPolarInfo() {
        val msg = StringBuilder()
        msg.append("Polar Device Name: ").append(deviceName).append("\n")
        msg.append("Polar Device Id: ").append(deviceId).append("\n")
        msg.append("Polar Device Address: ").append(deviceAddress).append("\n")
        msg.append("Polar Device Firmware: ").append(deviceFirmware).append("\n")
        msg.append("Polar Device Battery Level: ").append(deviceBatteryLevel).append("\n")
        msg.append("Polar API Connected: ").append(polarApi != null).append("\n")
        msg.append("Polar Device Connected: ").append(isPolarDeviceConnected).append("\n")
        msg.append("Recording: ").append(isRecording).append("\n")
        msg.append("Receiving ECG: ").append(ecgDisposable != null)
            .append("\n")
        if (ecgPlotter != null && ecgPlotter!!.getVisibleSeries() != null && ecgPlotter!!.getVisibleSeries()
                .getyVals() != null
        ) {
            val elapsed: Double = ecgPlotter!!.getDataIndex() / ECG_SAMPLE_RATE
            msg.append("Elapsed Time: ")
                .append(getString(R.string.elapsed_time, elapsed))
                .append("\n")
            msg.append("Points plotted: ")
                .append(ecgPlotter!!.getVisibleSeries().getyVals().size)
                .append("\n")
        }
        var versionName: String? = "NA"
        try {
            versionName = if (Build.VERSION.SDK_INT >= 33) {
                packageManager.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(0)
                ).versionName
            } else {
                packageManager.getPackageInfo(
                    packageName,
                    0
                ).versionName
            }
        } catch (ex: java.lang.Exception) {
            // Do nothing
        }
        msg.append("ECG-App Version: ").append(versionName).append("\n")
        msg.append("Polar BLE API Version: ").append(versionInfo()).append("\n")
        msg.append(UriUtils.getRequestedPermissionsInfo(this))
        val prefs = getPreferences(MODE_PRIVATE)
        val treeUriStr = prefs.getString(PREF_TREE_URI, null)
        if (treeUriStr == null) {
            msg.append("Data Directory: Not set")
        } else {
            val treeUri = Uri.parse(treeUriStr)
            msg.append("Data Directory: ").append(treeUri.path)
        }
        AppUtils.infoMsg(this, msg.toString())
    }

    companion object {
        private const val TAG = "Mobile.MainActivity"
        private const val NAME = PHONE_APP
        private val shortDateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
        private val timestampFormat = SimpleDateFormat("dd MMM yyyy HH:mm:ss:SSS Z")
        // Currently the sampling rate for ECG is fixed at 130
//        private const val MAX_DEVICES = 3
        const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS =
            mutableListOf(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION
            ).apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R) {
                    add(Manifest.permission.BLUETOOTH)
                    add(Manifest.permission.BLUETOOTH_ADMIN)
                }
            }.apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    add(Manifest.permission.BLUETOOTH_SCAN)
                    add(Manifest.permission.BLUETOOTH_CONNECT)
                }
            }.apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                }
            }.toTypedArray()
    }

    private fun logEpochInfo(timeZoneString: String) {
        val sdf = SimpleDateFormat("dd:MM:yyyy HH:mm:ss", Locale.US)
        val sdf2 = SimpleDateFormat("EEE MMM dd HH:mm:ss zzz yyyy", Locale.US)
        /// Set the timezone
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        sdf2.timeZone = TimeZone.getTimeZone("UTC")
        try {
            val epoch = sdf.parse("01:01:2000 00:00:00")
            val epoch1 = sdf.parse("01:01:2019 00:00:00")
            if (epoch != null && epoch1 != null) {
                Log.d(
                    TAG, "epoch=" + sdf2.format(epoch)
                            + " epoch1=" + sdf2.format(epoch1)
                            + " " + epoch.time
                            + " " + epoch1.time
                            + " " + (epoch1.time - epoch.time)
                            + timeZoneString
                )
            } else {
                Log.d(TAG, "epoch=null and/or epoch2=null")
            }
        } catch (ex: java.lang.Exception) {
            Log.e(TAG, "Error parsing date", ex)
        }
    }

    private fun timestampInfo(ts: Long, name: String?): String? {
        val sdf = SimpleDateFormat("MMM dd yyyy HH:mm:ss zzz", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        val sdf1 = SimpleDateFormat("MMM dd yyyy HH:mm:ss zzz", Locale.US)
        val date = Date(ts)
        return String.format(
            Locale.US, "%15d %-8s %.2f years  %s %s",
            ts, name, msToYears(ts),
            sdf.format(date), sdf1.format(date)
        )
    }

    private fun msToYears(longVal: Long): Double {
        return longVal / (1000.0 * 60.0 * 60.0 * 24.0 * 365.0)
    }
}