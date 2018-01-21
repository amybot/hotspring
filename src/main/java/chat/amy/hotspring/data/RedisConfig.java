package chat.amy.hotspring.data;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * @author amy
 * @since 1/19/18.
 */
@Component
public class RedisConfig {
    private static final String REDIS_HOST = Optional.ofNullable(System.getenv("REDIS_HOST")).orElse("127.0.0.1");
    private static final String REDIS_PASS = Optional.ofNullable(System.getenv("REDIS_PASS")).orElse("a");
    
    @Bean
    @Primary
    public JedisConnectionFactory jedisConnectionFactory() {
        final JedisConnectionFactory factory = new JedisConnectionFactory();
        factory.setHostName(REDIS_HOST);
        factory.setPassword(REDIS_PASS);
        return factory;
    }
    
    @Bean
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory cf) {
        RedisTemplate<String, String> redisTemplate = new StringRedisTemplate();
        redisTemplate.setConnectionFactory(cf);
        return redisTemplate;
    }
}
