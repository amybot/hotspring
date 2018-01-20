package chat.amy.hotspring.jda;

import net.dv8tion.jda.CoreClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

/**
 * @author amy
 * @since 1/17/18.
 */
public class HotspringCoreClient implements CoreClient {
    private static final Logger log = LoggerFactory.getLogger(HotspringCoreClient.class);
    
    @Override
    public void sendWS(final String s) {
        log.warn("Someone's trying to sendWS()!");
        log.warn("Stacktrace: ");
        log.warn(Arrays.toString(Thread.currentThread().getStackTrace()));
    }
    
    @Override
    public boolean isConnected() {
        return false;
    }
    
    @Override
    public boolean inGuild(final String s) {
        // TODO: Send this from the backend
        return true;
    }
    
    @Override
    public boolean voiceChannelExists(final String s, final String s1) {
        // TODO: Send this from the backend
        return true;
    }
    
    @Override
    public boolean hasPermissionInChannel(final String s, final String s1, final long l) {
        // TODO: Send this from the backend
        return true;
    }
}
