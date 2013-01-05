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
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.spdy.SpdyHttpHeaders;

public class SpdyRequestResponseHandler extends SimpleChannelHandler {

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        HttpRequest request = (HttpRequest) e.getMessage();
        RequestResponse requestResponse = new SpdyHttpRequestResponse(request);
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

    private class SpdyHttpRequestResponse implements RequestResponse {

        private Integer      streamId;
        private HttpRequest  request;
        private HttpResponse response;

        public SpdyHttpRequestResponse(HttpRequest request) {
            setRequest(request);
        }

        @Override
        public void setRequest(HttpRequest request) {
            this.streamId = HttpHeaders.getIntHeader(request, SpdyHttpHeaders.Names.STREAM_ID, -1);
            request.removeHeader(SpdyHttpHeaders.Names.STREAM_ID);
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
            response.setHeader(SpdyHttpHeaders.Names.STREAM_ID, streamId);
            response.setHeader(SpdyHttpHeaders.Names.PRIORITY, 0);
            this.response = response;
        }

        @Override
        public HttpResponse getResponse() {
            return response;
        }
    }
}
