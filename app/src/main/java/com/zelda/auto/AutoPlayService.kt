package com.zelda.auto

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast

class AutoPlayService : AccessibilityService() {

    companion object {
        var instance: AutoPlayService? = null
        val BOTOES = listOf("A", "B", "START", "Z", "R",
            "C_UP", "C_DOWN", "C_LEFT", "C_RIGHT", "STICK")
        const val STICK_RAIO = 130f
        @Volatile var parar = false
    }

    private val prefs by lazy { getSharedPreferences("mapa", Context.MODE_PRIVATE) }

    override fun onServiceConnected() { instance = this }
    override fun onAccessibilityEvent(e: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onUnbind(intent: Intent?): Boolean { instance = null; return super.onUnbind(intent) }

    // ---------- Mapeamento ----------
    fun iniciarMapeamento(segundos: Int = 10) {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val handler = Handler(Looper.getMainLooper())

        val contador = TextView(this).apply {
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.argb(200, 0, 0, 0))
            textSize = 22f
            setPadding(40, 20, 40, 20)
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 80
        }
        wm.addView(contador, lp)

        var restante = segundos
        val tick = object : Runnable {
            override fun run() {
                if (restante > 0) {
                    contador.text = "Mapeamento em ${restante}s - entre no jogo"
                    restante--
                    handler.postDelayed(this, 1000)
                } else {
                    wm.removeView(contador)
                    mostrarMapeamento()
                }
            }
        }
        handler.post(tick)
    }

    private fun mostrarMapeamento() {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val texto = TextView(this).apply {
            setTextColor(Color.WHITE); textSize = 20f; gravity = Gravity.CENTER
        }
        val raiz = FrameLayout(this).apply {
            setBackgroundColor(Color.argb(90, 0, 0, 0))
            addView(texto)
        }
        var i = 0
        fun atualizar() { texto.text = "Toque no botão: ${BOTOES[i]}  (${i + 1}/${BOTOES.size})" }
        atualizar()

        raiz.setOnTouchListener { _, ev ->
            if (ev.action == MotionEvent.ACTION_UP) {
                prefs.edit().putString(BOTOES[i], "${ev.rawX},${ev.rawY}").apply()
                i++
                if (i >= BOTOES.size) {
                    wm.removeView(raiz)
                    Toast.makeText(this, "Mapeamento salvo!", Toast.LENGTH_LONG).show()
                } else atualizar()
            }
            true
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        wm.addView(raiz, lp)
    }

    private fun pos(nome: String): Pair<Float, Float>? {
        val s = prefs.getString(nome, null) ?: return null
        val (x, y) = s.split(",")
        return x.toFloat() to y.toFloat()
    }

    // ---------- Gestos ----------
    private fun gesto(x1: Float, y1: Float, x2: Float, y2: Float, dur: Long) {
        val p = Path().apply {
            moveTo(x1, y1)
            lineTo(if (x1 == x2 && y1 == y2) x2 + 0.1f else x2, y2)
        }
        val g = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(p, 0, dur.coerceAtLeast(1)))
            .build()
        dispatchGesture(g, null, null)
        Thread.sleep(dur + 30)
    }

    private fun apertar(botao: String, ms: Long) {
        val (x, y) = pos(botao) ?: return
        gesto(x, y, x, y, ms)
    }

    private fun segurarStick(dir: String, ms: Long) {
        val (x, y) = pos("STICK") ?: return
        val (dx, dy) = when (dir) {
            "UP" -> 0f to -STICK_RAIO
            "DOWN" -> 0f to STICK_RAIO
            "LEFT" -> -STICK_RAIO to 0f
            else -> STICK_RAIO to 0f
        }
        gesto(x, y, x + dx, y + dy, ms)
    }

    // ---------- Roteiro ----------
    fun executar(roteiro: String) {
        parar = false
        val linhas = roteiro.lines().map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
        rodar(linhas)
        parar = true
    }

    private fun rodar(linhas: List<String>) {
        var i = 0
        while (i < linhas.size && !parar) {
            val t = linhas[i].split(" ")
            when (t[0].lowercase()) {
                "wait" -> Thread.sleep(t[1].toLong())
                "press" -> apertar(t[1].uppercase(), t.getOrNull(2)?.toLong() ?: 80)
                "hold" -> segurarStick(t[1].uppercase().removePrefix("STICK_"), t[2].toLong())
                "repeat" -> {
                    var prof = 1; var j = i + 1
                    while (j < linhas.size && prof > 0) {
                        if (linhas[j].startsWith("repeat")) prof++
                        if (linhas[j] == "end") prof--
                        j++
                    }
                    val corpo = linhas.subList(i + 1, j - 1)
                    repeat(t[1].toInt()) { if (!parar) rodar(corpo) }
                    i = j - 1
                }
            }
            i++
        }
    }
}
