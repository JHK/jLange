package org.jlange.proxy.outbound;

import java.util.LinkedList;
import java.util.List;

import org.jboss.netty.channel.Channel;
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
import org.jlange.proxy.plugin.ResponsePlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpHandler extends SimpleChannelUpstreamHandler implements ChannelHandler {

    private final Logger                      log = LoggerFactory.getLogger(getClass());
    private final Channel                     inboundChannel;
    private final List<ResponsePlugin>        responsePlugins;
    private final List<ChannelFutureListener> messageReceivedListener;

    public HttpHandler(final Channel inboundChannel, final List<ResponsePlugin> responsePlugins) {
        this.inboundChannel = inboundChannel;
        this.responsePlugins = responsePlugins;
        this.messageReceivedListener = new LinkedList<ChannelFutureListener>();
    }

    public void addMessageReceivedListener(ChannelFutureListener messageReceivedListener) {
        this.messageReceivedListener.add(messageReceivedListener);
    }

    @Override
    public void exceptionCaught(final ChannelHandlerContext ctx, final ExceptionEvent e) {
        log.error("Outboundchannel {} - {}", e.getChannel().getId(), e.getCause().getMessage());
        Tools.closeOnFlush(e.getChannel());
        Tools.closeOnFlush(inboundChannel);
    }

    @Override
    public void channelClosed(final ChannelHandlerContext ctx, final ChannelStateEvent e) throws Exception {
        log.info("Outboundchannel {} - closed", e.getChannel().getId());
    }

    @Override
    public void messageReceived(final ChannelHandlerContext ctx, final MessageEvent e) {
        final HttpResponse response = (HttpResponse) e.getMessage();
        final Channel outboundChannel = e.getChannel();
        log.info("Outboundchannel {} - response received - {}", outboundChannel.getId(), response.getStatus().toString());

        for (ResponsePlugin plugin : responsePlugins) {
            if (plugin.isApplicable(response)) {
                log.info("Outboundchannel {} - using plugin {}", outboundChannel.getId(), plugin.getClass().getName());
                plugin.run(response);
                plugin.updateResponse(response);
            }
        }

        // error handling for not connected inbound channel
        if (!inboundChannel.isConnected()) {
            log.error("Inboundchannel {} - inbound channel closed before sending response", inboundChannel.getId());
            log.debug("Inboundchannel {} - {}", response.toString());
            Tools.closeOnFlush(outboundChannel);
            return;
        }

        // check closing outbound connection and keep inbound
        if (!HttpHeaders.isKeepAlive(response)) {
            messageReceivedListener.add(ChannelFutureListener.CLOSE);
            HttpHeaders.setKeepAlive(response, true);
        }

        // write response
        log.info("Inboundchannel {} - sending response - {}", inboundChannel.getId(), response.getStatus().toString());
        log.debug("Inboundchannel {} - {}", inboundChannel.getId(), response.toString());
        inboundChannel.write(response);

        for (ChannelFutureListener futureListener : messageReceivedListener)
            e.getFuture().addListener(futureListener);
    }
}
