/*
 * Copyright (C) 2012 Julian Knocke
 * 
 * This file is part of jLange.
 * 
 * jLange is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * 
 * jLange is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with jLange. If not, see <http://www.gnu.org/licenses/>.
 */
package org.jlange.proxy.outbound;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.Executors;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jlange.proxy.util.RemoteAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OutboundChannelPool {

    // @formatter:off
    public final static ChannelFutureListener IDLE =
            new ChannelFutureListener() {
                @Override
                public void operationComplete(final ChannelFuture future) {
                   if (!future.isSuccess())
                       return;

                   final OutboundChannelPool pool = OutboundChannelPool.getInstance();
                   pool.log.info("Channel {} - channel is idle", future.getChannel().getId());
                   synchronized (pool.channelIdPool) {
                       pool.channelIdPool.idleChannelId(future.getChannel().getId());
                       pool.channelFuture.put(future.getChannel().getId(), future);
                       pool.channelIdPool.dump();
                   }

               }
           };
    // @formatter:on

    private final static NioClientSocketChannelFactory outboundFactory = new NioClientSocketChannelFactory(Executors.newCachedThreadPool(),
                                                                               Executors.newCachedThreadPool());

    private final static OutboundChannelPool           instance        = new OutboundChannelPool();

    public static OutboundChannelPool getInstance() {
        return instance;
    }

    public static NioClientSocketChannelFactory getNioClientSocketChannelFactory() {
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

    private OutboundChannelPool() {
        channelFuture = new HashMap<Integer, ChannelFuture>();
        channelIdPool = new ChannelIdPool();
    }

    public ChannelFuture getIdleOrNewChannelFuture(final RemoteAddress address, final ChannelPipelineFactory channelPipelineFactory) {
        ChannelFuture future = getIdleChannelFuture(address);
        if (future == null)
            future = getNewChannelFuture(address, channelPipelineFactory);
        return future;
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

        // remove from pool on close
        f.getChannel().getCloseFuture().addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(final ChannelFuture future) {
                final Integer channelId = future.getChannel().getId();
                log.info("Channel {} - closing, remove from pool", channelId);
                synchronized (channelIdPool) {
                    channelIdPool.removeChannelId(channelId);
                    channelFuture.remove(channelId);
                    channelIdPool.dump();
                }
            }
        });

        return f;
    }

    public ChannelFuture getIdleChannelFuture(final RemoteAddress address) {
        final Integer channelId;
        final ChannelFuture future;
        synchronized (channelIdPool) {
            // check if there is an idle channel
            channelId = channelIdPool.getIdleChannelId(address);
            if (channelId == null)
                return null;

            // get and remove channel from idle channels
            future = channelFuture.get(channelId);
            if (future == null)
                return null;

            // set channel as used
            channelIdPool.useChannelId(channelId);
            channelIdPool.dump();
        }

        if (future.getChannel().isConnected()) {
            log.info("Channel {} - reuse existing connection", channelId);
            return future;
        } else
            return null;
    }

    /**
     * A class for keeping track of idle and used channel ids
     */
    private class ChannelIdPool {

        private final Map<RemoteAddress, Queue<Integer>> channels;
        private final Map<Integer, Boolean>              isChannelIdle;

        public ChannelIdPool() {
            channels = new HashMap<RemoteAddress, Queue<Integer>>();
            isChannelIdle = new HashMap<Integer, Boolean>();
        }

        public synchronized void addUsedChannelId(final RemoteAddress address, final Integer channelId) {
            Queue<Integer> channelIds = getChannelIds(address);
            channelIds.add(channelId);
            isChannelIdle.put(channelId, false);
        }

        public synchronized void removeChannelId(final Integer channelId) {
            for (RemoteAddress address : channels.keySet())
                if (channels.get(address).remove(channelId)) {
                    if (channels.get(address).isEmpty())
                        channels.remove(address);
                    isChannelIdle.remove(channelId);
                    return;
                }
        }

        public synchronized Integer getIdleChannelId(final RemoteAddress address) {
            for (Integer channelId : getChannelIds(address))
                if (isChannelIdle.get(channelId))
                    return channelId;
            return null;
        }

        private synchronized Queue<Integer> getChannelIds(RemoteAddress address) {
            Queue<Integer> channelIds = channels.get(address);
            if (channelIds == null) {
                channelIds = new LinkedList<Integer>();
                channels.put(address, channelIds);
            }
            return channelIds;
        }

        /**
         * Moves a channel id from idle to used queue
         */
        public synchronized void useChannelId(final Integer channelId) {
            isChannelIdle.put(channelId, false);
        }

        /**
         * Moves a channel id from used to idle queue
         */
        public synchronized void idleChannelId(final Integer channelId) {
            isChannelIdle.put(channelId, true);
        }

        public void dump() {
            if (!log.isDebugEnabled())
                return;

            StringBuilder sb = new StringBuilder();
            for (RemoteAddress address : channels.keySet()) {
                sb.append("\n");
                sb.append(address.toString());
                for (Integer i : channels.get(address)) {
                    sb.append("\n\t - ");
                    sb.append(i);
                    sb.append(" -> ");
                    if (isChannelIdle.get(i))
                        sb.append("idle");
                    else
                        sb.append("used");

                }
            }

            log.debug(sb.toString());
        }
    }
}
