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
package org.jlange.proxy.plugin.response;

import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.handler.codec.http.HttpChunk;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jlange.proxy.plugin.ResponsePlugin;
import org.jlange.proxy.util.HttpContentHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HypertextCompressor implements ResponsePlugin {

    private static final String[]           BAD_URIS         = new String[] { "http://www.google.de/" };

    private static final Logger             log              = LoggerFactory.getLogger(HypertextCompressor.class);
    private static final HtmlCompressor     compressor       = new HtmlCompressor();

    private final Map<HttpRequest, Charset> contentEncodings = new HashMap<HttpRequest, Charset>();

    @Override
    public Boolean isApplicable(HttpRequest request) {
        final String uri = "http://" + HttpHeaders.getHost(request) + request.getUri();
        for (String bad_uri : BAD_URIS)
            if (uri.equals(bad_uri)) {
                log.debug("skip {} - considered bad", uri);
                return false;
            }

        return true;
    }

    @Override
    public Boolean isApplicable(HttpResponse response) {
        if (!response.getStatus().equals(HttpResponseStatus.OK))
            return false;

        if (!(HttpContentHeaders.isHtml(response) || HttpContentHeaders.isJavascript(response) || HttpContentHeaders.isCSS(response)))
            return false;

        return true;
    }

    @Override
    public void run(HttpRequest request, HttpResponse response) {
        Charset encoding = HttpContentHeaders.getCharset(response);

        if (response.isChunked()) {
            response.removeHeader(HttpHeaders.Names.CONTENT_LENGTH);
            contentEncodings.put(request, encoding);
        } else {
            ChannelBuffer content = compress(encoding, response.getContent());
            HttpHeaders.setContentLength(response, content.readableBytes());
            response.setContent(content);
        }
    }

    @Override
    public void run(HttpRequest request, HttpChunk chunk) {
        if (chunk.isLast()) {
            contentEncodings.remove(request);
        } else {
            Charset encoding = contentEncodings.get(request);
            chunk.setContent(compress(encoding, chunk.getContent()));
        }
    }

    private static ChannelBuffer compress(Charset encoding, ChannelBuffer buffer) {
        String content = "";
        try {
            content = buffer.toString(encoding);
            Boolean startWithWhitespace = false;
            Boolean endWithWhitespace = false;
            if (content.startsWith(" ") || content.startsWith("\n"))
                startWithWhitespace = true;
            if (content.endsWith(" ") || content.endsWith("\n"))
                endWithWhitespace = true;

            content = compressor.compress(content);

            if (startWithWhitespace || endWithWhitespace) {
                StringBuilder sb = new StringBuilder();
                if (startWithWhitespace)
                    sb.append(" ");
                sb.append(content);
                if (endWithWhitespace)
                    sb.append(" ");
                content = sb.toString();
            }
        } catch (Exception e) {
            log.error(e.getMessage());
        }

        if (log.isDebugEnabled()) {
            Integer savedBytes = compressor.getStatistics().getOriginalMetrics().getFilesize()
                    - compressor.getStatistics().getCompressedMetrics().getFilesize();

            log.debug(compressor.getStatistics().toString());
            log.info("saved " + (savedBytes < 1024 ? savedBytes + " Bytes" : savedBytes / 1024 + " KiB"));
        }

        return ChannelBuffers.copiedBuffer(content, encoding);
    }

    private static class HtmlCompressor extends com.googlecode.htmlcompressor.compressor.HtmlCompressor {
        public HtmlCompressor() {
            setCompressCss(true);
            setCompressJavaScript(true);
            setRemoveComments(true);
            setRemoveIntertagSpaces(false);
            setRemoveScriptAttributes(true);
            setSimpleBooleanAttributes(true);
            setPreserveLineBreaks(true);

            if (log.isDebugEnabled())
                setGenerateStatistics(true);
        }
    }
}
