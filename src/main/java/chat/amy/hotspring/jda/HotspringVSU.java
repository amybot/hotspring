package chat.amy.hotspring.jda;

import net.dv8tion.jda.Core;
import net.dv8tion.jda.audio.AudioConnection;
import net.dv8tion.jda.audio.AudioWebSocket;
import net.dv8tion.jda.manager.AudioManager;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Voice state/server update handler, since the default one goes :fire:
 *
 * @author amy
 * @since 1/19/18.
 */
public final class HotspringVSU {
    private static final Logger logger = LoggerFactory.getLogger(HotspringVSU.class);
    
    private HotspringVSU() {
    }
    
    public static void acceptVSU(final Core core, final String sessionId, final JSONObject content) {
        final String guildId = content.getString("guild_id");
        String endpoint = content.getString("endpoint");
        logger.info("Hotspring got endpoint: " + endpoint);
        endpoint = endpoint.replace(":80", "");
        logger.info("Hotspring guild: " + guildId);
        final String token = content.getString("token");
        final AudioManager audioManager = core.getAudioManager(guildId);
        synchronized(audioManager.CONNECTION_LOCK) {
            try {
                final AudioWebSocket socket = new AudioWebSocket(audioManager.getListenerProxy(), endpoint, core, guildId,
                        sessionId, token, audioManager.isAutoReconnect());
                final AudioConnection connection = new AudioConnection(socket, audioManager.getQueuedAudioConnectionId(),
                        core.getSendFactory());
                audioManager.setAudioConnection(connection);
                socket.startConnection();
            } catch(Throwable t) {
                t.printStackTrace();
            }
        }
    }
}
