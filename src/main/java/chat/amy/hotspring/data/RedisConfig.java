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
