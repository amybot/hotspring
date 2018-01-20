package chat.amy.hotspring.jda.audio;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrame;
import net.dv8tion.jda.audio.AudioSendHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author amy
 * @since 1/19/18.
 */
public class PlayerHandler extends AudioEventAdapter implements AudioSendHandler {
    private static final Logger logger = LoggerFactory.getLogger(PlayerHandler.class);
    public static final AudioPlayerManager AUDIO_PLAYER_MANAGER;
    
    private final AudioPlayer audioPlayer;
    private AudioFrame lastFrame;
    
    static {
        AUDIO_PLAYER_MANAGER = new DefaultAudioPlayerManager();
        AudioSourceManagers.registerRemoteSources(AUDIO_PLAYER_MANAGER);
    }
    
    public PlayerHandler(final AudioPlayer audioPlayer) {
        this.audioPlayer = audioPlayer;
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
    public void onTrackStart(final AudioPlayer player, final AudioTrack track) {
        logger.info("Starting track: " + track.getInfo());
    }
    
    @Override
    public void onTrackEnd(final AudioPlayer player, final AudioTrack track, final AudioTrackEndReason endReason) {
        logger.info("Ending track: " + track.getInfo());
        System.out.println("End reason: " + endReason);
    }
    
    @Override
    public void onTrackException(final AudioPlayer player, final AudioTrack track, final FriendlyException exception) {
        logger.info("Track exception: " + player.getPlayingTrack().getInfo());
        exception.printStackTrace();
    }
    
    @Override
    public void onTrackStuck(final AudioPlayer player, final AudioTrack track, final long thresholdMs) {
        logger.info("Track stuck: " + player.getPlayingTrack().getInfo());
    }
}
