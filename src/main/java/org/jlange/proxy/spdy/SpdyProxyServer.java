package org.jlange.proxy.spdy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.Channel;
import org.jlange.proxy.http.InboundSocketChannelFactory;
import org.jlange.proxy.http.OutboundChannelPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SpdyProxyServer {

    public static void main(String[] args) throws IOException {
        SpdyProxyServer proxy = new SpdyProxyServer(8443);
        proxy.start();
        System.in.read();
        proxy.stop();
    }

    private final InboundSocketChannelFactory inboundFactory;
    private final Logger                      log = LoggerFactory.getLogger(getClass());

    private final int                         port;

    public SpdyProxyServer(final int port) {
        this.port = port;
        inboundFactory = new InboundSocketChannelFactory(Executors.newCachedThreadPool(), Executors.newCachedThreadPool());
    }

    public void start() {
        final ServerBootstrap inbound = new ServerBootstrap(inboundFactory);
        inbound.setPipelineFactory(new InboundPipelineFactory());
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

        OutboundChannelPool.getOutboundFactory().getChannels().close().awaitUninterruptibly();
        OutboundChannelPool.getOutboundFactory().releaseExternalResources();

        log.info("shutdown complete");
    }
}
