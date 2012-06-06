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

import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jlange.proxy.util.RemoteAddress;

public interface ProxyStrategy {

    public ChannelFuture getOutboundChannelFuture(final RemoteAddress address);

    public ChannelFutureListener getRequestReceivedListener(final HttpRequest request);

    public ChannelFutureListener getResponseReceivedListener(final HttpResponse response);

    public ChannelFutureListener getOutboundChannelClosedListener();

    public ChannelFutureListener getInboundChannelClosedListener();

    public ChannelFutureListener getOutboundExceptionCaughtListener();

    public ChannelFutureListener getInboundExceptionCaughtListener();

}
