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
package org.jlange.proxy.plugin.preloadDectector;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.htmlparser.nodes.TagNode;
import org.jlange.proxy.plugin.preloadDectector.tag.ImgTag;
import org.jlange.proxy.plugin.preloadDectector.tag.Tag;

public class TagFactory {

    private static TagFactory instance;

    public static TagFactory instance() {
        if (TagFactory.instance == null) TagFactory.instance = new TagFactory();
        return TagFactory.instance;
    }

    private final Map<String, Tag> tagMap;

    private TagFactory() {
        List<Tag> instanceTypes = new LinkedList<Tag>();

        instanceTypes.add(new ImgTag());

        tagMap = buildTagMap(instanceTypes);
    }

    private Map<String, Tag> buildTagMap(List<Tag> instanceTypes) {
        Map<String, Tag> tagMap = new HashMap<String, Tag>();
        for (Tag tag : instanceTypes)
            tagMap.put(tag.getTagName(), tag);
        return tagMap;
    }

    // TODO: Use weights for link prefetching
    // Some tags will be loaded for sure like IMG or linked CSS and JS in the
    // header, so the weight can be 100%. There may be more than just these,
    // like ANCHORS or sth. else where loading this link from the browser
    // automatically or by the user has a probability between 0% and 100%. This
    // threshold can be configured then.
    // Be aware, prefetching ANCHORS can cause actions that are linked with
    // these.
    public List<String> getUrls(TagNode node) {
        Tag tag = tagMap.get(node.getTagName());

        List<String> urls = new LinkedList<String>();

        if (tag != null) urls = tag.getUrls(node);

        return urls;
    }
}
