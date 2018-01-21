package chat.amy.hotspring.api;

import chat.amy.hotspring.data.RedisHandle;
import chat.amy.hotspring.data.TrackContext;
import chat.amy.hotspring.data.event.TrackEvent;
import chat.amy.hotspring.jda.CoreManager;
import chat.amy.hotspring.jda.HotspringVSU;
import chat.amy.hotspring.jda.audio.PlayerHandle;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.manager.AudioManager;
import net.dv8tion.jda.utils.SimpleLog;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

import static chat.amy.hotspring.data.event.TrackEvent.Type.AUDIO_TRACK_START;

/**
 * @author amy
 * @since 1/17/18.
 */
@SuppressWarnings("unused")
@RestController
public class ApiController {
    @SuppressWarnings("unused")
    private static final Logger logger = LoggerFactory.getLogger(ApiController.class);
    
    static {
        //noinspection UnnecessarilyQualifiedInnerClassAccess
        SimpleLog.LEVEL = SimpleLog.Level.DEBUG;
        System.out.println("JDA-A LOGS: " + SimpleLog.LEVEL);
    }
    
    @Value("${version}")
    private String version;
    @Autowired
    private CoreManager coreManager;
    @Autowired
    private RedisHandle queue;
    
    @RequestMapping("/")
    public Map<String, String> index() {
        System.out.println(coreManager);
        System.out.println(PlayerHandle.AUDIO_PLAYER_MANAGER);
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
            logger.info("Shard: " + shard);
            final String session = o.getString("session");
            final JSONObject vsu = o.getJSONObject("vsu");
            HotspringVSU.acceptVSU(coreManager.getCore(bot, shard), session, vsu);
            map.put("connected", true);
        } catch(final Exception e) {
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
            logger.info("Shard: " + shard);
            final String guild = o.getString("guild_id");
            // TODO: Store and clean up the handle and etc
            final AudioManager audioManager = coreManager.getCore(bot, shard).getAudioManager(guild);
            audioManager.closeAudioConnection();
            map.put("disconnected", true);
        } catch(final Exception e) {
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
            logger.info("Shard: " + shard);
            logger.info("Attempting load...");
            //noinspection AnonymousInnerClassWithTooManyMethods
            new Thread(() -> PlayerHandle.AUDIO_PLAYER_MANAGER.loadItem(url, new AudioLoadResultHandler() {
                @Override
                public void trackLoaded(final AudioTrack audioTrack) {
                    logger.info("Track loaded! woo!");
                    final AudioPlayer audioPlayer = PlayerHandle.AUDIO_PLAYER_MANAGER.createPlayer();
                    logger.info("Created player.");
                    final PlayerHandle playerHandle = new PlayerHandle(audioPlayer);
                    logger.info("Created handle");
                    audioPlayer.addListener(playerHandle);
                    logger.info("Set up listener");
                    coreManager.getCore(bot, shard).getAudioManager(guild).setSendingHandler(playerHandle);
                    logger.info("Set up core");
                    audioPlayer.setVolume(10);
                    audioPlayer.playTrack(audioTrack);
                    logger.info("Should be playing!");
                    final TrackContext ctx = new TrackContext(audioTrack.getInfo(), guild, channel);
                    queue.queueTrackEvent(new TrackEvent(AUDIO_TRACK_START, ctx));
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
            })).start();
            logger.info("Done?");
            map.put("playing", true);
        } catch(final Exception e) {
            map.put("playing", false);
            map.put("error", e);
        }
        return map;
    }
}
