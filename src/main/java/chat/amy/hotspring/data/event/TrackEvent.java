package chat.amy.hotspring.data.event;

import chat.amy.hotspring.data.TrackContext;
import lombok.Value;

/**
 * @author amy
 * @since 1/19/18.
 */
@Value
@SuppressWarnings("unused")
public class TrackEvent {
    private final Type t;
    private final TrackContext d;
    
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
