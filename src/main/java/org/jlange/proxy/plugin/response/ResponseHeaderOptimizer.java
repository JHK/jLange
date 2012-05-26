package org.jlange.proxy.plugin.response;

import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jlange.proxy.plugin.ResponsePlugin;

public class ResponseHeaderOptimizer implements ResponsePlugin {

    private final String[] removeableHeaders = new String[] { "P3P", "Server", "Via", "Generator" };

    public Boolean isApplicable(final HttpRequest request) {
        return true;
    }

    public Boolean isApplicable(final HttpResponse response) {
        return response != null && !response.getHeaders().isEmpty();
    }

    public void run(HttpRequest request, HttpResponse response) {

    }

    public void updateResponse(HttpResponse response) {
        // remove all headers starting with "x-"
        for (String header : response.getHeaderNames())
            if (header.toUpperCase().startsWith("X-"))
                response.removeHeader(header);

        for (String header : removeableHeaders)
            if (HttpHeaders.getHeader(response, header) != null)
                response.removeHeader(header);

        // response.setHeader("Accept-Encoding", "gzip");
        // response.setHeader("Content-Encoding", "gzip");
        // response.removeHeader("Content-Length");
        // response.setHeader("Content-Length", "1200");
    }
}