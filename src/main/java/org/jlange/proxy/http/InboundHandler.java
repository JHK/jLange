package org.jlange.proxy.http;

import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.URL;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpRequestEncoder;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseDecoder;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.jlange.proxy.Tools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InboundHandler extends SimpleChannelUpstreamHandler {

    private final ClientSocketChannelFactory clientFactory;
    private final Logger                     log = LoggerFactory.getLogger(InboundHandler.class.getName());

    private volatile Channel                 outboundChannel;

    public InboundHandler(ClientSocketChannelFactory clientFactory) {
        this.clientFactory = clientFactory;
    }

    @Override
    public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e) {
        log.info("channel closed");
        if (outboundChannel != null)
            Tools.closeOnFlush(outboundChannel);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) {
        e.getCause().printStackTrace();
        Tools.closeOnFlush(e.getChannel());
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) {
        HttpRequest request = (HttpRequest) e.getMessage();
        log.info(request.toString());

        ClientBootstrap client = new ClientBootstrap(clientFactory);
        ChannelPipeline pipeline = client.getPipeline();
        pipeline.addLast("HttpRequestEncoder", new HttpRequestEncoder());
        pipeline.addLast("HttpResponseDecoder", new HttpResponseDecoder(8192, 8192 * 2, 8192 * 2));
        pipeline.addLast("OutboundHandler", new OutboundHandler(e.getChannel(), request));

        try {
            URL url = new URL(request.getUri());
            ChannelFuture f = client.connect(new InetSocketAddress(url.getHost(), url.getPort() == -1 ? 80 : url.getPort()));
            outboundChannel = f.getChannel();
            f.addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
        } catch (MalformedURLException e1) {
            e1.printStackTrace();
            if (e.getChannel().isConnected()) {
                HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_GATEWAY);
                e.getChannel().write(response).addListener(ChannelFutureListener.CLOSE);
            }
            Tools.closeOnFlush(outboundChannel);
        }
    }
}
