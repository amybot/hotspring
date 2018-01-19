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
        AUDIO_TRACK_START,
        AUDIO_TRACK_STOP,
        AUDIO_TRACK_PAUSE,
        AUDIO_TRACK_QUEUE,
    }
}
