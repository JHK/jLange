package org.jlange.proxy.strategy;

import javax.net.ssl.SSLEngine;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.jboss.netty.handler.ssl.SslHandler;
import org.jlange.proxy.inbound.ssl.SecureSslContextFactory;
import org.jlange.proxy.util.RemoteAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpsToHttp extends HttpToHttp implements ProxyStrategy {

    private final Logger  log = LoggerFactory.getLogger(getClass());
    private final Channel inboundChannel;
    private RemoteAddress address;

    public HttpsToHttp(final Channel inboundChannel) {
        super(inboundChannel);
        this.inboundChannel = inboundChannel;
    }

    @Override
    public ChannelFuture getOutboundChannelFuture(final RemoteAddress address) {
        // Everything goes to the same host, so the initial connected address will be the one forever
        if (this.address == null)
            // https to http
            if (address.getPort() == 443)
                this.address = new RemoteAddress(address.getHost(), 80);
            else
                this.address = address;

        log.info("{}", this.address.toString());

        return super.getOutboundChannelFuture(this.address);
    }

    @Override
    public ChannelFutureListener getRequestReceivedListener(HttpRequest request) {
        if (request.getMethod().equals(HttpMethod.CONNECT))
            return new ChannelFutureListener() {
                public void operationComplete(final ChannelFuture future) {
                    log.info("Channel {} - sending response - connect ok", inboundChannel.getId());
                    HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
                    HttpHeaders.setKeepAlive(response, true);
                    inboundChannel.write(response);

                    log.info("Channel {} - build SSL pipeline", inboundChannel.getId());
                    inboundChannel.setReadable(false);

                    // SSL does compression by itself
                    inboundChannel.getPipeline().remove("deflater");

                    // build and set SSL handler
                    SSLEngine engine = SecureSslContextFactory.getServerContext().createSSLEngine();
                    engine.setUseClientMode(false);
                    inboundChannel.getPipeline().addFirst("ssl", new SslHandler(engine));

                    inboundChannel.setReadable(true);
                }
            };
        else
            return super.getRequestReceivedListener(request);
    }

    @Override
    protected void proxyToBrowserRequest(final HttpRequest request) {
        // Browser speaks with this proxy as a webserver, so the request should not get updated
        log.info("{}", request.toString());
    }
}
