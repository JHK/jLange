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
package org.jlange.proxy.outbound;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.LinkedList;
import java.util.List;

import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.jlange.proxy.util.HttpHeaders2;
import org.jlange.proxy.util.HttpResponseListener;
import org.jlange.proxy.util.RemoteAddress;

public class UserAgent {

    private Integer       timeout = 30;
    private RemoteAddress proxy   = null;

    public Integer getTimeout() {
        return timeout;
    }

    public void setTimeout(final Integer timeout) {
        this.timeout = timeout;
    }

    public RemoteAddress getProxy() {
        return proxy;
    }

    public void setProxy(final RemoteAddress proxy) {
        this.proxy = proxy;
    }

    public void request(final HttpRequest request, final HttpResponseListener responseListener) throws MalformedURLException {
        List<HttpResponseListener> responseListenerList = new LinkedList<HttpResponseListener>();
        responseListenerList.add(responseListener);
        request(request, responseListenerList);
    }

    public void request(final HttpRequest request, final List<HttpResponseListener> responseListenerList) throws MalformedURLException {
        final RemoteAddress address;
        if (proxy != null) {
            address = proxy;
            updateProxyRequest(request);
        } else {
            address = RemoteAddress.parseRequest(request);
            updateRequest(request);
        }

        ChannelFuture outboundFuture = OutboundChannelPool.getInstance().getIdleChannelFuture(address);
        if (outboundFuture == null)
            outboundFuture = OutboundChannelPool.getInstance().getNewChannelFuture(address, new HttpPipelineFactory(timeout));

        // set actions when response arrives
        final HttpResponseHandler outboundHandler = outboundFuture.getChannel().getPipeline().get(HttpResponseHandler.class);
        for (HttpResponseListener responseListener : responseListenerList)
            outboundHandler.addResponseListener(responseListener);

        // perform request on outbound channel
        outboundHandler.sendRequest(outboundFuture, request);
    }

    private void updateProxyRequest(final HttpRequest request) throws MalformedURLException {
        request.setProtocolVersion(HttpVersion.HTTP_1_1);
        request.removeHeader(HttpHeaders.Names.CONNECTION);
        request.setHeader(HttpHeaders2.Proxy.CONNECTION, HttpHeaders.Values.KEEP_ALIVE);

        if (!request.getUri().toLowerCase().startsWith("http")) {
            RemoteAddress address = RemoteAddress.parseRequest(request);
            String schema = address.getPort() == 443 ? "https" : "http";
            URL url = new URL(schema, address.getHost(), address.getPort(), request.getUri());

            request.setUri(url.toString());
            request.setHeader(HttpHeaders.Names.HOST, address.toString());
        }
    }

    private void updateRequest(final HttpRequest request) {
        request.setProtocolVersion(HttpVersion.HTTP_1_1);
        request.removeHeader(HttpHeaders.Names.CONNECTION);
    }
}
