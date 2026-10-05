package fi.dy.masa.servux.paper;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.slf4j.Logger;

import io.papermc.paper.ServerBuildInfo;

import fi.dy.masa.servux.paper.util.ViaVersionHook;

/**
 * Small shared reference/logging helper, analogous to Fabric's {@code Reference} constants +
 * {@code Servux.debugLog(...)} - centralizes the mod identifier string (previously computed
 * ad hoc per-channel) and a config-gated debug log, used across the hud_data/structures/entity_data
 * channel and provider classes.
 *
 * @see <a href="../../../../../../../src/main/java/fi/dy/masa/servux/Reference.java">Reference.java (Fabric reference)</a>
 */
public final class ServuxPaperReference
{
    public static final String MOD_ID = "servux";
    public static final String MOD_TYPE = "paper";
    /** MiniHUD rejects any metadata whose "servux" string doesn't start with {@code servux-fabric-<client mc version>}. */
    private static final String WIRE_MOD_TYPE = "fabric";

    private static String serverMcVersion = "unknown";
    private static String pluginVersion = "unknown";
    private static String modString = buildModString(serverMcVersion);
    private static boolean viaVersionEnabled = false;
    private static boolean viaVersionFailed = false;
    private static boolean debugLogEnabled = false;
    private static Logger logger;

    private ServuxPaperReference()
    {
    }

    /** Call once from {@code onEnable()}. */
    public static void init(Plugin plugin)
    {
        logger = plugin.getSLF4JLogger();
        serverMcVersion = ServerBuildInfo.buildInfo().minecraftVersionId();
        pluginVersion = plugin.getPluginMeta().getVersion();
        modString = buildModString(serverMcVersion);
        viaVersionEnabled = plugin.getServer().getPluginManager().isPluginEnabled("ViaVersion");
        viaVersionFailed = false;

        if (viaVersionEnabled)
        {
            logger.info("ViaVersion detected; MiniHUD handshakes will report each client's own Minecraft version");
        }
    }

    private static String buildModString(String mcVersion)
    {
        return MOD_ID + "-" + WIRE_MOD_TYPE + "-" + mcVersion + "-" + pluginVersion + "-" + MOD_TYPE;
    }

    public static void setDebugLogEnabled(boolean enabled)
    {
        debugLogEnabled = enabled;
    }

    /** Mirrors Fabric's {@code Reference.MOD_STRING}, using the server's Minecraft version. */
    public static String modString()
    {
        return modString;
    }

    /**
     * Mod string for a metadata handshake with {@code player}. MiniHUD only accepts a server whose
     * string starts with its own Minecraft version, so when ViaVersion lets a different client
     * version join, report the client's version instead of the server's.
     */
    public static String modString(Player player)
    {
        String clientVersion = clientMinecraftVersion(player);
        return clientVersion.equals(serverMcVersion) ? modString : buildModString(clientVersion);
    }

    private static String clientMinecraftVersion(Player player)
    {
        if (viaVersionEnabled && !viaVersionFailed)
        {
            try
            {
                String version = ViaVersionHook.clientMinecraftVersion(player.getUniqueId());

                if (version != null)
                {
                    return version;
                }
            }
            catch (Exception | LinkageError e)
            {
                viaVersionFailed = true;
                logger.warn("Failed to query ViaVersion for client versions; using the server's Minecraft version", e);
            }
        }

        return serverMcVersion;
    }

    /** Unconditional logger, for Fabric-style {@code Servux.LOGGER.error(...)/.warn(...)} parity (not gated by `debug_log`). */
    public static Logger logger()
    {
        return logger;
    }

    /** Mirrors Fabric's {@code Servux.debugLog(...)} - gated on the `debug_log` config toggle. */
    public static void debugLog(String msg, Object... args)
    {
        if (debugLogEnabled && logger != null)
        {
            logger.info(msg, args);
        }
    }
}
