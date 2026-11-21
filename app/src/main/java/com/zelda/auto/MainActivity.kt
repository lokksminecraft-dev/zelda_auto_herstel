package com.zelda.auto

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast

class MainActivity : Activity() {

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val prefs = getSharedPreferences("mapa", MODE_PRIVATE)

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
            addView(botao("2. Mapear botões (abra o emulador depois)") {
                val s = AutoPlayService.instance
                if (s == null) toast("Ative o serviço primeiro") else {
                    s.iniciarMapeamento()
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
        setContentView(raiz)
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()

    companion object {
        const val EXEMPLO = """# Comandos: wait ms | press BOTAO [ms] | hold STICK_UP|DOWN|LEFT|RIGHT ms | repeat N ... end
wait 3000
press START
wait 1000
press A
hold STICK_UP 2500
repeat 4
press A
wait 500
end
"""
    }
}
