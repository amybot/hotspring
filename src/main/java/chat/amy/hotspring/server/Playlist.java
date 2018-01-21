package chat.amy.hotspring.server;

import chat.amy.hotspring.api.ApiContext;
import chat.amy.hotspring.data.RedisHandle;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Value;

/**
 * @author amy
 * @since 1/21/18.
 */
@RequiredArgsConstructor
public class Playlist {
    private static final String PLAYLIST_QUEUE = "%s:playlist-queue";
    @Getter
    private final RedisHandle handle;
    @Getter
    private final String guildId;
    @Getter
    private QueuedTrack currentTrack = null;
    
    public void queueTrack(QueuedTrack track) {
        handle.queue(PLAYLIST_QUEUE, track);
    }
    
    public QueuedTrack getNextTrack() {
        final QueuedTrack nextTrack = handle.deque(PLAYLIST_QUEUE, QueuedTrack.class);
        currentTrack = nextTrack;
        return nextTrack;
    }
    
    @Value
    public static final class QueuedTrack {
        private final String url;
        private final ApiContext ctx;
    }
}
