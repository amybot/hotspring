/*
 * This file is part of Hotspring.
 *
 * Hotspring is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Hotspring is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Hotspring.  If not, see <http://www.gnu.org/licenses/>.
 */

package chat.amy.hotspring.server;

import chat.amy.hotspring.api.ApiContext;
import chat.amy.hotspring.data.RedisHandle;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
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
    @Setter
    private QueuedTrack currentTrack;
    
    private String getQueueName() {
        return String.format(PLAYLIST_QUEUE, guildId);
    }
    
    public void queueTrack(final QueuedTrack track) {
        handle.queue(getQueueName(), track);
    }
    
    public int getLength() {
        return handle.getQueueSize(getQueueName());
    }
    
    public QueuedTrack getNextTrack() {
        final QueuedTrack nextTrack = handle.deque(getQueueName(), QueuedTrack.class);
        currentTrack = nextTrack;
        return nextTrack;
    }
    
    public void skipAmount(int amount) {
        while(amount > 0) {
            getNextTrack();
            --amount;
        }
    }
    
    public void deletePlaylist() {
        handle.delete(String.format(PLAYLIST_QUEUE, guildId));
    }
    
    @Value
    public static final class QueuedTrack {
        private final String url;
        private final ApiContext ctx;
    }
}
