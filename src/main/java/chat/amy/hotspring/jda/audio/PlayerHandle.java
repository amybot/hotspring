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

package chat.amy.hotspring.jda.audio;

import chat.amy.hotspring.data.RedisHandle;
import chat.amy.hotspring.data.event.TrackEvent;
import chat.amy.hotspring.server.ManagedGuild;
import chat.amy.hotspring.server.Playlist;
import chat.amy.hotspring.server.Playlist.QueuedTrack;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrame;
import lombok.Getter;
import net.dv8tion.jda.audio.AudioSendHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static chat.amy.hotspring.data.event.TrackEvent.Type.AUDIO_TRACK_START;
import static chat.amy.hotspring.data.event.TrackEvent.Type.AUDIO_TRACK_STOP;

/**
 * @author amy
 * @since 1/19/18.
 */
public class PlayerHandle extends AudioEventAdapter implements AudioSendHandler {
    public static final AudioPlayerManager AUDIO_PLAYER_MANAGER;
    private static final Logger logger = LoggerFactory.getLogger(PlayerHandle.class);
    
    static {
        AUDIO_PLAYER_MANAGER = new DefaultAudioPlayerManager();
        AudioSourceManagers.registerRemoteSources(AUDIO_PLAYER_MANAGER);
    }
    
    @Getter
    private final String guildId;
    @Getter
    private final AudioPlayer audioPlayer;
    private final RedisHandle handle;
    private AudioFrame lastFrame;
    
    public PlayerHandle(final String guildId, final AudioPlayer audioPlayer, final RedisHandle handle) {
        this.guildId = guildId;
        this.audioPlayer = audioPlayer;
        this.handle = handle;
    }
    
    @Override
    public boolean canProvide() {
        lastFrame = audioPlayer.provide();
        return lastFrame != null;
    }
    
    @Override
    public byte[] provide20MsAudio() {
        return lastFrame.data;
    }
    
    @Override
    public boolean isOpus() {
        return true;
    }
    
    @Override
    public void onTrackStart(final AudioPlayer player, final AudioTrack track) {
        if(player.isPaused()) {
            return;
        }
        logger.debug("Starting track: " + track.getInfo());
        final Playlist playlist = ManagedGuild.get(guildId, handle).getPlaylist();
        final QueuedTrack currentTrack = playlist.getCurrentTrack();
        if(currentTrack != null) {
            handle.queueTrackEvent(new TrackEvent(AUDIO_TRACK_START, currentTrack.getCtx(), track.getInfo()));
        }
    }
    
    @Override
    public void onTrackEnd(final AudioPlayer player, final AudioTrack track, final AudioTrackEndReason endReason) {
        logger.debug("Ending track: " + track.getInfo());
        logger.debug("End reason: " + endReason);
        final Playlist playlist = ManagedGuild.get(guildId, handle).getPlaylist();
        final QueuedTrack currentTrack = playlist.getCurrentTrack();
        if(currentTrack != null) {
            handle.queueTrackEvent(new TrackEvent(AUDIO_TRACK_STOP, currentTrack.getCtx(), track.getInfo()));
            // Start next track in the queue, if at all possible
            ManagedGuild.get(guildId, handle).startNextTrack(currentTrack.getCtx());
        }
    }
    
    @Override
    public void onTrackException(final AudioPlayer player, final AudioTrack track, final FriendlyException exception) {
        logger.warn("Track exception: " + player.getPlayingTrack().getInfo());
        exception.printStackTrace();
    }
    
    @Override
    public void onTrackStuck(final AudioPlayer player, final AudioTrack track, final long thresholdMs) {
        logger.warn("Track stuck: " + player.getPlayingTrack().getInfo());
    }
}
