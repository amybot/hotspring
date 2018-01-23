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

package chat.amy.hotspring.api;

import chat.amy.hotspring.data.RedisHandle;
import chat.amy.hotspring.data.event.TrackEvent;
import chat.amy.hotspring.data.event.TrackEvent.Type;
import chat.amy.hotspring.jda.CoreManager;
import chat.amy.hotspring.jda.audio.PlayerHandle;
import chat.amy.hotspring.server.ManagedGuild;
import com.google.common.collect.ImmutableMap;
import lombok.Getter;
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
    @Getter
    @SuppressWarnings("StaticVariableOfConcreteClass")
    private static ApiController instance;
    
    static {
        //noinspection UnnecessarilyQualifiedInnerClassAccess
        SimpleLog.LEVEL = SimpleLog.Level.DEBUG;
        // TODO: Is this stupid?
        // We hold on to the players forever, because each handle just holds a
        // single player that all of the guild's tracks go through.
        PlayerHandle.AUDIO_PLAYER_MANAGER.setPlayerCleanupThreshold(Long.MAX_VALUE);
    }

    @Value("${version}")
    private String version;
    @Getter
    @Autowired
    private CoreManager coreManager;
    @Getter
    @Autowired
    private RedisHandle queue;
    
    public ApiController() {
        instance = this;
    }
    
    @RequestMapping("/")
    public Map<String, String> index() {
        final Map<String, String> map = new HashMap<>();
        map.put("version", version);
        return map;
    }
    
    @ResponseBody
    @RequestMapping(value = "/connection/open", method = RequestMethod.POST)
    public Map<String, Object> openConnection(@RequestBody final String body) {
        final JSONObject data = new JSONObject(body);
        final ApiContext ctx = ApiContext.fromContext(data.getJSONObject("ctx"));
        
        ManagedGuild.get(ctx.getGuild(), queue)
                .openConnection(coreManager.getCore(ctx.getBotId(), ctx.getShardId()),
                        data.getString("session"), data.getJSONObject("vsu"));
        
        return ImmutableMap.of("connected", true);
    }
    
    @ResponseBody
    @RequestMapping(value = "/connection/close", method = RequestMethod.POST)
    public Map<String, Object> closeConnection(@RequestBody final String body) {
        final JSONObject data = new JSONObject(body);
        final ApiContext ctx = ApiContext.fromContext(data.getJSONObject("ctx"));
        
        ManagedGuild.get(ctx.getGuild(), queue).closeConnection(coreManager.getCore(ctx.getBotId(), ctx.getShardId()));
        
        return ImmutableMap.of("disconnected", true);
    }
    
    @ResponseBody
    @RequestMapping(value = "/connection/track/play", method = RequestMethod.POST)
    public Map<String, Object> playTrack(@RequestBody final String body) {
        final JSONObject data = new JSONObject(body);
        final ApiContext ctx = ApiContext.fromContext(data.getJSONObject("ctx"));
        final Core core = coreManager.getCore(ctx.getBotId(), ctx.getShardId());
        ManagedGuild.get(ctx.getGuild(), queue).playTrack(core, ctx, data.getString("url"), DIRECT_PLAY);
        
        return ImmutableMap.of("playing", true);
    }
    
    @ResponseBody
    @RequestMapping(value = "/connection/track/pause", method = RequestMethod.POST)
    public Map<String, Object> pauseTrack(@RequestBody final String body) {
        final JSONObject data = new JSONObject(body);
        final ApiContext ctx = ApiContext.fromContext(data.getJSONObject("ctx"));
        ManagedGuild.get(ctx.getGuild(), queue).pauseTrack();
        queue.queueTrackEvent(new TrackEvent(Type.AUDIO_TRACK_PAUSE, ctx,
                ManagedGuild.get(ctx.getGuild(), queue).getHandle()
                        .getAudioPlayer().getPlayingTrack().getInfo()));
        
        return ImmutableMap.of("paused", true);
    }
    
    @ResponseBody
    @RequestMapping(value = "/connection/track/current", method = RequestMethod.POST)
    public Map<String, Object> currentTrack(@RequestBody final String body) {
        final JSONObject data = new JSONObject(body);
        final ApiContext ctx = ApiContext.fromContext(data.getJSONObject("ctx"));
        ManagedGuild.get(ctx.getGuild(), queue).pauseTrack();
        
        return ImmutableMap.of("info", ManagedGuild.get(ctx.getGuild(), queue).getHandle()
                .getAudioPlayer().getPlayingTrack().getInfo());
    }
    
    @ResponseBody
    @RequestMapping(value = "/connection/queue/add", method = RequestMethod.POST)
    public Map<String, Object> queueAdd(@RequestBody final String body) {
        final JSONObject data = new JSONObject(body);
        final ApiContext ctx = ApiContext.fromContext(data.getJSONObject("ctx"));
        final Core core = coreManager.getCore(ctx.getBotId(), ctx.getShardId());
        ManagedGuild.get(ctx.getGuild(), queue).playTrack(core, ctx, data.getString("url"), QUEUE);
        
        return ImmutableMap.of("queued", true);
    }
    
    @ResponseBody
    @RequestMapping(value = "/connection/queue/start", method = RequestMethod.POST)
    public Map<String, Object> queueStart(@RequestBody final String body) {
        final JSONObject data = new JSONObject(body);
        final ApiContext ctx = ApiContext.fromContext(data.getJSONObject("ctx"));
        final Core core = coreManager.getCore(ctx.getBotId(), ctx.getShardId());
        ManagedGuild.get(ctx.getGuild(), queue).startNextTrack(ctx);
        
        return ImmutableMap.of("started", true);
    }
    
    @ResponseBody
    @RequestMapping(value = "/connection/queue/length", method = RequestMethod.POST)
    public Map<String, Object> queueLength(@RequestBody final String body) {
        final JSONObject data = new JSONObject(body);
        final ApiContext ctx = ApiContext.fromContext(data.getJSONObject("ctx"));
        final Core core = coreManager.getCore(ctx.getBotId(), ctx.getShardId());
        final int len = ManagedGuild.get(ctx.getGuild(), queue).getPlaylist().getLength();
        
        return ImmutableMap.of("length", len);
    }
}
