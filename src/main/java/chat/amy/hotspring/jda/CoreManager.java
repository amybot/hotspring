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

package chat.amy.hotspring.jda;

import net.dv8tion.jda.Core;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author amy
 * @since 1/17/18.
 */
@Component
public class CoreManager {
    private final Map<Integer, Core> cores = new ConcurrentHashMap<>();
    
    public Core getCore(final String botId, final int shard) {
        return cores.computeIfAbsent(shard, (Integer s) -> {
            return new Core(botId, new HotspringCoreClient());
        });
    }
}
