package fi.dy.masa.servux.paper.util;

import java.util.UUID;
import javax.annotation.Nullable;

import com.viaversion.viaversion.api.Via;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;

/**
 * Isolates every reference to the optional ViaVersion API, so this class is only loaded when
 * ViaVersion is actually enabled (the plugin must still load without it).
 */
public final class ViaVersionHook
{
    private ViaVersionHook()
    {
    }

    /**
     * @return the connecting client's Minecraft version name (e.g. {@code "26.2"}), or {@code null}
     *         if ViaVersion doesn't know it. For a protocol shared by several releases
     *         (e.g. {@code "26.1-26.1.2"}) this returns the newest one, since the exact client patch
     *         version can't be told apart on the wire.
     */
    @Nullable
    public static String clientMinecraftVersion(UUID uuid)
    {
        return versionName(Via.getAPI().getPlayerProtocolVersion(uuid));
    }

    @Nullable
    static String versionName(@Nullable ProtocolVersion version)
    {
        if (version == null || !version.isKnown())
        {
            return null;
        }

        String name = version.getName();

        if (version.isRange())
        {
            name = name.substring(name.lastIndexOf('-') + 1);
        }

        return name;
    }
}
