package org.jlange.proxy.http;

import java.net.URI;
import java.net.URL;
import java.util.Stack;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jlange.proxy.ChannelPipelineFactoryFactory;
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

        // consider proxy requests and choose channel factory
        final URL url = Tools.getURL(request);
        final ChannelPipelineFactoryFactory channelPipelineFactoryFactory;
        final ChannelFutureListener channelFutureListener;

        if (request.getMethod().equals(HttpMethod.CONNECT)) {
            log.error("connect");
            channelPipelineFactoryFactory = new ChannelPipelineFactoryFactory() {
                public ChannelPipelineFactory getChannelPipelineFactory() {
                    return new ChannelPipelineFactory() {
                        public ChannelPipeline getPipeline() throws Exception {
                            // TODO Auto-generated method stub
                            return null;
                        }
                    };
                }
            };
            channelFutureListener = new ChannelFutureListener() {
                public void operationComplete(ChannelFuture future) throws Exception {
                    // TODO Auto-generated method stub
                }
            };

        } else {
            channelPipelineFactoryFactory = new ChannelPipelineFactoryFactory() {
                public ChannelPipelineFactory getChannelPipelineFactory() {
                    return new OutboundPipelineFactory(inboundChannel, request, outboundChannelPool);
                }
            };
            // this needs to be here and not as connected listener on OutboundHandler, because the connection may not be new
            channelFutureListener = new ChannelFutureListener() {
                public void operationComplete(final ChannelFuture future) {
                    final Channel outboundChannel = future.getChannel();
                    if (outboundChannel.isConnected()) {
                        log.info("Outboundchannel {} - sending request - {}", outboundChannel.getId(), request.getUri());
                        outboundChannel.write(request);
                    } else {
                        log.warn("Outboundchannel {} - not connected, cannot send request", outboundChannel.getId());
                        // really close the connection here?
                        // how does this case happen?
                        Tools.closeOnFlush(inboundChannel);
                    }
                }
            };
        }

        // this proxy will always try to keep-alive connections
        request.removeHeader("Proxy-Connection");
        log.info("Inboundchannel {} - request received - {}", inboundChannel.getId(), request.getUri());
        log.debug(request.toString());
        requests.push(request);

        // get a channel future for target host
        final ChannelFuture outboundChannelFuture = outboundChannelPool.getChannelFuture(url, channelPipelineFactoryFactory);

        // send request
        outboundChannelFuture.addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
        outboundChannelFuture.addListener(channelFutureListener);
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
