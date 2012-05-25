package org.jlange.proxy.http;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jlange.proxy.Tools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InboundHandler extends SimpleChannelUpstreamHandler {

    private final Logger                     log = LoggerFactory.getLogger(getClass());
    private final ChannelFactory             outboundFactory;
    private final Map<String, ChannelFuture> outboundChannelFutureMap;

    public InboundHandler(final ChannelFactory outboundFactory) {
        this.outboundFactory = outboundFactory;
        this.outboundChannelFutureMap = new HashMap<String, ChannelFuture>();
    }

    @Override
    public void exceptionCaught(final ChannelHandlerContext ctx, final ExceptionEvent e) {
        System.err.println("Inbound Channel " + e.getChannel().getId() + " - " + e.getCause().getMessage());
        // log.error("Channel {} - {}", e.getChannel().getId(), e.getCause().getMessage());
        e.getCause().printStackTrace();
        Tools.closeOnFlush(e.getChannel());
    }

    @Override
    public void messageReceived(final ChannelHandlerContext ctx, final MessageEvent e) {
        final HttpRequest request = (HttpRequest) e.getMessage();
        final Channel inboundChannel = e.getChannel();

        log.info("Channel {} - request received for {}", inboundChannel.getId(), request.getUri());
        log.debug(request.toString());

        // this would be a good place to edit HttpRequests
        // TODO: add filters

        // get a channel future for target host
        final ChannelFuture outboundChannelFuture = getChannelFuture(inboundChannel, request);

        // send request
        outboundChannelFuture.addListener(new ChannelFutureListener() {
            public void operationComplete(final ChannelFuture future) {
                log.info("Channel {} - proxying channel {} - sending request", future.getChannel().getId(), inboundChannel.getId());
                future.getChannel().write(request);
            }
        });

        // close outbound channel too if inbound channel gets closed
        inboundChannel.getCloseFuture().addListener(new ChannelFutureListener() {
            public void operationComplete(ChannelFuture future) {
                outboundChannelFuture.addListener(ChannelFutureListener.CLOSE);
            }
        });
    }

    @Override
    public void channelClosed(final ChannelHandlerContext ctx, final ChannelStateEvent e) {
        log.info("Channel {} - closed", e.getChannel().getId());
    }

    private ChannelFuture getChannelFuture(final Channel inboundChannel, final HttpRequest request) {
        final URI uri = URI.create(request.getUri());
        final String host = uri.getHost();
        final Integer port = uri.getPort() == -1 ? 80 : uri.getPort();
        final String channelKey = uri.getHost() + ":" + port;

        ChannelFuture f = outboundChannelFutureMap.get(channelKey);

        // no channel future found for host, create a new one
        // TODO: use a queue for better channel usage
        if (f == null || !f.getChannel().isConnected()) {
            log.info("Channel {} - establishing new connection to {}", inboundChannel.getId(), host);

            // setup client
            final ClientBootstrap outboundClient = new ClientBootstrap(outboundFactory);
            outboundClient.setPipelineFactory(new OutboundPipelineFactory(inboundChannel, request));
            outboundClient.setOption("child.tcpNoDelay", true);
            outboundClient.setOption("child.keepAlive", true);

            // connect to remote host
            f = outboundClient.connect(new InetSocketAddress(host, port));

            // cleanup outboundChannels on close
            f.getChannel().getCloseFuture().addListener(new ChannelFutureListener() {
                public void operationComplete(ChannelFuture future) throws Exception {
                    outboundChannelFutureMap.remove(host + ":" + port);
                }
            });

            outboundChannelFutureMap.put(host + ":" + port, f);
        } else {
            log.info("Channel {} - reusing connection to {}", inboundChannel.getId(), host);
        }

        return f;
    }
}
