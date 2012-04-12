package org.jlange.proxy.http;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.ChannelGroupFuture;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Server {

    public static void main(String[] args) throws IOException {
        Server proxy = new Server(8080);
        proxy.start();
        System.in.read();
        proxy.stop();
    }

    // to collect all channels for shut down
    private final ChannelGroup               allChannels;
    private final ClientSocketChannelFactory clientFactory;
    private final ChannelFactory             factory;
    private final Logger                     log = LoggerFactory.getLogger(Server.class.getName());

    private final int                        port;

    public Server(int port) {
        this.port = port;
        allChannels = new DefaultChannelGroup("server");
        factory = new NioServerSocketChannelFactory(Executors.newCachedThreadPool(), Executors.newCachedThreadPool());
        clientFactory = new NioClientSocketChannelFactory(Executors.newCachedThreadPool(), Executors.newCachedThreadPool());
    }

    protected ServerBootstrap initServer() {
        ServerBootstrap bootstrap = new ServerBootstrap(factory);

        bootstrap.setPipelineFactory(new ChannelPipelineFactory(clientFactory));
        bootstrap.setOption("child.tcpNoDelay", true);
        bootstrap.setOption("child.keepAlive", true);

        return bootstrap;
    }

    public void start() {
        ServerBootstrap bootstrap = initServer();
        Channel channel = bootstrap.bind(new InetSocketAddress(port));
        allChannels.add(channel);
        log.info("started on port " + port);
    }

    public void stop() {
        ChannelGroupFuture future = allChannels.close();
        future.awaitUninterruptibly();
        clientFactory.releaseExternalResources();
        factory.releaseExternalResources();
        log.info("shutdown complete");
    }
}
