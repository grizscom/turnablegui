package ru.pnx.turnablegui

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.util.Log
import android.view.MotionEvent
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

private const val TURNABLE_MANUAL_CAPTCHA_SERVER = "http://127.0.0.1:1984"

private const val DESKTOP_USER_AGENT =
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36"

@Composable
fun ManualCaptchaCard(
    request: ManualCaptchaRequest,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var webView by remember { mutableStateOf<WebView?>(null) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("VK manual check", style = MaterialTheme.typography.titleLarge)

            Text(
                text = "Open the VK call page below, press Join manually or with the Join button. " +
                    "If VK shows a check, complete it manually. Token capture is logged as TurnableVK.",
                style = MaterialTheme.typography.bodySmall
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        webView?.zoomOut()
                        webView?.let { dumpVkPageState(it) }
                    }
                ) {
                    Text("Z-")
                }

                Button(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        webView?.zoomIn()
                        webView?.let { dumpVkPageState(it) }
                    }
                ) {
                    Text("Z+")
                }

                Button(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        webView?.let {
                            clickVkJoinButton(it)
                            dumpVkPageState(it)
                        }
                    }
                ) {
                    Text("Join")
                }

                Button(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        webView?.let {
                            focusVkCaptcha(it)
                            dumpVkPageState(it)
                        }
                    }
                ) {
                    Text("Captcha")
                }
            }

            ManualCaptchaWebView(
                request = request,
                onWebViewReady = { webView = it },
                onSolved = {
                    Toast.makeText(context, "VK token captured, waiting for Turnable", Toast.LENGTH_SHORT).show()
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(820.dp)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = { openExternalUrl(context, request.url) }
                ) {
                    Text("Browser")
                }

                Button(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        copyText(context, "Turnable captcha userscript", request.userScriptUrl)
                        Toast.makeText(context, "Userscript URL copied", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Script URL")
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = { openExternalUrl(context, request.guideUrl) }
                ) {
                    Text("Guide")
                }

                TextButton(
                    modifier = Modifier.weight(1f),
                    onClick = onDismiss
                ) {
                    Text("Hide")
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
private fun ManualCaptchaWebView(
    request: ManualCaptchaRequest,
    onWebViewReady: (WebView) -> Unit,
    onSolved: () -> Unit,
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                configureManualCaptchaWebView()
                setInitialScale(170)
                onWebViewReady(this)

                setOnTouchListener { view, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN,
                        MotionEvent.ACTION_MOVE -> view.parent?.requestDisallowInterceptTouchEvent(true)
                        MotionEvent.ACTION_UP,
                        MotionEvent.ACTION_CANCEL -> view.parent?.requestDisallowInterceptTouchEvent(false)
                    }
                    false
                }

                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView, newProgress: Int) {
                        super.onProgressChanged(view, newProgress)
                        if (newProgress >= 10) {
                            injectTurnableTokenCapture(view)
                        }
                    }

                    override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                        Log.i(
                            "TurnableVK",
                            "console: ${consoleMessage.message()} @ " +
                                "${consoleMessage.sourceId()}:${consoleMessage.lineNumber()}"
                        )
                        return true
                    }
                }
            }
        },
        update = { webView ->
            onWebViewReady(webView)

            webView.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest
                ): Boolean {
                    val nextUrl = request.url.toString()
                    Log.i("TurnableVK", "shouldOverrideUrlLoading: $nextUrl")

                    if (isForcedMvkRedirect(nextUrl)) {
                        Log.i("TurnableVK", "blocked forced m.vk redirect: $nextUrl")
                        injectTurnableTokenCapture(view)
                        return true
                    }

                    return false
                }

                override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    Log.i("TurnableVK", "onPageStarted: $url")
                }

                override fun onPageFinished(view: WebView, url: String?) {
                    super.onPageFinished(view, url)

                    Log.i("TurnableVK", "onPageFinished: $url title=${view.title}")

                    if (url != null && isManualCaptchaDoneUrl(url)) {
                        onSolved()
                        return
                    }

                    injectTurnableTokenCapture(view)
                    dumpVkPageState(view)
                    scheduleVkAutoJoin(view)
                }
            }

            val joinUrl = normalizeVkJoinUrl(request.url)
            val tag = "captcha:${request.createdAtMs}:$joinUrl"

            if (webView.getTag() != tag && joinUrl.isNotBlank()) {
                webView.setTag(tag)
                resetVkWebViewCookies(webView.context)

                Log.i("TurnableVK", "loadUrl: $joinUrl")

                webView.loadUrl(
                    joinUrl,
                    mapOf(
                        "Accept-Language" to "en-US,en;q=0.9",
                        "Upgrade-Insecure-Requests" to "1"
                    )
                )
            } else {
                injectTurnableTokenCapture(webView)
            }
        }
    )
}

@SuppressLint("SetJavaScriptEnabled")
private fun WebView.configureManualCaptchaWebView() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
        WebView.setWebContentsDebuggingEnabled(true)
    }

    isVerticalScrollBarEnabled = true
    isHorizontalScrollBarEnabled = true

    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    settings.loadsImagesAutomatically = true
    settings.javaScriptCanOpenWindowsAutomatically = true
    settings.setSupportMultipleWindows(false)
    settings.setSupportZoom(true)
    settings.builtInZoomControls = true
    settings.displayZoomControls = false
    settings.mediaPlaybackRequiresUserGesture = false
    settings.userAgentString = DESKTOP_USER_AGENT
    settings.useWideViewPort = true
    settings.loadWithOverviewMode = false
    settings.cacheMode = WebSettings.LOAD_NO_CACHE
    settings.textZoom = 100

    CookieManager.getInstance().setAcceptCookie(true)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
    }
}

private fun resetVkWebViewCookies(context: Context) {
    CookieManager.getInstance().removeAllCookies(null)
    CookieManager.getInstance().flush()

    runCatching {
        WebView(context).clearCache(true)
    }
}

private fun injectTurnableTokenCapture(webView: WebView) {
    webView.evaluateJavascript(turnableTokenCaptureScript(), null)
    webView.evaluateJavascript(installVkJoinHelperScript(), null)
}

private fun turnableTokenCaptureScript(): String {
    return """
        (function () {
            if (window.__turnableVkInjected) return;
            window.__turnableVkInjected = true;

            const SERVER = '$TURNABLE_MANUAL_CAPTCHA_SERVER';
            let done = false;

            console.log('[TurnableVK] token capture installed');

            window.__turnableVkFocusCaptcha = function () {
                const selectors = [
                    'iframe[src*="not_robot"]',
                    'iframe[src*="captcha"]',
                    '[src*="not_robot_captcha"]',
                    '[role="dialog"]',
                    '.vkuiModalRoot',
                    '.vkuiModalPage',
                    '.box_layout',
                    '[class*="Captcha"]',
                    '[class*="captcha"]',
                    '[id*="captcha"]'
                ];

                const nodes = selectors
                    .flatMap(function (selector) {
                        return Array.from(document.querySelectorAll(selector));
                    })
                    .filter(function (el) {
                        const rect = el.getBoundingClientRect();
                        return rect.width > 20 && rect.height > 20;
                    });

                const target = nodes[0];
                if (!target) {
                    console.log('[TurnableVK] captcha focus target not found');
                    return false;
                }

                target.scrollIntoView({block: 'center', inline: 'center'});
                try {
                    target.focus({preventScroll: true});
                } catch (e) {
                    try { target.focus(); } catch (ignored) {}
                }
                console.log('[TurnableVK] captcha focus target: ' + target.tagName + ' ' + (target.src || target.className || target.id || ''));
                return true;
            };

            window.__turnableVkFocusCaptchaSoon = function () {
                let attempts = 0;
                const timer = setInterval(function () {
                    attempts++;
                    if (window.__turnableVkFocusCaptcha() || attempts >= 20) {
                        clearInterval(timer);
                    }
                }, 500);
            };

            function extractFormParam(body, key) {
                if (!body || typeof body !== 'string') return '';
                for (const pair of body.split('&')) {
                    const idx = pair.indexOf('=');
                    if (idx < 0) continue;
                    try {
                        if (decodeURIComponent(pair.slice(0, idx)) === key) {
                            return decodeURIComponent(pair.slice(idx + 1));
                        }
                    } catch (e) {}
                }
                return '';
            }

            function sendTokens(messagesToken, anonToken) {
                if (done || !anonToken) return;
                done = true;
                console.log('[TurnableVK] anonymous token captured');
                window.location.replace(
                    SERVER + '/done?messages=' + encodeURIComponent(messagesToken || '') +
                    '&calls=' + encodeURIComponent(anonToken)
                );
            }

            function inspectText(url, bodyText, text) {
                if (!text) return;

                if (
                    url.indexOf('calls.getAnonymousToken') >= 0 ||
                    text.indexOf('anonymous') >= 0 ||
                    text.indexOf('token') >= 0
                ) {
                    console.log('[TurnableVK] response sample from ' + url + ': ' + text.slice(0, 700));
                }

                try {
                    const data = JSON.parse(text);
                    if (data && data.response && data.response.token) {
                        sendTokens(
                            extractFormParam(bodyText, 'access_token'),
                            data.response.token
                        );
                    } else if (data && data.error && data.error.error_code === 14) {
                        console.log('[TurnableVK] captcha required by API');
                        if (window.__turnableVkFocusCaptchaSoon) {
                            window.__turnableVkFocusCaptchaSoon();
                        }
                    }
                } catch (e) {}
            }

            const origFetch = window.fetch ? window.fetch.bind(window) : null;
            if (origFetch) {
                window.fetch = function (...args) {
                    const input = args[0];
                    const init = args[1];
                    const urlStr = typeof input === 'string'
                        ? input
                        : (input && input.url ? input.url : '');
                    const bodyArg = init && init.body;
                    const bodyText = bodyArg instanceof URLSearchParams
                        ? bodyArg.toString()
                        : (typeof bodyArg === 'string' ? bodyArg : '');

                    if (urlStr.indexOf('calls.') >= 0 || urlStr.indexOf('al_calls') >= 0) {
                        console.log('[TurnableVK] fetch: ' + urlStr);
                    }

                    return origFetch(...args).then(function (response) {
                        try {
                            response.clone().text().then(function (text) {
                                inspectText(urlStr, bodyText, text);
                            }).catch(function (e) {
                                console.log('[TurnableVK] fetch inspect failed: ' + e);
                            });
                        } catch (e) {
                            console.log('[TurnableVK] fetch clone failed: ' + e);
                        }
                        return response;
                    });
                };
            }

            const origXHROpen = XMLHttpRequest.prototype.open;
            XMLHttpRequest.prototype.open = function (...args) {
                if (typeof args[1] === 'string') {
                    this.__turnableUrl = args[1];
                    if (args[1].indexOf('calls.') >= 0 || args[1].indexOf('al_calls') >= 0) {
                        console.log('[TurnableVK] xhr open: ' + args[1]);
                    }
                }
                return origXHROpen.apply(this, args);
            };

            const origXHRSend = XMLHttpRequest.prototype.send;
            XMLHttpRequest.prototype.send = function (body) {
                const url = this.__turnableUrl || '';
                const bodyText = typeof body === 'string'
                    ? body
                    : (body instanceof URLSearchParams ? body.toString() : '');

                if (url.indexOf('calls.') >= 0 || url.indexOf('al_calls') >= 0) {
                    console.log('[TurnableVK] xhr send: ' + url);
                }

                this.addEventListener('load', function () {
                    try {
                        inspectText(url, bodyText, this.responseText);
                    } catch (e) {
                        console.log('[TurnableVK] xhr inspect failed: ' + e);
                    }
                });

                return origXHRSend.apply(this, arguments);
            };
        })();
    """.trimIndent()
}

private fun clickVkJoinButton(webView: WebView) {
    runVkJoinScript(
        webView = webView,
        mode = "manual",
        delayMs = 250,
        resultLogPrefix = "joinClickResult"
    )
}

private fun scheduleVkAutoJoin(webView: WebView) {
    webView.postDelayed({
        runVkAutoJoinLoop(webView)
    }, 700)
}

private fun focusVkCaptcha(webView: WebView) {
    webView.requestFocus()
    webView.evaluateJavascript(
        """
        (function () {
            if (window.__turnableVkFocusCaptchaSoon) {
                window.__turnableVkFocusCaptchaSoon();
                return 'focus_scheduled';
            }

            const target = document.querySelector(
                'iframe[src*="not_robot"], iframe[src*="captcha"], [role="dialog"], .vkuiModalRoot, .vkuiModalPage, .box_layout, [class*="Captcha"], [class*="captcha"], [id*="captcha"]'
            );
            if (!target) return 'not_found';
            target.scrollIntoView({block: 'center', inline: 'center'});
            try { target.focus({preventScroll: true}); } catch (e) { try { target.focus(); } catch (ignored) {} }
            return 'focused:' + target.tagName;
        })();
        """.trimIndent()
    ) { result ->
        Log.i("TurnableVK", "captchaFocusResult: $result")
    }
}

private fun runVkAutoJoinLoop(webView: WebView) {
    webView.requestFocus()
    webView.evaluateJavascript(
        """
        (function () {
            if (window.__turnableVkAutoJoinClicked) return 'already_clicked';
            if (window.__turnableVkAutoJoinLoopStarted) return 'already_started';

            window.__turnableVkAutoJoinLoopStarted = true;

            let attempts = 0;
            const maxAttempts = 35;
            const timer = setInterval(function () {
                attempts++;
                const result = window.__turnableVkTryJoin
                    ? window.__turnableVkTryJoin('auto', 120)
                    : 'helper_missing';

                console.log('[TurnableVK] auto join attempt ' + attempts + ': ' + result);

                if (
                    String(result).indexOf('prepared:') === 0 ||
                    String(result).indexOf('clicked:') === 0 ||
                    attempts >= maxAttempts
                ) {
                    clearInterval(timer);
                    if (attempts >= maxAttempts) {
                        console.log('[TurnableVK] auto join retry limit reached');
                    }
                }
            }, 700);

            return 'loop_started';
        })();
        """.trimIndent()
    ) { result ->
        Log.i("TurnableVK", "autoJoinLoopResult: $result")
    }
}

private fun runVkJoinScript(
    webView: WebView,
    mode: String,
    delayMs: Int,
    resultLogPrefix: String
) {
    webView.requestFocus()
    webView.evaluateJavascript(
        """
        (function () {
            return window.__turnableVkTryJoin
                ? window.__turnableVkTryJoin('$mode', $delayMs)
                : 'helper_missing';
        })();
        """.trimIndent()
    ) { result ->
        Log.i("TurnableVK", "$resultLogPrefix: $result")
    }
}

private fun installVkJoinHelperScript(): String {
    return """
        (function () {
            if (window.__turnableVkJoinHelperInstalled) return;
            window.__turnableVkJoinHelperInstalled = true;

            function textOf(el) {
                return (
                    el.innerText ||
                    el.textContent ||
                    el.getAttribute('value') ||
                    el.getAttribute('placeholder') ||
                    el.getAttribute('aria-label') ||
                    el.getAttribute('data-testid') ||
                    el.getAttribute('name') ||
                    el.getAttribute('title') ||
                    ''
                ).trim();
            }

            function isVisible(el) {
                if (!el) return false;
                const rect = el.getBoundingClientRect();
                const style = window.getComputedStyle(el);
                return rect.width > 1 &&
                    rect.height > 1 &&
                    style.display !== 'none' &&
                    style.visibility !== 'hidden' &&
                    Number(style.opacity || '1') > 0;
            }

            function contextText(el) {
                let node = el;
                for (let i = 0; node && i < 5; i++) {
                    const text = (node.innerText || node.textContent || '').trim();
                    if (text.length > 20) return text.toLowerCase();
                    node = node.parentElement;
                }
                return '';
            }

            function setNativeValue(el, value) {
                if (el.isContentEditable) {
                    el.focus();
                    el.textContent = value;
                    try {
                        el.dispatchEvent(new InputEvent('input', {
                            bubbles: true,
                            inputType: 'insertText',
                            data: value
                        }));
                    } catch (e) {
                        el.dispatchEvent(new Event('input', { bubbles: true }));
                    }
                    el.dispatchEvent(new Event('change', { bubbles: true }));
                    return;
                }

                const proto = el instanceof HTMLTextAreaElement
                    ? HTMLTextAreaElement.prototype
                    : HTMLInputElement.prototype;
                const setter = Object.getOwnPropertyDescriptor(proto, 'value').set;
                setter.call(el, value);
                try {
                    el.dispatchEvent(new InputEvent('input', {
                        bubbles: true,
                        inputType: 'insertText',
                        data: value
                    }));
                } catch (e) {
                    el.dispatchEvent(new Event('input', { bubbles: true }));
                }
                el.dispatchEvent(new Event('change', { bubbles: true }));
            }

            function findNameInput() {
                const inputs = Array.from(document.querySelectorAll('input,textarea,[contenteditable="true"]'));
                let best = null;
                let bestScore = -999;

                inputs.forEach(function (el) {
                    const type = (el.getAttribute('type') || '').toLowerCase();
                    const hint = textOf(el).toLowerCase();
                    const ctx = contextText(el);
                    const rect = el.getBoundingClientRect();
                    let score = 0;

                    if (!isVisible(el)) return;
                    if (el.disabled || el.readOnly) return;
                    if (type === 'hidden' || type === 'search' || type === 'email' || type === 'tel' || type === 'phone' || type === 'password') return;

                    if (hint.indexOf('search') >= 0 ||
                        hint.indexOf('clear') >= 0 ||
                        hint.indexOf('email') >= 0 ||
                        hint.indexOf('phone') >= 0 ||
                        hint.indexOf('country') >= 0) {
                        score -= 120;
                    }

                    if (hint.indexOf('name') >= 0 ||
                        hint.indexOf('user') >= 0 ||
                        hint.indexOf('your') >= 0 ||
                        hint.indexOf('display') >= 0 ||
                        hint.indexOf('имя') >= 0 ||
                        hint.indexOf('польз') >= 0) {
                        score += 80;
                    }

                    if (ctx.indexOf('by pressing join') >= 0 ||
                        ctx.indexOf('no one is in') >= 0 ||
                        ctx.indexOf('turn on your camera') >= 0 ||
                        ctx.indexOf('join through app') >= 0 ||
                        ctx.indexOf('user agreement') >= 0 ||
                        ctx.indexOf('privacy policy') >= 0) {
                        score += 50;
                    }

                    if (ctx.indexOf('search') >= 0 && ctx.indexOf('clear field') >= 0) {
                        score -= 80;
                    }

                    if (rect.top > 80) score += 8;
                    if (rect.width >= 80 && rect.width <= 800) score += 8;
                    if (el.value || el.textContent) score += 4;

                    if (score > bestScore) {
                        bestScore = score;
                        best = el;
                    }
                });

                if (best && bestScore > 0) {
                    console.log('[TurnableVK] selected name input score=' + bestScore + ' hint=' + textOf(best));
                    return best;
                }

                console.log('[TurnableVK] name input not found, bestScore=' + bestScore);
                return null;
            }

            function isDisabledButton(el) {
                const ariaDisabled = (el.getAttribute('aria-disabled') || '').toLowerCase() === 'true';
                const cls = (el.className || '').toString().toLowerCase();
                return !!el.disabled || ariaDisabled || cls.indexOf('disabled') >= 0;
            }

            function findJoinButton() {
                const candidates = Array.from(document.querySelectorAll('button,a,[role="button"],div[tabindex],span[tabindex]'))
                    .filter(isVisible);

                const exact = candidates.find(function (el) {
                    return textOf(el).toLowerCase() === 'join';
                });
                if (exact) return exact;

                return candidates.find(function (el) {
                    const text = textOf(el).toLowerCase();
                    return (text === 'join call' || text.indexOf('join') === 0) &&
                        text.indexOf('through app') < 0;
                }) || null;
            }

            function clickElement(el) {
                el.scrollIntoView({block: 'center', inline: 'center'});
                try { el.focus({preventScroll: true}); } catch (e) { try { el.focus(); } catch (ignored) {} }

                const rect = el.getBoundingClientRect();
                const x = rect.left + rect.width / 2;
                const y = rect.top + rect.height / 2;
                const opts = {
                    bubbles: true,
                    cancelable: true,
                    view: window,
                    clientX: x,
                    clientY: y
                };

                ['pointerdown', 'mousedown', 'pointerup', 'mouseup', 'click'].forEach(function (type) {
                    try {
                        el.dispatchEvent(new MouseEvent(type, opts));
                    } catch (e) {}
                });

                try { el.click(); } catch (e) {}
            }

            window.__turnableVkTryJoin = function (mode, delayMs) {
                const userName = 'VKUser_' + Math.floor(100000 + Math.random() * 900000);
                const nameInput = findNameInput();

                if (nameInput && !(nameInput.value || nameInput.textContent || '').trim()) {
                    nameInput.scrollIntoView({block: 'center', inline: 'center'});
                    nameInput.focus();
                    setNativeValue(nameInput, userName);
                    console.log('[TurnableVK] ' + mode + ' filled guest name: ' + userName);
                } else if (nameInput) {
                    console.log('[TurnableVK] ' + mode + ' name already set: ' + (nameInput.value || nameInput.textContent || '').trim());
                }

                const join = findJoinButton();

                if (!join) {
                    console.log('[TurnableVK] ' + mode + ' join button not found');
                    return 'not_found';
                }

                if (isDisabledButton(join)) {
                    console.log('[TurnableVK] ' + mode + ' join button disabled');
                    return 'join_disabled';
                }

                if (mode === 'auto') {
                    window.__turnableVkAutoJoinClicked = true;
                }

                setTimeout(function () {
                    console.log('[TurnableVK] ' + mode + ' clicking join: ' + textOf(join));
                    clickElement(join);

                    if (window.__turnableVkFocusCaptchaSoon) {
                        setTimeout(window.__turnableVkFocusCaptchaSoon, 1000);
                    }
                }, delayMs || 0);

                return 'prepared:' + userName + ':' + textOf(join);
            };
        })();
        """.trimIndent()
}

private fun copyText(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}

private fun openExternalUrl(context: Context, url: String) {
    if (url.isBlank()) {
        Toast.makeText(context, "URL is empty", Toast.LENGTH_SHORT).show()
        return
    }

    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    runCatching {
        context.startActivity(intent)
    }.onFailure {
        Toast.makeText(context, "Failed to open URL", Toast.LENGTH_SHORT).show()
    }
}

private fun isManualCaptchaDoneUrl(url: String): Boolean {
    return url.startsWith("http://127.0.0.1:1984/done") ||
        url.startsWith("http://localhost:1984/done")
}

private fun normalizeVkJoinUrl(url: String): String {
    return url
        .replace("http://m.vk.com/call/join/", "https://vk.com/call/join/")
        .replace("https://m.vk.com/call/join/", "https://vk.com/call/join/")
        .replace("http://vk.com/call/join/", "https://vk.com/call/join/")
}

private fun isForcedMvkRedirect(url: String): Boolean {
    val lower = url.lowercase()

    return lower.contains("force_redirect_to_mvk=1") ||
        lower.startsWith("https://m.vk.com/call/join/") ||
        lower.startsWith("http://m.vk.com/call/join/")
}

private fun dumpVkPageState(webView: WebView) {
    webView.evaluateJavascript(
        """
        (function () {
            function buttonTexts() {
                return Array.from(document.querySelectorAll('button,a,[role="button"]'))
                    .map(function (el) {
                        return (
                            el.innerText ||
                            el.textContent ||
                            el.getAttribute('aria-label') ||
                            el.getAttribute('title') ||
                            ''
                        ).trim();
                    })
                    .filter(Boolean)
                    .slice(0, 40);
            }

            return JSON.stringify({
                href: location.href,
                title: document.title,
                ua: navigator.userAgent,
                platform: navigator.platform,
                vendor: navigator.vendor,
                webdriver: navigator.webdriver,
                width: window.innerWidth,
                height: window.innerHeight,
                scrollX: window.scrollX,
                scrollY: window.scrollY,
                body: document.body ? document.body.innerText.slice(0, 700) : '',
                buttons: buttonTexts()
            });
        })();
        """.trimIndent()
    ) { result ->
        Log.i("TurnableVK", "pageState: $result")
    }
}

