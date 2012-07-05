package org.jlange.proxy.plugin.response;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.SimpleTimeZone;

import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.util.CharsetUtil;
import org.jlange.proxy.plugin.ResponsePlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WeakCacheHeader implements ResponsePlugin {

    private static final Logger               log = LoggerFactory.getLogger(WeakCacheHeader.class);
    private final Map<String, HttpIdentifier> hashes;
    private final SimpleDateFormat            sdf;

    public WeakCacheHeader() {
        sdf = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH);
        sdf.setTimeZone(new SimpleTimeZone(0, "GMT"));
        hashes = new HashMap<String, HttpIdentifier>();
    }

    @Override
    public Boolean isApplicable(final HttpRequest request, final HttpResponse response) {
        // only GET requests are allowed to cache
        if (!request.getMethod().equals(HttpMethod.GET))
            return false;

        // cache only valid responses
        if (!response.getStatus().equals(HttpResponseStatus.OK))
            return false;

        // do not cache stuff marked as private
        String cacheControl = response.getHeader(HttpHeaders.Names.CACHE_CONTROL);
        if (cacheControl != null) {
            if (cacheControl.contains(HttpHeaders.Values.PRIVATE))
                return false;
            if (cacheControl.contains(HttpHeaders.Values.NO_CACHE))
                return false;
        }

        // do not use proxy cache header mechanism if there is already one
        if (response.getHeader(HttpHeaders.Names.LAST_MODIFIED) != null)
            return false;

        return true;
    }

    @Override
    public synchronized void run(final HttpRequest request, final HttpResponse response) {

        String currentUri = getUri(request);
        Date clientsLastCache = getLastSeen(request);
        Integer currentHash = getHash(response);

        HttpIdentifier cachedIdentifier = hashes.get(currentUri);

        if (log.isDebugEnabled() && cachedIdentifier != null)
            log.debug("\nRequest\n\tURI\t{}\n\tDate\t{}\n\nResponse\n\tHash\t{}\n\nIdentifier\n\tHash\t{}\n\tDate\t{}\n\n", new Object[] {
                    currentUri, clientsLastCache, currentHash, cachedIdentifier.getHash(), cachedIdentifier.getDate() });

        if (cachedIdentifier == null) {
            log.info("cache miss - {}", currentUri);
            cachedIdentifier = new HttpIdentifier(currentHash, new Date());
            hashes.put(currentUri, cachedIdentifier);
            responseNotCached(response, cachedIdentifier.getDate());
        } else if (cachedIdentifier.getHash().intValue() != currentHash.intValue()) {
            log.info("cache invalid - {} (before: {}, after {})", new Object[] { currentUri, cachedIdentifier.getHash(), currentHash });
            cachedIdentifier.setHash(currentHash);
            cachedIdentifier.setDate(new Date());
            responseNotCached(response, cachedIdentifier.getDate());
        } else if (HttpHeaders.getHeader(request, HttpHeaders.Names.CACHE_CONTROL, "").contains(HttpHeaders.Values.NO_CACHE)) {
            log.info("cache valid but client wants explicit no cache - {}", currentUri);
            responseNotCached(response, cachedIdentifier.getDate());
        } else if (clientsLastCache == null
                && HttpHeaders.getHeader(request, HttpHeaders.Names.CACHE_CONTROL, "").contains(HttpHeaders.Values.MAX_AGE)) {
            // the proxy just revalidated the response, so the age of the cache is 0
            log.info("cache hit - {}", currentUri);
            responseCached(response);
        } else if (clientsLastCache == null || cachedIdentifier.getDate().compareTo(clientsLastCache) < 0) {
            log.info("cache valid but client is outdated - {}", currentUri);
            responseNotCached(response, cachedIdentifier.getDate());
        } else {
            log.info("cache hit - {}", currentUri);
            responseCached(response);
        }
    }

    private static String getUri(final HttpRequest request) {
        return request.getHeader(HttpHeaders.Names.HOST) + request.getUri();
    }

    private static Integer getHash(final HttpResponse response) {
        return response.getContent().toString(CharsetUtil.UTF_8).hashCode();
    }

    private void responseNotCached(final HttpResponse response, final Date lastUpdate) {
        response.setHeader(HttpHeaders.Names.LAST_MODIFIED, sdf.format(lastUpdate));
    }

    private void responseCached(final HttpResponse response) {
        response.setStatus(HttpResponseStatus.NOT_MODIFIED);
        response.setContent(ChannelBuffers.EMPTY_BUFFER);
        response.removeHeader(HttpHeaders.Names.CONTENT_ENCODING);
        response.removeHeader(HttpHeaders.Names.CONTENT_LENGTH);
    }

    private Date getLastSeen(final HttpRequest request) {
        Date lastSeen = null;
        String ifModifiedSince = request.getHeader(HttpHeaders.Names.IF_MODIFIED_SINCE);
        if (ifModifiedSince != null)
            try {
                lastSeen = sdf.parse(ifModifiedSince);
            } catch (ParseException e) {
                e.printStackTrace();
            }
        return lastSeen;
    }

    private class HttpIdentifier {
        private Integer hash;
        private Date    date;

        public HttpIdentifier(final Integer hash, final Date date) {
            setHash(hash);
            setDate(date);
        }

        public Integer getHash() {
            return hash;
        }

        public void setHash(final Integer hash) {
            this.hash = hash;
        }

        public Date getDate() {
            return date;
        }

        public void setDate(final Date date) {
            this.date = date;
        }
    }
}