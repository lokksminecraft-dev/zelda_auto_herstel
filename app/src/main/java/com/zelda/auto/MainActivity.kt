package com.zelda.auto

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val prefs = getSharedPreferences("mapa", MODE_PRIVATE)

        Thread.setDefaultUncaughtExceptionHandler { _, e ->
            prefs.edit().putString("erro", e.stackTraceToString()).commit()
            android.os.Process.killProcess(android.os.Process.myPid())
        }

        val erro = prefs.getString("erro", null)
        if (erro != null) {
            prefs.edit().remove("erro").commit()
            setContentView(ScrollView(this).apply {
                addView(TextView(this@MainActivity).apply {
                    text = "ERRO ANTERIOR:\n\n$erro"
                    setTextIsSelectable(true)
                    setPadding(24, 48, 24, 24)
                })
            })
            return
        }

        try {
            montarTela(prefs)
        } catch (e: Throwable) {
            setContentView(ScrollView(this).apply {
                addView(TextView(this@MainActivity).apply {
                    text = "ERRO:\n\n${e.stackTraceToString()}"
                    setTextIsSelectable(true)
                    setPadding(24, 48, 24, 24)
                })
            })
        }
    }

    private fun montarTela(prefs: android.content.SharedPreferences) {
        val roteiro = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setText(prefs.getString("roteiro", EXEMPLO))
            minLines = 12
            hint = "Roteiro"
        }

        fun botao(texto: String, acao: () -> Unit) = Button(this).apply {
            this.text = texto; setOnClickListener { acao() }
        }

        val raiz = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 48, 32, 32)
            addView(botao("1. Ativar serviço de acessibilidade") {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            })
            addView(botao("2. Mapear botões (10s para abrir o jogo)") {
                val s = AutoPlayService.instance
                if (s == null) toast("Ative o serviço primeiro") else {
                    s.iniciarMapeamento(10)
                    startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
                }
            })
            addView(botao("3. Iniciar (5s para abrir o emulador)") {
                val s = AutoPlayService.instance
                if (s == null) toast("Ative o serviço primeiro") else {
                    prefs.edit().putString("roteiro", roteiro.text.toString()).apply()
                    startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
                    Thread {
                        Thread.sleep(5000)
                        s.executar(roteiro.text.toString())
                    }.start()
                }
            })
            addView(botao("Parar") { AutoPlayService.parar = true })
            addView(roteiro)
        }
        setContentView(ScrollView(this).apply { addView(raiz) })
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()

    companion object {
        const val EXEMPLO = "# Comandos: wait ms | press BOTAO [ms] | hold STICK_UP|DOWN|LEFT|RIGHT ms | repeat N ... end\nwait 3000\npress START\nwait 1000\npress A\nhold STICK_UP 2500\nrepeat 4\npress A\nwait 500\nend\n"
    }
}
