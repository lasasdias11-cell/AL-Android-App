package com.alcommunication.app

import android.annotation.SuppressLint
import android.app.Activity
import android.app.KeyguardManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import android.webkit.SslError
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import java.io.File

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var lockedOverlay: View
    private lateinit var lockedMessage: TextView
    private lateinit var splashOverlay: View
    private lateinit var rootBanner: View
    private lateinit var biometricPrompt: BiometricPrompt

    private var hasLoadedUrl = false
    private var rootWarningShown = false

    private val deviceCredentialLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            unlockApp()
        } else {
            showLocked("Authentication cancelled. Try again.")
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyEdgeToEdgeSystemBars()
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        setContentView(R.layout.activity_main)
        webView = findViewById(R.id.webView)
        lockedOverlay = findViewById(R.id.lockedOverlay)
        lockedMessage = findViewById(R.id.lockedMessage)
        splashOverlay = findViewById(R.id.splashOverlay)
        rootBanner = findViewById(R.id.rootBanner)

        findViewById<Button>(R.id.btnUnlock).setOnClickListener { authenticate() }
        findViewById<Button>(R.id.btnSecuritySettings).setOnClickListener { openSecuritySettings() }

        configureBiometricPrompt()
        configureWebView(webView)

        webView.visibility = View.GONE
        splashOverlay.visibility = View.GONE
        rootBanner.visibility = View.GONE
        lockedOverlay.visibility = View.VISIBLE
        authenticate()
    }

    private fun configureBiometricPrompt() {
        biometricPrompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    showLocked("Authentication failed. Try again.")
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    unlockApp()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    showLocked("Authentication failed. Try again.")
                }
            }
        )
    }

    private fun authenticate() {
        val canAuthenticate = BiometricManager.from(this).canAuthenticate(allowedAuthenticators)
        if (canAuthenticate == BiometricManager.BIOMETRIC_SUCCESS) {
            showBiometricPrompt()
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R && isDeviceSecure()) {
            promptDeviceCredential()
        } else {
            showLocked("Biometric unlock is unavailable. Set up a screen lock or try again.")
        }
    }

    private fun showBiometricPrompt() {
        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock AL Communication")
            .setSubtitle("Authenticate to access secure data")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setAllowedAuthenticators(allowedAuthenticators)
        } else {
            builder.setAllowedAuthenticators(allowedAuthenticators)
                .setNegativeButtonText("Cancel")
        }

        biometricPrompt.authenticate(builder.build())
    }

    private fun promptDeviceCredential() {
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (!keyguardManager.isDeviceSecure) {
            showLocked("Set up a PIN, pattern, or password to unlock this app.")
            return
        }

        val intent = keyguardManager.createConfirmDeviceCredentialIntent(
            "Unlock AL Communication",
            "Confirm your screen lock"
        )
        if (intent == null) {
            showLocked("Unable to start device authentication.")
        } else {
            deviceCredentialLauncher.launch(intent)
        }
    }

    private fun isDeviceSecure(): Boolean {
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        return keyguardManager.isDeviceSecure
    }

    private fun openSecuritySettings() {
        try {
            startActivity(
                Intent(Settings.ACTION_BIOMETRIC_ENROLL)
                    .putExtra(Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED, allowedAuthenticators)
            )
        } catch (_: ActivityNotFoundException) {
            startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
        }
    }

    private fun showLocked(message: String) {
        lockedMessage.text = message
        lockedOverlay.visibility = View.VISIBLE
        splashOverlay.visibility = View.GONE
        webView.visibility = View.GONE
    }

    private fun unlockApp() {
        lockedOverlay.visibility = View.GONE
        webView.visibility = View.VISIBLE
        splashOverlay.visibility = View.VISIBLE

        if (hasLoadedUrl) {
            hideSplash()
        } else {
            hasLoadedUrl = true
            webView.loadUrl(APP_URL)
        }

        showRootWarningBanner()
    }

    private fun hideSplash() {
        if (::splashOverlay.isInitialized) {
            splashOverlay.visibility = View.GONE
        }
    }

    private fun showRootWarningBanner() {
        if (rootWarningShown) return
        rootWarningShown = true
        if (isRooted()) {
            rootBanner.visibility = View.VISIBLE
        }
    }

    private fun isRooted(): Boolean {
        if (Build.TAGS?.contains("test-keys") == true) return true
        return ROOT_PATHS.any { File(it).exists() }
    }

    private fun configureWebView(webView: WebView) {
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            loadWithOverviewMode = true
            useWideViewPort = true
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            setGeolocationEnabled(false)
            allowFileAccess = false
            allowContentAccess = false
            setAllowFileAccessFromFileURLs(false)
            setAllowUniversalAccessFromFileURLs(false)
            setSaveFormData(false)
            mediaPlaybackRequiresUserGesture = true
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean = shouldOpenExternally(request.url)

            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean =
                shouldOpenExternally(Uri.parse(url))

            override fun onPageFinished(view: WebView, url: String?) {
                super.onPageFinished(view, url)
                hideSplash()
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                super.onReceivedError(view, request, error)
                if (request.isForMainFrame) {
                    hideSplash()
                }
            }

            @SuppressLint("WebViewClientOnReceivedSslError")
            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                handler.cancel()
                view.stopLoading()
                hideSplash()
                Toast.makeText(
                    this@MainActivity,
                    "Security Warning: Invalid SSL Certificate.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun shouldOpenExternally(uri: Uri?): Boolean {
        if (uri == null || isAllowedAppUrl(uri)) return false

        return try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    private fun isAllowedAppUrl(uri: Uri): Boolean {
        if (uri.scheme?.equals("https", ignoreCase = true) != true) return false
        if (uri.host?.equals(APP_HOST, ignoreCase = true) != true) return false

        val path = uri.path ?: return false
        return path == APP_PATH || path.startsWith("$APP_PATH/")
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    private fun applyEdgeToEdgeSystemBars() {
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.parseColor("#020617")

        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
    }

    private val allowedAuthenticators: Int
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.BIOMETRIC_WEAK or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        } else {
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.BIOMETRIC_WEAK
        }
}

private const val APP_HOST = "lasasdias11-cell.github.io"
private const val APP_PATH = "/AL-P-System"
private const val APP_URL = "https://lasasdias11-cell.github.io/AL-P-System/"

private val ROOT_PATHS = arrayOf(
    "/system/bin/su",
    "/system/xbin/su",
    "/sbin/su",
    "/system/sd/xbin/su",
    "/system/bin/failsafe/su",
    "/data/local/xbin/su",
    "/data/local/bin/su",
    "/data/local/su",
    "/su/bin/su",
    "/magisk/.core/bin/su",
    "/system/xbin/daemonsu",
    "/system/app/Superuser.apk",
    "/system/app/SuperSU.apk"
)
