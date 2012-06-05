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
