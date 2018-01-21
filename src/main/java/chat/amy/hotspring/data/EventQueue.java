package chat.amy.hotspring.data;

import chat.amy.hotspring.data.event.TrackEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationConfig;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    @SuppressWarnings("unused")
    private static final Logger logger = LoggerFactory.getLogger(EventQueue.class);
    private static final ObjectMapper mapper = new ObjectMapper();
    
    @Autowired
    @Qualifier("redisTemplate")
    private RedisTemplate template;
    
    private ListOperations listOps;
    
    @PostConstruct
    public void init() {
        listOps = template.opsForList();
    }
    
    public void queue(final TrackEvent event) {
        listOps.rightPush(EVENT_QUEUE, serialize(event));
    }
    
    private String serialize(Object obj) {
        try {
            return mapper.writeValueAsString(obj);
        } catch(final JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
