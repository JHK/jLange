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

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jlange.proxy.plugin.ResponsePlugin;
import org.jlange.proxy.util.HttpHeaders2;
import org.mozilla.javascript.EvaluatorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.googlecode.htmlcompressor.compressor.HtmlCompressor;

public class HypertextCompressor implements ResponsePlugin {

    private final String[]       BAD_URIS = new String[] { "http://www.google.de/" };

    private final Logger         log      = LoggerFactory.getLogger(HypertextCompressor.class);
    private final HtmlCompressor compressor;

    public HypertextCompressor() {
        compressor = new HtmlCompressor();
        compressor.setCompressCss(true);
        compressor.setCompressJavaScript(true);
        compressor.setRemoveComments(true);
        compressor.setRemoveIntertagSpaces(false);
        compressor.setRemoveScriptAttributes(true);
        compressor.setSimpleBooleanAttributes(true);
        compressor.setPreserveLineBreaks(true);

        if (log.isDebugEnabled())
            compressor.setGenerateStatistics(true);
    }

    @Override
    public Boolean isApplicable(final HttpRequest request, final HttpResponse response) {
        if (!response.getStatus().equals(HttpResponseStatus.OK))
            return false;

        if (!(HttpHeaders2.isHtml(response) || HttpHeaders2.isJavascript(response) || HttpHeaders2.isCSS(response)))
            return false;

        final String uri = "http://" + HttpHeaders.getHost(request) + request.getUri();
        for (String bad_uri : BAD_URIS)
            if (uri.equals(bad_uri)) {
                log.debug("skip {} - considered bad", uri);
                return false;
            }

        return true;
    }

    @Override
    public void run(final HttpRequest request, final HttpResponse response) {
        final Charset encoding = HttpHeaders2.getCharset(response);

        try {
            final String content = compressor.compress(response.getContent().toString(encoding));

            ChannelBuffer buffer = ChannelBuffers.copiedBuffer(content, encoding);
            HttpHeaders.setContentLength(response, buffer.readableBytes());
            response.setContent(buffer);
        } catch (EvaluatorException e) {
            log.error(e.getMessage());
        }

        if (log.isDebugEnabled()) {
            Integer savedBytes = compressor.getStatistics().getOriginalMetrics().getFilesize()
                    - compressor.getStatistics().getCompressedMetrics().getFilesize();

            log.debug(compressor.getStatistics().toString());
            log.info("saved " + (savedBytes < 1024 ? savedBytes + " Bytes" : savedBytes / 1024 + " KiB"));
        }
    }
}
