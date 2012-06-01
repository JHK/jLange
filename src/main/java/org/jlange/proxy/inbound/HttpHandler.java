package org.jlange.proxy.inbound;

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
import org.jlange.proxy.strategy.Passthrough;
import org.jlange.proxy.strategy.ProxyStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpHandler extends SimpleChannelUpstreamHandler implements ChannelHandler {

    private final Logger log = LoggerFactory.getLogger(getClass());

    @Override
    public void exceptionCaught(final ChannelHandlerContext ctx, final ExceptionEvent e) {
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

        // choose proxy strategy
        final ProxyStrategy strategy;
        if (request.getMethod().equals(HttpMethod.CONNECT)) {
            // strategy = new HttpsToHttp(request);
            strategy = new Passthrough(request, e.getChannel());
        } else {
            strategy = new HttpToHttp(request, e.getChannel());
        }

        strategy.run();
    }

    @Override
    public void channelClosed(final ChannelHandlerContext ctx, final ChannelStateEvent e) {
        log.info("Channel {} - closed", e.getChannel().getId());
    }
}
