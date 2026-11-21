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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class AutoPlayService : AccessibilityService() {

    companion object {
        var instance: AutoPlayService? = null
        val BOTOES = listOf(
            "A", "B", "START", "Z", "R",
            "C_UP", "C_DOWN", "C_LEFT", "C_RIGHT",
            "STICK_CENTER", "STICK_UP", "STICK_DOWN", "STICK_LEFT", "STICK_RIGHT"
        )
        val DICAS = mapOf(
            "STICK_CENTER" to "ANALÓGICO: toque no CENTRO do analógico",
            "STICK_UP" to "ANALÓGICO: toque até onde o dedo vai para empurrar PARA CIMA",
            "STICK_DOWN" to "ANALÓGICO: toque até onde o dedo vai para empurrar PARA BAIXO",
            "STICK_LEFT" to "ANALÓGICO: toque até onde o dedo vai para empurrar para a ESQUERDA",
            "STICK_RIGHT" to "ANALÓGICO: toque até onde o dedo vai para empurrar para a DIREITA"
        )
        @Volatile var parar = false
    }

    private val prefs by lazy { getSharedPreferences("mapa", Context.MODE_PRIVATE) }
    private val handler = Handler(Looper.getMainLooper())
    private var statusView: TextView? = null
    @Volatile private var falhou = false

    override fun onServiceConnected() { instance = this }
    override fun onAccessibilityEvent(e: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onUnbind(intent: Intent?): Boolean { instance = null; return super.onUnbind(intent) }

    // ---------- Faixa de status no topo ----------
    private fun status(msg: String?) {
        handler.post {
            val wm = getSystemService(WINDOW_SERVICE) as WindowManager
            if (msg == null) {
                statusView?.let { try { wm.removeView(it) } catch (_: Exception) {} }
                statusView = null
            } else {
                if (statusView == null) {
                    val tv = TextView(this).apply {
                        setTextColor(Color.WHITE)
                        setBackgroundColor(Color.argb(210, 0, 0, 0))
                        textSize = 16f
                        setPadding(32, 12, 32, 12)
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
                        y = 40
                    }
                    wm.addView(tv, lp)
                    statusView = tv
                }
                statusView?.text = msg
            }
        }
    }

    // ---------- Mapeamento ----------
    fun iniciarMapeamento(segundos: Int = 10) {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager

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
        fun atualizar() {
            val nome = BOTOES[i]
            texto.text = "${DICAS[nome] ?: "Toque no botão: $nome"}\n(${i + 1}/${BOTOES.size})"
        }
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
        val p = s.split(",")
        return p[0].toFloat() to p[1].toFloat()
    }

    private fun falta(nome: String): Boolean {
        falhou = true
        parar = true
        status("Não mapeado: $nome - refaça o mapeamento")
        return false
    }

    // ---------- Gestos ----------
    private fun enviar(g: GestureDescription): Boolean {
        val latch = CountDownLatch(1)
        var ok = false
        val cb = object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(d: GestureDescription?) { ok = true; latch.countDown() }
            override fun onCancelled(d: GestureDescription?) { latch.countDown() }
        }
        if (!dispatchGesture(g, cb, null)) return false
        latch.await(70, TimeUnit.SECONDS)
        return ok
    }

    private fun duracao(ms: Long): Long =
        ms.coerceIn(1L, GestureDescription.getMaxGestureDuration())

    private fun apertar(botao: String, ms: Long): Boolean {
        val (x, y) = pos(botao) ?: return falta(botao)
        val p = Path().apply { moveTo(x, y) }
        val g = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(p, 0, duracao(ms)))
            .build()
        return enviar(g)
    }

    private fun arrastarSegurar(x1: Float, y1: Float, x2: Float, y2: Float, ms: Long): Boolean {
        val ida = Path().apply { moveTo(x1, y1); lineTo(x2, y2) }
        val s1 = GestureDescription.StrokeDescription(ida, 0, 60, true)
        if (!enviar(GestureDescription.Builder().addStroke(s1).build())) return false
        val seg = Path().apply { moveTo(x2, y2) }
        val s2 = s1.continueStroke(seg, 0, duracao(ms), false)
        return enviar(GestureDescription.Builder().addStroke(s2).build())
    }

    // dir: UP, DOWN, LEFT, RIGHT ou diagonais: UP_LEFT, UP_RIGHT, DOWN_LEFT, DOWN_RIGHT
    private fun segurarStick(dir: String, ms: Long): Boolean {
        val c = pos("STICK_CENTER") ?: return falta("STICK_CENTER")
        val partes = dir.split("_")
        var dx = 0f
        var dy = 0f
        for (d in partes) {
            val p = pos("STICK_$d") ?: return falta("STICK_$d")
            dx += p.first - c.first
            dy += p.second - c.second
        }
        if (partes.size > 1) { dx *= 0.75f; dy *= 0.75f }
        return arrastarSegurar(c.first, c.second, c.first + dx, c.second + dy, ms)
    }

    // ---------- Roteiro ----------
    fun executar(roteiro: String) {
        parar = false
        falhou = false
        try {
            val linhas = roteiro.lines().map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
            rodar(linhas)
            if (!falhou) status(if (parar) "Parado" else "Roteiro terminado")
        } catch (e: Exception) {
            status("Erro no roteiro: ${e.message}")
        }
        parar = true
        handler.postDelayed({ status(null) }, 5000)
    }

    private fun rodar(linhas: List<String>) {
        var i = 0
        while (i < linhas.size && !parar) {
            val t = linhas[i].split(" ").filter { it.isNotEmpty() }
            status("▶ ${linhas[i]}")
            when (t[0].lowercase()) {
                "wait" -> Thread.sleep(t[1].toLong())
                "press" -> {
                    val ok = apertar(t[1].uppercase(), t.getOrNull(2)?.toLong() ?: 100)
                    if (!ok && !falhou) { falhou = true; parar = true; status("Toque falhou: ${linhas[i]}") }
                }
                "hold" -> {
                    val ok = segurarStick(t[1].uppercase().removePrefix("STICK_"), t[2].toLong())
                    if (!ok && !falhou) { falhou = true; parar = true; status("Toque falhou: ${linhas[i]}") }
                }
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
