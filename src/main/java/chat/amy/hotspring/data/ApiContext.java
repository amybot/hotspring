package chat.amy.hotspring.data;

import lombok.Value;
import org.json.JSONObject;

/**
 * @author amy
 * @since 1/19/18.
 */
@Value
public class ApiContext {
    private final String guild;
    private final String channel;
    private final String botId;
    private final int shardId;
    
    public static ApiContext fromContext(final JSONObject ctx) {
        return new ApiContext(ctx.getString("guild_id"), ctx.getString("channel_id"),
                ctx.getString("bot_id"), ctx.getInt("shard_id"));
    }
}
