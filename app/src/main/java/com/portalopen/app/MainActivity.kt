package com.portalopen.app

import android.app.Activity
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import androidx.window.java.layout.WindowInfoTrackerCallbackAdapter
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo
import androidx.core.util.Consumer
import kotlin.math.roundToInt

class MainActivity : Activity() {
    private lateinit var preview: PortalPreviewView
    private lateinit var sensorStatus: TextView
    private lateinit var detectionLog: TextView
    private lateinit var progressLabel: TextView
    private lateinit var hingeSwitch: Switch
    private lateinit var seekBar: SeekBar
    private lateinit var windowAdapter: WindowInfoTrackerCallbackAdapter
    private var posture = "정보 수신 중"
    private var hingeDegrees: Float? = null
    private var autoHinge = false
    private val photoRequestCode = 1903
    private val sensorManager by lazy { getSystemService(SENSOR_SERVICE) as SensorManager }
    private val hingeSensor: Sensor? by lazy {
        if (Build.VERSION.SDK_INT >= 30) sensorManager.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE)
        else null
    }
    private val foldListener = Consumer<WindowLayoutInfo> { info ->
        val fold = info.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull()
        posture = when (fold?.state) {
            FoldingFeature.State.FLAT -> "FLAT · 펼친 상태"
            FoldingFeature.State.HALF_OPENED -> "HALF_OPENED · 반쯤 접힌 상태"
            else -> "현재 창에서 접힘 정보 없음"
        }
        updateStatus()
    }
    private val angleListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (event.values.isEmpty()) return
            hingeDegrees = event.values[0]
            updateStatus()
            if (autoHinge) {
                val progress = (event.values[0] / 180f).coerceIn(0f, 1f)
                preview.setManualProgress(progress)
                val seekValue = (progress * 100).roundToInt()
                if (seekBar.progress != seekValue) seekBar.progress = seekValue
            }
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        windowAdapter = WindowInfoTrackerCallbackAdapter(WindowInfoTracker.getOrCreate(this))
        buildUi()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    private fun makeText(value: String, size: Float, color: Int, bold: Boolean = false): TextView =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            includeFontPadding = true
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        }

    private fun makeButton(label: String, fillColor: Int, foreground: Int = Color.WHITE,
                           action: () -> Unit): TextView = TextView(this).apply {
        text = label
        textSize = 15f
        setTextColor(foreground)
        gravity = Gravity.CENTER
        isClickable = true
        isFocusable = true
        background = GradientDrawable().apply {
            cornerRadius = dp(14).toFloat()
            setColor(fillColor)
        }
        minHeight = dp(51)
        setPadding(dp(16), dp(12), dp(16), dp(12))
        setOnClickListener { action() }
    }

    private fun buildUi() {
        val scroll = ScrollView(this)
        scroll.setFillViewport(true)
        scroll.setBackgroundColor(Color.rgb(29, 31, 36))
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(25), dp(18), dp(24))
        }
        scroll.addView(root)
        setContentView(scroll)
        root.addView(makeText("GLASS SLIDE", 28f, Color.WHITE, true))
        root.addView(makeText("v0.4 · 접힘 → 펼침 자동 실행 감지", 13f,
            Color.rgb(192, 189, 189)).apply {
            setPadding(0, dp(3), 0, dp(16))
        })

        preview = PortalPreviewView(this)
        preview.contentDescription = "반투명 유리판 두 장이 좌우로 이동하며 배경화면이 선명하게 드러남"
        preview.background = GradientDrawable().apply {
            cornerRadius = dp(16).toFloat()
            setColor(Color.rgb(56, 56, 61))
        }
        preview.clipToOutline = true
        root.addView(preview, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(290)
        ))
        preview.post { preview.play() }

        root.addView(makeText("유리 슬라이드 진행도", 14f, Color.rgb(231, 227, 221)).apply {
            setPadding(0, dp(16), 0, dp(5))
        })
        progressLabel = makeText("100%", 14f, Color.rgb(214, 214, 224), true)
        root.addView(progressLabel)
        seekBar = SeekBar(this).apply {
            max = 100
            progress = 100
            progressTintList = android.content.res.ColorStateList.valueOf(Color.rgb(209, 207, 219))
            thumbTintList = android.content.res.ColorStateList.valueOf(Color.rgb(209, 207, 219))
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, value: Int, fromUser: Boolean) {
                    progressLabel.text = "$value%"
                    if (fromUser && !autoHinge) preview.setManualProgress(value / 100f)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }
        root.addView(seekBar)
        root.addView(makeButton("▶  유리 슬라이드 재생", Color.rgb(83, 91, 102)) {
            autoHinge = false
            hingeSwitch.isChecked = false
            preview.play()
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(51)).apply {
            topMargin = dp(7)
        })
        root.addView(makeButton("▧  내 사진 배경으로 선택", Color.rgb(103, 106, 112)) {
            choosePhoto()
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(51)).apply {
            topMargin = dp(9)
        })
        root.addView(makeButton("↗  라이브 배경화면 적용", Color.rgb(123, 109, 111)) {
            openWallpaperChooser()
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(51)).apply {
            topMargin = dp(9)
        })

        root.addView(makeText("폴드8 화면 상태 확인", 19f, Color.WHITE, true).apply {
            setPadding(0, dp(26), 0, dp(9))
        })
        sensorStatus = makeText("장치 정보 확인 중", 14f, Color.rgb(220, 221, 212))
        sensorStatus.setLineSpacing(dp(3).toFloat(), 1f)
        root.addView(sensorStatus)
        root.addView(makeText("실기기 펼침 감지 기록", 19f, Color.WHITE, true).apply {
            setPadding(0, dp(25), 0, dp(10))
        })
        root.addView(makeButton("↻  기록 새로고침", Color.rgb(75, 82, 89)) {
            detectionLog.text = FoldDiagnostics.get(this)
            updateStatus()
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)))
        detectionLog = makeText("기록 대기 중", 12f, Color.rgb(218, 221, 217)).apply {
            setTextIsSelectable(true)
            setLineSpacing(dp(4).toFloat(), 1f)
            setPadding(0, dp(12), 0, dp(14))
        }
        root.addView(detectionLog)
        root.addView(makeButton("기록 지우기", Color.rgb(54, 57, 62)) {
            FoldDiagnostics.clear(this)
            detectionLog.text = FoldDiagnostics.get(this)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(45)))
        root.addView(makeText(
            "사용 방법: 라이브 배경화면을 커버·내부 홈 화면에 적용 → 앱 종료 → " +
                "커버 홈 화면에서 폰을 접은 채 잠깐 사용 → 홈 화면이 켜진 상태에서 펼침 → " +
                "다시 이 앱을 열어 기록 새로고침. '애니메이션 시작'이 있으면 트리거가 작동한 것입니다.",
            13f, Color.rgb(220, 216, 209)
        ).apply { setPadding(0, dp(13), 0, dp(3)) })

        hingeSwitch = Switch(this).apply {
            text = "힌지 각도로 미리보기 움직이기 (센서 있을 때만)"
            textSize = 13f
            setTextColor(Color.rgb(226, 226, 215))
            isEnabled = hingeSensor != null
            setOnCheckedChangeListener { _: CompoundButton, checked: Boolean ->
                autoHinge = checked
                if (checked && hingeDegrees != null) {
                    val p = (hingeDegrees!! / 180f).coerceIn(0f, 1f)
                    preview.setManualProgress(p)
                    seekBar.progress = (p * 100).roundToInt()
                }
            }
            setPadding(0, dp(11), 0, dp(10))
        }
        root.addView(hingeSwitch)
        root.addView(makeText(
            "※ 이 앱은 선택한 배경 사진을 흐리게 표현할 뿐, 실제 홈 화면 아이콘이나 " +
                "시스템 화면을 흐리게 만들 수는 없습니다. 감지는 가능한 경우 힌지 각도 센서를 " +
                "사용하고, 없으면 화면 상태를 이용합니다. 삼성 시스템 전환은 교체하지 않습니다. " +
                "자동 실행은 폴드8 실기기 검증이 필요합니다.",
            12f, Color.rgb(193, 189, 188)
        ).apply { setPadding(0, dp(12), 0, 0) })
        detectionLog.text = FoldDiagnostics.get(this)
        updateStatus()
    }

    private fun choosePhoto() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        try {
            startActivityForResult(Intent.createChooser(intent, "배경에 사용할 사진 선택"), photoRequestCode)
        } catch (_: Exception) {
            android.widget.Toast.makeText(this, "사진 선택 화면을 열지 못했습니다.", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    @Deprecated("Legacy result callback for dependency-free sample")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == photoRequestCode && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            try {
                val selected = WallpaperArt.saveFromUri(this, uri)
                if (selected == null) throw IllegalArgumentException("Invalid image")
                preview.setImage(selected)
                preview.play()
                android.widget.Toast.makeText(this, "사진 적용! 배경화면에도 반영하려면 다시 설정해 주세요.", android.widget.Toast.LENGTH_LONG).show()
            } catch (_: Exception) {
                android.widget.Toast.makeText(this, "이미지를 열 수 없습니다. 다른 사진을 선택해 주세요.", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun openWallpaperChooser() {
        val component = ComponentName(this, PortalWallpaperService::class.java)
        val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
            putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component)
        }
        try {
            startActivity(intent)
        } catch (_: Exception) {
            try {
                startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
            } catch (_: Exception) {
                android.widget.Toast.makeText(this, "배경화면 설정 화면을 열지 못했습니다.",
                    android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun updateStatus() {
        if (!::sensorStatus.isInitialized) return
        val angle = if (hingeSensor == null) "힌지 각도 센서 미제공"
                    else hingeDegrees?.let { "힌지 각도 ${"%.1f".format(it)}°" }
                        ?: "힌지 센서 있음 · 측정 대기"
        val widthDp = resources.configuration.screenWidthDp
        val heightDp = resources.configuration.screenHeightDp
        sensorStatus.text = "WindowManager: $posture\n$angle\n화면 영역: ${widthDp} × ${heightDp} dp"
    }

    override fun onStart() {
        super.onStart()
        if (::detectionLog.isInitialized) detectionLog.text = FoldDiagnostics.get(this)
        windowAdapter.addWindowLayoutInfoListener(this, mainExecutor, foldListener)
        hingeSensor?.let { sensorManager.registerListener(angleListener, it, SensorManager.SENSOR_DELAY_UI) }
    }

    override fun onStop() {
        windowAdapter.removeWindowLayoutInfoListener(foldListener)
        sensorManager.unregisterListener(angleListener)
        super.onStop()
    }
}
