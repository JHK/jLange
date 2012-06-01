package org.jlange.proxy.strategy;

import javax.net.ssl.SSLEngine;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.jboss.netty.handler.ssl.SslHandler;
import org.jlange.proxy.inbound.ssl.SecureSslContextFactory;
import org.jlange.proxy.outbound.OutboundChannelPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpsToHttp {

    private final Logger  log = LoggerFactory.getLogger(getClass());
    private final Channel inboundChannel;
    
    public HttpsToHttp(Channel inboundChannel, HttpRequest request, OutboundChannelPool outboundChannelPool) {
//        super(inboundChannel, request, outboundChannelPool);
        this.inboundChannel = inboundChannel;
    }

    public ChannelFutureListener getChannelActionListener() {
        return new ChannelFutureListener() {
            public void operationComplete(ChannelFuture future) {
                log.info("Inboundchannel {} - sending response - connect ok", inboundChannel.getId());
                HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
                HttpHeaders.setKeepAlive(response, true);
                inboundChannel.write(response);

                log.info("Inboundchannel {} - build SSL pipeline");
                inboundChannel.setReadable(false);
                SSLEngine engine = SecureSslContextFactory.getServerContext().createSSLEngine();
                engine.setUseClientMode(false);
                inboundChannel.getPipeline().addFirst("ssl", new SslHandler(engine));
                inboundChannel.setReadable(true);
            }
        };
    }

}
