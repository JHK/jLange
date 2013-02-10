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

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.handler.codec.http.HttpChunk;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.spdy.SpdyHttpHeaders;

public class SpdyRequestResponseHandler extends SimpleChannelHandler {

    private SpdyHttpRequestResponse currentResponse;
    private Set<RequestResponse>    responseList = new HashSet<RequestResponse>();

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
            synchronized (responseList) {
                responseList.add(requestResponse);
                drainResponses(ctx.getChannel());
            }
        } else {
            super.writeRequested(ctx, e);
        }
    }

    private void drainResponses(Channel channel) {
        if (currentResponse == null || !responseList.contains(currentResponse))
            currentResponse = chooseNextRequestResponse();

        if (currentResponse == null)
            return;

        HttpResponse response = currentResponse.removeResponse();
        if (response != null) {
            if (channel.isConnected())
                channel.write(response);
            if (!response.isChunked())
                responseList.remove(currentResponse);
            drainResponses(channel);
        }
        // response is already sent
        else {
            HttpChunk chunk = currentResponse.pollChunk();
            if (chunk != null) {
                if (channel.isConnected())
                    channel.write(chunk);
                if (chunk.isLast())
                    responseList.remove(currentResponse);
                drainResponses(channel);
            }
        }
    }

    /*
     * Try to get the smallest completed response first before the biggest incomplete response
     */
    private SpdyHttpRequestResponse chooseNextRequestResponse() {
        SpdyHttpRequestResponse nextResponse = null;

        Iterator<RequestResponse> it = responseList.iterator();
        while (it.hasNext()) {
            SpdyHttpRequestResponse i = (SpdyHttpRequestResponse) it.next();
            if (currentResponse == null || !currentResponse.equals(i))
                if (i.response != null)
                    if (nextResponse == null)
                        nextResponse = i;
                    else if (!nextResponse.isComplete && i.isComplete)
                        nextResponse = i;
                    else if (!nextResponse.isComplete && nextResponse.getResponseContentSize() < i.getResponseContentSize())
                        nextResponse = i;
                    else if (nextResponse.getResponseContentSize() > i.getResponseContentSize())
                        nextResponse = i;
        }

        return nextResponse;
    }

    private class SpdyHttpRequestResponse implements RequestResponse {

        private Integer          streamId;
        private HttpRequest      request;
        private HttpResponse     response;
        private Queue<HttpChunk> chunks;
        private boolean          isComplete;

        public SpdyHttpRequestResponse(HttpRequest request) {
            setRequest(request);
            this.chunks = new LinkedList<HttpChunk>();
            this.isComplete = false;
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
            if (!response.isChunked())
                this.isComplete = true;
        }

        @Override
        public HttpResponse removeResponse() {
            HttpResponse response = this.response;
            this.response = null;
            return response;
        }

        @Override
        public void addChunk(HttpChunk chunk) {
            chunks.add(chunk);
            if (chunk.isLast())
                isComplete = true;
        }

        @Override
        public HttpChunk pollChunk() {
            return chunks.poll();
        }

        public Integer getResponseContentSize() {
            Integer contentSize = response.getContent().readableBytes();
            for (HttpChunk chunk : chunks)
                contentSize += chunk.getContent().readableBytes();
            return contentSize;
        }
    }
}
