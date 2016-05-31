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

public abstract class ContainerType implements Type {
    private final String name;

    public ContainerType(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
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
        if (v1 == null) {
            if (v2 == null) {
                return 0;
            } else {
                return -1;
            }
        } else if (v2 == null) {
            return 1;
        } else {
            throw new UnsupportedOperationException(MetadataConstants.ERR_COMPARE_NOT_SUPPORTED);
        }
    }

    @Override
    public Object cast(Object v) {
        if (v == null) {
            return null;
        } else {
            throw new UnsupportedOperationException(MetadataConstants.ERR_CAST_NOT_SUPPORTED);
        }
    }

    @Override
    public JsonNode toJson(JsonNodeFactory factory, Object value) {
        if (value == null) {
            return factory.nullNode();
        } else {
            throw new UnsupportedOperationException(MetadataConstants.ERR_TO_JSON_NOT_SUPPORTED);
        }
    }

    @Override
    public Object fromJson(JsonNode value) {
        if (value == null || value instanceof NullNode) {
            return null;
        } else {
            throw new UnsupportedOperationException(MetadataConstants.ERR_FROM_JSON_NOT_SUPPORTED);
        }
    }

    @Override
    public boolean equals(Object obj) {
        return obj != null && obj.getClass().equals(this.getClass());
    }

    @Override
    public int hashCode() {
        return getName().hashCode();
    }
}
