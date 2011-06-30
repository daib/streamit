/*
 * Copyright (C) 1990-2001 DMS Decision Management Systems Ges.m.b.H.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 *
 * $Id: JavadocComment.java,v 1.1 2009/02/24 18:15:16 hormati Exp $
 */

package at.dms.compiler.tools.common;

/**
 * A simple character constant
 */
public class JavadocComment extends JavaStyleComment {

    // ----------------------------------------------------------------------
    // CONSTRUCTORS
    // ----------------------------------------------------------------------

    /**
     * Construct a node in the parsing tree
     * @param   text        the string representation of this comment
     */
    public JavadocComment(String text, boolean spaceBefore, boolean spaceAfter) {
        super(text, false, spaceBefore, spaceAfter);
    }

    // ----------------------------------------------------------------------
    // ACCESSORS
    // ----------------------------------------------------------------------

    /**
     * Return if this javadoc comment contains a deprecated clause
     */
    public boolean isDeprecated() {
        return text.indexOf("@deprecated") >= 0;
    }

    /**
     *
     */
    public String getParams() {
        return text;
    }
}
