package org.jlange.proxy.http;

import java.util.List;

import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.HttpChunk;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jlange.proxy.Tools;
import org.jlange.proxy.plugin.PluginProvider;
import org.jlange.proxy.plugin.ResponsePlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class OutboundHandler extends SimpleChannelUpstreamHandler {

    private final Logger                  log = LoggerFactory.getLogger(getClass());
    private final Channel                 inboundChannel;
    private final HttpRequest             request;
    private volatile HttpResponse         response;
    private volatile List<ResponsePlugin> responsePlugins;

    OutboundHandler(final Channel inboundChannel, final HttpRequest request) {
        this.inboundChannel = inboundChannel;
        this.request = request;
    }

    @Override
    public void channelBound(ChannelHandlerContext ctx, ChannelStateEvent e) {
        log.info("Channel {} - bound, sending request to {}", e.getChannel().getId(), request.getUri());
        e.getChannel().write(request);
         responsePlugins = PluginProvider.getResponsePlugins(request);
        ctx.sendUpstream(e);
    }

    @Override
    public void channelClosed(final ChannelHandlerContext ctx, final ChannelStateEvent e) {
        log.info("Channel {} - closed", e.getChannel().getId());
        Tools.closeOnFlush(inboundChannel);
    }

    @Override
    public void exceptionCaught(final ChannelHandlerContext ctx, final ExceptionEvent e) {
        log.error("Channel {} - {}", e.getChannel().getId(), e.getCause().getMessage());
        Tools.closeOnFlush(e.getChannel());
    }

    @Override
    public void messageReceived(final ChannelHandlerContext ctx, final MessageEvent e) {

        if (e.getMessage() instanceof HttpResponse) {
            HttpResponse response = (HttpResponse) e.getMessage();
            log.info("Channel {} - response received - {}", e.getChannel().getId(), response.getStatus().toString());

            if (!response.isChunked())
                writeResponse(response);
            else {
                response.setChunked(false);
                if ("chunked".equals(response.getHeader("Transfer-Encoding")))
                    response.removeHeader("Transfer-Encoding");
                response.setContent(ChannelBuffers.dynamicBuffer());
                this.response = response;
            }
        } else if (e.getMessage() instanceof HttpChunk) {
            HttpChunk chunk = (HttpChunk) e.getMessage();
            log.info("Channel {} - response received - chunk", e.getChannel().getId());

            this.response.getContent().writeBytes(chunk.getContent());
            if (chunk.isLast())
                writeResponse(response);
        }
    }

    private void writeResponse(final HttpResponse response) {
        log.info("Channel {} - sending response", inboundChannel.getId());
        log.debug(response.toString());

        if (!inboundChannel.isConnected()) {
            log.error("Channel {} - not connected");
            return;
        }

        responsePlugins = PluginProvider.getResponsePlugins(responsePlugins, response);
        for (ResponsePlugin plugin : responsePlugins) {
            log.info("Channel {} - using plugin {}", inboundChannel.getId(), plugin.getClass().getName());
            plugin.run(request, response);
            plugin.updateResponse(response);
        }

        inboundChannel.write(response);
        log.info("Channel {} - sending response finished", inboundChannel.getId());
        Tools.closeOnFlush(inboundChannel);
    }
}
