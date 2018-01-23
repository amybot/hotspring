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

import net.dv8tion.jda.CoreClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

/**
 * @author amy
 * @since 1/17/18.
 */
public class HotspringCoreClient implements CoreClient {
    private static final Logger log = LoggerFactory.getLogger(HotspringCoreClient.class);
    
    @Override
    public void sendWS(final String s) {
        log.warn("Someone's trying to sendWS()!");
        log.warn("Stacktrace: ");
        log.warn(Arrays.toString(Thread.currentThread().getStackTrace()));
    }
    
    @Override
    public boolean isConnected() {
        return false;
    }
    
    @Override
    public boolean inGuild(final String s) {
        // TODO: Send this from the backend
        return true;
    }
    
    @Override
    public boolean voiceChannelExists(final String s, final String s1) {
        // TODO: Send this from the backend
        return true;
    }
    
    @Override
    public boolean hasPermissionInChannel(final String s, final String s1, final long l) {
        // TODO: Send this from the backend
        return true;
    }
}
