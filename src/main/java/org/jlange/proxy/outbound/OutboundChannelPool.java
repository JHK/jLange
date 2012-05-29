package org.jlange.proxy.outbound;

import java.net.InetSocketAddress;
import java.net.URL;
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
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.channel.socket.SocketChannel;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jlange.proxy.Tools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OutboundChannelPool {

    private static final OutboundSocketChannelFactory outboundFactory = new OutboundSocketChannelFactory(Executors.newCachedThreadPool(),
                                                                              Executors.newCachedThreadPool());

    public static OutboundSocketChannelFactory getOutboundFactory() {
        return outboundFactory;
    }

    private final Logger                            log = LoggerFactory.getLogger(getClass());

    // keeps track of outbound channels which are hold but currently not used
    private final Map<String, Queue<ChannelFuture>> idleOutboundChannelFutureMap;

    // keeps track of outbound channels which are currently in use
    private final Map<String, Queue<ChannelFuture>> usedOutboundChannelFutureMap;

    public OutboundChannelPool() {
        idleOutboundChannelFutureMap = new HashMap<String, Queue<ChannelFuture>>();
        usedOutboundChannelFutureMap = new HashMap<String, Queue<ChannelFuture>>();
    }

    public ChannelFuture getChannelFuture(final URL url, final ChannelPipelineFactoryFactory channelPipelineFactoryFactory) {
        final String channelKey = getChannelKey(url);

        final Queue<ChannelFuture> idleChannelQueue = getChannelQueue(idleOutboundChannelFutureMap, channelKey);
        final Queue<ChannelFuture> usedChannelQueue = getChannelQueue(usedOutboundChannelFutureMap, channelKey);
        final ChannelFuture f;

        if (!idleChannelQueue.isEmpty() && !idleChannelQueue.peek().getChannel().isConnected()) {
            // this may happen as a race condition if a channel is inserted into idle queue and going to get closed
            final ChannelFuture future = idleChannelQueue.remove();
            log.debug("Unconnected channel in idle queue {}, choosing another one", future.getChannel().getId());
            f = getChannelFuture(url, channelPipelineFactoryFactory);
        } else if (idleChannelQueue.isEmpty()) {
            log.info("Establishing new connection to {}", url.getHost());

            // setup client
            final ClientBootstrap outboundClient = new ClientBootstrap(outboundFactory);
            outboundClient.setPipelineFactory(channelPipelineFactoryFactory.getChannelPipelineFactory());
            outboundClient.setOption("child.tcpNoDelay", true);
            outboundClient.setOption("child.keepAlive", true);

            // connect to remote host
            f = outboundClient.connect(new InetSocketAddress(url.getHost(), url.getPort() == -1 ? 80 : url.getPort()));
            log.info("Outboundchannel {} - created", f.getChannel().getId());

            // cleanup outboundChannels on close
            f.getChannel().getCloseFuture().addListener(new ChannelFutureListener() {
                public void operationComplete(final ChannelFuture future) throws Exception {
                    log.debug("Outboundchannel {} - connection closed, remove from pool - {}", future.getChannel().getId(), channelKey);
                    idleOutboundChannelFutureMap.get(channelKey).clear();
                    usedOutboundChannelFutureMap.get(channelKey).clear();
                    if (log.isDebugEnabled())
                        dumpPool();
                }
            });
        } else {
            f = idleChannelQueue.remove();
            log.info("Outboundchannel {} - reused connection to {} ", f.getChannel().getId(), url.getHost());
        }

        // add this channel future to used channels
        if (!usedChannelQueue.contains(f))
            usedChannelQueue.add(f);

        if (log.isDebugEnabled())
            dumpPool();

        return f;
    }

    public ChannelFutureListener getIdleConnectionListener(final HttpRequest request) {
        return new ChannelFutureListener() {
            public void operationComplete(final ChannelFuture future) {
                String channelKey = getChannelKey(request);
                log.debug("Outboundchannel {} - channel is idle - {}", future.getChannel().getId(), channelKey);
                idleOutboundChannelFutureMap.get(channelKey).add(future);
                for (ChannelFuture f : usedOutboundChannelFutureMap.get(channelKey))
                    if (f.getChannel().getId() == future.getChannel().getId())
                        usedOutboundChannelFutureMap.get(channelKey).remove(f);

                if (log.isDebugEnabled())
                    dumpPool();
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

        for (String channelKey : idleOutboundChannelFutureMap.keySet())
            for (ChannelFuture f : idleOutboundChannelFutureMap.get(channelKey))
                channels.add(f.getChannel());
        for (String channelKey : usedOutboundChannelFutureMap.keySet())
            for (ChannelFuture f : usedOutboundChannelFutureMap.get(channelKey))
                channels.add(f.getChannel());

        return channels;
    }

    /**
     * Checks if no connection is left for a given request
     * 
     * @param request the target for the channels
     * @return true if there is no channel in use or idle
     */
    public boolean isEmpty(final HttpRequest request) {
        final String channelKey = getChannelKey(request);
        Queue<ChannelFuture> idleChannelQueue = getChannelQueue(idleOutboundChannelFutureMap, channelKey);
        Queue<ChannelFuture> usedChannelQueue = getChannelQueue(usedOutboundChannelFutureMap, channelKey);
        return usedChannelQueue.isEmpty() && idleChannelQueue.isEmpty();
    }

    private String getChannelKey(final HttpRequest request) {
        return getChannelKey(Tools.getURL(request));
    }

    private String getChannelKey(final URL url) {
        final String host = url.getHost();
        final Integer port = url.getPort() == -1 ? 80 : url.getPort();
        return new StringBuilder().append(host).append(":").append(port).toString();
    }

    private Queue<ChannelFuture> getChannelQueue(final Map<String, Queue<ChannelFuture>> channelFutureMap, final String channelKey) {
        Queue<ChannelFuture> channelQueue = channelFutureMap.get(channelKey);

        if (channelQueue == null) {
            channelQueue = new LinkedList<ChannelFuture>();
            channelFutureMap.put(channelKey, channelQueue);
        }

        return channelQueue;
    }

    private void dumpPool() {
        StringBuilder dump = new StringBuilder();
        dump.append("\nIn use channels:");
        for (String s : usedOutboundChannelFutureMap.keySet()) {
            for (ChannelFuture f : usedOutboundChannelFutureMap.get(s)) {
                dump.append("\n - ");
                dump.append(s);
                dump.append(" -> ");
                dump.append(f.getChannel().getId());
                dump.append(" connected ");
                dump.append(f.getChannel().isConnected());
            }
        }
        dump.append("\nIdle channels:");
        for (String s : idleOutboundChannelFutureMap.keySet()) {
            for (ChannelFuture f : idleOutboundChannelFutureMap.get(s)) {
                dump.append("\n - ");
                dump.append(s);
                dump.append(" -> ");
                dump.append(f.getChannel().getId());
                dump.append(" connected ");
                dump.append(f.getChannel().isConnected());
            }
        }
        log.debug(dump.toString());
    }

    /***
     * A class extending {@link NioServerSocketChannelFactory} to keep track of opened client channels.
     * 
     * @author Julian Knocke
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
                public void operationComplete(ChannelFuture future) throws Exception {
                    allChannels.remove(channel);
                }
            });

            return channel;
        }

        public ChannelGroup getChannels() {
            return allChannels;
        }

    }
}
