package chat.amy.hotspring.api;

import chat.amy.hotspring.data.EventQueue;
import chat.amy.hotspring.data.TrackContext;
import chat.amy.hotspring.data.event.TrackEvent;
import chat.amy.hotspring.data.event.TrackEvent.Type;
import chat.amy.hotspring.jda.CoreManager;
import chat.amy.hotspring.jda.audio.PlayerHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.manager.AudioManager;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * @author amy
 * @since 1/17/18.
 */
@RestController
public class ApiController {
    @SuppressWarnings("unused")
    private static final Logger logger = LoggerFactory.getLogger(ApiController.class);
    @Value("${version}")
    private String version;
    @Autowired
    private CoreManager coreManager;
    @Autowired
    private EventQueue queue;
    
    @RequestMapping("/")
    public Map<String, String> index() {
        System.out.println(coreManager);
        System.out.println(PlayerHandler.AUDIO_PLAYER_MANAGER);
        final Map<String, String> map = new HashMap<>();
        map.put("version", version);
        return map;
    }
    
    /*
      {
        "bot_id": "12345678901234",
        "shard_id": 7,
        "session": "voice session id",
        "vsu": {
          "voice_server_update": "event data goes here"
        },
      }
     */
    @ResponseBody
    @RequestMapping(value = "/connection/open", method = RequestMethod.POST)
    public Map<String, Object> openConnection(@RequestBody final String body) {
        final Map<String, Object> map = new HashMap<>();
        try {
            final JSONObject o = new JSONObject(body);
            final String bot = o.getString("bot_id");
            final int shard = o.getInt("shard_id");
            final String session = o.getString("session");
            final JSONObject vsu = o.getJSONObject("vsu");
            coreManager.getCore(bot, shard).provideVoiceServerUpdate(session, vsu);
            map.put("connected", true);
        } catch(Exception e) {
            map.put("connected", false);
            map.put("error", e);
        }
        return map;
    }
    
    /*
      {
        "bot_id": "1234",
        "shard_id": 7,
        "guild_id": "1234",
      }
     */
    @ResponseBody
    @RequestMapping(value = "/connection/close", method = RequestMethod.POST)
    public Map<String, Object> closeConnection(@RequestBody final String body) {
        final Map<String, Object> map = new HashMap<>();
        try {
            final JSONObject o = new JSONObject(body);
            final String bot = o.getString("bot_id");
            final int shard = o.getInt("shard_id");
            final String guild = o.getString("guild_id");
            final AudioManager audioManager = coreManager.getCore(bot, shard).getAudioManager(guild);
            audioManager.closeAudioConnection();
            map.put("disconnected", true);
        } catch(Exception e) {
            map.put("disconnected", false);
            map.put("error", e);
        }
        return map;
    }
    
    /*
      {
        "url": "https://youtube.com/watch?v=dQw4...",
        "bot_id": "1234",
        "shard_id": 7,
        "guild_id": "108723498023761",
        "channel_id": "189376549813756", <-- TEXT channel, ***NOT*** voice channel. This is used for track context info
      }
     */
    @ResponseBody
    @RequestMapping(value = "/connection/track/play", method = RequestMethod.POST)
    public Map<String, Object> loadTrack(@RequestBody final String body) {
        logger.info("Got track play request with data " + body);
        final Map<String, Object> map = new HashMap<>();
        try {
            final JSONObject o = new JSONObject(body);
            logger.info("Got the data...");
            final String url = o.getString("url");
            final String bot = o.getString("bot_id");
            final int shard = o.getInt("shard_id");
            final String guild = o.getString("guild_id");
            final String channel = o.getString("channel_id");
            logger.info("Attempting load...");
            new Thread(() -> {
                PlayerHandler.AUDIO_PLAYER_MANAGER.loadItem(url, new AudioLoadResultHandler() {
                    @Override
                    public void trackLoaded(final AudioTrack audioTrack) {
                        logger.info("Track loaded! woo!");
                        final AudioPlayer audioPlayer = PlayerHandler.AUDIO_PLAYER_MANAGER.createPlayer();
                        logger.info("Created player.");
                        final PlayerHandler playerHandler = new PlayerHandler(audioPlayer);
                        logger.info("Created handle");
                        coreManager.getCore(bot, shard).getAudioManager(guild).setSendingHandler(playerHandler);
                        logger.info("Set up core");
                        audioPlayer.addListener(playerHandler);
                        logger.info("Set up listener");
                        audioPlayer.playTrack(audioTrack);
                        logger.info("Should be playing!");
                        TrackContext ctx = new TrackContext(audioTrack.getInfo(), guild, channel);
                        //queue.queue(new TrackEvent(Type.AUDIO_TRACK_START, ctx));
                    }
        
                    @Override
                    public void playlistLoaded(final AudioPlaylist audioPlaylist) {
                        // TODO
                        logger.info("Playlist!?");
                    }
        
                    @Override
                    public void noMatches() {
                        // TODO
                        logger.info("No matches :^(");
                    }
        
                    @Override
                    public void loadFailed(final FriendlyException e) {
                        // TODO
                        logger.warn(":fire:");
                        e.printStackTrace();
                    }
                });
            }).start();
            logger.info("Done?");
            map.put("playing", true);
        } catch(Exception e) {
            map.put("playing", false);
            map.put("error", e);
        }
        return map;
    }
}
