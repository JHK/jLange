package org.jlange.proxy.test.client;

import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.handler.codec.http.HttpChunk;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jlange.proxy.Tools;

public class DebugHttpRequestHandler extends SimpleChannelHandler {

    private final HttpRequest request;

    public DebugHttpRequestHandler(HttpRequest request) {
        this.request = request;
    }

    @Override
    public void channelBound(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        e.getChannel().write(request);
        ctx.sendUpstream(e);
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        if (e.getMessage() instanceof HttpResponse) {
            HttpResponse response = (HttpResponse) e.getMessage();
            System.out.println(response.getStatus());
            System.out.println(Tools.readBuffer(response.getContent()));
        } else if (e.getMessage() instanceof HttpChunk) {
            HttpChunk response = (HttpChunk) e.getMessage();
            System.out.println(Tools.readBuffer(response.getContent()));
        }

        e.getChannel().close();
    }
}