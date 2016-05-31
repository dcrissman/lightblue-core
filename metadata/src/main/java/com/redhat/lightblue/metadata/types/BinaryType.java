/*
 Copyright 2013 Red Hat, Inc. and/or its affiliates.

 This file is part of lightblue.

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.redhat.lightblue.metadata.types;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.NullNode;
import com.redhat.lightblue.metadata.MetadataConstants;
import com.redhat.lightblue.metadata.Type;
import com.redhat.lightblue.util.Error;

import java.io.Serializable;

public final class BinaryType implements Type, Serializable {

    private static final long serialVersionUID = 1l;

    public static final Type TYPE = new BinaryType();
    public static final String NAME = "binary";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public boolean supportsEq() {
        return false;
    }

    @Override
    public boolean supportsOrdering() {
        return false;
    }

    @Override
    public int compare(Object v1, Object v2) {
        throw new UnsupportedOperationException(MetadataConstants.ERR_COMPARE_NOT_SUPPORTED);
    }

    @Override
    public Object cast(Object obj) {
        byte[] ret = null;
        if (obj != null) {
            if (obj.getClass().isArray()) {
                Class<?> component = obj.getClass().getComponentType();
                if (component.equals(byte.class)) {
                    ret = (byte[]) obj;
                } else {
                    throw Error.get(NAME, MetadataConstants.ERR_INCOMPATIBLE_VALUE, obj.toString());
                }
            } else {
                throw Error.get(NAME, MetadataConstants.ERR_INCOMPATIBLE_VALUE, obj.toString());
            }
        }

        return ret;
    }

    @Override
    public JsonNode toJson(JsonNodeFactory factory, Object obj) {
        return factory.binaryNode((byte[]) cast(obj));
    }

    @Override
    public Object fromJson(JsonNode node) {
        if (node == null || node instanceof NullNode) {
            return null;
        } else if (node.isValueNode()) {
            try {
                return node.binaryValue();
            } catch (Exception e) {
            }
        }
        throw Error.get(NAME, MetadataConstants.ERR_INCOMPATIBLE_VALUE, node.toString());
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof BinaryType;
    }

    @Override
    public int hashCode() {
        return 8;
    }

    @Override
    public String toString() {
        return NAME;
    }

    private BinaryType() {
    }
}
