package org.jlange.proxy.util;

import org.jboss.netty.handler.codec.http.HttpResponse;

public interface ResponseReceivedListener {

    /**
     * This method should be called when a response is available, so that the implementing listener can work with it.
     * 
     * @param response {@link HttpResponse}
     */
    public void responseReceived(final HttpResponse response);
}
