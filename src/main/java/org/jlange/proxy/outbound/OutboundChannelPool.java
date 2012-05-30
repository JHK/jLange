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
import org.jboss.netty.channel.Channel;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OutboundChannelPool {

    private static final OutboundSocketChannelFactory outboundFactory = new OutboundSocketChannelFactory(Executors.newCachedThreadPool(),
                                                                              Executors.newCachedThreadPool());

    public static OutboundSocketChannelFactory getOutboundFactory() {
        return outboundFactory;
    }

    private static String getChannelKey(final URL url) {
        final String host = url.getHost();
        final Integer port = url.getPort() == -1 ? 80 : url.getPort();
        return new StringBuilder().append(host).append(":").append(port).toString();
    }

    private final Logger                      log = LoggerFactory.getLogger(getClass());

    /**
     * holds a queue of channels for a host
     */
    private final Map<String, Queue<Channel>> channels;

    /**
     * holds the most recent future to a given channel
     */
    private final Map<Channel, ChannelFuture> channelFuture;

    /**
     * identifies a channel as idle
     */
    private final Map<Channel, Boolean>       channelIdle;

    public OutboundChannelPool() {
        channels = new HashMap<String, Queue<Channel>>();
        channelFuture = new HashMap<Channel, ChannelFuture>();
        channelIdle = new HashMap<Channel, Boolean>();
    }

    public ChannelFuture getNewChannelFuture(final URL url, final ChannelPipelineFactory channelPipelineFactory) {
        final String channelKey = getChannelKey(url);

        // setup client
        final ClientBootstrap outboundClient = new ClientBootstrap(outboundFactory);
        outboundClient.setPipelineFactory(channelPipelineFactory);
        outboundClient.setOption("child.tcpNoDelay", true);
        outboundClient.setOption("child.keepAlive", true);

        // connect to remote host
        final ChannelFuture f = outboundClient.connect(new InetSocketAddress(url.getHost(), url.getPort() == -1 ? 80 : url.getPort()));
        log.info("Outboundchannel {} - created", f.getChannel().getId());

        // cleanup channels on close
        f.getChannel().getCloseFuture().addListener(new ChannelFutureListener() {
            public void operationComplete(ChannelFuture future) {
                removeChannel(channelKey, future.getChannel());
            }
        });

        addChannelFuture(channelKey, f);

        return f;
    }

    public ChannelFuture getChannelFuture(final URL url, final ChannelPipelineFactoryFactory channelPipelineFactoryFactory) {
        final ChannelFuture f;

        final String channelKey = getChannelKey(url);

        if (channels.get(channelKey) == null || channels.get(channelKey).isEmpty()) {
            f = getNewChannelFuture(url, channelPipelineFactoryFactory.getChannelPipelineFactory());
        } else {
            ChannelFuture tmpF = null;
            for (Channel channel : channels.get(channelKey)) {
                if (channel.isConnected() && channelIdle.get(channel)) {
                    log.info("Outboundchannel {} - reused connection to {} ", channel.getId(), url.getHost());
                    tmpF = channelFuture.get(channel);
                    break;
                }
            }

            if (tmpF == null)
                f = getNewChannelFuture(url, channelPipelineFactoryFactory.getChannelPipelineFactory());
            else
                f = tmpF;
        }

        useChannel(f.getChannel());

        return f;
    }

    private void addChannelFuture(String channelKey, ChannelFuture future) {
        Queue<Channel> channels = new LinkedList<Channel>();
        this.channels.put(channelKey, channels);

        Channel channel = future.getChannel();

        channels.add(channel);
        channelFuture.put(channel, future);
        channelIdle.put(channel, false);
        log.debug("Outboundchannel {} - added to queue", channel.getId());
    }

    private void idleChannel(ChannelFuture future) {
        channelIdle.put(future.getChannel(), true);
        channelFuture.put(future.getChannel(), future);
        log.debug("Outboundchannel {} - set to idle", future.getChannel().getId());
    }

    private void useChannel(Channel channel) {
        channelIdle.put(channel, false);
        log.debug("Outboundchannel {} - set to in use", channel.getId());
    }

    private void removeChannel(String channelKey, Channel channel) {
        log.debug("Outboundchannel {} - removed from queue", channel.getId());
        channels.get(channelKey).remove(channel);
        channelFuture.remove(channel);
        channelIdle.remove(channel);
    }

    public ChannelFutureListener getConnectionIdleFutureListener() {
        return new ChannelFutureListener() {
            public void operationComplete(final ChannelFuture future) {
                idleChannel(future);
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

        for (Channel channel : channelIdle.keySet())
            channels.add(channel);

        return channels;
    }

    @SuppressWarnings("unused")
    private void dump() {
        StringBuilder dump = new StringBuilder();
        for (String channelKey : channels.keySet())
            for (Channel channel : channels.get(channelKey)) {
                dump.append("\n - ");
                dump.append(channelKey);
                dump.append("\n\t - Channel ");
                dump.append(channel.getId());
                dump.append(" - ");
                if (channelIdle.get(channel)) {
                    dump.append("idle");
                } else {
                    dump.append("in use");
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
}
