package org.jlange.proxy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.channel.socket.ServerSocketChannel;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jlange.proxy.inbound.HttpPipelineFactory;
import org.jlange.proxy.inbound.IdleShutdownHandler;
import org.jlange.proxy.outbound.OutboundChannelPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Server {

    public static void main(String[] args) throws IOException {
        Server proxy = new Server(8080);
        proxy.start();
        System.in.read();
        proxy.stop();
    }

    private final ServerSocketChannelFactory inboundFactory;
    private final Logger                     log = LoggerFactory.getLogger(getClass());

    private final int                        port;

    public Server(final int port) {
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

    /***
     * A class extending {@link NioServerSocketChannelFactory} to keep track of opened server channels.
     * 
     * @author Julian Knocke
     */
    private class ServerSocketChannelFactory extends NioServerSocketChannelFactory implements ChannelFactory {

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
}
