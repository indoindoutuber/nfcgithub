package com.gol.nfcgithub
import android.Manifest
import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.LocationManager
import android.nfc.NdefMessage
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.GridLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.io.IOException

import okhttp3.*
import java.lang.Integer.parseInt
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter


class MainActivity : AppCompatActivity() {

    private var nfcAdapter: NfcAdapter? = null
    private var pendingIntent: PendingIntent? = null

    // Layout elements
    private lateinit var nfcStatusText: TextView
    private lateinit var tagIdText: TextView
    private lateinit var locationTextView: TextView
    private lateinit var timestampTextView: TextView
    private lateinit var httpTextView: TextView

    private lateinit var holeTextView: TextView // 추가된 홀 텍스트뷰

    private lateinit var selectedHoleTextView: TextView
    private var hole: Int = 0

    private var selectedButton: Button? = null
    private val selectedButtons = mutableMapOf<Int, Button>()
    private val selectedNumbers = mutableMapOf<Int, Int>() // 열 번호 -> 숫자 저장


    private var distance: String = "0"

    private var selectedHundreds = 0
    private var selectedTens = 0

    // Keep track of the previously selected buttons
    var selectedHundredsButton: Button? = null
    var selectedTensButton: Button? = null
    var selectedOnesButton: Button? = null


    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1000
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Bind layout elements
        nfcStatusText = findViewById(R.id.nfcStatusText)
        tagIdText = findViewById(R.id.tagIdText)
        locationTextView = findViewById(R.id.locationTextView)
        timestampTextView = findViewById(R.id.timestampTextView)
        httpTextView  = findViewById(R.id.httpTextView)

        // 상단의 '홀 :' TextView 연결
        holeTextView = findViewById(R.id.holeTextView)

        /*
        // 라디오 버튼 0~17번 처리
        for (i in 1..18) {
            val radioId = resources.getIdentifier("radio$i", "id", packageName)
            val radio = findViewById<RadioButton>(radioId)
            if (radio != null) {
                radio.setOnClickListener {
                    holeTextView.text = "홀 : $i"
                }
            } else {
                Log.e("MainActivity", "RadioButton with ID radio$i not found.")
            }
        }*/

        val radioGroup1: RadioGroup = findViewById(R.id.holeRadioGroup1)
        val radioGroup2: RadioGroup = findViewById(R.id.holeRadioGroup2)

        // RadioGroup listener
        val radioGroupListener = RadioGroup.OnCheckedChangeListener { group, checkedId ->
            if (checkedId != -1) {
                // Uncheck any radio buttons in the other group
                if (group == radioGroup1) {
                    radioGroup2.clearCheck()
                } else {
                    radioGroup1.clearCheck()
                }

                // Get the selected hole number
                hole = when (checkedId) {
                    R.id.hole1 -> 1
                    R.id.hole2 -> 2
                    R.id.hole3 -> 3
                    R.id.hole4 -> 4
                    R.id.hole5 -> 5
                    R.id.hole6 -> 6
                    R.id.hole7 -> 7
                    R.id.hole8 -> 8
                    R.id.hole9 -> 9
                    R.id.hole10 -> 10
                    R.id.hole11 -> 11
                    R.id.hole12 -> 12
                    R.id.hole13 -> 13
                    R.id.hole14 -> 14
                    R.id.hole15 -> 15
                    R.id.hole16 -> 16
                    R.id.hole17 -> 17
                    R.id.hole18 -> 18
                    else -> 0 // Handle default case
                }
                holeTextView.text = "홀 : $hole"
                //selectedHoleTextView.text = "${hole}홀 선택됨"
            }
        }

        radioGroup1.setOnCheckedChangeListener(radioGroupListener)
        radioGroup2.setOnCheckedChangeListener(radioGroupListener)


        // Initialize the NFC adapter
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        // Set the initial status of NFC
        if (nfcAdapter == null) {
            nfcStatusText.text = getString(R.string.nfc_not_supported)
            Toast.makeText(this, getString(R.string.nfc_not_supported_toast), Toast.LENGTH_LONG).show()
            return
        } else if (!nfcAdapter!!.isEnabled) {
            nfcStatusText.text = getString(R.string.nfc_disabled)
            Toast.makeText(this, getString(R.string.nfc_disabled_toast), Toast.LENGTH_LONG).show()
            return
        } else {
            nfcStatusText.text = getString(R.string.nfc_enabled)
        }

        // Configure the pending intent for NFC foreground dispatch
        pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_MUTABLE
        )

        // 위치 권한 확인 및 요청
        checkAndRequestLocationPermission()

        val sendButton = findViewById<Button>(R.id.send)
        sendButton.setOnClickListener {
            // 버튼 눌렀을 때 실행할 코드
            // 앱이 포그라운드에 있을 때
            var _data = "HOLECUP"
            if (isAppInForeground()) {
                // UI 업데이트
                updateUI(_data, hole)
                //Toast.makeText(this, "Fore " + data, Toast.LENGTH_LONG).show()
            } else {
                // 백그라운드에서 처리
                showToastInBackground(_data)
            }
            val location = getLocation()
            locationTextView.text = "위치: ${location.first}, ${location.second}"
            val timestamp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            } else {
                TODO("VERSION.SDK_INT < O")
            }
            Log.d("TIME_LOG", "현재 시간: $timestamp")
            timestampTextView.text = "시간: $timestamp"

            sendHttpPost(_data, hole, location, "0", timestamp)

            Toast.makeText(this, "Send 버튼이 눌렸습니다", Toast.LENGTH_SHORT).show()

        }

        /*
        // 100의 자리 버튼 등록
        for (i in 0..9) {
            val id = resources.getIdentifier("h100_$i", "id", packageName)
            val btn = findViewById<Button>(id)
            btn.setOnClickListener {
                selectedHundreds = i
            }
        }

        // 10의 자리 버튼 등록
        for (i in 0..9) {
            val id = resources.getIdentifier("h10_$i", "id", packageName)
            val btn = findViewById<Button>(id)
            btn.setOnClickListener {
                selectedTens = i
            }
        }

        // 1의 자리 버튼 등록 & Toast 출력
        for (i in 0..9) {
            val id = resources.getIdentifier("h1_$i", "id", packageName)
            val btn = findViewById<Button>(id)
            btn.setOnClickListener {
                val fullNumber = selectedHundreds * 100 + selectedTens * 10 + i

                distance = fullNumber.toString()
                Toast.makeText(this, fullNumber.toString(), Toast.LENGTH_SHORT).show()
            }
        }
*/
        // 100's place button click listeners
        for (i in 0..9) {
            val id = resources.getIdentifier("h100_$i", "id", packageName)
            val btn = findViewById<Button>(id)
            btn.setOnClickListener {
                // If a button was previously selected, reset its color
                selectedHundredsButton?.setBackgroundColor(Color.parseColor("#3A6FE5")) // Original color for hundreds

                // Set the new selected button's color
                btn.setBackgroundColor(Color.parseColor("#FF6347")) // New color (e.g., tomato red)

                // Update the selected hundreds value
                selectedHundreds = i
                selectedHundredsButton = btn // Update the reference to the newly selected button
            }
        }

// 10's place button click listeners
        for (i in 0..9) {
            val id = resources.getIdentifier("h10_$i", "id", packageName)
            val btn = findViewById<Button>(id)
            btn.setOnClickListener {
                // If a button was previously selected, reset its color
                selectedTensButton?.setBackgroundColor(Color.parseColor("#32C858")) // Original color for tens

                // Set the new selected button's color
                btn.setBackgroundColor(Color.parseColor("#FF6347")) // New color (e.g., tomato red)

                // Update the selected tens value
                selectedTens = i
                selectedTensButton = btn // Update the reference to the newly selected button
            }
        }

// 1's place button click listeners
        for (i in 0..9) {
            val id = resources.getIdentifier("h1_$i", "id", packageName)
            val btn = findViewById<Button>(id)
            btn.setOnClickListener {
                // If a button was previously selected, reset its color
                selectedOnesButton?.setBackgroundColor(Color.parseColor("#FFA500")) // Original color for ones

                // Set the new selected button's color
                btn.setBackgroundColor(Color.parseColor("#FF6347")) // New color (e.g., tomato red)

                // Calculate the full number and update the distance variable
                val fullNumber = selectedHundreds * 100 + selectedTens * 10 + i
                distance = fullNumber.toString()

                // Display the number using Toast
                Toast.makeText(this, fullNumber.toString(), Toast.LENGTH_SHORT).show()

                // Update the selected ones value
                selectedOnesButton = btn // Update the reference to the newly selected button
            }
        }


    } //onCreate End

    private fun onHoleButtonClick(clickedButton: Button) {
        selectedButton?.isSelected = false
        clickedButton.isSelected = true
        selectedButton = clickedButton
    }

    private fun setupButtons() {
        val gridLayout = findViewById<GridLayout>(R.id.numberGrid)

        for (i in 0 until gridLayout.childCount) {
            val button = gridLayout.getChildAt(i) as? Button ?: continue

            button.setOnClickListener {
                val number = button.text.toString().toInt()
                val tag = resources.getResourceEntryName(button.id)
                val column = tag.split("_").last().toInt() // 열 번호 추출 (1,2,3)

                // 기존 선택된 버튼이 있다면 초기화
                selectedButtons[column]?.let {
                    it.setBackgroundColor(ContextCompat.getColor(this, android.R.color.darker_gray))
                }

                // 현재 버튼 색 변경 및 저장
                button.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_blue_light))
                selectedButtons[column] = button
                selectedNumbers[column] = number

                // 3열 버튼이 눌렸을 경우
                if (column == 3) {
                    val hundreds = selectedNumbers[1] ?: return@setOnClickListener
                    val tens = selectedNumbers[2] ?: return@setOnClickListener
                    val ones = selectedNumbers[3] ?: return@setOnClickListener

                    val result = hundreds * 100 + tens * 10 + ones
                    distance = result.toString()
                    Toast.makeText(this, "숫자: $result", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setupGridSelection() {
        val gridLayout = findViewById<GridLayout>(R.id.numberGrid)

        // 열 번호 → 리스트로 묶기
        val columns = arrayOf(
            mutableListOf<Button>(), // column 0
            mutableListOf<Button>(), // column 1
            mutableListOf<Button>()  // column 2
        )

        // 버튼들을 분류
        for (i in 0 until gridLayout.childCount) {
            val view = gridLayout.getChildAt(i)
            if (view is Button) {
                val column = i % 3
                columns[column].add(view)
            }
        }

        // 각 버튼에 클릭 리스너 설정
        columns.forEachIndexed { columnIndex, buttonList ->
            buttonList.forEach { button ->
                button.setOnClickListener {
                    // 이전에 선택된 버튼이 있다면 색상 원래대로
                    selectedButtons[columnIndex]?.setBackgroundColor(Color.LTGRAY)

                    // 현재 버튼을 선택 상태로 변경
                    button.setBackgroundColor(Color.GREEN)
                    selectedButtons[columnIndex] = button
                }

                // 초기 배경색 설정
                button.setBackgroundColor(Color.LTGRAY)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Enable NFC foreground dispatch
        nfcAdapter?.enableForegroundDispatch(this, pendingIntent, null, null)

    }

    override fun onPause() {
        super.onPause()
        // Disable NFC foreground dispatch
        nfcAdapter?.disableForegroundDispatch(this)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)

        // Detect NFC tag
        // Doesnot work for Ali NFC tag
        /*
        if (NfcAdapter.ACTION_TAG_DISCOVERED == intent?.action) {
            val tag: Tag? = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
            }
            val tagId = tag?.id?.joinToString(separator = "") { String.format("%02X", it) }
            if (tagId != null) {
                tagIdText.text = getString(R.string.tag_id_detected, tagId)
                Toast.makeText(this, getString(R.string.tag_detected_toast, tagId), Toast.LENGTH_LONG).show()
            } else {
                tagIdText.text = getString(R.string.tag_not_detected)
                Toast.makeText(this, getString(R.string.tag_not_detected_toast), Toast.LENGTH_LONG).show()
            }
        }*/

        // This is Real
        //Log.d("NFC_DEBUG", "onNewIntent called: ${intent.action}")
        if (NfcAdapter.ACTION_NDEF_DISCOVERED == intent?.action) {
            intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)?.also { rawMessages ->
                val messages: List<NdefMessage> = rawMessages.map { it as NdefMessage }
                //processNfcMessage(messages[0])
                val payload = messages[0].records[0].payload
                // 언어 코드 길이를 가져옵니다 (첫 번째 바이트)
                val languageCodeLength = payload[0].toInt()
                // 실제 텍스트 시작 위치를 계산합니다
                val textStartIndex = 1 + languageCodeLength
                // 순수한 텍스트만 추출합니다
                val pureText = String(payload, textStartIndex, payload.size - textStartIndex)

                // 여기서 pureText를 처리합니다
                processNfcData(pureText)
                //Toast.makeText(this, pureText, Toast.LENGTH_LONG).show()
                //tagIdText.text = pureText

            }

        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun processNfcData(data: String) {
        // 앱이 포그라운드에 있을 때
        if (isAppInForeground()) {
            // UI 업데이트
            updateUI(data, hole)
            Toast.makeText(this, "Fore " + data, Toast.LENGTH_LONG).show()
        } else {
            // 백그라운드에서 처리
            showToastInBackground(data)
        }
        val location = getLocation()
        locationTextView.text = "위치: ${location.first}, ${location.second}"
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        Log.d("TIME_LOG", "현재 시간: $timestamp")
        timestampTextView.text = "시간: $timestamp"

        sendHttpPost(data, hole, location, distance, timestamp)
    }

    private fun isAppInForeground(): Boolean {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val appProcesses = activityManager.runningAppProcesses ?: return false
        val packageName = packageName
        for (appProcess in appProcesses) {
            if (appProcess.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND && appProcess.processName == packageName) {
                return true
            }
        }
        return false
    }

    private fun updateUI(data: String, hole:Int) {
        // UI 업데이트 로직
        findViewById<TextView>(R.id.tagIdText).text = data
        findViewById<TextView>(R.id.holeTextView).text =  "홀 : $hole"
    }

    private fun showToastInBackground(message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun checkAndRequestLocationPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        } else {
            // 이미 권한이 있는 경우 위치 정보 가져오기
            updateLocationInfo()
        }
    }

    private fun updateLocationInfo() {
        val location = getLocation()
        locationTextView.text = "위치: ${location.first}, ${location.second}"
    }

    private fun getLocation(): Pair<Double, Double> {
        val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return Pair(0.0, 0.0)
        }

        val location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        return Pair(location?.latitude ?: 0.0, location?.longitude ?: 0.0)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // 권한이 승인된 경우
                    updateLocationInfo()
                } else {
                    // 권한이 거부된 경우
                    Toast.makeText(this, "위치 권한이 필요합니다.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun sendHttpPost(nfcContent: String, _hole:Int, location: Pair<Double, Double>, _distance:String, timestamp: String) {
        val client = OkHttpClient()
        var message = _hole.toString() + "/" + nfcContent + "/" + location.first.toString()  + "/" + location.second.toString()  + "/"+ _distance + "/" + timestamp
        val requestBody = FormBody.Builder()
            .add("message", message).build()

        /*
        val requestBody = FormBody.Builder()
            .add("nfcContent", nfcContent)
            .add("latitude", location.first.toString())
            .add("longitude", location.second.toString())
            .add("timestamp", timestamp)
            .build()
        */
        val request = Request.Builder()
            .url("http://a.duckdns.org:1234/nfc")
            .post(requestBody)
            .build()

        Log.d("NFC DEBUG HTTP to ", request.url.toString())
        Log.d("NFC DEBUG HTTP to ", message)

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "데이터 전송 실패", Toast.LENGTH_SHORT).show()
                    httpTextView.text = "전송 실패 : ${e.toString()}"
                    Log.d("NFC DEBUG HTTP",e.toString())
                }
            }

            override fun onResponse(call: Call, response: Response) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "데이터 전송 성공 : " + response.toString(), Toast.LENGTH_SHORT).show()
                    Log.d("NFC DEBUG HTTP",response.toString())
                    httpTextView.text = "전송 성공 : ${response.toString()}"
                }
            }
        })
    }

}
