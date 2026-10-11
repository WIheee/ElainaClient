package com.elainaclient.ui.component

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.elainaclient.game.AccountManager
import net.raphimc.minecraftauth.MinecraftAuth
import net.raphimc.minecraftauth.bedrock.BedrockAuthManager
import net.raphimc.minecraftauth.msa.data.MsaConstants
import net.raphimc.minecraftauth.msa.model.MsaApplicationConfig
import net.raphimc.minecraftauth.msa.service.impl.DeviceCodeMsaAuthService
import java.util.function.Consumer
import kotlin.concurrent.thread

val auth = "UCxb4pcHvdYpqv7i5Xt9mOUw"

val authId = "$auth"

// Bedrock Android title ID + the "title auth" scope. Using a "title" client ID is what lets
// BedrockAuthManager fetch additional scopes on demand instead of building them up front.
private val BEDROCK_ANDROID_APPLICATION_CONFIG: MsaApplicationConfig =
    MsaApplicationConfig(MsaConstants.BEDROCK_ANDROID_TITLE_ID, MsaConstants.SCOPE_TITLE_AUTH)


@SuppressLint("SetJavaScriptEnabled")
class AuthWebView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : WebView(context, attrs) {

    var callback: ((Throwable?) -> Unit)? = null

    init {
        CookieManager.getInstance()
            .removeAllCookies(null)

        settings.javaScriptEnabled = true
        webViewClient = AuthWebViewClient()
    }

    fun addAccount() {
        thread {
            runCatching {
                val httpClient = MinecraftAuth.createHttpClient()

                // BedrockAuthManager fetches tokens lazily and per-purpose; a "title" client ID
                // is what lets it fetch additional scopes on demand rather than up front.
                //
                // NOTE: we build the DeviceCodeMsaAuthService ourselves (instead of passing
                // `::DeviceCodeMsaAuthService` into `.login(supplier, callback)`) because
                // Kotlin can't reliably resolve which constructor overload a bare `::Class`
                // reference should bind to when it has to flow through a generic Java
                // functional-interface parameter - it fails with a cryptic
                // "Argument type mismatch: actual type is 'Function<Nothing>'" error, even
                // though the equivalent `DeviceCodeMsaAuthService::new` compiles fine in Java.
                // Calling the constructor directly here sidesteps that inference problem.
                val deviceCodeCallback = Consumer<net.raphimc.minecraftauth.msa.model.MsaDeviceCode> { deviceCode ->
                    post {
                        loadUrl(deviceCode.directVerificationUri)
                    }
                }
                val authService = DeviceCodeMsaAuthService(
                    httpClient,
                    BEDROCK_ANDROID_APPLICATION_CONFIG,
                    deviceCodeCallback
                )
                val msaToken = authService.acquireToken()

                val authManager = BedrockAuthManager
                    .create(httpClient, AccountManager.GAME_VERSION)
                    .msaApplicationConfig(BEDROCK_ANDROID_APPLICATION_CONFIG)
                    .login(msaToken)

                AccountManager.addAccount(authManager)
                callback?.invoke(null)
            }.exceptionOrNull()?.let {
                callback?.invoke(it)
            }
        }
    }

    inner class AuthWebViewClient : WebViewClient() {
        override fun shouldOverrideUrlLoading(
            view: WebView?,
            request: WebResourceRequest?
        ): Boolean {
            return false
        }

    }

}
