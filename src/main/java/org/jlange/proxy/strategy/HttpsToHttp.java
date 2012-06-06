/*
 * Copyright (C) 2012 Julian Knocke
 * 
 * This file is part of jLange.
 * 
 * jLange is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * 
 * jLange is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with jLange. If not, see <http://www.gnu.org/licenses/>.
 */
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
import org.jlange.proxy.inbound.ssl.KeyStoreManager;
import org.jlange.proxy.inbound.ssl.SelfSignedKeyStoreManager;
import org.jlange.proxy.inbound.ssl.SslContextFactory;
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
                    KeyStoreManager ksm = new SelfSignedKeyStoreManager();
                    SslContextFactory scf = new SslContextFactory(ksm);
                    SSLEngine engine = scf.getServerContext().createSSLEngine();
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
