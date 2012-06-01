package org.jlange.proxy.inbound.strategy;

import java.net.MalformedURLException;
import java.net.URL;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jlange.proxy.outbound.HttpHandler;
import org.jlange.proxy.outbound.HttpPipelineFactory;
import org.jlange.proxy.outbound.OutboundChannelPool;
import org.jlange.proxy.util.RemoteAddress;
import org.jlange.proxy.util.ResponseReceivedListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpToHttp implements ProxyStrategy {

    private final Logger      log = LoggerFactory.getLogger(getClass());
    private final HttpRequest request;
    private final Channel     inboundChannel;

    public HttpToHttp(final HttpRequest request, final Channel inboundChannel) {
        this.request = request;
        this.inboundChannel = inboundChannel;
    }

    public void run() {
        // change the request to an default browser like request
        // this proxy will always try to keep-alive connections
        request.removeHeader("Proxy-Connection");
        HttpHeaders.setKeepAlive(request, true);
        try {
            final StringBuilder sb = new StringBuilder();
            final URL url = new URL(request.getUri());
            sb.append(url.getPath());
            if (url.getQuery() != null)
                sb.append("?").append(url.getQuery());
            request.setUri(sb.toString());
        } catch (MalformedURLException e2) {
            log.error("Channel {} - {}\n" + e2.fillInStackTrace().toString(), inboundChannel.getId(), e2.toString());
        }

        // define what needs to be done with the response
        ResponseReceivedListener responseReceivedListener = new ResponseReceivedListener() {
            public void responseReceived(HttpResponse response) {
                // error handling for not connected inbound channel
                if (!inboundChannel.isConnected()) {
                    log.error("Channel {} - inbound channel closed before sending response", inboundChannel.getId());
                    log.debug("Channel {} - {}", response.toString());
                    return;
                }

                HttpHeaders.setKeepAlive(response, true);

                // write response
                log.info("Channel {} - sending response - {}", inboundChannel.getId(), response.getStatus().toString());
                log.debug("Channel {} - {}", inboundChannel.getId(), response.toString());
                inboundChannel.write(response);
            }
        };

        // get a channel future for target host
        final RemoteAddress address = RemoteAddress.parseRequest(request);
        final OutboundChannelPool channelPool = OutboundChannelPool.getInstance();

        ChannelFuture future = channelPool.getIdleChannelFuture(address);
        if (future != null) {
            HttpHandler handler = (HttpHandler) future.getChannel().getPipeline().get("handler");
            handler.setRequest(request);
            handler.setResponseReceivedListener(responseReceivedListener);
        } else {
            HttpHandler handler = new HttpHandler();
            handler.setRequest(request);
            handler.setResponseReceivedListener(responseReceivedListener);
            future = channelPool.getNewChannelFuture(address, new HttpPipelineFactory(handler));
        }

        // send the request
        future.addListener(new ChannelFutureListener() {
            public void operationComplete(ChannelFuture future) {
                log.info("Channel {} - sending request to {}", future.getChannel().getId(), HttpHeaders.getHost(request));
                log.debug(request.toString());
                future.getChannel().write(request);
            }
        });
    }

}
