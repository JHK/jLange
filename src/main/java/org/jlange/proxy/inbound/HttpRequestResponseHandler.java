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

import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;

public class HttpRequestResponseHandler extends SimpleChannelHandler {

    // TODO: implement pipelining
    
    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        HttpRequest request = (HttpRequest) e.getMessage();
        RequestResponse requestResponse = new HttpRequestResponse(request);
        Channels.fireMessageReceived(ctx, requestResponse);
    }

    @Override
    public void writeRequested(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        if (e.getMessage() instanceof RequestResponse) {
            RequestResponse requestResponse = (RequestResponse) e.getMessage();
            ctx.getChannel().write(requestResponse.getResponse());
        } else {
            super.writeRequested(ctx, e);
        }
    }

    private class HttpRequestResponse implements RequestResponse {

        private HttpRequest  request;
        private HttpResponse response;

        public HttpRequestResponse(HttpRequest request) {
            setRequest(request);
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
    }
}
