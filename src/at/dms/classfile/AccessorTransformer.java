/*
 * Copyright (C) 1990-2001 DMS Decision Management Systems Ges.m.b.H.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 *
 * $Id: AccessorTransformer.java,v 1.1 2009/02/24 18:14:52 hormati Exp $
 */

package at.dms.classfile;

/**
 * Convert a generic instruction accessor to a specific type
 */
public interface AccessorTransformer {

    /**
     * Transforms the specified accessor.
     * @param   accessor        the accessor to transform
     * @param   container       the object which contains the accessor
     * @return  the transformed accessor
     */
    InstructionAccessor transform(InstructionAccessor accessor,
                                  AccessorContainer container)
        throws BadAccessorException;
}
