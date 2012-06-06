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

import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;

import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.util.CharsetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Tools {

    private static final String[] javascriptContentTypes = new String[] { "application/x-javascript", "text/javascript",
            "application/javascript"                    };
    private static final Logger   log                    = LoggerFactory.getLogger(Tools.class);

    /**
     * Check response headers content type for being a html document
     * 
     * @param response {@link HttpResponse}
     * @return true if the responses content is html
     */
    public static final Boolean isHtml(final HttpResponse response) {
        String contentType = HttpHeaders.getHeader(response, "content-type");
        return contentType != null && contentType.contains("text/html");
    }

    /**
     * Check response headers content type for being a javascript document
     * 
     * @param response {@link HttpResponse}
     * @return true if the responses content is javascript
     */
    public static final Boolean isJavascript(final HttpResponse response) {
        String contentType = HttpHeaders.getHeader(response, "content-type");

        Boolean result = false;
        if (contentType != null)
            for (String s : javascriptContentTypes)
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
        String contentType = HttpHeaders.getHeader(response, "content-type");
        return contentType != null && contentType.contains("text/css");
    }

    /**
     * Reads the content of the {@link HttpResponse} and tries to determine the correct encoding
     * 
     * @param response {@link HttpResponse}
     * @return a suggestion of the contents encoding
     */
    public static final Charset getCharset(final HttpResponse response) {
        Charset encoding = null;

        String contentType = HttpHeaders.getHeader(response, "content-type");

        if (contentType.contains("charset")) {
            try {
                String charsetName = contentType.toUpperCase().replaceAll("\\w+/[\\w-]+\\s*?;\\s*?CHARSET=([\\w-]+)\\s*?;?", "$1");
                encoding = Charset.forName(charsetName);
            } catch (IllegalCharsetNameException e) {
                log.error("Charset found but not applicable: " + contentType);
            }
        }

        if (encoding == null) {
            // try to suggest encoding
            encoding = CharsetUtil.UTF_8;
            String content = response.getContent().toString(encoding);

            // FIXME: regex does not work, nevertheless it works through bad character finding
            if (content.matches("<head>.*?charset\\s*?=\\s*?iso-8859-1.*?</head>")) {
                encoding = CharsetUtil.ISO_8859_1;
            } else if (content.contains("�")) {
                encoding = CharsetUtil.ISO_8859_1;
            }
            // TODO: other cases for encodings
        }

        return encoding;
    }
}
