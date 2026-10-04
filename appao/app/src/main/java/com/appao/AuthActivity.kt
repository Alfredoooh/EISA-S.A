package com.appao

import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

/** Native login/register screen following the supplied HTML form geometry. */
class AuthActivity : AppCompatActivity() {

    private lateinit var root: View
    private lateinit var loginForm: View
    private lateinit var registerForm: View
    private lateinit var indicator: View
    private lateinit var loginTab: TextView
    private lateinit var registerTab: TextView
    private lateinit var subtitle: TextView
    private lateinit var loginIdentifier: EditText
    private lateinit var loginPassword: EditText
    private lateinit var registerIdentifier: EditText
    private lateinit var registerPassword: EditText

    private var register = false
    private var loginPhone = false
    private var registerPhone = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_auth)
        SystemBarHelper.sync(this)

        root = findViewById(R.id.authRoot)
        loginForm = findViewById(R.id.loginForm)
        registerForm = findViewById(R.id.registerForm)
        indicator = findViewById(R.id.authIndicator)
        loginTab = findViewById(R.id.authTabLogin)
        registerTab = findViewById(R.id.authTabRegister)
        subtitle = findViewById(R.id.authSubtitle)
        loginIdentifier = findViewById(R.id.loginIdentifier)
        loginPassword = findViewById(R.id.loginPassword)
        registerIdentifier = findViewById(R.id.registerIdentifier)
        registerPassword = findViewById(R.id.registerPassword)

        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars()
            )
            val ime = insets.getInsets(
                WindowInsetsCompat.Type.ime()
            ).bottom
            view.updatePadding(
                top = bars.top,
                bottom = maxOf(bars.bottom, ime)
            )
            insets
        }

        IconLoader.applySvg(
            findViewById(R.id.authCloseIcon),
            "close",
            R.color.iconTint
        )

        findViewById<View>(R.id.authClose).setOnClickListener {
            finish()
        }

        loginTab.setOnClickListener {
            setRegister(false, true)
        }

        registerTab.setOnClickListener {
            setRegister(true, true)
        }

        findViewById<View>(R.id.loginIdType).setOnClickListener {
            showIdTypePicker(
                findViewById(R.id.loginIdType),
                true
            )
        }

        findViewById<View>(R.id.registerIdType).setOnClickListener {
            showIdTypePicker(
                findViewById(R.id.registerIdType),
                false
            )
        }

        findViewById<View>(R.id.loginSubmit).setOnClickListener {
            submitLogin()
        }

        findViewById<View>(R.id.registerSubmit).setOnClickListener {
            submitRegister()
        }

        setRegister(
            intent.getBooleanExtra("register", false),
            false
        )
    }

    private fun setRegister(
        value: Boolean,
        animated: Boolean
    ) {
        register = value

        val duration = if (animated) 360L else 0L

        if (animated) {
            val out = if (value) loginForm else registerForm
            val incoming = if (value) registerForm else loginForm

            out.animate()
                .alpha(0f)
                .translationX(
                    if (value) -dp(18).toFloat() else dp(18).toFloat()
                )
                .setDuration(duration)
                .setInterpolator(Curves.IOS)
                .withEndAction {
                    out.visibility = View.GONE
                }
                .start()

            incoming.visibility = View.VISIBLE
            incoming.alpha = 0f
            incoming.translationX = if (value) dp(18).toFloat() else -dp(18).toFloat()
            incoming.animate()
                .alpha(1f)
                .translationX(0f)
                .setDuration(duration)
                .setInterpolator(Curves.IOS)
                .start()
        } else {
            loginForm.visibility = if (value) View.GONE else View.VISIBLE
            registerForm.visibility = if (value) View.VISIBLE else View.GONE
            loginForm.alpha = 1f
            registerForm.alpha = 1f
            loginForm.translationX = 0f
            registerForm.translationX = 0f
        }

        subtitle.text = if (value) {
            "Cria a tua conta para personalizar o teu app ao"
        } else {
            "Entra na tua conta para personalizar o teu app ao"
        }

        updateTabs(animated)
    }

    private fun updateTabs(animated: Boolean = false) {
        val parent = indicator.parent as? View ?: return
        val width = parent.width
        if (width > 0) {
            val indicatorParams = indicator.layoutParams
            indicatorParams.width = (width - dp(10)) / 2
            indicator.layoutParams = indicatorParams
            val targetX = if (register) {
                ((width - dp(10)) / 2).toFloat()
            } else {
                0f
            }
            indicator.animate().cancel()
            if (animated) {
                indicator.animate()
                    .translationX(targetX)
                    .setDuration(280L)
                    .setInterpolator(Curves.IOS)
                    .start()
            } else {
                indicator.translationX = targetX
            }
        } else {
            indicator.post { updateTabs(animated) }
        }

        loginTab.setTextColor(
            androidx.core.content.ContextCompat.getColor(
                this,
                if (!register) R.color.onpri else R.color.dim
            )
        )
        registerTab.setTextColor(
            androidx.core.content.ContextCompat.getColor(
                this,
                if (register) R.color.onpri else R.color.dim
            )
        )
    }

    private fun showIdTypePicker(
        anchor: View,
        login: Boolean
    ) {
        val current =
            if (login) loginPhone else registerPhone

        HtmlStylePopup.show(
            anchor,
            listOf(
                HtmlStylePopup.Item(
                    "Email",
                    "mail",
                    !current
                ) {
                    setPhoneMode(login, false)
                },
                HtmlStylePopup.Item(
                    "Número",
                    "phone",
                    current
                ) {
                    setPhoneMode(login, true)
                }
            )
        )
    }

    private fun setPhoneMode(
        login: Boolean,
        phone: Boolean
    ) {
        if (login) {
            loginPhone = phone
            updateIdentifier(
                loginIdentifier,
                phone,
                R.id.loginIdType
            )
        } else {
            registerPhone = phone
            updateIdentifier(
                registerIdentifier,
                phone,
                R.id.registerIdType
            )
        }
    }

    private fun updateIdentifier(
        field: EditText,
        phone: Boolean,
        labelId: Int
    ) {
        field.inputType = if (phone) {
            InputType.TYPE_CLASS_PHONE
        } else {
            InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        }
        field.hint = if (phone) {
            "+244 912 345 678"
        } else {
            "tu@email.com"
        }
        field.setSelection(field.text.length)

        findViewById<TextView>(labelId).text =
            if (phone) "NÚMERO" else "EMAIL"
    }

    private fun submitLogin() {
        val error = findViewById<TextView>(R.id.loginError)
        val value = loginIdentifier.text.toString().trim()
        val password = loginPassword.text.toString()

        if (value.isBlank()) {
            error.text = "Preenche o teu email ou número."
            return
        }

        if (password.length < 8) {
            error.text = "A palavra-passe deve ter pelo menos 8 caracteres."
            return
        }

        error.text = ""
        AppAoToast.show(this, "Ligação de autenticação em breve")
    }

    private fun submitRegister() {
        val error = findViewById<TextView>(R.id.registerError)
        val username =
            findViewById<EditText>(R.id.registerUsername)
                .text.toString().trim()
        val value = registerIdentifier.text.toString().trim()
        val password = registerPassword.text.toString()

        when {
            username.isBlank() ->
                error.text = "Escolhe um nome de utilizador."
            value.isBlank() ->
                error.text = "Preenche o teu email ou número."
            password.length < 8 ->
                error.text = "A palavra-passe deve ter pelo menos 8 caracteres."
            else -> {
                error.text = ""
                AppAoToast.show(
                    this,
                    "Ligação de autenticação em breve"
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        SystemBarHelper.sync(this)
        updateTabs(false)
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + .5f).toInt()
}
