package org.jlange.proxy.test.client;

import java.nio.charset.Charset;

import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;

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
        HttpResponse response = (HttpResponse) e.getMessage();

        System.out.println(response.getStatus());
        System.out.println(response.getContent().toString(Charset.defaultCharset()));

        e.getChannel().close();
    }
}