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
package org.jlange.proxy.plugin.preloadDectector.tag;

import java.util.LinkedList;
import java.util.List;

import org.htmlparser.nodes.TagNode;

public class ImgTag implements Tag {

    @Override
    public String getTagName() {
        return "IMG";
    }

    @Override
    public List<String> getUrls(TagNode tag) {
        List<String> urls = new LinkedList<String>();

        urls.add(tag.getAttribute("src"));

        return urls;
    }

}
