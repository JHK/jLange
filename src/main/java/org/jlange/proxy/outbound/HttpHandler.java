package org.jlange.proxy.outbound;

import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jlange.proxy.Tools;
import org.jlange.proxy.strategy.ProxyStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpHandler extends SimpleChannelUpstreamHandler implements ChannelHandler {

    private final Logger  log = LoggerFactory.getLogger(getClass());
    private ProxyStrategy strategy;

    public void setStrategy(final ProxyStrategy strategy) {
        this.strategy = strategy;
    }

    @Override
    public void exceptionCaught(final ChannelHandlerContext ctx, final ExceptionEvent e) {
        final ChannelFutureListener listener = strategy.getOutboundExceptionCaughtListener();
        if (listener != null)
            e.getFuture().addListener(listener);

        log.error("Channel {} - {}", e.getChannel().getId(), e.getCause().getMessage());
        Tools.closeOnFlush(e.getChannel());
    }

    @Override
    public void channelClosed(final ChannelHandlerContext ctx, final ChannelStateEvent e) throws Exception {
        log.info("Channel {} - closed", e.getChannel().getId());

        final ChannelFutureListener listener = strategy.getOutboundChannelClosedListener();
        if (listener != null)
            e.getFuture().addListener(listener);
    }

    @Override
    public void messageReceived(final ChannelHandlerContext ctx, final MessageEvent e) {
        final HttpResponse response = (HttpResponse) e.getMessage();

        log.info("Channel {} - response received - {}", e.getChannel().getId(), response.getStatus().toString());
        log.debug(response.toString());

        e.getFuture().addListener(strategy.getResponseReceivedListener(response));

        if (!HttpHeaders.isKeepAlive(response))
            e.getFuture().addListener(ChannelFutureListener.CLOSE);
    }
}
