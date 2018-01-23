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

package chat.amy.hotspring.data.event;

import chat.amy.hotspring.api.ApiContext;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import lombok.Value;

/**
 * @author amy
 * @since 1/19/18.
 */
@Value
public class TrackEvent {
    private final Type t;
    private final TrackData d;
    
    public TrackEvent(Type type, ApiContext ctx, AudioTrackInfo info) {
        t = type;
        d = new TrackData(ctx, info);
    }
    
    @Value
    public static final class TrackData {
        private final ApiContext ctx;
        private final AudioTrackInfo info;
    }
    
    public enum Type {
        /**
         * Track started playing
         */
        AUDIO_TRACK_START,
        /**
         * Track stopped playing
         */
        AUDIO_TRACK_STOP,
        /**
         * Track was paused
         */
        AUDIO_TRACK_PAUSE,
        /**
         * Track was queued
         */
        AUDIO_TRACK_QUEUE,
        /**
         * Track was invalid
         */
        AUDIO_TRACK_INVALID,
        /**
         * Queue ended
         */
        AUDIO_QUEUE_END,
    }
}
