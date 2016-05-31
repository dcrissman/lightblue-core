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

import com.redhat.lightblue.metadata.constraints.IdentityConstraint;
import com.redhat.lightblue.metadata.constraints.ArrayElementIdConstraint;
import com.redhat.lightblue.util.Error;
import com.redhat.lightblue.util.MutablePath;
import com.redhat.lightblue.util.Path;
import com.redhat.lightblue.util.JsonCompare;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Version specific bits of metadata.
 *
 * @author nmalik
 */
public class EntitySchema extends MetadataObject {
    private static final Logger LOGGER = LoggerFactory.getLogger(EntitySchema.class);

    private final String name;
    private Version version;
    private MetadataStatus status;
    private final ArrayList<StatusChange> statusChangeLog;
    //hooks
    private final EntityAccess access;
    private final ArrayList<EntityConstraint> constraints;
    private Fields fields;
    private FieldTreeNode fieldRoot;

    protected class RootNode implements FieldTreeNode {

        private static final long serialVersionUID = 1L;

        @Override
        public String getName() {
            return "";
        }

        @Override
        public Type getType() {
            return null;
        }

        @Override
        public boolean hasChildren() {
            return true;
        }

        @Override
        public Iterator<? extends FieldTreeNode> getChildren() {
            return fields.getFields();
        }

        @Override
        public FieldTreeNode resolve(Path p) {
            return fields.resolve(p);
        }

        @Override
        public FieldTreeNode resolve(Path p, int level) {
            return fields.resolve(p, level);
        }

        @Override
        public FieldTreeNode getParent() {
            return null;
        }

        @Override
        public Path getFullPath() {
            return Path.EMPTY;
        }

        @Override
        public MutablePath getFullPath(MutablePath mp) {
            return Path.EMPTY.mutableCopy();
        }
    };

    public EntitySchema(String name) {
        this.name = name;
        this.fieldRoot = new RootNode();
        this.fields = new Fields(fieldRoot);
        this.statusChangeLog = new ArrayList<>();
        this.access = new EntityAccess();
        this.constraints = new ArrayList<>();
    }

    /**
     * Copy ctor with shallow copy
     */
    protected EntitySchema(EntitySchema source) {
        this.name = source.name;
        this.version = source.version;
        this.status = source.status;
        this.statusChangeLog = source.statusChangeLog;
        this.access = source.access;
        this.constraints = source.constraints;
        this.fields = source.fields;
        this.fieldRoot = source.fieldRoot;
        super.shallowCopyFrom(source);
    }

    /**
     * Gets the value of name
     *
     * @return the value of name
     */
    public String getName() {
        return this.name;
    }

    /**
     * Return the status of this particular version of the entity
     */
    public MetadataStatus getStatus() {
        return status;
    }

    /**
     * Sets the status of this particular version of the entity
     */
    public void setStatus(MetadataStatus status) {
        this.status = status;
    }

    /**
     * Returns the status change log
     */
    @SuppressWarnings("unchecked")
    public List<StatusChange> getStatusChangeLog() {
        return (List<StatusChange>) statusChangeLog.clone();
    }

    /**
     * Sets the status change log
     */
    public void setStatusChangeLog(Collection<StatusChange> log) {
        statusChangeLog.clear();
        if (log != null) {
            statusChangeLog.addAll(log);
        }
    }

    /**
     * Gets the value of version
     *
     * @return the value of version
     */
    public Version getVersion() {
        return this.version;
    }

    /**
     * Sets the value of version
     *
     * @param argVersion Value to assign to this.version
     */
    public void setVersion(Version argVersion) {
        this.version = argVersion;
    }

    /**
     * Gets the value of access
     *
     * @return the value of access
     */
    public EntityAccess getAccess() {
        return this.access;
    }

    /**
     * Returns a deep copy list of constraints
     */
    @SuppressWarnings("unchecked")
    public List<EntityConstraint> getConstraints() {
        return (List<EntityConstraint>) constraints.clone();
    }

    /**
     * Sets the constraints
     */
    public void setConstraints(Collection<EntityConstraint> constraints) {
        this.constraints.clear();
        if (constraints != null) {
            this.constraints.addAll(constraints);
        }
    }

    /**
     * Gets the value of fields
     *
     * @return the value of fields
     */
    public Fields getFields() {
        return this.fields;
    }

    protected void setFields(Fields fields) {
        this.fields = fields;
    }

    public FieldTreeNode getFieldTreeRoot() {
        return fieldRoot;
    }

    protected void setFieldTreeRoot(FieldTreeNode fieldRoot) {
        this.fieldRoot = fieldRoot;
    }

    public FieldCursor getFieldCursor() {
        return new FieldCursor(new Path(), getFieldTreeRoot());
    }

    public FieldCursor getFieldCursor(Path p) {
        if (p.numSegments() == 0) {
            return getFieldCursor();
        } else {
            FieldTreeNode tn = resolve(p);
            if (tn != null) {
                return new FieldCursor(p, tn);
            } else {
                return null;
            }
        }
    }

    public FieldTreeNode resolve(Path p) {
        Error.push(name);
        try {
            return fields.resolve(p);
        } catch (Error e) {
            // rethrow lightblue error
            throw e;
        } catch (Exception e) {
            // throw new Error (preserves current error context)
            LOGGER.error(e.getMessage(), e);
            throw Error.get(MetadataConstants.ERR_ILL_FORMED_METADATA, e.getMessage());
        } finally {
            Error.pop();
        }
    }

    /**
     * Returns the full field name relative to the current entity
     */
    public Path getEntityRelativeFieldName(FieldTreeNode field) {
        return field.getFullPath();
    }

    /**
     * Returns a map where the key is the array field name, and value is a
     * List<Path> listing the identity fields for that array
     */
    public Map<Path, List<Path>> getArrayIdentities() {
        FieldCursor cursor = getFieldCursor();
        Map<Path, List<Path>> idMap = new HashMap<>();
        while (cursor.next()) {
            FieldTreeNode fn = cursor.getCurrentNode();
            if (fn instanceof SimpleField) {
                for (FieldConstraint fc : ((SimpleField) fn).getConstraints()) {
                    if (fc instanceof ArrayElementIdConstraint) {
                        Path fieldName = cursor.getCurrentPath();
                        int lastAny = findLastAnyIndex(fieldName);
                        if (lastAny != -1) {
                            Path arrayName = fieldName.prefix(lastAny);
                            Path idName = fieldName.suffix(-(lastAny + 1));
                            List<Path> ids = idMap.get(arrayName);
                            if (ids == null) {
                                idMap.put(arrayName, ids = new ArrayList<>());
                            }
                            ids.add(idName);
                        }
                    }
                }
            }
        }
        return idMap;
    }

    /**
     * Builds a document comparator for comparing documents of this type. That
     * involves registering all array element identities with the comparator so
     * array comparisons can be done corectly and efficiently.
     */
    public JsonCompare getDocComparator() {
        Map<Path, List<Path>> idMap = getArrayIdentities();
        JsonCompare cmp = new JsonCompare();
        for (Map.Entry<Path, List<Path>> entry : idMap.entrySet()) {
            cmp.addArrayIdentity(entry.getKey(), entry.getValue().toArray(new Path[entry.getValue().size()]));
        }
        return cmp;
    }

    private int findLastAnyIndex(Path p) {
        for (int i = p.numSegments() - 1; i >= 0; i--) {
            if (p.head(i).equals(Path.ANY)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Returns the identity fields for this entity. It does not descend down the
     * relations.
     */
    public Field[] getIdentityFields() {
        FieldCursor cursor = getFieldCursor();
        TreeMap<Path, Field> fieldMap = new TreeMap<>();
        getIdentityFields(fieldMap, cursor);
        Field[] ret = new Field[fieldMap.size()];
        int i = 0;
        for (Field f : fieldMap.values()) {
            ret[i++] = f;
        }
        return ret;
    }

    private void getIdentityFields(TreeMap<Path, Field> fieldMap, FieldCursor cursor) {
        if (cursor.firstChild()) {
            do {
                FieldTreeNode fn = cursor.getCurrentNode();
                if (fn instanceof ObjectField) {
                    getIdentityFields(fieldMap, cursor);
                } else if (fn instanceof SimpleField) {
                    SimpleField f = (SimpleField) fn;
                    for (FieldConstraint fc : f.getConstraints()) {
                        if (fc instanceof IdentityConstraint) {
                            fieldMap.put(getEntityRelativeFieldName(f), f);
                            break;
                        }
                    }
                }
            } while (cursor.nextSibling());
            cursor.parent();
        }
    }
}
