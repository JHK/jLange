package org.jlange.proxy.inbound;

import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jlange.proxy.Tools;
import org.jlange.proxy.strategy.HttpToHttp;
import org.jlange.proxy.strategy.HttpsToHttp;
import org.jlange.proxy.strategy.Passthrough;
import org.jlange.proxy.strategy.ProxyStrategy;
import org.jlange.proxy.util.RemoteAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpHandler extends SimpleChannelUpstreamHandler implements ChannelHandler {

    private final Logger  log = LoggerFactory.getLogger(getClass());
    private ProxyStrategy strategy;

    @Override
    public void exceptionCaught(final ChannelHandlerContext ctx, final ExceptionEvent e) {
        final ChannelFutureListener listener = strategy.getInboundExceptionCaughtListener();
        if (listener != null)
            e.getFuture().addListener(listener);

        log.error("Channel {} - {}", e.getChannel().getId(), e.getCause().getMessage());
        Tools.closeOnFlush(e.getChannel());
    }

    @Override
    public void channelBound(final ChannelHandlerContext ctx, final ChannelStateEvent e) {
        log.info("Channel {} - created", e.getChannel().getId());
    }

    @Override
    public void messageReceived(final ChannelHandlerContext ctx, final MessageEvent e) {
        final HttpRequest request = (HttpRequest) e.getMessage();

        log.info("Channel {} - request received - {}", e.getChannel().getId(), request.getUri());
        log.debug(request.toString());

        // the first message on this channel decides about the strategy
        if (strategy == null)
            if (request.getMethod().equals(HttpMethod.CONNECT))
                // strategy = new HttpsToHttp(e.getChannel());
                strategy = new Passthrough(e.getChannel());
            else
                strategy = new HttpToHttp(e.getChannel());

        strategy.getOutboundChannelFuture(RemoteAddress.parseRequest(request)).addListener(strategy.getRequestReceivedListener(request));

    }

    @Override
    public void channelClosed(final ChannelHandlerContext ctx, final ChannelStateEvent e) {
        log.info("Channel {} - closed", e.getChannel().getId());
        final ChannelFutureListener listener = strategy.getInboundChannelClosedListener();
        if (listener != null)
            e.getFuture().addListener(listener);
    }
}
