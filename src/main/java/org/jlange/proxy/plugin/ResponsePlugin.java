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
    public void run(final HttpRequest request, final HttpResponse response);

    /**
     * Update the response regarding the plugins intention.
     * 
     * @param response {@link HttpResponse} to update
     */
    public void updateResponse(final HttpResponse response);

}