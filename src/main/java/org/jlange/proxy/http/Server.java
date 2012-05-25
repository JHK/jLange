package org.jlange.proxy.http;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Server {

    public static void main(String[] args) throws IOException {
        Server proxy = new Server(8080);
        proxy.start();
        System.in.read();
        proxy.stop();
    }

    private final InboundSocketChannelFactory  inboundFactory;
    private final OutboundSocketChannelFactory outboundFactory;
    private final Logger                       log = LoggerFactory.getLogger(getClass());

    private final int                          port;

    public Server(final int port) {
        this.port = port;
        inboundFactory = new InboundSocketChannelFactory(Executors.newCachedThreadPool(), Executors.newCachedThreadPool());
        outboundFactory = new OutboundSocketChannelFactory(Executors.newCachedThreadPool(), Executors.newCachedThreadPool());
    }

    public void start() {
        final ServerBootstrap inbound = new ServerBootstrap(inboundFactory);
        inbound.setPipelineFactory(new InboundPipelineFactory(outboundFactory));
        inbound.setOption("child.tcpNoDelay", true);
        inbound.setOption("child.keepAlive", true);

        Channel channel = inbound.bind(new InetSocketAddress(port));
        inboundFactory.addChannel(channel);

        log.info("started on port {}", port);
    }

    public void stop() {
        log.info("shutdown requested...");
        inboundFactory.getChannels().close().awaitUninterruptibly();
        outboundFactory.getChannels().close().awaitUninterruptibly();
        inboundFactory.releaseExternalResources();
        outboundFactory.releaseExternalResources();
        log.info("shutdown complete");
    }
}
