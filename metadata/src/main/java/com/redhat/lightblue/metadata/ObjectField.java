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
package com.redhat.lightblue.metadata;

import com.redhat.lightblue.metadata.types.ObjectType;
import com.redhat.lightblue.util.Path;

import java.util.Iterator;

public class ObjectField extends Field {
    private static final long serialVersionUID = 1L;

    private final Fields fields;

    public ObjectField(String name) {
        super(name, ObjectType.TYPE);
        fields = new Fields(this);
    }

    public Fields getFields() {
        return fields;
    }

    @Override
    public boolean hasChildren() {
        return fields.getNumChildren() > 0;
    }

    @Override
    public Iterator<? extends FieldTreeNode> getChildren() {
        return fields.getFields();
    }

    @Override
    public FieldTreeNode resolve(Path p, int level) {
        if (p.numSegments() == level) {
            return this;
        } else if (Path.PARENT.equals(p.head(level))) {
            return this.getParent().resolve(p, level + 1);
        } else {
            return fields.resolve(p, level);
        }
    }
}
