/*
 *     Copyright 2015-2017 Austin Keener & Michael Ritter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.dv8tion.jda.audio;

import com.neovisionaries.ws.client.*;
import net.dv8tion.jda.Core;
import net.dv8tion.jda.audio.VoiceCode.Close;
import net.dv8tion.jda.audio.hooks.ConnectionListener;
import net.dv8tion.jda.audio.hooks.ConnectionStatus;
import net.dv8tion.jda.manager.AudioManager;
import net.dv8tion.jda.utils.SimpleLog;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.net.ssl.SSLPeerUnverifiedException;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.NoRouteToHostException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@SuppressWarnings({"WeakerAccess", "RedundantThrows", "StatementWithEmptyBody", "unused", "PublicField"})
public class AudioWebSocket extends WebSocketAdapter {
    public static final SimpleLog LOG = SimpleLog.getLog("JDAAudioSocket");
    public static final int DISCORD_SECRET_KEY_LENGTH = 32;
    
    protected final ConnectionListener listener;
    protected final ScheduledThreadPoolExecutor keepAlivePool;
    private final Core core;
    private final String guildId;
    private final String endpoint;
    private final String sessionId;
    private final String token;
    private final String wssEndpoint;
    public WebSocket socket;
    protected AudioConnection audioConnection;
    protected ConnectionStatus connectionStatus = ConnectionStatus.NOT_CONNECTED;
    private boolean reconnecting;
    private boolean connected;
    private boolean ready;
    private boolean shutdown;
    private Future<?> keepAliveHandle;
    private boolean shouldReconnect;
    private int ssrc;
    private byte[] secretKey;
    private DatagramSocket udpSocket;
    private InetSocketAddress address;
    
    public AudioWebSocket(final ConnectionListener listener, final String endpoint, final Core core, final String guildId, final String sessionId, final String token, final boolean shouldReconnect) {
        this.core = core;
        this.listener = listener;
        this.endpoint = endpoint;
        this.guildId = guildId;
        this.sessionId = sessionId;
        this.token = token;
        this.shouldReconnect = shouldReconnect;
        
        keepAlivePool = core.getAudioKeepAlivePool();
        
        //Append the Secure Websocket scheme so that our websocket library knows how to connect
        //specify v3 to work according to documentation details
        wssEndpoint = String.format("wss://%s/?v=3", endpoint);
        
        if(sessionId == null || sessionId.isEmpty()) {
            throw new IllegalArgumentException("Cannot create a voice connection using a null/empty sessionId!");
        }
        if(token == null || token.isEmpty()) {
            throw new IllegalArgumentException("Cannot create a voice connection using a null/empty token!");
        }
    }
    
    public void send(final String message) {
        socket.sendText(message);
    }
    
    public void send(final int code, final Object object) {
        send(new JSONObject()
                .put("op", code)
                .put("d", object).toString());
    }
    
    @Override
    public void onConnected(final WebSocket websocket, final Map<String, List<String>> headers) {
        
        if(shutdown) {
            //Somehow this AudioWebSocket was shutdown before we finished connecting....
            // thus we just disconnect here since we were asked to shutdown
            socket.sendClose(1000);
            return;
        }
        
        connected = true;
        if(reconnecting) {
            sendResume();
        } else {
            sendIdentify();
        }
        changeStatus(ConnectionStatus.CONNECTING_AWAITING_AUTHENTICATING);
    }
    
    private void sendIdentify() {
        send(VoiceCode.IDENTIFY,
                new JSONObject()
                        .put("server_id", guildId)
                        .put("user_id", core.getUserId())
                        .put("session_id", sessionId)
                        .put("token", token));
    }
    
    private void sendResume() {
        send(VoiceCode.RESUME,
                new JSONObject()
                        .put("server_id", guildId)
                        .put("session_id", sessionId)
                        .put("token", token));
    }
    
    private void sendKeepAlive() {
        send(VoiceCode.HEARTBEAT, System.currentTimeMillis());
    }
    
    @Override
    public void onTextMessage(final WebSocket websocket, final String message) {
        final JSONObject contentAll = new JSONObject(message);
        final int opCode = contentAll.getInt("op");
        
        switch(opCode) {
            case VoiceCode.HELLO: {
                final JSONObject payload = contentAll.getJSONObject("d");
                final int heartbeatInterval = payload.getInt("heartbeat_interval");
                stopKeepAlive();
                setupKeepAlive(heartbeatInterval / 2);
                //FIXME: discord will rollout a working interval once that is done we need to use it properly
                break;
            }
            case VoiceCode.READY: {
                final JSONObject content = contentAll.getJSONObject("d");
                ssrc = content.getInt("ssrc");
                final int port = content.getInt("port");
                
                //Find our external IP and Port using Discord
                InetSocketAddress externalIpAndPort;
                
                changeStatus(ConnectionStatus.CONNECTING_ATTEMPTING_UDP_DISCOVERY);
                int tries = 0;
                do {
                    externalIpAndPort = handleUdpDiscovery(new InetSocketAddress(endpoint, port), ssrc);
                    tries++;
                    if(externalIpAndPort == null && tries > 5) {
                        close(ConnectionStatus.ERROR_UDP_UNABLE_TO_CONNECT);
                        return;
                    }
                } while(externalIpAndPort == null);
                
                send(VoiceCode.SELECT_PROTOCOL,
                        new JSONObject()
                                .put("protocol", "udp")
                                .put("data", new JSONObject()
                                        .put("address", externalIpAndPort.getHostString())
                                        .put("port", externalIpAndPort.getPort())
                                        .put("mode", "xsalsa20_poly1305")   //Discord requires encryption
                                )
                );
                
                changeStatus(ConnectionStatus.CONNECTING_AWAITING_READY);
                break;
            }
            case VoiceCode.SESSION_DESCRIPTION: {
                //secret_key is an array of 32 ints that are less than 256, so they are bytes.
                final JSONArray keyArray = contentAll.getJSONObject("d").getJSONArray("secret_key");
                
                secretKey = new byte[DISCORD_SECRET_KEY_LENGTH];
                for(int i = 0; i < keyArray.length(); i++) {
                    secretKey[i] = (byte) keyArray.getInt(i);
                }
                
                LOG.trace("Audio connection has finished connecting!");
            } // fall through to change state
            case VoiceCode.RESUMED: {
                changeStatus(ConnectionStatus.CONNECTED);
                ready = true;
                break;
            }
            case VoiceCode.HEARTBEAT: {
                sendKeepAlive();
                break;
            }
            case VoiceCode.HEARTBEAT_ACK: {
                final long ping = System.currentTimeMillis() - contentAll.getLong("d");
                listener.onPing(ping);
                break;
            }
            case VoiceCode.USER_SPEAKING_UPDATE: {
                final JSONObject content = contentAll.getJSONObject("d");
                final boolean speaking = content.getBoolean("speaking");
                final int ssrc = content.getInt("ssrc");
                final String userId = content.getString("user_id");
                
                audioConnection.updateUserSSRC(ssrc, userId);
                listener.onUserSpeaking(userId, speaking);
                break;
            }
            case VoiceCode.USER_DISCONNECT: {
                final JSONObject payload = contentAll.getJSONObject("d");
                final String userId = payload.getString("user_id");
                audioConnection.removeUserSSRC(userId);
                break;
            }
            case 12:
                break;
            default:
                LOG.debug("Unknown Audio OP code.\n" + contentAll.toString(4));
        }
    }
    
    @Override
    public void onDisconnected(final WebSocket websocket, final WebSocketFrame serverCloseFrame, final WebSocketFrame clientCloseFrame, final boolean closedByServer) {
        LOG.debug("The Audio connection was closed!");
        LOG.debug("By remote? " + closedByServer);
        if(serverCloseFrame != null) {
            LOG.debug("Reason: " + serverCloseFrame.getCloseReason());
            LOG.debug("Close code: " + serverCloseFrame.getCloseCode());
            final int code = serverCloseFrame.getCloseCode();
            final Close closeCode = Close.from(code);
            switch(closeCode) {
                case SERVER_NOT_FOUND:
                case SERVER_CRASH:
                case INVALID_SESSION:
                    close(ConnectionStatus.ERROR_CANNOT_RESUME);
                    break;
                case AUTHENTICATION_FAILED:
                    close(ConnectionStatus.DISCONNECTED_AUTHENTICATION_FAILURE);
                    break;
                default:
                    reconnect(ConnectionStatus.ERROR_LOST_CONNECTION);
            }
            return;
        }
        if(clientCloseFrame != null) {
            LOG.debug("ClientReason: " + clientCloseFrame.getCloseReason());
            LOG.debug("ClientCode: " + clientCloseFrame.getCloseCode());
            if(clientCloseFrame.getCloseCode() != 1000) {
                // unexpected close -> error -> attempt resume
                reconnect(ConnectionStatus.ERROR_LOST_CONNECTION);
                return;
            }
        }
        close(ConnectionStatus.NOT_CONNECTED);
    }
    
    @Override
    public void onUnexpectedError(final WebSocket websocket, final WebSocketException cause) {
        handleCallbackError(websocket, cause);
    }
    
    @Override
    public void handleCallbackError(final WebSocket websocket, final Throwable cause) {
        LOG.log(cause);
    }
    
    @Override
    public void onThreadCreated(final WebSocket websocket, final ThreadType threadType, final Thread thread) throws Exception {
        final String identifier = "JDA-Audio";
        final String guildId = this.guildId;
        switch(threadType) {
            case CONNECT_THREAD:
                thread.setName(identifier + " AudioWS-ConnectThread (guildId: " + guildId + ')');
                break;
            case FINISH_THREAD:
                thread.setName(identifier + " AudioWS-FinishThread (guildId: " + guildId + ')');
                break;
            case WRITING_THREAD:
                thread.setName(identifier + " AudioWS-WriteThread (guildId: " + guildId + ')');
                break;
            case READING_THREAD:
                thread.setName(identifier + " AudioWS-ReadThread (guildId: " + guildId + ')');
                break;
            default:
                thread.setName(identifier + " AudioWS-" + threadType + " (guildId: " + guildId + ')');
        }
    }
    
    @Override
    public void onConnectError(final WebSocket webSocket, final WebSocketException e) {
        if(e instanceof HostnameUnverifiedException) {
            final HostnameUnverifiedException ex = (HostnameUnverifiedException) e;
            LOG.warn("HOSTNAME:  " + ex.getHostname());
            LOG.warn("Peer host: " + ex.getSSLSocket().getSession().getPeerHost());
            try {
                LOG.warn("PEER:     " + ex.getSSLSocket().getSession().getPeerPrincipal());
            } catch(final SSLPeerUnverifiedException e1) {
                LOG.warn("Couldn't log peer!:");
                e1.printStackTrace();
            }
        }
        LOG.warn("Failed to establish websocket connection: " + e.getError() + " - " + e.getMessage()
                + "\nClosing connection and attempting to reconnect.");
        close(ConnectionStatus.ERROR_WEBSOCKET_UNABLE_TO_CONNECT);
    }
    
    public void startConnection() {
        if(!reconnecting && socket != null) {
            throw new RuntimeException("Somehow, someway, this AudioWebSocket has already attempted to start a connection!");
        }
        
        try {
            socket = new WebSocketFactory()
                    .createSocket(wssEndpoint)
                    .addListener(this);
            changeStatus(ConnectionStatus.CONNECTING_AWAITING_WEBSOCKET_CONNECT);
            socket.connectAsynchronously();
        } catch(final IOException e) {
            LOG.warn("Encountered IOException while attempting to connect: " + e.getMessage()
                    + "\nClosing connection and attempting to reconnect.");
            close(ConnectionStatus.ERROR_WEBSOCKET_UNABLE_TO_CONNECT);
        }
    }
    
    public void reconnect(final ConnectionStatus status) {
        if(shutdown) {
            return;
        }
        reconnecting = true;
        ready = false;
        connected = false;
        changeStatus(status);
        startConnection();
    }
    
    public void close(final ConnectionStatus closeStatus) {
        //Makes sure we don't run this method again after the socket.close(1000) call fires onDisconnect
        if(shutdown) {
            return;
        }
        connected = false;
        ready = false;
        shutdown = true;
        if(closeStatus != ConnectionStatus.AUDIO_REGION_CHANGE) {
            final JSONObject obj = new JSONObject()
                    .put("op", 4)
                    .put("d", new JSONObject()
                            .put("guild_id", guildId)
                            .put("channel_id", JSONObject.NULL)
                            .put("self_mute", false)
                            .put("self_deaf", false)
                    );
            core.getClient().sendWS(obj.toString());
        }
        if(keepAliveHandle != null) {
            keepAliveHandle.cancel(false);
            keepAliveHandle = null;
        }
        
        if(udpSocket != null) {
            udpSocket.close();
        }
        if(socket != null && socket.isOpen()) {
            socket.sendClose(1000);
        }
        
        final String disconnectedChannelId;
        final AudioManager manager = core.getAudioManager(guildId);
        
        synchronized(manager.CONNECTION_LOCK) {
            if(audioConnection != null) {
                audioConnection.shutdown();
            }
            if(manager.isConnected()) {
                disconnectedChannelId = manager.getConnectedChannel();
            } else {
                disconnectedChannelId = manager.getQueuedAudioConnectionId();
            }
            
            manager.setAudioConnection(null);
        }
        
        //Verify that it is actually a lost of connection and not due the connected channel being deleted.
        if(closeStatus == ConnectionStatus.ERROR_LOST_CONNECTION) {
            //Get guildId from JDA, don't use [guildId] field to make sure that we don't have
            // a problem of an out of date guildId stored in [guildId] during a possible mWS invalidate.
            
            //This is meant to check if the channel we were just DCd from was still in the cache.
            //If it wasn't then it was deleted, so we wouldn't attempt to reconnect.

//            Guild connGuild = api.getGuildById(guildId.getId());
//            if (connGuild != null)
//            {
//                if (connGuild.getVoiceChannelById(audioConnection.getChannelId().getIdLong()) == null)
//                    closeStatus = ConnectionStatus.DISCONNECTED_CHANNEL_DELETED;
//            }
        }
        
        changeStatus(closeStatus);
        
        //decide if we reconnect.
        if(shouldReconnect
                && closeStatus != ConnectionStatus.NOT_CONNECTED    //indicated that the connection was purposely closed. don't reconnect.
                && closeStatus != ConnectionStatus.DISCONNECTED_CHANNEL_DELETED
                && closeStatus != ConnectionStatus.DISCONNECTED_REMOVED_FROM_GUILD
                && closeStatus != ConnectionStatus.AUDIO_REGION_CHANGE) //Already handled.
        {
            manager.setQueuedAudioConnectionId(disconnectedChannelId);
            core.getConnectionManager().queueAudioConnect(guildId, disconnectedChannelId);
        }
    }
    
    public DatagramSocket getUdpSocket() {
        return udpSocket;
    }
    
    public InetSocketAddress getAddress() {
        return address;
    }
    
    public byte[] getSecretKey() {
        return Arrays.copyOf(secretKey, secretKey.length);
    }
    
    public int getSSRC() {
        return ssrc;
    }
    
    public boolean isConnected() {
        return connected;
    }
    
    public boolean isReady() {
        return ready;
    }
    
    private InetSocketAddress handleUdpDiscovery(final InetSocketAddress address, final int ssrc) {
        //We will now send a packet to discord to punch a port hole in the NAT wall.
        //This is called UDP hole punching.
        try {
            udpSocket = new DatagramSocket();   //Use UDP, not TCP.
            
            //Create a byte array of length 70 containing our ssrc.
            final ByteBuffer buffer = ByteBuffer.allocate(70);    //70 taken from https://github.com/Rapptz/discord.py/blob/async/discord/voice_client.py#L208
            buffer.putInt(ssrc);                            //Put the ssrc that we were given into the packet to send back to discord.
            
            //Construct our packet to be sent loaded with the byte buffer we store the ssrc in.
            final DatagramPacket discoveryPacket = new DatagramPacket(buffer.array(), buffer.array().length, address);
            udpSocket.send(discoveryPacket);
            
            //Discord responds to our packet, returning a packet containing our external ip and the port we connected through.
            final DatagramPacket receivedPacket = new DatagramPacket(new byte[70], 70);   //Give a buffer the same size as the one we sent.
            udpSocket.setSoTimeout(1000);
            udpSocket.receive(receivedPacket);
            
            //The byte array returned by discord containing our external ip and the port that we used
            //to connect to discord with.
            final byte[] received = receivedPacket.getData();
            
            //Example string:"   121.83.253.66                                                   ��"
            //You'll notice that there are 4 leading nulls and a large amount of nulls between the the ip and
            // the last 2 bytes. Not sure why these exist.  The last 2 bytes are the port. More info below.
            String ourIP = new String(receivedPacket.getData());//Puts the entire byte array in. nulls are converted to spaces.
            ourIP = ourIP.substring(4, ourIP.length() - 2); //Removes the port that is stuck on the end of this string. (last 2 bytes are the port)
            ourIP = ourIP.trim();                           //Removes the extra whitespace(nulls) attached to both sides of the IP
            
            //The port exists as the last 2 bytes in the packet data, and is encoded as an UNSIGNED short.
            //Furthermore, it is stored in Little Endian instead of normal Big Endian.
            //We will first need to convert the byte order from Little Endian to Big Endian (reverse the order)
            //Then we will need to deal with the fact that the bytes represent an unsigned short.
            //Java cannot deal with unsigned types, so we will have to promote the short to a higher type.
            //Options:  char or int.  I will be doing int because it is just easier to work with.
            final byte[] portBytes = new byte[2];                 //The port is exactly 2 bytes in size.
            portBytes[0] = received[received.length - 1];   //Get the second byte and store as the first
            portBytes[1] = received[received.length - 2];   //Get the first byte and store as the second.
            //We have now effectively converted from Little Endian -> Big Endian by reversing the order.
            
            //For more information on how this is converting from an unsigned short to an int refer to:
            //http://www.darksleep.com/player/JavaAndUnsignedTypes.html
            final int firstByte = 0x000000FF & (int) portBytes[0];    //Promotes to int and handles the fact that it was unsigned.
            final int secondByte = 0x000000FF & (int) portBytes[1];   //
            
            //Combines the 2 bytes back together.
            final int ourPort = firstByte << 8 | secondByte;
            
            this.address = address;
            
            return new InetSocketAddress(ourIP, ourPort);
        } catch(final IOException e) {
            return null;
        }
    }
    
    private void setupKeepAlive(final int keepAliveInterval) {
        if(keepAliveHandle != null) {
            LOG.fatal("Setting up a KeepAlive runnable while the previous one seems to still be active!!");
        }
        
        final Runnable keepAliveRunnable = () ->
        {
            if(socket != null && socket.isOpen()) {
                sendKeepAlive();
            }
            if(udpSocket != null && !udpSocket.isClosed()) {
                final long seq = 0;
                try {
                    final ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES + 1);
                    buffer.put((byte) 0xC9);
                    buffer.putLong(seq);
                    final DatagramPacket keepAlivePacket = new DatagramPacket(buffer.array(), buffer.array().length, address);
                    udpSocket.send(keepAlivePacket);
                } catch(final NoRouteToHostException e) {
                    LOG.warn("Closing AudioConnection due to inability to ping audio packets.");
                    LOG.warn("Cannot send audio packet because JDA navigate the route to Discord.\n" +
                            "Are you sure you have internet connection? It is likely that you've lost connection.");
                    close(ConnectionStatus.ERROR_LOST_CONNECTION);
                } catch(final IOException e) {
                    LOG.log(e);
                }
            }
        };
        
        try {
            keepAliveHandle = keepAlivePool.scheduleAtFixedRate(keepAliveRunnable, 0, keepAliveInterval, TimeUnit.MILLISECONDS);
        } catch(final RejectedExecutionException ignored) {
        } //ignored because this is probably caused due to a race condition
        // related to the threadpool shutdown.
    }
    
    private void stopKeepAlive() {
        if(keepAliveHandle != null) {
            keepAliveHandle.cancel(true);
        }
        keepAliveHandle = null;
    }
    
    public void changeStatus(final ConnectionStatus newStatus) {
        connectionStatus = newStatus;
        listener.onStatusChange(newStatus);
    }
    
    public ConnectionStatus getConnectionStatus() {
        return connectionStatus;
    }
    
    public void setAutoReconnect(final boolean shouldReconnect) {
        this.shouldReconnect = shouldReconnect;
    }
    
    @Override
    protected void finalize() throws Throwable {
        if(!shutdown) {
            LOG.fatal("Finalization hook of AudioWebSocket was triggered without properly shutting down");
            close(ConnectionStatus.ERROR_LOST_CONNECTION);
        }
    }
    
    @SuppressWarnings("PackageVisibleField")
    public static class KeepAliveThreadFactory implements ThreadFactory {
        final String identifier;
        AtomicInteger threadCount = new AtomicInteger(1);
        
        public KeepAliveThreadFactory() {
            identifier = "JDA Audio-KeepAlive Pool";
        }
        
        @Override
        public Thread newThread(final Runnable r) {
            final Thread t = new Thread(AudioManager.AUDIO_THREADS, r, identifier + " - Thread " + threadCount.getAndIncrement());
            t.setDaemon(true);
            
            return t;
        }
    }
}

