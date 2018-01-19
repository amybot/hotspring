package chat.amy.hotspring.data;

import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import lombok.Value;

/**
 * @author amy
 * @since 1/19/18.
 */
@Value
public class TrackContext {
    private final AudioTrackInfo info;
    private final String guild;
    private final String channel;
}
