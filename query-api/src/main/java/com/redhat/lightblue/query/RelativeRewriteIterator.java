
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
package com.redhat.lightblue.query;

import com.redhat.lightblue.util.Path;

/**
 * This class rewrites a query by replacing absolute fields with relative ones
 * with respect to a point in the field tree
 */
public class RelativeRewriteIterator extends QueryIterator {

    private Path relativeTo;

    public RelativeRewriteIterator(Path relativeTo) {
        this.relativeTo = relativeTo;
    }

    @Override
    protected QueryExpression itrValueComparisonExpression(ValueComparisonExpression q, Path context) {
        return new ValueComparisonExpression(toRelative(q.getField(), context), q.getOp(), q.getRvalue());
    }

    @Override
    protected QueryExpression itrFieldComparisonExpression(FieldComparisonExpression q, Path context) {
        return new FieldComparisonExpression(toRelative(q.getField(), context), q.getOp(),
                toRelative(q.getRfield(), context));
    }

    @Override
    protected QueryExpression itrRegexMatchExpression(RegexMatchExpression q, Path context) {
        return new RegexMatchExpression(toRelative(q.getField(), context),
                q.getRegex(), q.isCaseInsensitive(),
                q.isMultiline(), q.isExtended(), q.isDotAll());
    }

    @Override
    protected QueryExpression itrNaryValueRelationalExpression(NaryValueRelationalExpression q, Path context) {
        return new NaryValueRelationalExpression(toRelative(q.getField(), context),
                q.getOp(), q.getValues());
    }

    @Override
    protected QueryExpression itrNaryFieldRelationalExpression(NaryFieldRelationalExpression q, Path context) {
        return new NaryFieldRelationalExpression(toRelative(q.getField(), context),
                q.getOp(), toRelative(q.getRfield(), context));
    }

    @Override
    protected QueryExpression itrArrayContainsExpression(ArrayContainsExpression q, Path context) {
        return new ArrayContainsExpression(toRelative(q.getArray(), context),
                q.getOp(), q.getValues());
    }

    @Override
    protected QueryExpression itrArrayMatchExpression(ArrayMatchExpression q, Path context) {
        // No need to rewrite elemMatch using relative context, because that is already interpreted relative to the array
        return new ArrayMatchExpression(toRelative(q.getArray(), context), q.getElemMatch());
    }

    private Path toRelative(Path field, Path context) {
        Path abs = context.isEmpty() ? field : new Path(context, field);
        if (relativeTo.matchingPrefix(abs)) {
            return abs.suffix(-relativeTo.numSegments());
        } else {
            throw new IllegalArgumentException("Cannot write " + abs + " relative to " + relativeTo);
        }
    }
}
