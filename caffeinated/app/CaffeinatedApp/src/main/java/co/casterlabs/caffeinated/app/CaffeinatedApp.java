package co.casterlabs.caffeinated.app;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import co.casterlabs.caffeinated.app.api.AppApi;
import co.casterlabs.caffeinated.app.auth.AppAuth;
import co.casterlabs.caffeinated.app.auth.AuthPreferences;
import co.casterlabs.caffeinated.app.chatbot.AppChatbot;
import co.casterlabs.caffeinated.app.chatbot.ChatbotPreferences;
import co.casterlabs.caffeinated.app.controldeck.AppControlDeck;
import co.casterlabs.caffeinated.app.controldeck.ControlDeckPreferences;
import co.casterlabs.caffeinated.app.koi.GlobalKoi;
import co.casterlabs.caffeinated.app.music_integration.MusicIntegration;
import co.casterlabs.caffeinated.app.music_integration.MusicIntegrationPreferences;
import co.casterlabs.caffeinated.app.plugins.PluginIntegration;
import co.casterlabs.caffeinated.app.plugins.PluginIntegrationPreferences;
import co.casterlabs.caffeinated.app.theming.ThemeManager;
import co.casterlabs.caffeinated.app.ui.AppUI;
import co.casterlabs.caffeinated.app.ui.UIBackgroundColor;
import co.casterlabs.caffeinated.pluginsdk.Caffeinated;
import co.casterlabs.caffeinated.pluginsdk.CasterlabsAccount;
import co.casterlabs.caffeinated.util.ClipboardUtil;
import co.casterlabs.kaimen.util.threading.Promise;
import co.casterlabs.kaimen.webview.Webview;
import co.casterlabs.kaimen.webview.WebviewWindowState;
import co.casterlabs.kaimen.webview.bridge.JavascriptFunction;
import co.casterlabs.kaimen.webview.bridge.JavascriptGetter;
import co.casterlabs.kaimen.webview.bridge.JavascriptObject;
import co.casterlabs.kaimen.webview.bridge.JavascriptValue;
import co.casterlabs.kaimen.webview.bridge.WebviewBridge;
import co.casterlabs.rakurai.json.element.JsonObject;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.SneakyThrows;
import net.harawata.appdirs.AppDirs;
import net.harawata.appdirs.AppDirsFactory;
import xyz.e3ndr.consoleutil.ConsoleUtil;

@Getter
public class CaffeinatedApp extends JavascriptObject implements Caffeinated {
    public static final String caffeinatedClientId = "LmHG2ux992BxqQ7w9RJrfhkW";
    public static final String appDataDir;

    // I chose JsonObject because of the builder syntax.
    // @formatter:off
    public static final JsonObject AUTH_URLS = new JsonObject()
        .put("caffeinated_spotify", "https://casterlabs.co/auth/redirect/spotify")
        .put("caffeinated_twitch",  "https://casterlabs.co/auth/redirect")
        .put("caffeinated_trovo",   "https://casterlabs.co/auth/redirect")
        .put("caffeinated_glimesh", "https://casterlabs.co/auth/redirect")
        .put("caffeinated_brime",   "https://casterlabs.co/auth/redirect")
        .put("caffeinated_youtube", "https://casterlabs.co/auth/redirect");
    // @formatter:on

    private static @Getter CaffeinatedApp instance;

    private final @JavascriptValue(allowSet = false) BuildInfo buildInfo;
    private final @JavascriptValue(allowSet = false) boolean isDev;

    private @Setter WebviewBridge appBridge;
    private @Setter Webview webview;
    private @Setter String appUrl;

    private @JavascriptValue(allowSet = false) boolean isTraySupported;

    // @formatter:off

    // Integrations
    private PluginIntegration plugins = new PluginIntegration();
    private MusicIntegration  music = new MusicIntegration();
    private AppControlDeck    controlDeck = new AppControlDeck();
    private ThemeManager      themeManager = new ThemeManager();
    private AppChatbot        chatbot = new AppChatbot();
    private GlobalKoi         koi = new GlobalKoi();
    private AppAuth           auth = new AppAuth();
    private AppApi            api = new AppApi();
    private AppUI             UI = new AppUI();

    // Preferences
    private PreferenceFile<PluginIntegrationPreferences> pluginIntegrationPreferences = new PreferenceFile<>("plugins", PluginIntegrationPreferences.class);
    private PreferenceFile<MusicIntegrationPreferences>  musicIntegrationPreferences = new PreferenceFile<>("music", MusicIntegrationPreferences.class);
    private PreferenceFile<ControlDeckPreferences>       controlDeckPreferences = new PreferenceFile<>("controldeck", ControlDeckPreferences.class);
    private PreferenceFile<ChatbotPreferences>           chatbotPreferences = new PreferenceFile<>("chatbot", ChatbotPreferences.class);
    private PreferenceFile<WebviewWindowState>           windowPreferences = new PreferenceFile<>("window", WebviewWindowState.class);
    private PreferenceFile<AuthPreferences>              authPreferences = new PreferenceFile<>("auth", AuthPreferences.class);

    // @formatter:on

    @JavascriptValue(allowSet = false)
    private PreferenceFile<AppPreferences> appPreferences = new PreferenceFile<>("app", AppPreferences.class);

    // Event stuff
    private Map<String, List<Consumer<JsonObject>>> appEventListeners = new HashMap<>();

    private @Getter Promise<Void> initPromise = new Promise<>();

    static {
        AppDirs appDirs = AppDirsFactory.getInstance();
        appDataDir = appDirs.getUserDataDir("casterlabs-caffeinated", null, null, true);

        new File(appDataDir, "preferences").mkdirs();
    }

    public CaffeinatedApp(@NonNull BuildInfo buildInfo, boolean isDev) {
        this.buildInfo = buildInfo;
        this.isDev = isDev;
        instance = this;

        this.UI.updateIcon();
    }

    public void init(boolean traySupported) {
        this.isTraySupported = traySupported;

        this.chatbot.init();
        this.UI.init();
        this.themeManager.init();
        this.auth.init();
        this.controlDeck.init();
        this.api.init();
        this.plugins.init();
//            this.koi.init();
        this.music.init();

        this.appPreferences.save();

        this.initPromise.fulfill(null);
    }

    @Override
    public String getLocale() {
        return this.UI.getPreferences().getLanguage();
    }

    public boolean canCloseUI() {
        // We can prevent ui closure if needed.
        // Maybe during plugin installs?
        // TODO
        return true;
    }

    public void shutdown() {
        this.auth.shutdown();
    }

    /**
     * Word of caution, you're not supposed to be able to unsubscribe to an event.
     * You have been warned.
     * 
     * If u throw err, i kil.
     */
    @Deprecated
    public void onAppEvent(@NonNull String type, @NonNull Consumer<JsonObject> handler) {
        if (!this.appEventListeners.containsKey(type)) {
            this.appEventListeners.put(type, new LinkedList<>());
        }

        this.appEventListeners.get(type).add(handler);
    }

    @Deprecated
    public void emitAppEvent(@NonNull String type, @NonNull JsonObject data) {
        if (this.appEventListeners.containsKey(type)) {
            this.appEventListeners
                .get(type)
                .forEach((c) -> c.accept(data));
        }
    }

    @Override
    public void openLink(String url) {
        this.UI.openLink(url);
    }

    @JavascriptFunction
    @Override
    public void copyText(@NonNull String text, @Nullable String toastText) {
        if (toastText == null) {
            toastText = "Copied link to clipboard";
        }

        ClipboardUtil.copy(text);
        this.UI.showToast(toastText, UIBackgroundColor.PRIMARY);
    }

    @JavascriptGetter("enableStupidlyUnsafeSettings")
    public boolean enableStupidlyUnsafeSettings() {
        return this.UI.getPreferences().isEnableStupidlyUnsafeSettings();
    }

    @SneakyThrows
    @JavascriptFunction
    public void unsafe_restartWithConsole() {
        ConsoleUtil.summonConsoleWindow();
    }

    @Override
    public @Nullable CasterlabsAccount getCasterlabsAccount() {
        return this.auth.getCasterlabsAccount();
    }

}
