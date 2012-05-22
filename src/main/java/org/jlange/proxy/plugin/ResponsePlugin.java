package org.jlange.proxy.plugin;

import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;

public interface ResponsePlugin {

    /**
     * Decides if the response will get filtered by investigating the request
     * 
     * @param request the request to investigate
     * @return true if the response should get modified
     */
    public Boolean filterResponses(final HttpRequest request);

    /**
     * Decides if the plugin is applicable for the given request and response
     * 
     * @param request {@link HttpRequest}
     * @param response matching {@link HttpResponse}
     * @return is the plugin applicable
     */
    public Boolean isApplicable(final HttpRequest request, final HttpResponse response);

    /**
     * Run the main code of the plugin. This is intended to run in parallel and the response may not be changed.
     * 
     * @param request {@link HttpRequest}
     * @param response matching {@link HttpResponse}
     */
    public void run(final HttpRequest request, final HttpResponse response);

    /**
     * Update the response regarding the plugins intention.
     * 
     * @param response {@link HttpResponse} to update
     */
    public void updateResponse(final HttpResponse response);

}