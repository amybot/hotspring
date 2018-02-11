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
import chat.amy.hotspring.api.ApiController;
import chat.amy.hotspring.data.RedisHandle;
import chat.amy.hotspring.data.event.TrackEvent;
import chat.amy.hotspring.jda.HotspringVSU;
import chat.amy.hotspring.jda.audio.PlayerHandle;
import chat.amy.hotspring.server.Playlist.QueuedTrack;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.FunctionalResultHandler;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import lombok.Getter;
import net.dv8tion.jda.Core;
import net.dv8tion.jda.manager.AudioManager;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static chat.amy.hotspring.data.event.TrackEvent.Type.*;
import static chat.amy.hotspring.server.ManagedGuild.PlayMode.*;

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
        try {
            final URI uri = new URI(url);
            return uri.getHost().replaceFirst("www.", "");
        } catch(final NullPointerException ignored) {
            throw new URISyntaxException("null", "invalid uri");
        }
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
    
    public void skipTracks(final ApiContext ctx, int toSkip) {
        if(toSkip == -1) {
            // Skip EVERYTHING
            playlist.deletePlaylist();
            queue.queueTrackEvent(new TrackEvent(AUDIO_QUEUE_END, ctx, null));
        } else {
            // Skip only MOSTLY everything
            // Ensure sanity cap
            if(toSkip > 1000) {
                toSkip = 1000;
            }
            if(toSkip > 0) {
                playlist.skipAmount(toSkip);
                if(playlist.getLength() == 0) {
                    queue.queueTrackEvent(new TrackEvent(AUDIO_QUEUE_END, ctx, null));
                }
            }
        }
    }
    
    public void playTrack(final Core core, final ApiContext ctx, final String track, final PlayMode mode) {
        pool.execute(() -> {
            try {
                final String domainName = getDomainName(track);
                // Check if it's a YT track
                if(domainName.equalsIgnoreCase("youtube.com") || mode == FORCE_PLAY) {
                    // Valid track, do something
                    loadTrackFromURL(core, mode, ctx, track);
                } else {
                    // Invalid track
                    queue.queueTrackEvent(new TrackEvent(AUDIO_TRACK_INVALID, ctx, null));
                }
            } catch(final URISyntaxException e) {
                // Not a valid URL, search YT
                loadTrackFromSearch(core, mode, ctx, track);
            }
        });
    }
    
    public void startNextTrack(final ApiContext context) {
        pool.execute(() -> {
            // TODO: Verify that we're actually connected before starting
            final QueuedTrack next = playlist.getNextTrack();
            if(next != null) {
                try {
                    final ApiContext ctx = next.getCtx();
                    final Core core = ApiController.getInstance().getCoreManager().getCore(ctx.getBotId(), ctx.getShardId());
                    PlayerHandle.AUDIO_PLAYER_MANAGER.loadItem(next.getUrl(), new FunctionalResultHandler(audioTrack -> {
                        core.getAudioManager(ctx.getGuild()).setSendingHandler(handle);
                        handle.getAudioPlayer().playTrack(audioTrack);
                    }, null,
                            () -> queue.queueTrackEvent(new TrackEvent(AUDIO_TRACK_INVALID, ctx, null)),
                            e -> queue.queueTrackEvent(new TrackEvent(AUDIO_TRACK_INVALID, ctx, null))));
                } catch(final Throwable t) {
                    t.printStackTrace();
                }
            } else {
                queue.queueTrackEvent(new TrackEvent(AUDIO_QUEUE_END, context, null));
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
                        final String domainName = getDomainName(track);
                        if(!domainName.equalsIgnoreCase("youtube.com") && mode == DIRECT_PLAY) {
                            // Invalid track
                        } else {
                            // We're good
                            playlist.setCurrentTrack(new QueuedTrack(track, ctx));
                            core.getAudioManager(ctx.getGuild()).setSendingHandler(handle);
                            handle.getAudioPlayer().playTrack(audioTrack);
                        }
                    } catch(final URISyntaxException e) {
                        e.printStackTrace();
                    }
                    break;
                case FORCE_PLAY:
                    playlist.setCurrentTrack(new QueuedTrack(track, ctx));
                    core.getAudioManager(ctx.getGuild()).setSendingHandler(handle);
                    handle.getAudioPlayer().playTrack(audioTrack);
                    break;
            }
        }, pl -> {
            final List<AudioTrack> tracks = pl.getTracks();
            tracks.forEach(audioTrack -> playlist.queueTrack(new QueuedTrack(audioTrack.getInfo().uri, ctx)));
            
            // So this is kinda retarded, but:
            // Basically, because I REALLY don't wanna change the way that track events
            // are constructed, this goes full meme and crams info about how many tracks
            // were queued into an AudioTrackInfo instance
            //
            // Because #lazy
            //
            // Note that this only sets the "title" and "length" fields.
            queue.queueTrackEvent(new TrackEvent(AUDIO_TRACK_QUEUE, ctx, new AudioTrackInfo("Tracks Queued", null,
                    tracks.size(), null, false, null)));
        }, () -> {
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
                    playlist.queueTrack(new QueuedTrack(audioTrack.getInfo().uri, ctx));
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
        FORCE_PLAY,
    }
}
