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
    }
}
