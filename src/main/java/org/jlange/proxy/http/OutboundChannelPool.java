package org.jlange.proxy.http;

import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.Executors;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OutboundChannelPool {

    private static final OutboundSocketChannelFactory outboundFactory = new OutboundSocketChannelFactory(Executors.newCachedThreadPool(),
                                                                              Executors.newCachedThreadPool());

    protected static OutboundSocketChannelFactory getOutboundFactory() {
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

    public ChannelFuture getChannelFuture(final Channel inboundChannel, final HttpRequest request) {
        final URL url = getURL(request);
        final Integer port = url.getPort() == -1 ? 80 : url.getPort();
        final String channelKey = getChannelKey(url);

        final Queue<ChannelFuture> idleChannelQueue = getChannelQueue(idleOutboundChannelFutureMap, channelKey);
        final Queue<ChannelFuture> usedChannelQueue = getChannelQueue(usedOutboundChannelFutureMap, channelKey);
        final ChannelFuture f;

        if (!idleChannelQueue.isEmpty() && !idleChannelQueue.peek().getChannel().isConnected()) {
            // this may happen as a race condition if a channel is inserted into idle queue and going to get closed
            final ChannelFuture future = idleChannelQueue.remove();
            log.debug("Inboundchannel {} - unconnected channel in idle queue {}, choosing another one", inboundChannel.getId(), future
                    .getChannel().getId());
            f = getChannelFuture(inboundChannel, request);
        } else if (idleChannelQueue.isEmpty()) {
            log.info("Inboundchannel {} - establishing new connection to {}", inboundChannel.getId(), channelKey);

            // setup client
            final ClientBootstrap outboundClient = new ClientBootstrap(outboundFactory);
            outboundClient.setPipelineFactory(new OutboundPipelineFactory(inboundChannel, request, this));
            outboundClient.setOption("child.tcpNoDelay", true);
            outboundClient.setOption("child.keepAlive", true);

            // connect to remote host
            f = outboundClient.connect(new InetSocketAddress(url.getHost(), port));
            log.info("Outboundchannel {} <-> Inboundchannel {}", f.getChannel().getId(), inboundChannel.getId());

            // cleanup outboundChannels on close
            f.getChannel().getCloseFuture().addListener(new ChannelFutureListener() {
                public void operationComplete(final ChannelFuture future) throws Exception {
                    log.debug("Outboundchannel {} - connection closed, remove from pool - {}", future.getChannel().getId(), channelKey);
                    idleOutboundChannelFutureMap.get(channelKey).clear();
                    usedOutboundChannelFutureMap.get(channelKey).clear();
                    if (log.isDebugEnabled())
                        dumpPool(inboundChannel);
                }
            });
        } else {
            f = idleChannelQueue.remove();
            log.info("Outboundchannel {} - reused connection for channel {} - " + channelKey, f.getChannel().getId(),
                    inboundChannel.getId());
        }

        // add this channel future to used channels
        if (!usedChannelQueue.contains(f))
            usedChannelQueue.add(f);

        if (log.isDebugEnabled())
            dumpPool(inboundChannel);

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
                    dumpPool(null);
            }
        };
    }

    /**
     * Gets all outbound channels, idle and in use, matching to a given request
     * 
     * @param request the target for the channels
     * @return a {@link ChannelGroup} of all channels
     */
    public synchronized ChannelGroup getChannels(final HttpRequest request) {
        final ChannelGroup channels = new DefaultChannelGroup();

        final String channelKey = getChannelKey(request);
        for (ChannelFuture f : getChannelQueue(idleOutboundChannelFutureMap, channelKey))
            channels.add(f.getChannel());
        for (ChannelFuture f : getChannelQueue(usedOutboundChannelFutureMap, channelKey))
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

    private URL getURL(final HttpRequest request) {
        URL url = null;

        try {
            url = new URL(request.getUri());
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }

        return url;
    }

    private String getChannelKey(final HttpRequest request) {
        return getChannelKey(getURL(request));
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

    private void dumpPool(final Channel inboundChannel) {
        StringBuilder dump = new StringBuilder();
        if (inboundChannel != null) {
            dump.append("Inboundchannel ");
            dump.append(inboundChannel.getId());
        }
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

}
