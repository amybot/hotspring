package chat.amy.hotspring.jda;

import net.dv8tion.jda.Core;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author amy
 * @since 1/17/18.
 */
@Component
public class CoreManager {
    private final Map<Integer, Core> cores = new ConcurrentHashMap<>();
    
    public Core getCore(final String botId, final int shard) {
        return cores.computeIfAbsent(shard, (Integer s) -> {
            return new Core(botId, new HotspringCoreClient());
        });
    }
}
