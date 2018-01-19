package chat.amy.hotspring.data;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericToStringSerializer;
import org.springframework.stereotype.Component;

/**
 * @author amy
 * @since 1/19/18.
 */
@Component
@ComponentScan("chat.amy.hotspring.data")
@SuppressWarnings("unused")
public class RedisConfig {
    @Bean
    public JedisConnectionFactory jedisConnectionFactory() {
        final JedisConnectionFactory factory = new JedisConnectionFactory();
        // TODO: Configurable
        factory.setPassword("a");
        return factory;
    }
    
    @Bean(name="soRedisTemplate")
    public RedisTemplate<String, Object> redisTemplate() {
        final RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(jedisConnectionFactory());
        template.setValueSerializer(new GenericToStringSerializer<>(Object.class));
        return template;
    }
}
