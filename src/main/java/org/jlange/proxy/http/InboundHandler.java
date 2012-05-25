package org.jlange.proxy.http;

import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.URL;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jlange.proxy.Tools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InboundHandler extends SimpleChannelUpstreamHandler {

    private final Logger         log = LoggerFactory.getLogger(getClass());
    private final ChannelFactory outboundFactory;

    public InboundHandler(final ChannelFactory outboundFactory) {
        this.outboundFactory = outboundFactory;
    }

    @Override
    public void exceptionCaught(final ChannelHandlerContext ctx, final ExceptionEvent e) {
        log.error("Channel {} - {}", e.getChannel().getId(), e.getCause().getMessage());
        e.getCause().printStackTrace();
        Tools.closeOnFlush(e.getChannel());
    }

    @Override
    public void messageReceived(final ChannelHandlerContext ctx, final MessageEvent e) {
        final HttpRequest request = (HttpRequest) e.getMessage();

        log.info("Channel {} - request received for {}", e.getChannel().getId(), request.getUri());
        log.debug(request.toString());

        // this would be a good place to edit HttpRequests
        // TODO: add filters

        // the proxy needs a client to connect to remote servers
        final ClientBootstrap outbound = new ClientBootstrap(outboundFactory);
        outbound.setPipelineFactory(new OutboundPipelineFactory(e.getChannel(), request));
        outbound.setOption("child.tcpNoDelay", true);
        outbound.setOption("child.keepAlive", true);

        try {
            final URL url = new URL(request.getUri());
            ChannelFuture f = outbound.connect(new InetSocketAddress(url.getHost(), url.getPort() == -1 ? 80 : url.getPort()));
            f.addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
            // f.addListener(new ChannelFutureListener() {
            // public void operationComplete(final ChannelFuture future) throws Exception {
            // log.info("Channel {} - connected, sending request to {}", future.getChannel().getId(), request.getUri());
            // future.getChannel().write(request);
            // }
            // });
        } catch (MalformedURLException e1) {
            log.error("Channel {} - malformed url - {}", e.getChannel().getId(), e1.getMessage());
        }
    }

    @Override
    public void channelClosed(final ChannelHandlerContext ctx, final ChannelStateEvent e) throws Exception {
        log.info("Channel {} - closed", e.getChannel().getId());
    }
}
