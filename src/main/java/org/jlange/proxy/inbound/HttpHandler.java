package org.jlange.proxy.inbound;

import java.net.URL;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jlange.proxy.Tools;
import org.jlange.proxy.inbound.strategy.HttpToHttp;
import org.jlange.proxy.inbound.strategy.Passthrough;
import org.jlange.proxy.inbound.strategy.ProxyStrategy;
import org.jlange.proxy.outbound.OutboundChannelPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpHandler extends SimpleChannelUpstreamHandler implements ChannelHandler {

    private final Logger              log = LoggerFactory.getLogger(getClass());
    private final OutboundChannelPool outboundChannelPool;

    public HttpHandler() {
        outboundChannelPool = new OutboundChannelPool();
    }

    @Override
    public void exceptionCaught(final ChannelHandlerContext ctx, final ExceptionEvent e) {
        log.error("Inboundchannel {} - {}", e.getChannel().getId(), e.getCause().getMessage());
        Tools.closeOnFlush(e.getChannel());
    }

    @Override
    public void channelBound(final ChannelHandlerContext ctx, final ChannelStateEvent e) {
        log.info("Inboundchannel {} - created", e.getChannel().getId());
    }

    @Override
    public void messageReceived(final ChannelHandlerContext ctx, final MessageEvent e) {
        final HttpRequest request = (HttpRequest) e.getMessage();
        final Channel inboundChannel = e.getChannel();

        // this proxy will always try to keep-alive connections
        log.info("Inboundchannel {} - request received - {}", inboundChannel.getId(), request.getUri());
        log.debug(request.toString());
        request.removeHeader("Proxy-Connection");

        // investigate proxy request and choose strategy for in and outbound channels
        final ProxyStrategy strategy;
        if (request.getMethod().equals(HttpMethod.CONNECT)) {
            // strategy = new HttpsToHttp(inboundChannel, request, outboundChannelPool);
            strategy = new Passthrough(inboundChannel);
        } else {
            strategy = new HttpToHttp(inboundChannel, request, outboundChannelPool);
        }
        log.debug("Inboundchannel {} - chosen strategy: {}", inboundChannel.getId(), strategy.getClass().getName());

        // get a channel future for target host
        final URL url = Tools.getURL(request);
        final ChannelFuture outboundChannelFuture = outboundChannelPool.getChannelFuture(url, strategy);

        // do what needs to be done when the outbound channel is connected
        outboundChannelFuture.addListener(strategy.getChannelActionListener());
    }

    @Override
    public void channelClosed(final ChannelHandlerContext ctx, final ChannelStateEvent e) {
        log.info("Inboundchannel {} - closed", e.getChannel().getId());

        // close corresponding outbound channels
        outboundChannelPool.getChannels().close().awaitUninterruptibly();

    }
}
