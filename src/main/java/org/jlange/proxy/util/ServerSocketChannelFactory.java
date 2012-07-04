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
package org.jlange.proxy.util;

import java.util.concurrent.ExecutorService;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.channel.socket.ServerSocketChannel;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;

/***
 * A class extending {@link NioServerSocketChannelFactory} to keep track of opened server channels.
 * 
 * @author Julian Knocke
 */
public class ServerSocketChannelFactory extends NioServerSocketChannelFactory implements ChannelFactory {

    private final ChannelGroup allChannels;

    public ServerSocketChannelFactory(final ExecutorService newCachedThreadPool, final ExecutorService newCachedThreadPool2) {
        super(newCachedThreadPool, newCachedThreadPool2);

        allChannels = new DefaultChannelGroup("server");
    }

    @Override
    public ServerSocketChannel newChannel(final ChannelPipeline pipeline) {
        final ServerSocketChannel channel = super.newChannel(pipeline);

        allChannels.add(channel);
        channel.getCloseFuture().addListener(new ChannelFutureListener() {
            public void operationComplete(ChannelFuture future) throws Exception {
                allChannels.remove(channel);
            }
        });

        return channel;
    }

    public void addChannel(final Channel e) {
        allChannels.add(e);
    }

    public ChannelGroup getChannels() {
        return allChannels;
    }

}
