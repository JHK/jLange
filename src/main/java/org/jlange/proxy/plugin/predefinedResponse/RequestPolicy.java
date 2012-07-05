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
package org.jlange.proxy.plugin.predefinedResponse;

import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.jlange.proxy.plugin.PredefinedResponsePlugin;
import org.jlange.proxy.util.RemoteAddress;

public class RequestPolicy implements PredefinedResponsePlugin {

    private final static HttpResponse HTTP_FORBIDDEN = buildForbiddenHttpResponse();

    private final static String[]     HOST_BLACKLIST = new String[] { "localhost", "127.0.0.1", "127.0.1.1" };

    @Override
    public HttpResponse getPredefinedResponse(final HttpRequest request) {
        final RemoteAddress address = RemoteAddress.parseRequest(request);

        for (String s : HOST_BLACKLIST)
            if (address.getHost().equals(s))
                return HTTP_FORBIDDEN;

        return null;
    }

    private static HttpResponse buildForbiddenHttpResponse() {
        final HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.FORBIDDEN);
        HttpHeaders.setContentLength(response, response.getContent().readableBytes());
        return response;
    }
}
