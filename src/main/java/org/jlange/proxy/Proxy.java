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
package org.jlange.proxy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelPipeline;
import org.jlange.proxy.inbound.HttpPipelineFactory;
import org.jlange.proxy.outbound.OutboundChannelPool;
import org.jlange.proxy.util.Config;
import org.jlange.proxy.util.IdleShutdownHandler;
import org.jlange.proxy.util.ServerSocketChannelFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Proxy {

    public static void main(String[] args) throws IOException {
        Proxy proxy = new Proxy(Config.HTTP_PORT);
        Spdy spdy = new Spdy(Config.SPDY_PORT);
        proxy.start();
        spdy.start();
        System.in.read();
        proxy.stop();
        spdy.stop();
    }

    private final ServerSocketChannelFactory inboundFactory;
    private final Logger                     log = LoggerFactory.getLogger(getClass());

    private final int                        port;

    public Proxy(final int port) {
        this.port = port;
        inboundFactory = new ServerSocketChannelFactory(Executors.newCachedThreadPool(), Executors.newCachedThreadPool());
    }

    public void start() {
        final ServerBootstrap inbound = new ServerBootstrap(inboundFactory);
        inbound.setPipelineFactory(new HttpPipelineFactory());
        inbound.setOption("child.tcpNoDelay", true);
        inbound.setOption("child.keepAlive", true);

        Channel channel = inbound.bind(new InetSocketAddress(port));
        inboundFactory.addChannel(channel);

        log.info("started on port {}", port);
    }

    public void stop() {
        log.info("shutdown requested...");

        inboundFactory.getChannels().close().awaitUninterruptibly();
        inboundFactory.releaseExternalResources();

        OutboundChannelPool.getNioClientSocketChannelFactory().releaseExternalResources();

        IdleShutdownHandler.timer.stop();

        log.info("shutdown complete");
    }

}
