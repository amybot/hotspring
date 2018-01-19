package chat.amy.hotspring.data;

import chat.amy.hotspring.data.event.TrackEvent;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import javax.annotation.PostConstruct;
import java.util.Optional;

/**
 * @author amy
 * @since 1/19/18.
 */
@Repository
public class EventQueue {
    private static final String EVENT_QUEUE = Optional.ofNullable(System.getenv("EVENT_QUEUE")).orElse("event-queue");
    
    @Autowired
    @Qualifier("soRedisTemplate")
    private RedisTemplate<String, Object> template;
    
    private ListOperations<String, Object> listOps;
    
    @PostConstruct
    public void init() {
        listOps = template.opsForList();
    }
    
    public void queue(final TrackEvent event) {
        listOps.rightPush(EVENT_QUEUE, new JSONObject(event).toString());
    }
}
