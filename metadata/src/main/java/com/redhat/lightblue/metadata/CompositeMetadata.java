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

import com.redhat.lightblue.query.*;
import com.redhat.lightblue.util.Error;
import com.redhat.lightblue.util.MutablePath;
import com.redhat.lightblue.util.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Composite metedata is a directed tree. The requested entity is at the root of
 * the composite metadata. Every entity arrived by following an association is
 * another node in the composite metadata, and the edge points to the
 * destination of the association.
 *
 * Composite metadata extends EntityMetadata with these functions:
 * <ul>
 * <li>The tree structure of entities are visible through CompositeMetadata</li>
 * <li>Reference fields are extended to include the projected fields from the
 * associated entities</li>
 * </ul>
 *
 * Composite metadata needs to be computed for every request. The computation
 * takes into account the request queries and projections to determine how deep
 * the reference tree needs to be traversed.
 */
public class CompositeMetadata extends EntityMetadata {

    private static final Logger LOGGER = LoggerFactory.getLogger(CompositeMetadata.class);

    private final Path entityPath;
    private final CompositeMetadata parent;
    private final Map<Path, ResolvedReferenceField> children = new HashMap<>();

    /**
     * Interface that returns an instance of entity metadata given the entity
     * name, version, and the field into which entity needs to be injected. The
     * logic to include or exclude the entity based on the projection, query,
     * and sort requirements is implemented by this class. If an instance of
     * entity metadata is not to be included in the composite metadata, the
     * getMetadata method must return null.
     *
     * If a particular version of a requested entity has references, the
     * getMetadata method should return an instance of EntityMetadata, not
     * CompositeMetadata. The caller will recursively descend into all the
     * entities retrieved and construct the top-level composite metadata.
     *
     * If metadata cannot be retrieved, this call should throw an exception.
     * Returning null means the particular entity metadata is not projected.
     */
    public static interface GetMetadata {
        public EntityMetadata getMetadata(Path injectionField,
                                          String entityName,
                                          String version);
    }

    /**
     * Construct a composite metadata using the given entity info and composite
     * schema. This constructor is to construct a CompositeMetadata at the root
     * of an entity tree.
     */
    public CompositeMetadata(EntityInfo info,
                             CompositeSchema schema) {
        this(info, schema, Path.EMPTY, null);
    }

    public CompositeMetadata(EntityInfo info,
                             CompositeSchema schema,
                             Path path,
                             CompositeMetadata parent) {
        super(info, schema);
        this.entityPath = path;
        this.parent = parent;
    }

    /**
     * If this composite metadata is the root of an entity metadata tree,
     * returns empty path. Otherwise, this composite metadata is a descendant of
     * another composite metadata, and this call returns the full path to the
     * reference field containing this metadata.
     */
    public Path getEntityPath() {
        return entityPath;
    }

    /**
     * Returns if this is a simple metadata, one that has no children
     */
    public boolean isSimple() {
        return children.isEmpty();
    }

    /**
     * If this composite metadata is the root of an entity metadata tree,
     * returns null. Otherwise, returns the metadata containing this metadata.
     */
    public CompositeMetadata getParent() {
        return parent;
    }

    /**
     * Returns a direct child metadata of this metadata.
     *
     * @param entityPath The absolute path to the field containing the requested
     * child
     */
    public CompositeMetadata getChildMetadata(Path entityPath) {
        ResolvedReferenceField rf = children.get(entityPath);
        return rf == null ? null : rf.getReferencedMetadata();
    }

    /**
     * Returns a descendant resolved reference of this metadata
     *
     * @param entityPath The absolute path to the field containing the requested
     * child
     */
    public ResolvedReferenceField getDescendantReference(Path entityPath) {
        ResolvedReferenceField rf = getChildReference(entityPath);
        if (rf == null) {
            for (Map.Entry<Path, ResolvedReferenceField> entry : children.entrySet()) {
                rf = entry.getValue().getReferencedMetadata().getDescendantReference(entityPath);
                if (rf != null) {
                    break;
                }
            }
        }
        return rf;
    }

    /**
     * Returns descendant of this metadata
     *
     * @param entityPath The absolute path to the field containing the requested
     * child
     */
    public CompositeMetadata getDescendantMetadata(Path entityPath) {
        ResolvedReferenceField rf = getDescendantReference(entityPath);
        return rf == null ? null : rf.getReferencedMetadata();
    }

    /**
     * Returns a direct child resolved reference of this metadata.
     *
     * @param entityPath The absolute path to the field containing the requested
     * child
     */
    public ResolvedReferenceField getChildReference(Path entityPath) {
        return children.get(entityPath);
    }

    /**
     * Returns the absolute paths of the direct children of this metadata
     */
    public Set<Path> getChildPaths() {
        return children.keySet();
    }

    /**
     * Returns the entity tree structure as a string
     */
    public String toTreeString() {
        StringBuilder bld = new StringBuilder();
        toTreeString(0, bld);
        return bld.toString();
    }

    /**
     * Returns the composite metadata containing the field pointed by the given
     * path
     */
    public CompositeMetadata getEntityOfPath(Path path) {
        // Resolve the path
        FieldTreeNode node = resolve(path);
        // Find the entity of the node
        return getEntityOfField(node);
    }

    /**
     * Returns the composite metadata containing the field
     */
    public CompositeMetadata getEntityOfField(FieldTreeNode field) {
        if (field != null) {
            ResolvedReferenceField rr = getResolvedReferenceOfField(field);
            if (rr == null) {
                return this;
            } else {
                return rr.getReferencedMetadata();
            }
        } else {
            return null;
        }
    }

    /**
     * Returns the resolved reference containing the field
     */
    public ResolvedReferenceField getResolvedReferenceOfField(Path field) {
        return getResolvedReferenceOfField(resolve(field));
    }

    /**
     * Returns the resolved reference containing the field
     */
    public ResolvedReferenceField getResolvedReferenceOfField(FieldTreeNode field) {
        ResolvedReferenceField ret = null;
        if (field != null) {
            do {
                if (field instanceof ResolvedReferenceField) {
                    ret = (ResolvedReferenceField) field;
                } else {
                    field = field.getParent();
                }
            } while (field != null && ret == null);
        }
        return ret;
    }

    /**
     * Returns the field name for the given field node relative to the entity it
     * is contained in
     */
    public Path getEntityRelativeFieldName(FieldTreeNode fieldNode) {
        return getEntitySchema().getEntityRelativeFieldName(fieldNode);
    }

    /**
     * Builds a composite metadata rooted at the given entity metadata.
     */
    public static CompositeMetadata buildCompositeMetadata(EntityMetadata root,
                                                           GetMetadata gmd) {
        LOGGER.debug("enter buildCompositeMetadata");
        Error.push("compositeMetadata");
        try {
            CompositeMetadata cmd = buildCompositeMetadata(root, gmd, new Path(), null, new MutablePath());
            return cmd;
        } finally {
            LOGGER.debug("end buildCompositeMetadata");
            Error.pop();
        }
    }

    /**
     *
     * @param root the root metadata
     * @param gmd the GetMetadata for resolving metadata
     * @param entityPath the path to the metadata
     * @param parentEntity the parent metadata
     * @param path relative path in processing, if not a recursive call it
     * should be a new empty MutablePath object
     * @return
     */
    private static CompositeMetadata buildCompositeMetadata(EntityMetadata root,
                                                            GetMetadata gmd,
                                                            Path entityPath,
                                                            CompositeMetadata parentEntity,
                                                            MutablePath path) {
        // Recursively process and copy the fields, retrieving
        // metadata for references
        Error.push(root.getName());
        try {
            CompositeSchema cschema = CompositeSchema.newSchemaWithEmptyFields(root.getEntitySchema());
            CompositeMetadata cmd = new CompositeMetadata(root.getEntityInfo(), cschema, entityPath, parentEntity);
            // copy fields, resolve references
            copyFields(cschema.getFields(), root.getEntitySchema().getFields(), path, cmd, gmd);
            return cmd;
        } finally {
            Error.pop();
        }
    }

    /**
     * QueryIterator implementation that creates new instances of
     * QueryExpression implementations when the rewritten absolute path is not
     * used in the query already.
     */
    private static final class AbsRewriteItr extends QueryIterator {
        /**
         * we'll interpret field names with respect to this entity
         */
        private FieldTreeNode interpretWRTEntity;

        public AbsRewriteItr(FieldTreeNode root) {
            interpretWRTEntity = root;
        }

        @Override
        protected QueryExpression itrValueComparisonExpression(ValueComparisonExpression q, Path context) {
            Path p = rewrite(context, q.getField());
            if (!p.equals(q.getField())) {
                return new ValueComparisonExpression(p, q.getOp(), q.getRvalue());
            } else {
                return q;
            }
        }

        @Override
        protected QueryExpression itrRegexMatchExpression(RegexMatchExpression q, Path context) {
            Path p = rewrite(context, q.getField());
            if (!p.equals(q.getField())) {
                return new RegexMatchExpression(p, q.getRegex(),
                        q.isCaseInsensitive(),
                        q.isMultiline(),
                        q.isExtended(),
                        q.isDotAll());
            } else {
                return q;
            }
        }

        @Override
        protected QueryExpression itrFieldComparisonExpression(FieldComparisonExpression q, Path context) {
            Path left = rewrite(context, q.getField());
            Path right = rewrite(context, q.getRfield());
            if (!left.equals(q.getField()) || !right.equals(q.getRfield())) {
                return new FieldComparisonExpression(left, q.getOp(), right);
            } else {
                return q;
            }
        }

        @Override
        protected QueryExpression itrArrayContainsExpression(ArrayContainsExpression q, Path context) {
            Path p = rewrite(context, q.getArray());
            if (!p.equals(q.getArray())) {
                return new ArrayContainsExpression(p, q.getOp(), q.getValues());
            } else {
                return q;
            }
        }

        @Override
        protected QueryExpression itrArrayMatchExpression(ArrayMatchExpression q, Path context) {
            Path p = rewrite(context, q.getArray());
            if (!p.equals(q.getArray())) {
                return new ArrayMatchExpression(p, q.getElemMatch());
            } else {
                return q;
            }
        }

        private Path rewrite(Path context, Path field) {
            LOGGER.debug("rewriting {}", field);

            if (context != null && !Path.EMPTY.equals(context)) {
                throw Error.get(MetadataConstants.ERR_INVALID_CONTEXT, "Expected empty path, got: " + context.toString());
            }

            // We interpret field name with respect to the current entity
            FieldTreeNode fieldNode = interpretWRTEntity.resolve(field);

            Path absFieldPath = fieldNode.getFullPath();
            LOGGER.debug("Field full path={}", absFieldPath);
            return absFieldPath;
        }
    }

    /**
     * Copy fields from source to dest.
     *
     * @param dest where to write copied fields
     * @param source source for fields to copy
     * @param path relative path in processing
     * @param parentEntity the parent metadata
     * @param gmd impl of GetMetadata for finding metadata for a given field
     */
    private static void copyFields(Fields dest,
                                   Fields source,
                                   MutablePath path,
                                   CompositeMetadata parentEntity,
                                   GetMetadata gmd) {
        // Iterate over source fields.
        // If field is simple
        //      shallow copy SimpleField
        //      add result to dest
        // else if field is object
        //      shallow copy new ObjectField
        //      recursively call copyFields on the new ObjectField to copy object fields
        //      add result to dest
        // else if field is array
        //      if elements are objects call copyFields on a new ObjectArrayElement
        //      else create new SimpleArrayElement
        //      shallow copy to a new ArrayField created with *Element created above
        //      add result to dest
        // else (field is a reference)
        //      resolve ResolvedReferenceField
        //      if found, add result to dest
        // copy all properties from source to dest
        for (Iterator<Field> itr = source.getFields(); itr.hasNext();) {
            Field field = itr.next();
            Error.push(field.getName());
            path.push(field.getName()); // push even for simple field since it won't matter in that case
            LOGGER.debug("Processing {}", path);
            try {
                if (field instanceof SimpleField) {
                    SimpleField newField = new SimpleField(field.getName(), field.getType());
                    newField.shallowCopyFrom(field);
                    dest.put(newField);
                } else if (field instanceof ObjectField) {
                    ObjectField newField = new ObjectField(field.getName());
                    newField.shallowCopyFrom(field);
                    copyFields(newField.getFields(),
                            ((ObjectField) field).getFields(),
                            path,
                            parentEntity,
                            gmd);
                    dest.put(newField);
                } else if (field instanceof ArrayField) {
                    ArrayElement sourceEl = ((ArrayField) field).getElement();
                    ArrayElement newElement;
                    if (sourceEl instanceof ObjectArrayElement) {
                        path.push(Path.ANY);
                        // Need to copy an Object array, there is a Fields object in it
                        newElement = new ObjectArrayElement();
                        copyFields(((ObjectArrayElement) newElement).getFields(),
                                ((ObjectArrayElement) sourceEl).getFields(),
                                path,
                                parentEntity,
                                gmd);
                        path.pop();
                    } else {
                        newElement = new SimpleArrayElement(((SimpleArrayElement) sourceEl).getType());
                    }
                    newElement.getProperties().putAll(sourceEl.getProperties());
                    ArrayField newField = new ArrayField(field.getName(), newElement);
                    newField.shallowCopyFrom(field);
                    dest.put(newField);
                } else {
                    // Field is a reference
                    ReferenceField reference = (ReferenceField) field;
                    ResolvedReferenceField newField = resolveReference(reference, path, parentEntity, gmd);
                    if (newField != null) {
                        dest.put(newField);
                    }
                }
            } finally {
                Error.pop();
            }
            path.pop();
        }
    }

    private static ResolvedReferenceField resolveReference(ReferenceField source,
                                                           MutablePath path,
                                                           CompositeMetadata parentEntity,
                                                           GetMetadata gmd) {
        LOGGER.debug("resolveReference {}:{}", path, source);
        EntityMetadata md = gmd.getMetadata(path.immutableCopy(),
                source.getEntityName(),
                source.getVersionValue());
        // If metadata is null, the entity is not projected, so we
        // don't even set it in the containing Fields.  If somehow the
        // GetMetadata cannot retrieve the metadata, it throws an
        // exception.
        if (md != null) {
            LOGGER.debug("resolved");
            // We have the entity metadata. We insert this as a
            // resolved reference
            Path fpath = path.immutableCopy();
            path.push(Path.ANY);
            CompositeMetadata cmd = buildCompositeMetadata(md, gmd, fpath, parentEntity, path);
            path.pop();
            ResolvedReferenceField newField = new ResolvedReferenceField(source, md, cmd);
            parentEntity.children.put(fpath, newField);
            return newField;
        } else {
            LOGGER.debug("Not resolved");
        }
        return null;
    }

    private void toTreeString(int depth, StringBuilder bld) {
        for (int i = 0; i < depth; i++) {
            bld.append("  ");
        }
        bld.append(getName()).append(':').append(entityPath.toString()).append('\n');
        for (ResolvedReferenceField ch : children.values()) {
            ch.getReferencedMetadata().toTreeString(depth + 1, bld);
        }
    }
}
