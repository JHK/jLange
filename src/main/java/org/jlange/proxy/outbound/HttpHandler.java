package org.jlange.proxy.outbound;

import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.jlange.proxy.Tools;
import org.jlange.proxy.plugin.PluginProvider;
import org.jlange.proxy.plugin.ResponsePlugin;
import org.jlange.proxy.util.RemoteAddress;
import org.jlange.proxy.util.ResponseReceivedListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpHandler extends SimpleChannelUpstreamHandler implements ChannelHandler {

    private final Logger             log = LoggerFactory.getLogger(getClass());
    private HttpRequest              request;
    private ResponseReceivedListener responseReceivedListener;

    public void setRequest(final HttpRequest request) {
        this.request = request;
    }
    
    public void setResponseReceivedListener(final ResponseReceivedListener responseReceivedListener) {
        this.responseReceivedListener = responseReceivedListener;
    }

    @Override
    public void exceptionCaught(final ChannelHandlerContext ctx, final ExceptionEvent e) {
        log.error("Channel {} - {}", e.getChannel().getId(), e.getCause().getMessage());
        Tools.closeOnFlush(e.getChannel());

        HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_GATEWAY);
        responseReceivedListener.responseReceived(response);
    }

    @Override
    public void channelClosed(final ChannelHandlerContext ctx, final ChannelStateEvent e) throws Exception {
        log.info("Channel {} - closed", e.getChannel().getId());
        OutboundChannelPool.getInstance().closeChannel(RemoteAddress.parseRequest(request), e.getChannel().getId());
    }

    @Override
    public void messageReceived(final ChannelHandlerContext ctx, final MessageEvent e) {
        final HttpResponse response = (HttpResponse) e.getMessage();

        log.info("Channel {} - response received - {}", e.getChannel().getId(), response.getStatus().toString());

        for (ResponsePlugin plugin : PluginProvider.getInstance().getResponsePlugins()) {
            if (plugin.isApplicable(request) && plugin.isApplicable(response)) {
                log.info("Channel {} - using plugin {}", e.getChannel().getId(), plugin.getClass().getName());
                plugin.run(response);
                plugin.updateResponse(response);
            }
        }

        responseReceivedListener.responseReceived(response);

        if (HttpHeaders.isKeepAlive(response))
            OutboundChannelPool.getInstance().setChannelIdle(RemoteAddress.parseRequest(request), e.getFuture());
        else
            e.getFuture().addListener(ChannelFutureListener.CLOSE);
    }
}
