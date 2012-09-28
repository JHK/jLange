package org.jlange.test.tools;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpContentCompressor;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpRequestDecoder;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseEncoder;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.jboss.netty.util.CharsetUtil;
import org.jlange.proxy.outbound.OutboundChannelPool;
import org.jlange.proxy.util.ServerSocketChannelFactory;

public class WebServer {

    public static EchoHandler ECHO_HANDLER        = new EchoHandler();
    public static HtmlHandler SIMPLE_HTML_HANDLER = new HtmlHandler(HtmlHandler.SIMPLE_HTML);

    public static void main(String[] args) throws IOException {
        WebServer server = new WebServer(8000);
        server.setChannelHandler(ECHO_HANDLER);
        server.start();

        System.in.read();

        server.stop();
    }

    private final ServerSocketChannelFactory inboundFactory;
    private final int                        port;
    private ChannelHandler                   handler;

    public WebServer(final int port) {
        this.port = port;
        this.inboundFactory = new ServerSocketChannelFactory(Executors.newCachedThreadPool(), Executors.newCachedThreadPool());
        this.handler = ECHO_HANDLER;
    }

    public void start() {
        final ServerBootstrap inbound = new ServerBootstrap(inboundFactory);
        inbound.setPipelineFactory(new ChannelPipelineFactory() {
            @Override
            public ChannelPipeline getPipeline() throws Exception {
                ChannelPipeline pipeline = Channels.pipeline();

                pipeline.addLast("decoder", new HttpRequestDecoder());
                pipeline.addLast("encoder", new HttpResponseEncoder());
                pipeline.addLast("deflater", new HttpContentCompressor());
                pipeline.addLast("server", getChannelHandler());

                return pipeline;
            }
        });
        inbound.setOption("child.tcpNoDelay", true);
        inbound.setOption("child.keepAlive", true);

        Channel channel = inbound.bind(new InetSocketAddress(port));
        inboundFactory.addChannel(channel);
    }

    public void stop() {
        inboundFactory.getChannels().close().awaitUninterruptibly();
        inboundFactory.releaseExternalResources();

        OutboundChannelPool.getNioClientSocketChannelFactory().releaseExternalResources();
    }

    public void setChannelHandler(final ChannelHandler handler) {
        this.handler = handler;
    }

    public ChannelHandler getChannelHandler() {
        return handler;
    }

    private static class EchoHandler extends SimpleChannelUpstreamHandler {

        @Override
        public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
            super.messageReceived(ctx, e);

            HttpRequest request = (HttpRequest) e.getMessage();

            HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
            response.setContent(ChannelBuffers.copiedBuffer(request.toString(), CharsetUtil.UTF_8));
            HttpHeaders.setHeader(response, HttpHeaders.Names.CONTENT_TYPE, "text/plain; charset=" + CharsetUtil.UTF_8.toString());
            HttpHeaders.setContentLength(response, response.getContent().readableBytes());

            ctx.getChannel().write(response);
        }

    }

    public static class HtmlHandler extends SimpleChannelUpstreamHandler {

        public static String SIMPLE_HTML = "<head><title>Title</title><head><body><h1>Body</h1><p>Lorem ipsum</p></body>";

        private String       html;

        public HtmlHandler(String html) {
            this.html = html;
        }

        public void setHtml(String html) {
            this.html = html;
        }

        public String getHtml(String html) {
            return this.html;
        }

        @Override
        public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
            super.messageReceived(ctx, e);

            HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
            response.setContent(ChannelBuffers.copiedBuffer(html, CharsetUtil.UTF_8));
            HttpHeaders.setHeader(response, HttpHeaders.Names.CONTENT_TYPE, "text/html; charset=" + CharsetUtil.UTF_8.toString());
            HttpHeaders.setContentLength(response, response.getContent().readableBytes());

            ctx.getChannel().write(response);
        }
    }
}
