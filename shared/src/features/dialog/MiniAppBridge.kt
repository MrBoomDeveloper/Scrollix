package com.mrboomdev.scrollix.features.dialog

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/**
 * The bottom bar a mini app asks for. Everything is optional: a page that
 * never touches `MainButton` leaves the bar hidden.
 */
internal data class MiniAppMainButton(
    val isVisible: Boolean = false,
    val isActive: Boolean = true,
    val isProgressVisible: Boolean = false,
    val text: String = ""
)

/**
 * Injected into every mini app before its own scripts run. Telegram Mini Apps
 * detect `TelegramWebviewProxy` and send their bridge events through it; the
 * proxy forwards them to the native WebView bridge on Android and records them
 * for the desktop build, which drains the queue.
 *
 * The desktop and Android WebViews have no `telegram-web-app.js`, so the
 * script also provides the `Telegram.WebApp` object pages read their launch
 * data, theme and viewport from. `initData` is taken from the launch URL the
 * provider produced; it is never reconstructed here, because its hash is what
 * the bot verifies.
 *
 * `window.__scrollixMiniAppDispatch` is the way back: the native host calls it
 * to deliver `mainButtonClicked` and `backButtonClicked`.
 */
internal val MiniAppBridgeScript = """
    (function () {
      if (window.__scrollixMiniAppBridge) return;
      window.__scrollixMiniAppBridge = true;
      window.__scrollixMiniAppEvents = window.__scrollixMiniAppEvents || [];

      function send(type, data) {
        var eventType = String(type || '');
        var eventData = data == null ? '' : String(data);

        if (eventType === 'web_app_close') {
          window.__scrollixMiniAppClose = true;
        }

        try { window.__scrollixMiniAppEvents.push([eventType, eventData]); } catch (ignored) {}

        try {
          if (window.ScrollixMiniApp && ScrollixMiniApp.postEvent) {
            ScrollixMiniApp.postEvent(eventType, eventData);
          }
        } catch (ignored) {}
      }

      window.TelegramWebviewProxy = { postEvent: send };
      window.TelegramWebviewProxyProto = window.TelegramWebviewProxy;

      function launchParams() {
        var result = {};

        function read(source) {
          if (!source) return;

          source.split('&').forEach(function (pair) {
            if (!pair) return;

            var index = pair.indexOf('=');
            if (index < 0) return;

            var key = decodeURIComponent(pair.slice(0, index));
            var value = pair.slice(index + 1);

            try { value = decodeURIComponent(value); } catch (ignored) {}
            result[key] = value;
          });
        }

        var hash = window.location.hash || '';
        read(hash.indexOf('#') === 0 ? hash.slice(1) : hash);
        read((window.location.search || '').replace(/^\?/, ''));

        return result;
      }

      function parseJson(value) {
        if (!value) return {};
        try { return JSON.parse(value); } catch (ignored) { return {}; }
      }

      var params = launchParams();
      var initData = params['tgWebAppData'] || '';

      var decoded = {};
      initData.split('&').forEach(function (pair) {
        var index = pair.indexOf('=');
        if (index < 0) return;

        var key = pair.slice(0, index);
        var value = pair.slice(index + 1);

        try { decoded[key] = JSON.parse(decodeURIComponent(value)); } catch (ignored) {
          try { decoded[key] = decodeURIComponent(value); } catch (ignored2) {}
        }
      });

      var theme = parseJson(params['tgWebAppThemeParams']);
      var unsafe = {
        query_id: decoded.query_id,
        user: decoded.user,
        receiver: decoded.receiver,
        chat: decoded.chat,
        start_param: decoded.start_param,
        auth_date: decoded.auth_date,
        hash: decoded.hash
      };

      var listeners = {};
      var shareCallbacks = {};
      var shareCallbackCounter = 0;

      function emit(name, payload) {
        (listeners[name] || []).forEach(function (listener) {
          try { listener(payload); } catch (ignored) {}
        });
      }

      window.__scrollixMiniAppDispatch = function (name, payload) {
        emit(String(name || ''), payload);
      };

      window.__scrollixMiniAppShareResult = function (token, success) {
        var key = String(token || '');
        var callback = shareCallbacks[key];
        if (!callback) return;

        delete shareCallbacks[key];

        try { callback(!!success); } catch (ignored) {}
      };

      function copyText(text) {
        try {
          if (navigator.clipboard && navigator.clipboard.writeText) {
            navigator.clipboard.writeText(String(text));
            return true;
          }
        } catch (ignored) {}

        return false;
      }

      var mainButton = {
        text: '',
        color: '',
        textColor: '',
        isVisible: false,
        isActive: true,
        isProgressVisible: false
      };

      function syncMainButton() {
        send('web_app_setup_main_button', JSON.stringify({
          text: mainButton.text,
          is_visible: mainButton.isVisible,
          is_active: mainButton.isActive,
          is_progress_visible: mainButton.isProgressVisible,
          color: mainButton.color,
          text_color: mainButton.textColor
        }));
      }

      var WebApp = {
        initData: initData,
        initDataUnsafe: unsafe,
        version: params['tgWebAppVersion'] || '7.0',
        platform: params['tgWebAppPlatform'] || 'android',
        colorScheme: 'light',
        themeParams: theme,
        isExpanded: true,
        viewportHeight: window.innerHeight,
        viewportStableHeight: window.innerHeight,
        isClosingConfirmationEnabled: false,
        ready: function (callback) { if (callback) callback(); },
        expand: function () {},
        close: function () { send('web_app_close', ''); },
        sendData: function (data) { send('web_app_data_send', JSON.stringify({ data: String(data) })); },
        openLink: function (url, options) {
          var instant = options && options.try_instant_view;
          send('web_app_open_link', JSON.stringify({ url: String(url), try_instant_view: !!instant }));
        },
        openTelegramLink: function (url) {
          send('web_app_open_tg_link', JSON.stringify({ path_full: String(url) }));
        },
        openInvoice: function (url, callback) {
          send('web_app_open_invoice', JSON.stringify({ slug: String(url) }));
          if (callback) callback('cancelled');
        },
        shareURL: function (url, text) {
          send('web_app_share_url', JSON.stringify({ url: String(url), text: text ? String(text) : '' }));
          copyText(url);
        },
        shareMessage: function (messageId, callback) {
          var token = '';

          // The callback is answered once the host has actually shared the
          // message, not before the user picked a chat.
          if (callback) {
            token = 'share_' + (++shareCallbackCounter);
            shareCallbacks[token] = callback;
          }

          send('web_app_share_message', JSON.stringify({
            msg_id: String(messageId),
            callback_id: token
          }));
        },
        showAlert: function (message, callback) {
          send('web_app_alert', JSON.stringify({ message: String(message) }));
          if (callback) callback();
        },
        showConfirm: function (message, callback) {
          send('web_app_confirm', JSON.stringify({ message: String(message) }));
          if (callback) callback(false);
        },
        enableClosingConfirmation: function () {
          WebApp.isClosingConfirmationEnabled = true;
          send('web_app_setup_closing_behavior', JSON.stringify({ need_confirmation: true }));
        },
        disableClosingConfirmation: function () {
          WebApp.isClosingConfirmationEnabled = false;
          send('web_app_setup_closing_behavior', JSON.stringify({ need_confirmation: false }));
        },
        setHeaderColor: function (color) {
          send('web_app_set_header_color', JSON.stringify({ color: String(color) }));
        },
        setBackgroundColor: function (color) {
          send('web_app_set_background_color', JSON.stringify({ color: String(color) }));
        },
        setBottomBarColor: function (color) {
          send('web_app_set_bottom_bar_color', JSON.stringify({ color: String(color) }));
        },
        onEvent: function (name, listener) {
          (listeners[name] = listeners[name] || []).push(listener);
        },
        offEvent: function (name, listener) {
          listeners[name] = (listeners[name] || []).filter(function (item) {
            return item !== listener;
          });
        },
        HapticFeedback: {
          impactOccurred: function (style) {
            send('web_app_trigger_haptic_feedback', JSON.stringify({ type: 'impact', impact_style: style }));
          },
          notificationOccurred: function (type) {
            send('web_app_trigger_haptic_feedback', JSON.stringify({ type: 'notification', notification_type: type }));
          },
          selectionChanged: function () {
            send('web_app_trigger_haptic_feedback', JSON.stringify({ type: 'selection_change' }));
          }
        },
        BackButton: {
          isVisible: false,
          show: function () {
            WebApp.BackButton.isVisible = true;
            send('web_app_setup_back_button', JSON.stringify({ is_visible: true }));
          },
          hide: function () {
            WebApp.BackButton.isVisible = false;
            send('web_app_setup_back_button', JSON.stringify({ is_visible: false }));
          },
          onClick: function (listener) { WebApp.onEvent('backButtonClicked', listener); },
          offClick: function (listener) { WebApp.offEvent('backButtonClicked', listener); }
        },
        MainButton: {
          get text() { return mainButton.text; },
          set text(value) {
            mainButton.text = String(value);
            syncMainButton();
          },
          get color() { return mainButton.color; },
          set color(value) {
            mainButton.color = String(value);
            syncMainButton();
          },
          get textColor() { return mainButton.textColor; },
          set textColor(value) {
            mainButton.textColor = String(value);
            syncMainButton();
          },
          get isVisible() { return mainButton.isVisible; },
          get isActive() { return mainButton.isActive; },
          get isProgressVisible() { return mainButton.isProgressVisible; },
          setText: function (text) { mainButton.text = String(text); syncMainButton(); },
          onClick: function (listener) { WebApp.onEvent('mainButtonClicked', listener); },
          offClick: function (listener) { WebApp.offEvent('mainButtonClicked', listener); },
          show: function () { mainButton.isVisible = true; syncMainButton(); },
          hide: function () { mainButton.isVisible = false; syncMainButton(); },
          enable: function () { mainButton.isActive = true; syncMainButton(); },
          disable: function () { mainButton.isActive = false; syncMainButton(); },
          showProgress: function () { mainButton.isProgressVisible = true; syncMainButton(); },
          hideProgress: function () { mainButton.isProgressVisible = false; syncMainButton(); },
          setParams: function (params) {
            if (!params) return;
            if (params.text != null) mainButton.text = String(params.text);
            if (params.is_active != null) mainButton.isActive = !!params.is_active;
            if (params.is_visible != null) mainButton.isVisible = !!params.is_visible;
            if (params.is_progress_visible != null) {
              mainButton.isProgressVisible = !!params.is_progress_visible;
            }
            syncMainButton();
          }
        }
      };

      window.Telegram = window.Telegram || {};
      window.Telegram.WebApp = WebApp;
      window.Telegram.WebView = { postEvent: send };
    })();
""".trimIndent()

/** Reads the bottom bar state a mini app asked for. */
internal fun parseMiniAppMainButton(data: String): MiniAppMainButton {
    val json = runCatching { Json.parseToJsonElement(data).jsonObject }.getOrNull()
        ?: return MiniAppMainButton()

    fun field(name: String) = (json[name] as? JsonPrimitive)?.contentOrNull

    return MiniAppMainButton(
        isVisible = field("is_visible")?.toBooleanStrictOrNull() == true,
        isActive = field("is_active")?.toBooleanStrictOrNull() != false,
        isProgressVisible = field("is_progress_visible")?.toBooleanStrictOrNull() == true,
        text = field("text").orEmpty()
    )
}

/** Reads the `url` (or `path_full`) out of a bridge event payload. */
internal fun extractMiniAppUrl(data: String): String? {
    val json = runCatching { Json.parseToJsonElement(data).jsonObject }.getOrNull()
        ?: return null

    val url = (json["url"] as? JsonPrimitive)?.contentOrNull
        ?.takeIf { value -> value.isNotBlank() }
        ?: (json["path_full"] as? JsonPrimitive)?.contentOrNull
            ?.takeIf { value -> value.isNotBlank() }

    return url
}

/** Reads `need_confirmation` out of a closing-behavior payload. */
internal fun parseMiniAppClosingConfirmation(data: String): Boolean {
    val json = runCatching { Json.parseToJsonElement(data).jsonObject }.getOrNull()
        ?: return false

    return (json["need_confirmation"] as? JsonPrimitive)?.booleanOrNull == true
}

/** A `shareMessage` request: the prepared message and its callback token. */
internal data class MiniAppShareRequest(
    val messageId: String,
    val callbackId: String
)

/** Reads `shareMessage` out of a bridge event payload. */
internal fun parseMiniAppShareRequest(data: String): MiniAppShareRequest? {
    val json = runCatching { Json.parseToJsonElement(data).jsonObject }.getOrNull()
        ?: return null

    val messageId = (json["msg_id"] as? JsonPrimitive)?.contentOrNull
        ?.takeIf { value -> value.isNotBlank() }
        ?: return null

    return MiniAppShareRequest(
        messageId = messageId,
        callbackId = (json["callback_id"] as? JsonPrimitive)?.contentOrNull.orEmpty()
    )
}

/** Reads `is_visible` out of a bridge event payload. */
internal fun parseMiniAppVisible(data: String): Boolean? {
    val json = runCatching { Json.parseToJsonElement(data).jsonObject }.getOrNull()
        ?: return null

    return (json["is_visible"] as? JsonPrimitive)?.booleanOrNull
}

/**
 * Drains the event queue the desktop build polls. Android delivers the same
 * events one by one through the JS interface, so this only runs there.
 */
internal fun parseMiniAppEvents(raw: String): List<Pair<String, String>> {
    val parsed = runCatching { Json.parseToJsonElement(raw) }.getOrNull() as? JsonArray
        ?: return emptyList()

    return parsed.mapNotNull { element ->
        val pair = element as? JsonArray ?: return@mapNotNull null
        val type = (pair.getOrNull(0) as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
        val data = (pair.getOrNull(1) as? JsonPrimitive)?.contentOrNull.orEmpty()

        type to data
    }
}

/** JavaScript that empties the queue and returns it as JSON. */
internal const val MiniAppDrainScript =
    "(function(){var q=window.__scrollixMiniAppEvents||[];" +
        "window.__scrollixMiniAppEvents=[];return JSON.stringify(q);})()"
