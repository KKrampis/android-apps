package io.github.kkrampis.searchvault.ui

import android.annotation.SuppressLint
import android.content.Context
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView

/**
 * The AI Mode page runs in a WebView set up to be as anonymous as Android allows:
 * no Google sign-in, no third-party cookies, no saved form data or location, Safe Browsing
 * and WebView usage metrics off (see the manifest), and in private mode every cookie, cache
 * entry and bit of site storage is wiped before each search, so Google sees a new visitor each time.
 */
object PrivateWeb {
    /**
     * Google's consent cookie set to "rejected". Without it, visitors in Europe get the cookie
     * consent page on every search once cookies are wiped. It records a refusal and carries no id.
     */
    private const val CONSENT_COOKIE = "SOCS=CAI; Domain=.google.com; Path=/; Secure; SameSite=Lax"

    @SuppressLint("SetJavaScriptEnabled")
    fun create(context: Context): WebView = WebView(context).apply {
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        settings.apply {
            javaScriptEnabled = true // AI Mode does not render without it
            domStorageEnabled = true // needed by the page, wiped with everything else
            setGeolocationEnabled(false)
            allowFileAccess = false
            allowContentAccess = false
            safeBrowsingEnabled = false // otherwise visited URLs are checked against Google's servers
            cacheMode = WebSettings.LOAD_DEFAULT
            setSupportMultipleWindows(false)
            // Google serves a reduced page to the WebView user agent; drop the "wv" marker so AI Mode loads.
            userAgentString = userAgentString.replace("; wv)", ")").replace(Regex("Version/\\S+ "), "")
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
    }

    /** Forgets everything Google could use to recognise this phone, then calls [done]. */
    fun wipe(webView: WebView, done: () -> Unit = {}) {
        webView.stopLoading()
        webView.clearCache(true)
        webView.clearHistory()
        webView.clearFormData()
        WebStorage.getInstance().deleteAllData()
        val cookies = CookieManager.getInstance()
        cookies.removeAllCookies {
            cookies.setCookie("https://www.google.com", CONSENT_COOKIE)
            cookies.flush()
            done()
        }
    }

    /** Hosts that would tie the session to a Google account. Never loaded in the app. */
    fun isSignIn(host: String) = host == "accounts.google.com" || host.startsWith("myaccount.google.") ||
        host == "accounts.youtube.com"

    /**
     * Reads the page: the answer area's text, the outside links (the sources), the short texts of
     * buttons and menus to strip, and whether Google is showing a consent page or a captcha instead.
     */
    const val CAPTURE_JS = """
(function () {
  var state = 'ok';
  var h = location.hostname, p = location.pathname;
  if (h.indexOf('consent.') === 0) state = 'consent';
  else if (h.indexOf('accounts.') === 0) state = 'signin';
  else if (p.indexOf('/sorry') === 0 || document.querySelector('#captcha-form, #recaptcha, iframe[src*="recaptcha"]')) state = 'captcha';
  var root = document.querySelector('[data-container-id="main-col"]') || document.querySelector('[data-subtree="aimc"]') ||
    document.querySelector('[role="main"]') || document.querySelector('main') || document.getElementById('rso') || document.body;
  var text = root ? (root.innerText || '') : '';
  var drop = {}, n = 0;
  document.querySelectorAll('button, [role="button"], [role="tab"], [role="navigation"], [role="search"], [role="dialog"], nav, footer, form, g-snackbar').forEach(function (e) {
    if (n > 600) return;
    (e.innerText || e.getAttribute('aria-label') || '').split('\n').forEach(function (l) {
      l = l.trim();
      if (l && l.length <= 60 && !drop[l]) { drop[l] = 1; n++; }
    });
  });
  var seen = {}, links = [];
  (root || document).querySelectorAll('a[href^="http"]').forEach(function (a) {
    var u = a.href, host = a.hostname || '';
    if (/(^|\.)google\.[a-z.]+$|gstatic\.com$|googleusercontent\.com$/.test(host) || seen[u]) return;
    seen[u] = 1;
    var t = (a.getAttribute('aria-label') || a.innerText || host).trim().split('\n')[0].slice(0, 160);
    if (links.length < 30) links.push({ t: t || host, u: u });
  });
  return JSON.stringify({ state: state, text: text.slice(0, 60000), drop: Object.keys(drop), links: links });
})()
"""
}
