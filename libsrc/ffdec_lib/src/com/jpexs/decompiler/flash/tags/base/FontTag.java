/*
 *  Copyright (C) 2010-2015 JPEXS, All rights reserved.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3.0 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.
 */
package com.jpexs.decompiler.flash.tags.base;

import com.jpexs.decompiler.flash.SWF;
import com.jpexs.decompiler.flash.configuration.Configuration;
import com.jpexs.decompiler.flash.exporters.commonshape.Matrix;
import com.jpexs.decompiler.flash.exporters.commonshape.SVGExporter;
import com.jpexs.decompiler.flash.exporters.shape.CanvasShapeExporter;
import com.jpexs.decompiler.flash.helpers.FontHelper;
import com.jpexs.decompiler.flash.tags.DefineFontNameTag;
import com.jpexs.decompiler.flash.tags.DefineText2Tag;
import com.jpexs.decompiler.flash.tags.DefineTextTag;
import com.jpexs.decompiler.flash.tags.Tag;
import com.jpexs.decompiler.flash.types.ColorTransform;
import com.jpexs.decompiler.flash.types.GLYPHENTRY;
import com.jpexs.decompiler.flash.types.RECT;
import com.jpexs.decompiler.flash.types.SHAPE;
import com.jpexs.decompiler.flash.types.TEXTRECORD;
import com.jpexs.decompiler.flash.types.shaperecords.SHAPERECORD;
import com.jpexs.helpers.ByteArrayRange;
import com.jpexs.helpers.SerializableImage;
import java.awt.Color;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphMetrics;
import java.awt.font.GlyphVector;
import java.awt.geom.Area;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 *
 * @author JPEXS
 */
public abstract class FontTag extends CharacterTag implements AloneTag, DrawableTag {

    public static final int PREVIEWSIZE = 500;

    public FontTag(SWF swf, int id, String name, ByteArrayRange data) {
        super(swf, id, name, data);
    }

    public abstract int getFontId();

    public abstract List<SHAPE> getGlyphShapeTable();

    public abstract void addCharacter(char character, Font font);

    public abstract void setAdvanceValues(Font font);

    public abstract char glyphToChar(int glyphIndex);

    public abstract int charToGlyph(char c);

    public abstract double getGlyphAdvance(int glyphIndex);

    public abstract int getGlyphKerningAdjustment(int glyphIndex, int nextGlyphIndex);

    public abstract int getCharKerningAdjustment(char c1, char c2);

    public abstract int getGlyphWidth(int glyphIndex);

    public abstract String getFontNameIntag();

    public abstract boolean isSmall();

    public abstract boolean isBold();

    public abstract boolean isItalic();

    public abstract boolean isSmallEditable();

    public abstract boolean isBoldEditable();

    public abstract boolean isItalicEditable();

    public abstract void setSmall(boolean value);

    public abstract void setBold(boolean value);

    public abstract void setItalic(boolean value);

    public abstract double getDivider();

    public abstract int getAscent();

    public abstract int getDescent();

    public abstract int getLeading();

    public String getFontName() {
        DefineFontNameTag fontNameTag = getFontNameTag();
        if (fontNameTag == null) {
            return getFontNameIntag();
        }
        return fontNameTag.fontName;
    }

    public String getFontCopyright() {
        DefineFontNameTag fontNameTag = getFontNameTag();
        if (fontNameTag == null) {
            return "";
        }
        return fontNameTag.fontCopyright;
    }

    public static Map<String, Map<String, Font>> installedFontsByFamily;

    public static Map<String, Font> installedFontsByName;

    public static String defaultFontName;

    static {
        reload();
    }

    public boolean hasLayout() {
        return false;
    }

    public boolean containsChar(char character) {
        return charToGlyph(character) > -1;
    }

    public int getFontStyle() {
        int fontStyle = 0;
        if (isBold()) {
            fontStyle |= Font.BOLD;
        }
        if (isItalic()) {
            fontStyle |= Font.ITALIC;
        }
        return fontStyle;
    }

    public abstract String getCharacters(List<Tag> tags);

    @Override
    public String getName() {
        String nameAppend = "";
        if (exportName != null) {
            nameAppend = ": " + exportName;
        }
        if (className != null) {
            nameAppend = ": " + className;
        }
        String fontName = getFontNameIntag();
        if (fontName != null) {
            nameAppend = ": " + fontName;
        }
        return tagName + " (" + getCharacterId() + nameAppend + ")";
    }

    @Override
    public String getExportFileName() {
        String result = super.getExportFileName();
        String fontName = getFontNameIntag();
        if (fontName != null) {
            fontName = fontName.replace(" ", "_");
        }
        return result + (fontName != null ? "_" + fontName : "");
    }

    public String getSystemFontName() {
        Map<String, String> fontPairs = Configuration.getFontToNameMap();
        String key = swf.getShortFileName() + "_" + getFontId() + "_" + getFontNameIntag();
        if (fontPairs.containsKey(key)) {
            return fontPairs.get(key);
        }
        key = getFontNameIntag();
        if (fontPairs.containsKey(key)) {
            return fontPairs.get(key);
        }
        return defaultFontName;
    }

    protected void shiftGlyphIndices(int fontId, int startIndex) {
        for (Tag t : swf.tags) {
            List<TEXTRECORD> textRecords = null;
            if (t instanceof DefineTextTag) {
                textRecords = ((DefineTextTag) t).textRecords;
            } else if (t instanceof DefineText2Tag) {
                textRecords = ((DefineText2Tag) t).textRecords;
            }

            if (textRecords != null) {
                int curFontId = 0;
                for (TEXTRECORD tr : textRecords) {
                    if (tr.styleFlagsHasFont) {
                        curFontId = tr.fontId;
                    }

                    if (curFontId != fontId) {
                        continue;
                    }

                    for (GLYPHENTRY en : tr.glyphEntries) {
                        if (en == null) { //Currently edited
                            continue;
                        }
                        if (en.glyphIndex >= startIndex) {
                            en.glyphIndex++;
                        }
                    }

                    t.setModified(true);
                }
            }
        }
    }

    public static float getSystemFontAdvance(String fontName, int fontStyle, int fontSize, Character character, Character nextCharacter) {
        return getSystemFontAdvance(new Font(fontName, fontStyle, fontSize), character, nextCharacter);
    }

    public static float getSystemFontAdvance(Font aFont, Character character, Character nextCharacter) {
        GlyphVector gv = aFont.createGlyphVector(new FontRenderContext(aFont.getTransform(), true, true), "" + character + (nextCharacter == null ? "" : nextCharacter));
        GlyphMetrics gm = gv.getGlyphMetrics(0);
        return gm.getAdvanceX();
    }

    public static void reload() {
        installedFontsByFamily = FontHelper.getInstalledFonts();
        installedFontsByName = new HashMap<>();

        for (String fam : installedFontsByFamily.keySet()) {
            for (String nam : installedFontsByFamily.get(fam).keySet()) {
                installedFontsByName.put(nam, installedFontsByFamily.get(fam).get(nam));
            }
        }

        if (installedFontsByFamily.containsKey("Times New Roman")) {
            defaultFontName = "Times New Roman";
        } else if (installedFontsByFamily.containsKey("Arial")) {
            defaultFontName = "Arial";
        } else {
            defaultFontName = installedFontsByFamily.keySet().iterator().next();
        }
    }

    public static String getFontNameWithFallback(String fontName) {
        if (installedFontsByFamily.containsKey(fontName)) {
            return fontName;
        }
        if (installedFontsByFamily.containsKey("Times New Roman")) {
            return "Times New Roman";
        }
        if (installedFontsByFamily.containsKey("Arial")) {
            return "Arial";
        }

        //First font
        return installedFontsByFamily.keySet().iterator().next();
    }

    public static String isFontFamilyInstalled(String fontFamily) {
        if (installedFontsByFamily.containsKey(fontFamily)) {
            return fontFamily;
        }
        if (fontFamily.contains("_")) {
            String beforeUnderscore = fontFamily.substring(0, fontFamily.indexOf('_'));
            if (installedFontsByFamily.containsKey(beforeUnderscore)) {
                return beforeUnderscore;
            }
        }
        return null;
    }

    public static String findInstalledFontName(String fontName) {
        if (installedFontsByName.containsKey(fontName)) {
            return fontName;
        }
        if (fontName != null && fontName.contains("_")) {
            String beforeUnderscore = fontName.substring(0, fontName.indexOf('_'));
            if (installedFontsByName.containsKey(beforeUnderscore)) {
                return beforeUnderscore;
            }
        }
        return defaultFontName;
    }

    @Override
    public void toImage(int frame, int time, int ratio, RenderContext renderContext, SerializableImage image, Matrix transformation, ColorTransform colorTransform) {
        SHAPERECORD.shapeListToImage(swf, getGlyphShapeTable(), image, frame, Color.black, colorTransform);
    }

    @Override
    public void toSVG(SVGExporter exporter, int ratio, ColorTransform colorTransform, int level, double zoom) {
    }

    @Override
    public int getNumFrames() {
        int frameCount = (getGlyphShapeTable().size() - 1) / SHAPERECORD.MAX_CHARACTERS_IN_FONT_PREVIEW + 1;
        if (frameCount < 1) {
            frameCount = 1;
        }
        return frameCount;
    }

    @Override
    public boolean isSingleFrame() {
        return true;
    }

    @Override
    public Shape getOutline(int frame, int time, int ratio, RenderContext renderContext, Matrix transformation) {
        RECT r = getRect();
        return new Area(new Rectangle(r.Xmin, r.Ymin, r.getWidth(), r.getHeight()));
    }

    @Override
    public RECT getRect() {
        return getRect(null); // parameter not used
    }

    @Override
    public RECT getRect(Set<BoundedTag> added) {
        return new RECT(0, (int) (PREVIEWSIZE * SWF.unitDivisor), 0, (int) (PREVIEWSIZE * SWF.unitDivisor));
    }

    @Override
    public String getCharacterExportFileName() {
        return super.getCharacterExportFileName() + "_" + getFontNameIntag();
    }

    public DefineFontNameTag getFontNameTag() {
        for (Tag t : swf.tags) {
            if (t instanceof DefineFontNameTag) {
                DefineFontNameTag dfn = (DefineFontNameTag) t;
                if (dfn.fontId == getFontId()) {
                    return dfn;
                }
            }
        }
        return null;
    }

    public String getCopyright() {
        DefineFontNameTag dfn = getFontNameTag();
        if (dfn == null) {
            return null;
        }
        return dfn.fontCopyright;
    }

    @Override
    public String toHtmlCanvas(double unitDivisor) {
        List<SHAPE> shapes = getGlyphShapeTable();
        StringBuilder sb = new StringBuilder();
        sb.append("\tdefaultFill = textColor;\r\n");
        sb.append("\tswitch(ch){\r\n");
        for (int i = 0; i < shapes.size(); i++) {
            char c = glyphToChar(i);
            String cs = "" + c;
            cs = cs.replace("\\", "\\\\").replace("\"", "\\\"");
            sb.append("\t\tcase \"").append(cs).append("\":\r\n");
            CanvasShapeExporter exporter = new CanvasShapeExporter(null, unitDivisor, swf, shapes.get(i), new ColorTransform(), 0, 0);
            exporter.export();
            sb.append("\t\t").append(exporter.getShapeData().replaceAll("\r\n", "\r\n\t\t"));
            sb.append("\tbreak;\r\n");
        }
        sb.append("\t}\r\n");
        return sb.toString();
    }

    public RECT getGlyphBounds(int glyphIndex) {
        return getGlyphShapeTable().get(glyphIndex).getBounds();
    }

    public FontTag toClassicFont() {
        return this;
    }
}
