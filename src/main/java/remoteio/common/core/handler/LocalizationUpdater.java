package remoteio.common.core.handler;

import java.io.InputStream;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.util.StringTranslate;
import net.minecraftforge.common.config.Configuration;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.collect.Maps;
import com.google.common.io.ByteStreams;
import com.google.gson.Gson;

import cpw.mods.fml.common.ObfuscationReflectionHelper;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * @author dmillerw
 */
public class LocalizationUpdater {

    private static final String LANG_DIR = "https://api.github.com/repos/%s/%s/contents/%s?ref=%s";
    private static final String RAW_URL = "https://raw.githubusercontent.com/%s/%s/%s";

    private static final Logger LOGGER = LogManager.getLogger("RemoteIO:Localization");

    private Map<String, Map<String, String>> loadedLangFiles = Maps.newConcurrentMap();

    private final String langUrl;
    private final String rawUrl;

    private boolean optout = false;

    public LocalizationUpdater(String owner, String repo, String branch, String langPath) {
        this(
                String.format(LANG_DIR, owner, repo, langPath, branch),
                String.format(RAW_URL, owner, repo, branch) + "/%s");
    }

    public LocalizationUpdater(String langUrl, String rawUrl) {
        this.langUrl = langUrl;
        this.rawUrl = rawUrl;
    }

    // Called in preInit to start the download thread
    public void initializeThread(Configuration configuration) {
        optout = configuration
                .get(
                        "optout",
                        "localization_update",
                        false,
                        "Opt-out of localization updates, and only use lang files packaged with the JAR")
                .getBoolean(false);
        if (!optout) {
            new Thread(() -> {
                try {
                    URL url = new URL(langUrl);
                    InputStream con = url.openStream();
                    String data = new String(ByteStreams.toByteArray(con));
                    con.close();

                    @SuppressWarnings("unchecked")
                    Map<String, Object>[] json = new Gson().fromJson(data, Map[].class);

                    for (Map<String, Object> aJson : json) {
                        String name = ((String) aJson.get("name"));
                        if (name.endsWith(".lang")) {
                            LOGGER.info("Discovered " + name + ". Downloading...");
                            URL url1 = new URL(String.format(rawUrl, aJson.get("path")));
                            InputStream con1 = url1.openStream();
                            Map<String, String> map = StringTranslate.parseLangFile(con1);
                            LocalizationUpdater.this.loadedLangFiles
                                    .put(name.substring(0, name.lastIndexOf(".lang")), map);
                            con1.close();
                        }
                    }
                } catch (UnknownHostException e) {
                    // Most likely due to the lack of an internet connection. No need to print
                    optout = true;
                } catch (Exception e) {
                    LOGGER.warn("Failed to update localization!", e);
                }
            }).start();
        }
    }

    @SideOnly(Side.CLIENT)
    public void registerListener() {
        ((IReloadableResourceManager) Minecraft.getMinecraft().getResourceManager())
                .registerReloadListener(p_110549_1_ -> this.loadLangFiles());
    }

    // Called whenever the resource manager reloads, to load any lang files that the thread gathered
    private void loadLangFiles() {
        if (this.optout) {
            return;
        }
        Map<String, String> map = loadedLangFiles
                .get(Minecraft.getMinecraft().getLanguageManager().getCurrentLanguage().getLanguageCode());
        if (map == null) {
            return;
        }
        try {
            // Due to Forge patches being applied after Access Transformers in dev, it is still required to use
            // reflection here.
            final Map<String, String> languageList = ObfuscationReflectionHelper.getPrivateValue(
                    StringTranslate.class,
                    StringTranslate.instance,
                    "languageList",
                    "field_74816_c",
                    "d");
            languageList.putAll(map);
            StringTranslate.instance.lastUpdateTimeInMilliseconds = System.currentTimeMillis();
        } catch (Exception e) {}
    }
}
