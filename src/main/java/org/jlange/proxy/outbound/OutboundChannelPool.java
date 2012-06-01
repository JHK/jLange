package org.jlange.proxy.outbound;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.channel.socket.SocketChannel;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jlange.proxy.util.RemoteAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OutboundChannelPool {

    private static OutboundChannelPool instance;

    public static OutboundChannelPool getInstance() {
        if (instance == null)
            instance = new OutboundChannelPool();
        return instance;
    }

    private final Logger                       log = LoggerFactory.getLogger(getClass());
    private final OutboundSocketChannelFactory outboundFactory;

    /**
     * keeps track of used/idle channel ids
     */
    private final ChannelIdPool                channelIdPool;

    /**
     * holds the most recent future to a channel id
     */
    private final Map<Integer, ChannelFuture>  channelFuture;

    private OutboundChannelPool() {
        channelFuture = new HashMap<Integer, ChannelFuture>();
        channelIdPool = new ChannelIdPool();
        outboundFactory = new OutboundSocketChannelFactory(Executors.newCachedThreadPool(), Executors.newCachedThreadPool());
    }

    public OutboundSocketChannelFactory getOutboundFactory() {
        return outboundFactory;
    }

    public ChannelFuture getNewChannelFuture(final RemoteAddress address, final ChannelPipelineFactory channelPipelineFactory) {
        // setup client
        final ClientBootstrap outboundClient = new ClientBootstrap(outboundFactory);
        outboundClient.setPipelineFactory(channelPipelineFactory);
        outboundClient.setOption("child.tcpNoDelay", true);
        outboundClient.setOption("child.keepAlive", true);

        // connect to remote host
        final ChannelFuture f = outboundClient.connect(new InetSocketAddress(address.getHost(), address.getPort()));
        log.info("Channel {} - created", f.getChannel().getId());

        channelIdPool.addUsedChannelId(address, f.getChannel().getId());
        channelFuture.put(f.getChannel().getId(), f);
        channelIdPool.dump();

        return f;
    }

    public ChannelFuture getIdleChannelFuture(final RemoteAddress address) {
        // check if there is an idle channel
        final Integer channelId = channelIdPool.getIdleChannelId(address);

        // get and remove channel from idle channels
        final ChannelFuture future = channelFuture.get(channelId);

        // set channel as used
        channelIdPool.useChannelId(address, channelId);
        channelIdPool.dump();

        if (future != null && future.getChannel().isConnected()) {
            log.info("Channel {} - reuse existing connection", channelId);
            return future;
        } else
            return null;
    }

    public void setChannelIdle(final RemoteAddress address, final ChannelFuture future) {
        log.info("Channel {} - channel is idle", future.getChannel().getId());
        channelIdPool.idleChannelId(address, future.getChannel().getId());
        channelFuture.put(future.getChannel().getId(), future);
        channelIdPool.dump();
    }

    public void closeChannel(final RemoteAddress address, final Integer channelId) {
        log.info("Channel {} - closed, remove from pool", channelId);
        channelIdPool.removeChannelId(address, channelId);
        channelFuture.remove(channelId);
        channelIdPool.dump();
    }

    /***
     * A class extending {@link NioServerSocketChannelFactory} to keep track of opened client channels.
     */
    public class OutboundSocketChannelFactory extends NioClientSocketChannelFactory implements ClientSocketChannelFactory {

        private final ChannelGroup allChannels;

        public OutboundSocketChannelFactory(final ExecutorService newCachedThreadPool, final ExecutorService newCachedThreadPool2) {
            super(newCachedThreadPool, newCachedThreadPool2);

            allChannels = new DefaultChannelGroup("client");
        }

        @Override
        public SocketChannel newChannel(ChannelPipeline pipeline) {
            final SocketChannel channel = super.newChannel(pipeline);

            allChannels.add(channel);
            channel.getCloseFuture().addListener(new ChannelFutureListener() {
                public void operationComplete(ChannelFuture future) {
                    allChannels.remove(channel);
                }
            });

            return channel;
        }

        public ChannelGroup getChannels() {
            return allChannels;
        }
    }

    /**
     * A class for keeping track of idle and used channel ids
     */
    private class ChannelIdPool {

        private final Map<RemoteAddress, Queue<Integer>> idleChannels;
        private final Map<RemoteAddress, Queue<Integer>> usedChannels;

        public ChannelIdPool() {
            idleChannels = new HashMap<RemoteAddress, Queue<Integer>>();
            usedChannels = new HashMap<RemoteAddress, Queue<Integer>>();
        }

        public void addUsedChannelId(final RemoteAddress address, final Integer channelId) {
            Queue<Integer> usedChannelIds = getUsedChannelIds(address);
            usedChannelIds.add(channelId);
        }

        public void removeChannelId(final RemoteAddress address, final Integer channelId) {
            getUsedChannelIds(address).remove(channelId);
            getIdleChannelIds(address).remove(channelId);
        }

        public Integer getIdleChannelId(final RemoteAddress address) {
            return getIdleChannelIds(address).peek();
        }

        /**
         * Moves a channel id from idle to used queue
         */
        public void useChannelId(final RemoteAddress address, final Integer channelId) {
            Queue<Integer> idleChannelIds = getIdleChannelIds(address);
            if (idleChannelIds.remove(channelId)) {
                Queue<Integer> usedChannelIds = getUsedChannelIds(address);
                usedChannelIds.add(channelId);
            }
        }

        /**
         * Moves a channel id from used to idle queue
         */
        public void idleChannelId(final RemoteAddress address, final Integer channelId) {
            Queue<Integer> usedChannelIds = getUsedChannelIds(address);
            if (usedChannelIds.remove(channelId)) {
                Queue<Integer> idleChannelIds = getIdleChannelIds(address);
                idleChannelIds.add(channelId);
            }
        }

        private Queue<Integer> getUsedChannelIds(final RemoteAddress address) {
            Queue<Integer> usedChannelIds = usedChannels.get(address);

            if (usedChannelIds == null) {
                usedChannelIds = new LinkedList<Integer>();
                usedChannels.put(address, usedChannelIds);
            }

            return usedChannelIds;
        }

        private Queue<Integer> getIdleChannelIds(final RemoteAddress address) {
            Queue<Integer> idleChannelIds = idleChannels.get(address);

            if (idleChannelIds == null) {
                idleChannelIds = new LinkedList<Integer>();
                idleChannels.put(address, idleChannelIds);
            }

            return idleChannelIds;
        }

        public void dump() {
            if (!log.isDebugEnabled())
                return;

            StringBuilder sb = new StringBuilder();
            sb.append("\nidle:");
            for (RemoteAddress address : idleChannels.keySet()) {
                sb.append("\n");
                sb.append(address.toString());
                for (Integer i : getIdleChannelIds(address)) {
                    sb.append("\n\t -> ");
                    sb.append(i);
                }
            }

            sb.append("\nused:");
            for (RemoteAddress address : usedChannels.keySet()) {
                sb.append("\n");
                sb.append(address.toString());
                for (Integer i : getUsedChannelIds(address)) {
                    sb.append("\n\t -> ");
                    sb.append(i);
                }
            }

            log.debug(sb.toString());
        }
    }
}
