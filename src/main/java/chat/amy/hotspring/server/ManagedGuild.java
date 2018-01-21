package chat.amy.hotspring.server;

import chat.amy.hotspring.data.RedisHandle;
import chat.amy.hotspring.data.event.TrackEvent;
import chat.amy.hotspring.jda.HotspringVSU;
import chat.amy.hotspring.jda.audio.PlayerHandle;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.FunctionalResultHandler;
import lombok.Getter;
import net.dv8tion.jda.Core;
import org.json.JSONObject;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author amy
 * @since 1/21/18.
 */
@SuppressWarnings("unused")
public final class ManagedGuild {
    private static final Map<String, ManagedGuild> MANAGED_GUILDS = new ConcurrentHashMap<>();
    @Getter
    private final String guildId;
    @Getter
    private final PlayerHandle handle;
    private final ExecutorService pool = Executors.newCachedThreadPool();
    private final RedisHandle queue;
    
    private ManagedGuild(final String guildId, final PlayerHandle handle, final RedisHandle queue) {
        this.guildId = guildId;
        this.handle = handle;
        this.queue = queue;
    }
    
    private static ManagedGuild create(final String guildId, final RedisHandle queue) {
        final AudioPlayer audioPlayer = PlayerHandle.AUDIO_PLAYER_MANAGER.createPlayer();
        final PlayerHandle handle = new PlayerHandle(audioPlayer);
        audioPlayer.addListener(handle);
        
        return new ManagedGuild(guildId, handle, queue);
    }
    
    public static ManagedGuild get(final String guildId, final RedisHandle queue) {
        return MANAGED_GUILDS.computeIfAbsent(guildId, __ -> create(guildId, queue));
    }
    
    private static String getDomainName(final String url) throws URISyntaxException {
        final URI uri = new URI(url);
        return uri.getHost().replaceFirst("www.", "");
    }
    
    public void openConnection(final Core core, final String session, final JSONObject vsu) {
        HotspringVSU.acceptVSU(core, session, vsu);
    }
    
    public void closeConnection(final Core core) {
        core.getAudioManager(guildId).closeAudioConnection();
    }
    
    public void playTrack(final Core core, final String track, final PlayMode mode) {
        pool.execute(() -> {
            try {
                final String domainName = getDomainName(track);
                // Check if it's a YT track
                if(domainName.equalsIgnoreCase("youtube.com")) {
                    // Valid track, do something
                    loadTrackFromURL(mode, track);
                } else {
                    // Invalid track
                    // TODO: Queue an event to say that
                    
                    // TODO: This will bork radio probably
                    queue.queueTrackEvent(new TrackEvent(null, null));
                }
            } catch(final URISyntaxException e) {
                // Not a valid URL, search YT
                loadTrackFromSearch(mode, track);
            }
        });
    }
    
    private void loadTrackFromURL(final PlayMode mode, final String track) {
    
    }
    
    private void loadTrackFromSearch(final PlayMode mode, final String track) {
        final AtomicInteger counter = new AtomicInteger();
        PlayerHandle.AUDIO_PLAYER_MANAGER.loadItem("ytsearch:" + track, new FunctionalResultHandler(null, e -> {
            // Consume the playlist
            if(counter.get() != 0) {
                return;
            }
            counter.set(1);
            // Queue
            switch(mode) {
                case QUEUE:
                    break;
                case DIRECT_PLAY:
                    break;
            }
        }, () -> {
            // Couldn't find a track, give up
        }, null));
    }
    
    public enum PlayMode {
        QUEUE,
        DIRECT_PLAY,
    }
}
