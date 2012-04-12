package org.jlange.proxy.http;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

class OutboundHandler extends SimpleChannelUpstreamHandler {

    private final Channel     inboundChannel;
    private final Logger      log;
    private final HttpRequest request;
    private HttpResponse      response;

    OutboundHandler(Channel inboundChannel, HttpRequest request) {
        this.inboundChannel = inboundChannel;
        this.log = LoggerFactory.getLogger(OutboundHandler.class.getName());
        this.request = request;
    }

    @Override
    public void channelBound(ChannelHandlerContext ctx, ChannelStateEvent e) {
        e.getChannel().write(request);
        ctx.sendUpstream(e);
    }

    @Override
    public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e) {
        log.info("channel closed");
        Tools.closeOnFlush(inboundChannel);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) {
        e.getCause().printStackTrace();
        Tools.closeOnFlush(e.getChannel());
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, final MessageEvent e) {
        if (e.getMessage() instanceof HttpResponse) {
            HttpResponse response = (HttpResponse) e.getMessage();
            log.info("Response\n" + response.toString());
            inboundChannel.write(response);
        }
        else if (e.getMessage() instanceof HttpChunk) {
            HttpChunk chunk = (HttpChunk) e.getMessage();
            log.info("Chunk\n" + chunk.toString());
            // inboundChannel.write(chunk);
        }

        // TODO optimize response

    }
}
