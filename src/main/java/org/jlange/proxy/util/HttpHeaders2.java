package org.jlange.proxy.util;

import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpResponse;

public class HttpHeaders2 {

    public static final class Proxy {
        public final static String CONNECTION = "Proxy-Connection";
    }

    public static final class SPDY {
        public final static String STREAM_ID   = "X-SPDY-Stream-ID";
        public final static String STREAM_PRIO = "X-SPDY-Stream-Priority";
    }

    public static final class ContentType {
        public final static String APP_JS    = "application/javascript";
        public final static String APP_XJS   = "application/x-javascript";
        public final static String IMAGE_JPG = "image/jpeg";
        public final static String IMAGE_PNG = "image/png";
        public final static String TEXT_CSS  = "text/css";
        public final static String TEXT_HTML = "text/html";
        public final static String TEXT_JS   = "text/javascript";
    }

    /**
     * Check response headers content type for being a html document
     * 
     * @param response {@link HttpResponse}
     * @return true if the responses content is html
     */
    public static final Boolean isHtml(final HttpResponse response) {
        String contentType = HttpHeaders.getHeader(response, HttpHeaders.Names.CONTENT_TYPE);
        return contentType != null && contentType.contains(ContentType.TEXT_HTML);
    }

    /**
     * Check response headers content type for being a javascript document
     * 
     * @param response {@link HttpResponse}
     * @return true if the responses content is javascript
     */
    public static final Boolean isJavascript(final HttpResponse response) {
        String contentType = HttpHeaders.getHeader(response, HttpHeaders.Names.CONTENT_TYPE);

        Boolean result = false;
        if (contentType != null)
            for (String s : new String[] { ContentType.APP_JS, ContentType.APP_XJS, ContentType.TEXT_JS })
                result = result || contentType.contains(s);

        return result;
    }

    /**
     * Check response headers content type for being a CSS document
     * 
     * @param response {@link HttpResponse}
     * @return true if the responses content is CSS
     */
    public static final Boolean isCSS(final HttpResponse response) {
        String contentType = HttpHeaders.getHeader(response, HttpHeaders.Names.CONTENT_TYPE);
        return contentType != null && contentType.contains(ContentType.TEXT_CSS);
    }

    /**
     * Check response headers content type for being a jpg image
     * 
     * @param response {@link HttpResponse}
     * @return true if the responses content is a jpg image
     */
    public static final Boolean isJPG(final HttpResponse response) {
        String contentType = HttpHeaders.getHeader(response, HttpHeaders.Names.CONTENT_TYPE);
        return contentType != null && contentType.equals(ContentType.IMAGE_JPG);
    }

    /**
     * Check response headers content type for being a png image
     * 
     * @param response {@link HttpResponse}
     * @return true if the responses content is a png image
     */
    public static final Boolean isPNG(final HttpResponse response) {
        String contentType = HttpHeaders.getHeader(response, HttpHeaders.Names.CONTENT_TYPE);
        return contentType != null && contentType.equals(ContentType.IMAGE_PNG);
    }

}