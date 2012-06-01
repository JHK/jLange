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

    private static final OutboundSocketChannelFactory outboundFactory = new OutboundSocketChannelFactory(Executors.newCachedThreadPool(),
                                                                              Executors.newCachedThreadPool());

    public static OutboundSocketChannelFactory getOutboundFactory() {
        return outboundFactory;
    }

    private final Logger                      log = LoggerFactory.getLogger(getClass());

    /**
     * keeps track of used/idle channel ids
     */
    private final ChannelIdPool               channelIdPool;

    /**
     * holds the most recent future to a channel id
     */
    private final Map<Integer, ChannelFuture> channelFuture;

    public OutboundChannelPool() {
        channelFuture = new HashMap<Integer, ChannelFuture>();
        channelIdPool = new ChannelIdPool();
    }

    public ChannelFuture getNewChannelFuture(final RemoteAddress address, final ChannelPipelineFactory channelPipelineFactory) {
        // setup client
        final ClientBootstrap outboundClient = new ClientBootstrap(outboundFactory);
        outboundClient.setPipelineFactory(channelPipelineFactory);
        outboundClient.setOption("child.tcpNoDelay", true);
        outboundClient.setOption("child.keepAlive", true);

        // connect to remote host
        final ChannelFuture f = outboundClient.connect(new InetSocketAddress(address.getHost(), address.getPort()));
        log.info("Outboundchannel {} - created", f.getChannel().getId());

        // cleanup channels on close
        f.getChannel().getCloseFuture().addListener(new ChannelFutureListener() {
            public void operationComplete(ChannelFuture future) {
                channelIdPool.removeChannelId(address, future.getChannel().getId());
            }
        });

        channelIdPool.addUsedChannelId(address, f.getChannel().getId());
        channelFuture.put(f.getChannel().getId(), f);

        return f;
    }

    public ChannelFuture getIdleChannelFuture(final RemoteAddress address) {
        // check if there is an idle channel
        final Integer channelId = channelIdPool.getIdleChannelId(address);

        // get and remove channel from idle channels
        final ChannelFuture fut = channelFuture.get(channelId);

        // set channel as used
        channelIdPool.useChannelId(address, channelId);

        if (fut != null)
            log.info("Outboundchannel {} - reuse existing connection", channelId);

        return fut;
    }

    public ChannelFutureListener getConnectionIdleFutureListener(final RemoteAddress address) {
        return new ChannelFutureListener() {
            public void operationComplete(final ChannelFuture future) {
                channelIdPool.idleChannelId(address, future.getChannel().getId());
                channelFuture.put(future.getChannel().getId(), future);
            }
        };
    }

    /**
     * Gets all outbound channels, idle and in use
     * 
     * @return a {@link ChannelGroup} of all channels
     */
    public ChannelGroup getChannels() {
        final ChannelGroup channels = new DefaultChannelGroup();

        for (ChannelFuture future : channelFuture.values())
            channels.add(future.getChannel());

        return channels;
    }

    /***
     * A class extending {@link NioServerSocketChannelFactory} to keep track of opened client channels.
     */
    public static class OutboundSocketChannelFactory extends NioClientSocketChannelFactory implements ClientSocketChannelFactory {

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

        public void addUsedChannelId(final RemoteAddress channelKey, final Integer channelId) {
            Queue<Integer> usedChannelIds = getUsedChannelIds(channelKey);
            usedChannelIds.add(channelId);
        }

        public void removeChannelId(final RemoteAddress channelKey, final Integer channelId) {
            getUsedChannelIds(channelKey).remove(channelId);
            getIdleChannelIds(channelKey).remove(channelId);
        }

        public Integer getIdleChannelId(final RemoteAddress channelKey) {
            return getIdleChannelIds(channelKey).peek();
        }

        /**
         * Moves a channel id from idle to used queue
         */
        public void useChannelId(final RemoteAddress channelKey, final Integer channelId) {
            Queue<Integer> idleChannelIds = getIdleChannelIds(channelKey);
            idleChannelIds.remove(channelId);

            Queue<Integer> usedChannelIds = getUsedChannelIds(channelKey);
            usedChannelIds.add(channelId);
        }

        /**
         * Moves a channel id from used to idle queue
         */
        public void idleChannelId(final RemoteAddress channelKey, final Integer channelId) {
            Queue<Integer> usedChannelIds = getUsedChannelIds(channelKey);
            usedChannelIds.remove(channelId);

            Queue<Integer> idleChannelIds = getIdleChannelIds(channelKey);
            idleChannelIds.add(channelId);
        }

        private Queue<Integer> getUsedChannelIds(final RemoteAddress channelKey) {
            Queue<Integer> usedChannelIds = usedChannels.get(channelKey);

            if (usedChannelIds == null) {
                usedChannelIds = new LinkedList<Integer>();
                usedChannels.put(channelKey, usedChannelIds);
            }

            return usedChannelIds;
        }

        private Queue<Integer> getIdleChannelIds(final RemoteAddress channelKey) {
            Queue<Integer> idleChannelIds = idleChannels.get(channelKey);

            if (idleChannelIds == null) {
                idleChannelIds = new LinkedList<Integer>();
                idleChannels.put(channelKey, idleChannelIds);
            }

            return idleChannelIds;
        }
    }
}
