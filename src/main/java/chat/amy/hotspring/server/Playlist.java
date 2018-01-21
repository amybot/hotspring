package chat.amy.hotspring.server;

import chat.amy.hotspring.data.RedisHandle;
import lombok.RequiredArgsConstructor;
import lombok.Value;

/**
 * @author amy
 * @since 1/21/18.
 */
@Value
@RequiredArgsConstructor
public class Playlist {
    private final RedisHandle handle;
    private final String guildId;
    
    private static final String PLAYLIST_QUEUE = "%s:playlist-queue";
    
    public void queueTrack(QueuedTrack track) {
        handle.queue(PLAYLIST_QUEUE, track);
    }
    
    public QueuedTrack getNextTrack() {
        return handle.deque(PLAYLIST_QUEUE, QueuedTrack.class);
    }
    
    @Value
    public static final class QueuedTrack {
        private final String url;
        private final String guild;
        private final String channel;
    }
}
