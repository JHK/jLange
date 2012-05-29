package org.jlange.proxy.inbound;

import java.net.URL;
import java.util.List;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.jlange.proxy.Tools;
import org.jlange.proxy.outbound.ChannelPipelineFactoryFactory;
import org.jlange.proxy.outbound.HttpPipelineFactory;
import org.jlange.proxy.outbound.OutboundChannelPool;
import org.jlange.proxy.outbound.PassthroughHandler;
import org.jlange.proxy.plugin.PluginProvider;
import org.jlange.proxy.plugin.ResponsePlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpHandler extends SimpleChannelUpstreamHandler implements ChannelHandler {

    private final Logger              log = LoggerFactory.getLogger(getClass());
    private final OutboundChannelPool outboundChannelPool;

    public HttpHandler() {
        outboundChannelPool = new OutboundChannelPool();
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
            channelPipelineFactoryFactory = new ChannelPipelineFactoryFactory() {
                public ChannelPipelineFactory getChannelPipelineFactory() {
                    return new ChannelPipelineFactory() {
                        public ChannelPipeline getPipeline() throws Exception {
                            final ChannelPipeline pipeline = Channels.pipeline();
                            pipeline.addLast("handler", new PassthroughHandler(inboundChannel));
                            return pipeline;
                        }
                    };
                }
            };
            channelFutureListener = new ChannelFutureListener() {
                public void operationComplete(final ChannelFuture f) {
                    log.info("Inboundchannel {} - sending response - connect ok", inboundChannel.getId());
                    HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
                    HttpHeaders.setKeepAlive(response, true);
                    inboundChannel.write(response);
                    inboundChannel.setReadable(false);
                    for (String name : inboundChannel.getPipeline().getNames())
                        inboundChannel.getPipeline().remove(name);
                    inboundChannel.getPipeline().addLast("handler", new PassthroughHandler(f.getChannel()));
                    inboundChannel.setReadable(true);
                    log.info("Inboundchannel {} - passthrough to outboundchannel {}", inboundChannel.getId(), f.getChannel().getId());
                }
            };
        } else {
            channelPipelineFactoryFactory = new ChannelPipelineFactoryFactory() {
                public ChannelPipelineFactory getChannelPipelineFactory() {
                    List<ResponsePlugin> responsePlugins = PluginProvider.getInstance().getResponsePlugins(request);
                    return new HttpPipelineFactory(inboundChannel, responsePlugins, new ChannelFutureListener() {
                        public void operationComplete(ChannelFuture future) throws Exception {
                            outboundChannelPool.getIdleConnectionListener(request);
                        }
                    });
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
        outboundChannelPool.getChannels().close().awaitUninterruptibly();

    }
}
