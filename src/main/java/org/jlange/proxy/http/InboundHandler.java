package org.jlange.proxy.http;

import java.net.URI;
import java.util.Stack;

import org.jboss.netty.channel.Channel;
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

    private final Logger              log = LoggerFactory.getLogger(getClass());
    private final OutboundChannelPool outboundChannelPool;
    private final Stack<HttpRequest>  requests;

    public InboundHandler() {
        outboundChannelPool = new OutboundChannelPool();
        requests = new Stack<HttpRequest>();
    }

    @Override
    public void exceptionCaught(final ChannelHandlerContext ctx, final ExceptionEvent e) {
        log.error("Channel {} - {}", e.getChannel().getId(), e.getCause().getMessage());
        Tools.closeOnFlush(e.getChannel());
    }

    @Override
    public void messageReceived(final ChannelHandlerContext ctx, final MessageEvent e) {
        final HttpRequest request = (HttpRequest) e.getMessage();
        final Channel inboundChannel = e.getChannel();

        log.info("Inboundchannel {} - request received - {}", inboundChannel.getId(), request.getUri());
        log.debug(request.toString());
        requests.push(request);

        // this would be a good place to edit HttpRequests
        // TODO: add filters

        // get a channel future for target host
        final ChannelFuture outboundChannelFuture = outboundChannelPool.getChannelFuture(inboundChannel, request);

        // send request
        // this needs to be here and not as connected listener on OutboundHandler, because the connection may not be new
        outboundChannelFuture.addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
        outboundChannelFuture.addListener(new ChannelFutureListener() {
            public void operationComplete(final ChannelFuture future) {
                final Channel outboundChannel = future.getChannel();
                if (outboundChannel.isConnected()) {
                    log.info("Outboundchannel {} - sending request - {}", outboundChannel.getId(), request.getUri());
                    outboundChannel.write(request);
                } else {
                    log.warn("Outboundchannel {} - not connected, cannot send request", outboundChannel.getId());
                    // really close the connection here, how does this case happen?
                    Tools.closeOnFlush(inboundChannel);
                }
            }
        });
    }

    @Override
    public void channelClosed(final ChannelHandlerContext ctx, final ChannelStateEvent e) {
        log.info("Inboundchannel {} - closed", e.getChannel().getId());

        // close corresponding outbound channels
        for (HttpRequest request : requests) {
            // potential expensive logging, so it is covered by a condition
            if (log.isDebugEnabled())
                log.debug("Inboundchannel {} - cleaning up pool {}", e.getChannel().getId(), URI.create(request.getUri()).getHost());
            outboundChannelPool.getChannels(request).close().awaitUninterruptibly();
        }
    }
}
