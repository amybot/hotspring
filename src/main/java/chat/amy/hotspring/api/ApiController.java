package chat.amy.hotspring.api;

import chat.amy.hotspring.data.RedisHandle;
import chat.amy.hotspring.data.event.TrackEvent;
import chat.amy.hotspring.data.event.TrackEvent.Type;
import chat.amy.hotspring.jda.CoreManager;
import chat.amy.hotspring.jda.audio.PlayerHandle;
import chat.amy.hotspring.server.ManagedGuild;
import com.google.common.collect.ImmutableMap;
import net.dv8tion.jda.Core;
import net.dv8tion.jda.utils.SimpleLog;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

import static chat.amy.hotspring.server.ManagedGuild.PlayMode.DIRECT_PLAY;
import static chat.amy.hotspring.server.ManagedGuild.PlayMode.QUEUE;

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
    
    @ResponseBody
    @RequestMapping(value = "/connection/open", method = RequestMethod.POST)
    public Map<String, Object> openConnection(@RequestBody final String body) {
        final JSONObject data = new JSONObject(body);
        final ApiContext ctx = ApiContext.fromContext(new JSONObject(data.getJSONObject("ctx")));
        
        ManagedGuild.get(ctx.getGuild(), queue)
                .openConnection(coreManager.getCore(ctx.getBotId(), ctx.getShardId()),
                        data.getString("session"), data.getJSONObject("vsu"));
        
        return ImmutableMap.of("connected", true);
    }
    
    @ResponseBody
    @RequestMapping(value = "/connection/close", method = RequestMethod.POST)
    public Map<String, Object> closeConnection(@RequestBody final String body) {
        final JSONObject data = new JSONObject(body);
        final ApiContext ctx = ApiContext.fromContext(new JSONObject(data.getJSONObject("ctx")));
        
        ManagedGuild.get(ctx.getGuild(), queue).closeConnection(coreManager.getCore(ctx.getBotId(), ctx.getShardId()));
        
        return ImmutableMap.of("disconnected", true);
    }
    
    @ResponseBody
    @RequestMapping(value = "/connection/track/play", method = RequestMethod.POST)
    public Map<String, Object> playTrack(@RequestBody final String body) {
        final JSONObject data = new JSONObject(body);
        final ApiContext ctx = ApiContext.fromContext(new JSONObject(data.getJSONObject("ctx")));
        final Core core = coreManager.getCore(ctx.getBotId(), ctx.getShardId());
        ManagedGuild.get(ctx.getGuild(), queue).playTrack(core, ctx, data.getString("url"), DIRECT_PLAY);
        
        return ImmutableMap.of("playing", true);
    }
    
    @ResponseBody
    @RequestMapping(value = "/connection/track/queue", method = RequestMethod.POST)
    public Map<String, Object> queueTrack(@RequestBody final String body) {
        final JSONObject data = new JSONObject(body);
        final ApiContext ctx = ApiContext.fromContext(new JSONObject(data.getJSONObject("ctx")));
        final Core core = coreManager.getCore(ctx.getBotId(), ctx.getShardId());
        ManagedGuild.get(ctx.getGuild(), queue).playTrack(core, ctx, data.getString("url"), QUEUE);
        
        return ImmutableMap.of("playing", true);
    }
    
    @ResponseBody
    @RequestMapping(value = "/connection/track/pause", method = RequestMethod.POST)
    public Map<String, Object> pauseTrack(@RequestBody final String body) {
        final JSONObject data = new JSONObject(body);
        final ApiContext ctx = ApiContext.fromContext(new JSONObject(data.getJSONObject("ctx")));
        ManagedGuild.get(ctx.getGuild(), queue).pauseTrack();
        queue.queueTrackEvent(new TrackEvent(Type.AUDIO_TRACK_PAUSE, ctx,
                ManagedGuild.get(ctx.getGuild(), queue).getHandle()
                        .getAudioPlayer().getPlayingTrack().getInfo()));
        
        return ImmutableMap.of("playing", true);
    }
}
