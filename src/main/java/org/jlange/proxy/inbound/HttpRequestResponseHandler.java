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
package org.jlange.proxy.inbound;

import java.util.HashMap;
import java.util.Map;

import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;

public class HttpRequestResponseHandler extends SimpleChannelHandler {

    private int                        currentRequestId = 0;
    private int                        maxRequestId     = 0;
    private Map<Integer, HttpResponse> responseMap      = new HashMap<Integer, HttpResponse>();

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        HttpRequest request = (HttpRequest) e.getMessage();
        RequestResponse requestResponse = new HttpRequestResponse(request, maxRequestId);
        Channels.fireMessageReceived(ctx, requestResponse);
        maxRequestId++;
    }

    @Override
    public void writeRequested(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        if (!(e.getMessage() instanceof RequestResponse)) {
            super.writeRequested(ctx, e);
            return;
        }

        HttpRequestResponse requestResponse = (HttpRequestResponse) e.getMessage();
        synchronized (responseMap) {
            responseMap.put(requestResponse.getRequestId(), requestResponse.getResponse());

            HttpResponse response;
            while ((response = responseMap.remove(currentRequestId)) != null) {
                currentRequestId++;
                if (ctx.getChannel().isConnected())
                    ctx.getChannel().write(response);
            }
        }
    }

    class HttpRequestResponse implements RequestResponse {

        private int          requestId;
        private HttpRequest  request;
        private HttpResponse response;

        public HttpRequestResponse(HttpRequest request, int requestId) {
            setRequest(request);
            this.requestId = requestId;
        }

        @Override
        public void setRequest(HttpRequest request) {
            this.request = request;
        }

        @Override
        public HttpRequest removeRequest() {
            HttpRequest request = this.request;
            this.request = null;
            return request;
        }

        @Override
        public void setResponse(HttpResponse response) {
            this.response = response;
        }

        @Override
        public HttpResponse getResponse() {
            return response;
        }

        public int getRequestId() {
            return requestId;
        }
    }
}
