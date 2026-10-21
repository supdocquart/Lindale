/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens
import metrolist.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource


import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.YouTubeAccount
import com.metrolist.innertube.utils.parseCookieString
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.constants.AccountChannelHandleKey
import com.metrolist.music.constants.AccountEmailKey
import com.metrolist.music.constants.AccountNameKey
import com.metrolist.music.constants.DataSyncIdKey
import com.metrolist.music.constants.InnerTubeAuthUserKey
import com.metrolist.music.constants.InnerTubeCookieKey
import com.metrolist.music.constants.VisitorDataKey
import com.metrolist.music.ui.component.IconButton
import com.metrolist.music.ui.utils.backToMain
import com.metrolist.music.utils.reportException
import com.metrolist.music.utils.safeDataStoreEdit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

private enum class LoginStage {
    Authenticating,
    CheckingAccounts,
    SelectingAccount,
    SwitchingAccount,
    Completing,
}

private data class AuthData(
    val cookie: String,
    val visitorData: String,
    val dataSyncId: String,
    val authUser: String,
)

@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    navController: NavController,
    isSwitchingChannel: Boolean = false,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val jsInterface = remember { LoginJsInterface() }
    var loginStage by remember { mutableStateOf(LoginStage.Authenticating) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var accounts by remember { mutableStateOf<List<YouTubeAccount>>(emptyList()) }
    var selectedAccount by remember { mutableStateOf<YouTubeAccount?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    suspend fun extractAuthData(webView: WebView?): AuthData? {
        val view = webView ?: return null
        var hasAuthCookie = false
        var hasVisitorData = false
        repeat(20) {
            val cookie = CookieManager.getInstance().getCookie("https://music.youtube.com").orEmpty()
            val cookieMap = runCatching { parseCookieString(cookie) }.getOrDefault(emptyMap())
            val visitorDataDeferred = CompletableDeferred<String?>()
            val dataSyncIdDeferred = CompletableDeferred<String?>()
            val authUserDeferred = CompletableDeferred<String?>()
            jsInterface.onVisitorDataReceived = { visitorData ->
                if (!visitorDataDeferred.isCompleted) visitorDataDeferred.complete(visitorData)
            }
            jsInterface.onDataSyncIdReceived = { dataSyncId ->
                if (!dataSyncIdDeferred.isCompleted) dataSyncIdDeferred.complete(dataSyncId)
            }
            jsInterface.onAuthUserReceived = { authUser ->
                if (!authUserDeferred.isCompleted) authUserDeferred.complete(authUser)
            }

            view.loadUrl(
                "javascript:Android.onRetrieveVisitorData(" +
                    "window.yt&&window.yt.config_?window.yt.config_.VISITOR_DATA:null)",
            )
            view.loadUrl(
                "javascript:Android.onRetrieveDataSyncId(" +
                    "window.yt&&window.yt.config_?window.yt.config_.DATASYNC_ID:null)",
            )
            view.loadUrl(
                "javascript:Android.onRetrieveAuthUser(" +
                    "window.yt&&window.yt.config_?String(window.yt.config_.SESSION_INDEX||0):'0')",
            )

            val pageAuthData =
                withTimeoutOrNull(1_000) {
                    WebAuthData(
                        visitorData = visitorDataDeferred.await(),
                        dataSyncId = dataSyncIdDeferred.await(),
                        authUser = authUserDeferred.await().orEmpty(),
                    )
                }
            hasAuthCookie = "SAPISID" in cookieMap
            val visitorData = pageAuthData?.visitorData
            hasVisitorData = !visitorData.isNullOrBlank()
            if (hasAuthCookie && pageAuthData != null && !visitorData.isNullOrBlank()) {
                return AuthData(
                    cookie = cookie,
                    visitorData = visitorData,
                    dataSyncId = pageAuthData.dataSyncId.orEmpty().substringBefore("||"),
                    authUser = pageAuthData.authUser.filter(Char::isDigit).ifBlank { "0" },
                )
            }
            delay(500)
        }

        Timber.w(
            "Login: Timed out waiting for the YouTube Music session " +
                "(authCookie=$hasAuthCookie, visitorData=$hasVisitorData)",
        )
        return null
    }

    var cookieInput by remember { mutableStateOf(YouTube.cookie.orEmpty()) }
    var visitorDataInput by remember { mutableStateOf(YouTube.visitorData.orEmpty()) }
    var dataSyncIdInput by remember { mutableStateOf(YouTube.dataSyncId.orEmpty()) }
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current

    fun parsePastedCookieInput(raw: String): AuthData {
        val trimmed = raw.trim()
        var cookie = trimmed
        var vData = visitorDataInput.trim()
        var dSync = dataSyncIdInput.trim()
        var aUser = "0"

        if (trimmed.contains("***INNERTUBE COOKIE***")) {
            trimmed.lines().forEach { line ->
                when {
                    line.startsWith("***INNERTUBE COOKIE*** =") -> cookie = line.substringAfter("=").trim()
                    line.startsWith("***VISITOR DATA*** =") -> vData = line.substringAfter("=").trim()
                    line.startsWith("***DATASYNC ID*** =") -> dSync = line.substringAfter("=").trim()
                    line.startsWith("***AUTH USER*** =") -> aUser = line.substringAfter("=").trim()
                }
            }
        } else if (trimmed.lowercase().startsWith("cookie:")) {
            cookie = trimmed.substringAfter(":").trim()
        }
        return AuthData(
            cookie = cookie,
            visitorData = vData,
            dataSyncId = dSync.substringBefore("||"),
            authUser = aUser.filter(Char::isDigit).ifBlank { "0" },
        )
    }

    suspend fun finalizeLogin(authData: AuthData) {
        loginStage = LoginStage.Completing
        YouTube.cookie = authData.cookie
        if (authData.visitorData.isNotBlank()) {
            YouTube.visitorData = authData.visitorData
        } else if (YouTube.visitorData.isNullOrBlank()) {
            YouTube.visitorData().getOrNull()?.takeIf { it.isNotBlank() }?.let {
                YouTube.visitorData = it
            }
        }
        YouTube.dataSyncId = authData.dataSyncId.takeIf { it.isNotBlank() }
        YouTube.authUser = authData.authUser

        try {
            val accountInfo = runCatching { YouTube.accountInfo().getOrNull() }.getOrNull()
            val selectedAcc = selectedAccount
            val resolvedName = accountInfo?.name ?: selectedAcc?.name ?: "YouTube Music"
            val resolvedEmail = accountInfo?.email.orEmpty()
            val resolvedHandle = accountInfo?.channelHandle ?: selectedAcc?.channelHandle.orEmpty()
            val resolvedVisitorData = YouTube.visitorData.orEmpty()

            val saved =
                withContext(Dispatchers.IO) {
                    context.safeDataStoreEdit { settings ->
                        settings[InnerTubeCookieKey] = authData.cookie
                        if (resolvedVisitorData.isNotBlank()) {
                            settings[VisitorDataKey] = resolvedVisitorData
                        }
                        settings[DataSyncIdKey] = authData.dataSyncId
                        settings[InnerTubeAuthUserKey] = authData.authUser
                        settings[AccountNameKey] = resolvedName
                        settings[AccountEmailKey] = resolvedEmail
                        settings[AccountChannelHandleKey] = resolvedHandle
                    }
                }
            check(saved) { "Failed to persist account data" }

            withContext(Dispatchers.Main) {
                navController.navigateUp()
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (throwable: Exception) {
            Timber.e(throwable, "Login: Failed to validate or save the selected YouTube channel")
            reportException(throwable)
            errorMessage = throwable.message ?: "Giriş kaydedilirken hata oluştu"
            loginStage = LoginStage.Authenticating
        }
    }

    suspend fun checkAvailableAccounts(authData: AuthData) {
        loginStage = LoginStage.CheckingAccounts
        YouTube.cookie = authData.cookie
        if (authData.visitorData.isNotBlank()) {
            YouTube.visitorData = authData.visitorData
        } else if (YouTube.visitorData.isNullOrBlank()) {
            YouTube.visitorData().getOrNull()?.takeIf { it.isNotBlank() }?.let {
                YouTube.visitorData = it
            }
        }
        YouTube.dataSyncId = authData.dataSyncId.takeIf { it.isNotBlank() }
        YouTube.authUser = authData.authUser

        val availableAccounts =
            YouTube.accountsList().getOrElse { throwable ->
                Timber.e(throwable, "Login: Failed to retrieve YouTube channels")
                emptyList()
            }

        if (availableAccounts.size > 1) {
            val currentAccount =
                availableAccounts.firstOrNull { account ->
                    account.pageId == authData.dataSyncId ||
                        account.dataSyncId?.substringBefore("||") == authData.dataSyncId
                } ?: availableAccounts.firstOrNull { it.isSelected }
            accounts = availableAccounts.map { it.copy(isSelected = it == currentAccount) }
            selectedAccount = accounts.firstOrNull { it.isSelected } ?: accounts.first()
            loginStage = LoginStage.SelectingAccount
        } else {
            val singleAccount = availableAccounts.firstOrNull()
            if (singleAccount != null) {
                selectedAccount = singleAccount
            }
            finalizeLogin(
                authData.copy(
                    dataSyncId = singleAccount?.dataSyncId?.substringBefore("||")?.takeIf { it.isNotBlank() }
                        ?: authData.dataSyncId
                )
            )
        }
    }

    Box(
        modifier =
            Modifier
                .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
                .fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Spacer(Modifier.height(56.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = stringResource(Res.string.login),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Masaüstü sürümünde YouTube Music hesabınızla oturum açmak için:\n" +
                            "1. Aşağıdaki butonla tarayıcınızda music.youtube.com adresini açın ve giriş yapın.\n" +
                            "2. Tarayıcıda F12 tuşuna basıp Geliştirici Araçları -> Network (Ağ) sekmesini açın.\n" +
                            "3. Sayfayı yenileyip 'browse' veya 'music.youtube.com' isteğine tıklayın.\n" +
                            "4. Request Headers (İstek Başlıkları) altındaki 'Cookie' değerini kopyalayıp aşağıya yapıştırın.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    Row {
                        Button(
                            onClick = { uriHandler.openUri("https://music.youtube.com") },
                        ) {
                            Icon(
                                painter = painterResource(Res.drawable.login),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Tarayıcıda YouTube Music'i Aç")
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            androidx.compose.material3.OutlinedTextField(
                value = cookieInput,
                onValueChange = { cookieInput = it },
                label = { Text("YouTube Music Cookie (SAPISID içermelidir)") },
                placeholder = { Text("VISITOR_INFO1_LIVE=...; SAPISID=...; __Secure-3PAPISID=...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 140.dp, max = 260.dp),
                maxLines = 10,
            )

            Spacer(Modifier.height(12.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.material3.OutlinedButton(
                    onClick = {
                        runCatching {
                            val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
                            val text = clipboard.getData(java.awt.datatransfer.DataFlavor.stringFlavor) as? String
                            if (!text.isNullOrBlank()) {
                                cookieInput = text.trim()
                            }
                        }
                    }
                ) {
                    Text("Panodan Yapıştır")
                }

                Spacer(Modifier.width(12.dp))

                Button(
                    enabled = cookieInput.isNotBlank() &&
                        loginStage != LoginStage.CheckingAccounts &&
                        loginStage != LoginStage.Completing,
                    onClick = {
                        val parsed = parsePastedCookieInput(cookieInput)
                        val cookieMap = runCatching { parseCookieString(parsed.cookie) }.getOrDefault(emptyMap())
                        if ("SAPISID" !in cookieMap) {
                            errorMessage = "Geçersiz Cookie: Kopyaladığınız Cookie içerisinde 'SAPISID' bulunamadı. Lütfen music.youtube.com üzerinde oturum açtığınızdan ve tam Cookie başlığını kopyaladığınızdan emin olun."
                            return@Button
                        }
                        coroutineScope.launch {
                            checkAvailableAccounts(parsed)
                        }
                    },
                ) {
                    Text(stringResource(Res.string.login))
                }
            }
        }

        if (loginStage == LoginStage.CheckingAccounts || loginStage == LoginStage.Completing) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        }

        TopAppBar(
            title = {
                Text(
                    stringResource(
                        if (isSwitchingChannel) Res.string.switch_youtube_channel else Res.string.login,
                    ),
                )
            },
            navigationIcon = {
                IconButton(
                    onClick = navController::navigateUp,
                    onLongClick = navController::backToMain,
                ) {
                    Icon(
                        painterResource(Res.drawable.arrow_back),
                        contentDescription = null,
                    )
                }
            },
        )
    }

    if (loginStage == LoginStage.SelectingAccount) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(Res.string.choose_youtube_channel)) },
            text = {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.choose_youtube_channel_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                    ) {
                        accounts.forEach { account ->
                            YouTubeAccountPickerItem(
                                account = account,
                                isSelected = account == selectedAccount,
                                onClick = { selectedAccount = account },
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = selectedAccount != null,
                    onClick = {
                        val account = selectedAccount ?: return@Button
                        val parsed = parsePastedCookieInput(cookieInput)
                        val selectedDataSyncId = account.dataSyncId?.substringBefore("||")?.takeIf { it.isNotBlank() }
                            ?: account.pageId?.takeIf { it.isNotBlank() }
                            ?: parsed.dataSyncId
                        coroutineScope.launch {
                            finalizeLogin(
                                parsed.copy(dataSyncId = selectedDataSyncId)
                            )
                        }
                    },
                ) {
                    Text(stringResource(Res.string.continue_action))
                }
            },
        )
    }

    errorMessage?.let { error ->
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            title = { Text(stringResource(Res.string.login_failed)) },
            text = { Text(error) },
            confirmButton = {
                Button(
                    onClick = {
                        errorMessage = null
                        webViewRef?.reload()
                    },
                ) {
                    Text(stringResource(Res.string.retry))
                }
            },
        )
    }

    BackHandler(
        enabled = webViewRef?.canGoBack() == true && loginStage == LoginStage.Authenticating && errorMessage == null,
    ) {
        webViewRef?.goBack()
    }
}

@Composable
private fun YouTubeAccountPickerItem(
    account: YouTubeAccount,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
            ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick)
                    .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (account.thumbnailUrl != null) {
                AsyncImage(
                    model = account.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier =
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape),
                )
            } else {
                Icon(
                    painter = painterResource(Res.drawable.account),
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = account.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                account.channelHandle?.let { channelHandle ->
                    Text(
                        text = channelHandle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                account.byline?.let { byline ->
                    Text(
                        text = byline,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            RadioButton(selected = isSelected, onClick = null)
        }
    }
}

private class LoginJsInterface {
    var onVisitorDataReceived: ((String?) -> Unit)? = null
    var onDataSyncIdReceived: ((String?) -> Unit)? = null
    var onAuthUserReceived: ((String?) -> Unit)? = null

    @JavascriptInterface
    fun onRetrieveVisitorData(visitorData: String?) {
        onVisitorDataReceived?.invoke(visitorData)
    }

    @JavascriptInterface
    fun onRetrieveDataSyncId(dataSyncId: String?) {
        onDataSyncIdReceived?.invoke(dataSyncId)
    }

    @JavascriptInterface
    fun onRetrieveAuthUser(authUser: String?) {
        onAuthUserReceived?.invoke(authUser)
    }
}

private data class WebAuthData(
    val visitorData: String?,
    val dataSyncId: String?,
    val authUser: String,
)

private fun restoreYouTubeCookies(cookie: String?) {
    val cookieManager = CookieManager.getInstance()
    cookieManager.setAcceptCookie(true)
    cookie
        ?.let(::parseCookieString)
        .orEmpty()
        .forEach { (name, value) ->
            cookieManager.setCookie(
                "https://music.youtube.com",
                "$name=$value; Domain=.youtube.com; Path=/; Secure",
            )
        }
    cookieManager.flush()
}
