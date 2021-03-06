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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.jboss.netty.buffer.ChannelBufferInputStream;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.handler.codec.http.HttpChunk;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jlange.proxy.plugin.ResponsePlugin;
import org.jlange.proxy.util.Config;
import org.jlange.proxy.util.HttpContentHeaders;
import org.jlange.proxy.util.Tools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ImageCompressor implements ResponsePlugin {

    private static final Logger log       = LoggerFactory.getLogger(ImageCompressor.class);
    private static Boolean      isEnabled = true;

    @Override
    public Boolean isApplicable(final HttpRequest request) {
        return isEnabled;
    }

    @Override
    public Boolean isApplicable(final HttpResponse response) {
        if (!response.getStatus().equals(HttpResponseStatus.OK))
            return false;

        if (HttpHeaders.getHeader(response, HttpHeaders.Names.CACHE_CONTROL, "").contains(HttpHeaders.Values.NO_TRANSFORM))
            return false;

        if (!HttpContentHeaders.isJPG(response) && !HttpContentHeaders.isPNG(response))
            return false;

        if (response.isChunked()) {
            log.info("Received image but could not apply plugin, because response is chunked!");
            return false;
        }

        return true;
    }

    @Override
    public void run(final HttpRequest request, final HttpResponse response) {
        String uniqueName = String.valueOf(Thread.currentThread().getId());
        ImageType type = HttpContentHeaders.isJPG(response) ? new JPG(uniqueName)
                : HttpContentHeaders.isPNG(response) ? new PNG(uniqueName) : null;

        try {

            int before, after;
            log.debug("Using tmpfile {}", type.getFile().getAbsolutePath());

            { // write file to disk
                InputStream in = new ChannelBufferInputStream(response.getContent());
                OutputStream out = new FileOutputStream(type.getFile());
                byte[] b = new byte[in.available()];
                in.read(b);
                out.write(b);
                before = b.length;
                in.close();
                out.close();
            }

            // optimize file
            type.optimize();

            { // read file to response
                InputStream in = new FileInputStream(type.getFile());
                byte[] b = new byte[in.available()];
                in.read(b);
                response.setContent(ChannelBuffers.copiedBuffer(b));
                HttpHeaders.setContentLength(response, b.length);
                after = b.length;
                in.close();
            }

            log.info("saved {} bytes ({}%) - {}", new Object[] { before - after, 100 - ((float) after / before) * 100, request.getUri() });

        } catch (IOException e) {
            log.warn("deactivate plugin: {}", e.getMessage());
            isEnabled = false;
        }
    }

    @Override
    public void run(HttpRequest request, HttpChunk chunk) {}

    private interface ImageType {
        public File getFile();

        public void optimize() throws IOException;
    }

    private static final class JPG implements ImageType {

        private final static String ENDING = ".jpg";
        private final File          file;

        public JPG(final String name) {
            file = new File(Config.TMP_DIRECTORY, name + ENDING);
        }

        @Override
        public File getFile() {
            return file;
        }

        @Override
        public void optimize() throws IOException {
            Tools.nativeCall("jpegoptim", "--strip-all", file.getAbsolutePath());
        }
    }

    private static final class PNG implements ImageType {

        private final static String ENDING = ".png";
        private final File          file;

        public PNG(final String name) {
            file = new File(Config.TMP_DIRECTORY, name + ENDING);
        }

        @Override
        public File getFile() {
            return file;
        }

        @Override
        public void optimize() throws IOException {
            Tools.nativeCall("optipng", file.getAbsolutePath());
        }
    }
}
