// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package com.dom_distiller.client;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gwt.core.client.JsArray;

import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.MetaElement;
import com.google.gwt.dom.client.Node;
import com.google.gwt.dom.client.NodeList;

import org.timepedia.exporter.client.Export;
import org.timepedia.exporter.client.Exportable;

/**
 * This class recognizes and parses the Open Graph Protocol markup tags and returns the properties
 * that matter to distilled content.
 * First, it extracts the prefix and/or xmlns attributes from the HTML or HEAD tags to determine the
 * prefixes that will be used for the procotol.  If no prefix is specified, we fall back to the
 * commonly used ones, e.g. "og".  Then, it scans the OpenGraph Protocol <meta> elements that we
 * care about, extracts their content, and stores them semantically, i.e. taking into consideration
 * arrays, structures, and object types.  Callers call get* to access these properties.
 * The properties we care about are:
 * - 4 required properties: title, type, image, url.
 * - 2 optional properties: description, site_name.
 * - image structured properties: image:url, image:secure_url, image:type, image:width, image:height
 * - profile object properties: first_name, last_name
 * - article object properties: section, published_time, modified_time, expiration_time, author;
 *                              each author is a URL to the author's profile.
 */
@Export()
public class OpenGraphProtocolParser implements Exportable {
    private static final String TITLE_PROP = "title";
    private static final String TYPE_PROP = "type";
    private static final String IMAGE_PROP = "image";
    private static final String URL_PROP = "url";
    private static final String DESCRIPTION_PROP = "description";
    private static final String SITE_NAME_PROP = "site_name";
    private static final String IMAGE_STRUCT_PROP_PFX = "image:";
    private static final String IMAGE_URL_PROP = "image:url";
    private static final String IMAGE_SECURE_URL_PROP = "image:secure_url";
    private static final String IMAGE_TYPE_PROP = "image:type";
    private static final String IMAGE_WIDTH_PROP = "image:width";
    private static final String IMAGE_HEIGHT_PROP = "image:height";
    private static final String PROFILE_FIRSTNAME_PROP = "first_name";
    private static final String PROFILE_LASTNAME_PROP = "last_name";
    private static final String ARTICLE_SECTION_PROP = "section";
    private static final String ARTICLE_PUBLISHED_TIME_PROP = "published_time";
    private static final String ARTICLE_MODIFIED_TIME_PROP = "modified_time";
    private static final String ARTICLE_EXPIRATION_TIME_PROP = "expiration_time";
    private static final String ARTICLE_AUTHOR_PROP = "author";

    private static final String PROFILE_OBJTYPE = "profile";
    private static final String ARTICLE_OBJTYPE = "article";

    private final Map<String, String> mPropertyTable;
    private final Map<Prefix, String> mPrefixes;
    private final ImageParser mImageParser = new ImageParser();
    private final ProfileParser mProfileParser = new ProfileParser();
    private final ArticleParser mArticleParser = new ArticleParser();

    private enum Prefix {
        OG,
        PROFILE,
        ARTICLE,
    }

    private class PropertyRecord {
        private String mName = null;
        private Prefix mPrefix;
        private Parser mParser = null;

        PropertyRecord(String name, Prefix prefix, Parser parser) {
            mName = name;
            mPrefix = prefix;
            mParser = parser;
        }
    }

    private final PropertyRecord[] mProperties = {
        new PropertyRecord(TITLE_PROP, Prefix.OG, null),
        new PropertyRecord(TYPE_PROP, Prefix.OG, null),
        new PropertyRecord(URL_PROP, Prefix.OG, null),
        new PropertyRecord(DESCRIPTION_PROP, Prefix.OG, null),
        new PropertyRecord(SITE_NAME_PROP, Prefix.OG, null),
        new PropertyRecord(IMAGE_PROP, Prefix.OG, mImageParser),
        new PropertyRecord(IMAGE_STRUCT_PROP_PFX, Prefix.OG, mImageParser),
        new PropertyRecord(PROFILE_FIRSTNAME_PROP, Prefix.PROFILE, mProfileParser),
        new PropertyRecord(PROFILE_LASTNAME_PROP, Prefix.PROFILE, mProfileParser),
        new PropertyRecord(ARTICLE_SECTION_PROP, Prefix.ARTICLE, mArticleParser),
        new PropertyRecord(ARTICLE_PUBLISHED_TIME_PROP, Prefix.ARTICLE, mArticleParser),
        new PropertyRecord(ARTICLE_MODIFIED_TIME_PROP, Prefix.ARTICLE, mArticleParser),
        new PropertyRecord(ARTICLE_EXPIRATION_TIME_PROP, Prefix.ARTICLE, mArticleParser),
        new PropertyRecord(ARTICLE_AUTHOR_PROP, Prefix.ARTICLE, mArticleParser),
    };

    // TODO(kuan): this class is not exported yet, and hence not accessible from native javascript.
    // If export is needed, move it to a file of its own, because GWT doesn't seem to allow children
    // of Exportable classes to implement Exportable.
    public class Image {
        public String image = null;
        public String url = null;
        public String secureUrl = null;
        public String type = null;
        public int width = 0;
        public int height = 0;
    }

    // TODO(kuan): same as for class Image above.
    public class Article {
        public String publishedTime = null;
        public String modifiedTime = null;
        public String expirationTime = null;
        public String section = null;
        public String[] authors = null;
    }

    /**
     * OpenGraph Protocol prefixes are determined and properties are parsed.  Returns the
     * OpenGraphProtocolParser object if the properties conform to the protocol, i.e. all required
     * properties exist.  Otherwise, return null.
     *
     */
    public static OpenGraphProtocolParser parse(Element root) {
        try {
            return new OpenGraphProtocolParser(root);
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Returns the required "title" of the document.
     */
    public String getTitle() {
        return getPropertyContent(TITLE_PROP);
    }

    /**
     * Returns the required "type" of the document.
     */
    public String getType() {
        return getPropertyContent(TYPE_PROP);
    }

    /**
     * Returns the required "url" of the document.
     */
    public String getUrl() {
        return getPropertyContent(URL_PROP);
    }

    /**
     * Returns the structured properties of all "image" structures.  Each "image" structure consists
     * of image, image:url, image:secure_url, image:type, image:width, and image:height.
     */
    public Image[] getImages() {
        return mImageParser.getImages();
    }

    /**
     * Returns the optional "description" of the document.
     */
    public String getDescription() {
        return getPropertyContent(DESCRIPTION_PROP);
    }

    /**
     * Returns the optional "site_name" of the document.
     */
    public String getSiteName() {
        return getPropertyContent(SITE_NAME_PROP);
    }

    /**
     * Returns the concatenated first_name and last_name (delimited by a whitespace) of the
     * "profile" object when value of "og:type" is "profile".
     */
    public String getProfile() {
        return mProfileParser.getFullName(mPropertyTable);
    }

    /**
     * Returns the properties of the "article" object when value of "og:type" is "article".  The
     * properties are published_time, modified_time and expiration_time, section, and a list of URLs
     * to each author's profile.
     */
    public Article getArticle() {
        Article article = new Article();
        article.publishedTime = getPropertyContent(ARTICLE_PUBLISHED_TIME_PROP);
        article.modifiedTime = getPropertyContent(ARTICLE_MODIFIED_TIME_PROP);
        article.expirationTime = getPropertyContent(ARTICLE_EXPIRATION_TIME_PROP);
        article.section = getPropertyContent(ARTICLE_SECTION_PROP);
        article.authors = mArticleParser.getAuthors();

        if (article.section == null && article.publishedTime == null &&
                article.modifiedTime == null && article.expirationTime == null &&
                article.authors == null) {
            return null;
        }

        return article;
    }

    /**
     * The object that has successfully extracted OpenGraphProtocol markup information from |root|.
     *
     * @throws Exception if the properties do not conform to the procotol i.e. not all required
     * properties exist.
     */
    private OpenGraphProtocolParser(Element root) throws Exception {
        mPropertyTable = new HashMap<String, String>();
        mPrefixes = new EnumMap<Prefix, String>(Prefix.class);

        findPrefixes(root);
        parseMetaTags(root);

        mImageParser.verifyImages();

        String prefix = mPrefixes.get(Prefix.OG) + ":";
        if (getTitle() == null)
            throw new Exception("Required \"" + prefix + "title\" property is missing.");
        if (getType() == null)
            throw new Exception("Required \"" + prefix + "type\" property is missing.");
        if (getUrl() == null)
            throw new Exception("Required \"" + prefix + "url\" property is missing.");
        if (getImages() == null)
            throw new Exception("Required \"" + prefix + "image\" property is missing.");
    }

    private void findPrefixes(Element root) {
        String prefixes = "";

        // See if HTML tag has "prefix" attribute.
        if (root.hasTagName("HTML")) prefixes = root.getAttribute("prefix");

        // Otherwise, see if HEAD tag has "prefix" attribute.
        if (prefixes.isEmpty()) {
            NodeList<Element> heads = root.getElementsByTagName("HEAD");
            if (heads.getLength() == 1)
                prefixes = heads.getItem(0).getAttribute("prefix");
        }

        // If there's "prefix" attribute, its value is something like
       // "og: http://ogp.me/ns# profile: http://og.me/ns/profile# article: http://ogp.me/ns/article#".
        if (!prefixes.isEmpty()) {
            final String ogpNSRegex = "((\\w+):\\s+(http:\\/\\/ogp.me\\/ns(\\/\\w+)*#))\\s*";
            Pattern pattern = Pattern.compile(ogpNSRegex, Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(prefixes);
            while (matcher.find()) {  // There could be multiple prefixes.
                setPrefixForObjectType(matcher.group(2), matcher.group(4)); 
            }
        } else {
            // Still no "prefix" attribute, see if HTMl tag has "xmlns" attributes e.g.:
            // - "xmlns:og="http://ogp.me/ns#"
            // - "xmlns:profile="http://ogp.me/ns/profile#"
            // - "xmlns:article="http://ogp.me/ns/article#".
            final String ogpNSRegex = "^http:\\/\\/ogp.me\\/ns(\\/\\w+)*#";
            final JsArray<Node> attributes = getAttributes(root);
            for (int i = 0; i < attributes.length(); i++) {
                final Node node = attributes.get(i);
                // Look for attribute name that starts with "xmlns:".
                String attributeName = node.getNodeName().toLowerCase();
                Pattern namePattern = Pattern.compile("^xmlns:(\\w+)", Pattern.CASE_INSENSITIVE);
                Matcher nameMatcher = namePattern.matcher(attributeName);
                if (!nameMatcher.find()) continue;

                // Extract OGP namespace URI from attribute value, if available.
                String attributeValue = node.getNodeValue();
                Pattern valuePattern = Pattern.compile(ogpNSRegex, Pattern.CASE_INSENSITIVE);
                Matcher valueMatcher = valuePattern.matcher(attributeValue);
                if (valueMatcher.find()) {
                    setPrefixForObjectType(nameMatcher.group(1), valueMatcher.group(1)); 
                }
            }
        }

        setDefaultPrefixes();
    }

    private void setPrefixForObjectType(String prefix, String objTypeWithLeadingSlash) {
        if (objTypeWithLeadingSlash == null || objTypeWithLeadingSlash.isEmpty()) {
            mPrefixes.put(Prefix.OG, prefix);
            return;
        }

        // Remove leading '/'.
        String objType = objTypeWithLeadingSlash.substring("/".length());
        if (objType.equals(PROFILE_OBJTYPE)) {
            mPrefixes.put(Prefix.PROFILE, prefix);
        } else if (objType.equals(ARTICLE_OBJTYPE)) {
            mPrefixes.put(Prefix.ARTICLE, prefix);
        }
    }

    private void setDefaultPrefixes() {
        // For any unspecified prefix, use common ones:
        // - "og": http://ogp.me/ns#
        // - "profile": http://ogp.me/ns/profile#
        // - "article": http://ogp.me/ns/article#.
        if (mPrefixes.get(Prefix.OG) == null) mPrefixes.put(Prefix.OG, "og");
        if (mPrefixes.get(Prefix.PROFILE) == null) mPrefixes.put(Prefix.PROFILE, PROFILE_OBJTYPE);
        if (mPrefixes.get(Prefix.ARTICLE) == null) mPrefixes.put(Prefix.ARTICLE, ARTICLE_OBJTYPE);
    }

    private void parseMetaTags(Element root) {
        NodeList<Element> allMeta = root.getElementsByTagName("META");
        for (int i = 0; i < allMeta.getLength(); i++) {
            MetaElement meta = MetaElement.as(allMeta.getItem(i));
            String property = meta.getAttribute("property").toLowerCase();

            // Only store properties that we care about for distillation.
            for (int j = 0; j < mProperties.length; j++) {
                String prefixWithColon = mPrefixes.get(mProperties[j].mPrefix) + ":";
                // Note that property.equals() won't work here because |mProperties| uses "image:"
                // (IMAGE_STRUCT_PROP_PFX) for all image structured properties, so as to prevent
                // repetitive property name comparison - here and then again in ImageParser.
                if (!property.startsWith(prefixWithColon + mProperties[j].mName)) continue;
                property = property.substring(prefixWithColon.length());

                boolean addProperty = true;
                if (mProperties[j].mParser != null) {
                    addProperty = mProperties[j].mParser.parse(property, meta.getContent(),
                            mPropertyTable);
                }
                if (addProperty) mPropertyTable.put(mProperties[j].mName, meta.getContent());
            }
        }
    }

    private String getPropertyContent(String property) {
        if (!mPropertyTable.containsKey(property))
            return null;
        return mPropertyTable.get(property);
    }

    /**
     * Called when parsing a stateful property, returns true if the property and its content should
     * be added to the property table.
     */
    private interface Parser {
        public boolean parse(String property, String content, Map<String, String> propertyTable);
    }

    private class ImageParser implements Parser {
        private final String[] mProperties = {
            IMAGE_PROP,
            IMAGE_URL_PROP,
            IMAGE_SECURE_URL_PROP,
            IMAGE_TYPE_PROP,
            IMAGE_WIDTH_PROP,
            IMAGE_HEIGHT_PROP,
        };

        private final List<String[]> mImages;

        @Override
        public boolean parse(String property, String content, Map<String, String> propertyTable) {
            String[] image = null;

            if (property.equals(IMAGE_PROP)) {  // Root property means end of current structure.
                image = new String[mProperties.length];
                image[0] = content;
                mImages.add(image);
            } else {  // Non-root property means it's for current structure.
                if (mImages.isEmpty()) {  // No image yet, create new one.
                    image = new String[mProperties.length];
                    mImages.add(image);
                } else {  // Property is for current structure, i.e. last in list.
                    image = mImages.get(mImages.size() - 1);
                }
                // 0th property is IMAGE_PROP, which is already handled above.
                for (int i = 1; i < mProperties.length; i++) {
                    if (property.equals(mProperties[i])) {
                        image[i] = content;
                        break;
                    }
                }
            }
    
            return false;   // Don't insert into property table.
        }

        private ImageParser() {
            mImages = new ArrayList<String[]>();
        }

        private Image[] getImages() {
            if (mImages.isEmpty()) return null;

            Image[] imagesOut = new Image[mImages.size()];
            for (int i = 0; i < mImages.size(); i++) {
                String[] imageIn = mImages.get(i);
                Image imageOut = new Image();
                imagesOut[i] = imageOut;
                imageOut.image = imageIn[0];
                imageOut.url = imageIn[1];
                imageOut.secureUrl = imageIn[2];
                imageOut.type = imageIn[3];
                try {
                    imageOut.width = Integer.parseInt(imageIn[4], 10);
                } catch (NumberFormatException e) {
                }
                try {
                    imageOut.height = Integer.parseInt(imageIn[5], 10);
                } catch (NumberFormatException e) {
                }
            }
            return imagesOut;
        }

        private void verifyImages() {
            if (mImages.isEmpty()) return;

            // Remove any image without the required root IMAGE_PROP.
            for (int i = mImages.size() - 1; i >= 0; i--) {
                String image_prop = mImages.get(i)[0];
                if (image_prop == null || image_prop.isEmpty()) mImages.remove(i);
            }
        }
    }

    private class ProfileParser implements Parser {
        private boolean mCheckedType;
        private boolean mIsProfileType;

        @Override
        public boolean parse(String property, String content, Map<String, String> propertyTable) {
            if (!mCheckedType) {  // Check that "type" property exists and has "profile" value.
                String requiredType = propertyTable.get(TYPE_PROP);
                mIsProfileType = requiredType != null &&
                        requiredType.equalsIgnoreCase(PROFILE_OBJTYPE);
                mCheckedType = true;
            }

            return mIsProfileType;  // If it's profile object, insert into property table.
        }

        private ProfileParser() {
            mCheckedType = false;
            mIsProfileType = false;
        }

        private String getFullName(Map<String, String> propertyTable) {
            if (!mIsProfileType) return null;

            String fullname = propertyTable.get(PROFILE_FIRSTNAME_PROP);
            if (fullname == null) fullname = "";
            String lastname = propertyTable.get(PROFILE_LASTNAME_PROP);
            if (lastname != null && !fullname.isEmpty() && !lastname.isEmpty()) fullname += " ";
            fullname += lastname;
            return fullname;
        }
    }

    private class ArticleParser implements Parser {
        private boolean mCheckedType;
        private boolean mIsArticleType;
        private final List<String> mAuthors;

        @Override
        public boolean parse(String property, String content, Map<String, String> propertyTable) {
            if (!mIsArticleType) {  // Check that "type" property exists and has "article" value.
                String requiredType = propertyTable.get(TYPE_PROP);
                mIsArticleType = requiredType != null &&
                        requiredType.equalsIgnoreCase(ARTICLE_OBJTYPE);
                mCheckedType = true;
            }
            if (!mIsArticleType) return false;

            // "author" property is an array of URLs, so we special-handle it here.
            if (property.equals(ARTICLE_AUTHOR_PROP)) {
                mAuthors.add(content);
                return false; // We've handled it, don't insert into propertyTable.
            }
                
            // Other properties are stateless, so inserting into property table is good enough.
            return true;
        }

        private ArticleParser() {
            mCheckedType = false;
            mIsArticleType = false;
            mAuthors = new ArrayList<String>();
        }

        private String[] getAuthors() {
            return mAuthors.isEmpty() ? null : mAuthors.toArray(new String[mAuthors.size()]);
        }
    }

    // There's no GWT API to get all attributes of an element, so resort to javascript code.
    private static native JsArray<Node> getAttributes(Element elem) /*-{
        return elem.attributes;
    }-*/;
}