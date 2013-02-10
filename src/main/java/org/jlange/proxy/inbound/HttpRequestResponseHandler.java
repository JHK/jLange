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
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.handler.codec.http.HttpChunk;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;

public class HttpRequestResponseHandler extends SimpleChannelHandler {

    private int                           currentRequestId = 0;
    private int                           maxRequestId     = 0;
    private Map<Integer, RequestResponse> responseMap      = new HashMap<Integer, RequestResponse>();

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        HttpRequest request = (HttpRequest) e.getMessage();
        RequestResponse requestResponse = new HttpRequestResponse(request, maxRequestId);
        Channels.fireMessageReceived(ctx, requestResponse);
        maxRequestId++;
    }

    @Override
    public void writeRequested(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        if (e.getMessage() instanceof RequestResponse) {
            HttpRequestResponse requestResponse = (HttpRequestResponse) e.getMessage();
            synchronized (responseMap) {
                responseMap.put(requestResponse.getRequestId(), requestResponse);
                drainResponses(ctx.getChannel());
            }
        } else {
            super.writeRequested(ctx, e);
        }
    }

    private void drainResponses(Channel channel) {
        RequestResponse requestResponse = responseMap.get(currentRequestId);

        // the response is not available yet
        if (requestResponse == null)
            return;

        HttpResponse response = requestResponse.removeResponse();
        if (response != null) {
            if (channel.isConnected())
                channel.write(response);
            if (!response.isChunked()) {
                responseMap.remove(currentRequestId);
                currentRequestId++;
            }
            drainResponses(channel);
        }
        // response is already sent
        else {
            HttpChunk chunk = requestResponse.pollChunk();
            if (chunk != null) {
                if (channel.isConnected())
                    channel.write(chunk);
                if (chunk.isLast()) {
                    responseMap.remove(currentRequestId);
                    currentRequestId++;
                }
                drainResponses(channel);
            }
        }
    }

    class HttpRequestResponse implements RequestResponse {

        private int              requestId;
        private HttpRequest      request;
        private HttpResponse     response;
        private Queue<HttpChunk> chunks;

        public HttpRequestResponse(HttpRequest request, int requestId) {
            setRequest(request);
            this.requestId = requestId;
            this.chunks = new LinkedList<HttpChunk>();
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
        public HttpResponse removeResponse() {
            HttpResponse response = this.response;
            this.response = null;
            return response;
        }

        public int getRequestId() {
            return requestId;
        }

        @Override
        public void addChunk(HttpChunk chunk) {
            chunks.add(chunk);
        }

        @Override
        public HttpChunk pollChunk() {
            return chunks.poll();
        }
    }
}
