package org.jlange.proxy.plugin;

import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;

public interface ResponsePlugin {

    /**
     * Decides if the plugin is applicable for the given request
     * 
     * @param request {@link HttpRequest}
     * @return is the plugin applicable
     */
    public Boolean isApplicable(final HttpRequest request);

    /**
     * Decides if the plugin is applicable for the given response
     * 
     * @param response {@link HttpResponse}
     * @return is the plugin applicable
     */
    public Boolean isApplicable(final HttpResponse response);
    
    /**
     * Run the main code of the plugin. This is intended to run in parallel and the response may not be changed.
     * 
     * @param request {@link HttpRequest}
     * @param response matching {@link HttpResponse}
     */
    public void run(final HttpResponse response);

    /**
     * Update the response regarding the plugins intention.
     * 
     * @param response {@link HttpResponse} to update
     */
    public void updateResponse(final HttpResponse response);

}