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
