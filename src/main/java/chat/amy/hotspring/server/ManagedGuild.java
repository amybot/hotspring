package chat.amy.hotspring.server;

import chat.amy.hotspring.api.ApiContext;
import chat.amy.hotspring.data.RedisHandle;
import chat.amy.hotspring.data.event.TrackEvent;
import chat.amy.hotspring.jda.HotspringVSU;
import chat.amy.hotspring.jda.audio.PlayerHandle;
import chat.amy.hotspring.server.Playlist.QueuedTrack;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.FunctionalResultHandler;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import lombok.Getter;
import net.dv8tion.jda.Core;
import net.dv8tion.jda.manager.AudioManager;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static chat.amy.hotspring.data.event.TrackEvent.Type.*;

/**
 * @author amy
 * @since 1/21/18.
 */
@SuppressWarnings("unused")
public final class ManagedGuild {
    private static final Map<String, ManagedGuild> MANAGED_GUILDS = new ConcurrentHashMap<>();
    private static final Logger logger = LoggerFactory.getLogger(ManagedGuild.class);
    @Getter
    private final String guildId;
    @Getter
    private final PlayerHandle handle;
    private final ExecutorService pool = Executors.newCachedThreadPool();
    private final RedisHandle queue;
    @Getter
    private final Playlist playlist;
    
    private ManagedGuild(final String guildId, final PlayerHandle handle, final RedisHandle queue) {
        this.guildId = guildId;
        this.handle = handle;
        this.queue = queue;
        playlist = new Playlist(queue, guildId);
    }
    
    private static ManagedGuild create(final String guildId, final RedisHandle queue) {
        final AudioPlayer audioPlayer = PlayerHandle.AUDIO_PLAYER_MANAGER.createPlayer();
        final PlayerHandle handle = new PlayerHandle(guildId, audioPlayer, queue);
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
        handle.getAudioPlayer().stopTrack();
        getAudioManager(core).closeAudioConnection();
    }
    
    @SuppressWarnings("WeakerAccess")
    public AudioManager getAudioManager(final Core core) {
        return core.getAudioManager(guildId);
    }
    
    public void pauseTrack() {
        handle.getAudioPlayer().setPaused(!handle.getAudioPlayer().isPaused());
    }
    
    public void playTrack(final Core core, final ApiContext ctx, final String track, final PlayMode mode) {
        pool.execute(() -> {
            try {
                final String domainName = getDomainName(track);
                // Check if it's a YT track
                if(domainName.equalsIgnoreCase("youtube.com")) {
                    // Valid track, do something
                    loadTrackFromURL(core, mode, ctx, track);
                } else {
                    // Invalid track
                    // TODO: This will bork radio probably
                    queue.queueTrackEvent(new TrackEvent(AUDIO_TRACK_INVALID, ctx, null));
                }
            } catch(final URISyntaxException e) {
                // Not a valid URL, search YT
                loadTrackFromSearch(core, mode, ctx, track);
            }
        });
    }
    
    private void loadTrackFromURL(final Core core, final PlayMode mode, final ApiContext ctx, final String track) {
        PlayerHandle.AUDIO_PLAYER_MANAGER.loadItem(track, new FunctionalResultHandler(audioTrack -> {
            switch(mode) {
                case QUEUE:
                    playlist.queueTrack(new QueuedTrack(track, ctx));
                    queue.queueTrackEvent(new TrackEvent(AUDIO_TRACK_QUEUE, ctx, audioTrack.getInfo()));
                    break;
                case DIRECT_PLAY:
                    try {
                        playlist.setCurrentTrack(new QueuedTrack(track, ctx));
                        core.getAudioManager(ctx.getGuild()).setSendingHandler(handle);
                        handle.getAudioPlayer().playTrack(audioTrack);
                    } catch(Throwable t) {
                        t.printStackTrace();
                    }
                    break;
            }
        }, null, () -> {
            // Couldn't find a track, give up
        }, null));
    }
    
    private void loadTrackFromSearch(final Core core, final PlayMode mode, final ApiContext ctx, final String track) {
        final AtomicInteger counter = new AtomicInteger();
        PlayerHandle.AUDIO_PLAYER_MANAGER.loadItem("ytsearch:" + track, new FunctionalResultHandler(null, e -> {
            // Consume the playlist
            if(counter.get() != 0) {
                return;
            }
            counter.set(1);
            // Queue
            final AudioTrack audioTrack = e.getTracks().get(0);
            switch(mode) {
                case QUEUE:
                    playlist.queueTrack(new QueuedTrack(track, ctx));
                    queue.queueTrackEvent(new TrackEvent(AUDIO_TRACK_QUEUE, ctx, audioTrack.getInfo()));
                    break;
                case DIRECT_PLAY:
                    handle.getAudioPlayer().playTrack(audioTrack);
                    core.getAudioManager(ctx.getGuild()).setSendingHandler(handle);
                    playlist.setCurrentTrack(new QueuedTrack(audioTrack.getInfo().uri, ctx));
                    break;
            }
        }, () -> {
            // Couldn't find a track, give up
            queue.queueTrackEvent(new TrackEvent(AUDIO_TRACK_INVALID, ctx, null));
        }, null));
    }
    
    public enum PlayMode {
        QUEUE,
        DIRECT_PLAY,
    }
}
