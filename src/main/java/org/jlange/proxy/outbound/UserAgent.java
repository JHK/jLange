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

import java.util.LinkedList;
import java.util.List;

import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jlange.proxy.util.HttpResponseListener;
import org.jlange.proxy.util.RemoteAddress;

public class UserAgent {

    private Integer timeout = 30;

    public Integer getTimeout() {
        return timeout;
    }

    public void setTimeout(final Integer timeout) {
        this.timeout = timeout;
    }

    public void request(final HttpRequest request, final HttpResponseListener responseListener) {
        List<HttpResponseListener> responseListenerList = new LinkedList<HttpResponseListener>();
        responseListenerList.add(responseListener);
        request(request, responseListenerList);
    }

    public void request(final HttpRequest request, final List<HttpResponseListener> responseListenerList) {
        final RemoteAddress address = RemoteAddress.parseRequest(request);

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
}
