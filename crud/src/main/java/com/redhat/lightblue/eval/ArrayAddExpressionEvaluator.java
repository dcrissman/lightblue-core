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
package com.redhat.lightblue.eval;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.NullNode;
import com.redhat.lightblue.crud.CrudConstants;
import com.redhat.lightblue.metadata.ArrayElement;
import com.redhat.lightblue.metadata.ArrayField;
import com.redhat.lightblue.metadata.FieldTreeNode;
import com.redhat.lightblue.metadata.ObjectArrayElement;
import com.redhat.lightblue.metadata.Type;
import com.redhat.lightblue.query.ArrayAddExpression;
import com.redhat.lightblue.query.RValueExpression;
import com.redhat.lightblue.query.UpdateOperator;
import com.redhat.lightblue.query.Value;
import com.redhat.lightblue.util.JsonDoc;
import com.redhat.lightblue.util.MutablePath;
import com.redhat.lightblue.util.Path;

/**
 * Adds a field to an array
 */
public class ArrayAddExpressionEvaluator extends Updater {

    private static final Logger LOGGER = LoggerFactory.getLogger(ArrayAddExpressionEvaluator.class);

    private final Path arrayField;
    private final Path arraySizeField;
    private final Path absArrayField;
    private final int insertionIndex;
    private final ArrayField fieldMd;
    private final List<RValueData> values;
    private final JsonNodeFactory factory;

    private static final class RValueData {
        private final Path refPath;
        private final Type refType;
        private final Value value;
        private RValueExpression.RValueType rvalueType;

        public RValueData(Path refPath, Type refType, Value value, RValueExpression.RValueType rvType) {
            this.refPath = refPath;
            this.refType = refType;
            this.value = value;
            this.rvalueType = rvType;
        }

        public String toString() {
            return "refPath:" + refPath + " refType:" + refType + " value:" + value + " rvType:" + rvalueType;
        }
    }

    public ArrayAddExpressionEvaluator(JsonNodeFactory factory, FieldTreeNode context, ArrayAddExpression expr) {
        this.factory = factory;
        if (expr.getOp() == UpdateOperator._insert) {
            // Path should include an index
            if (expr.getField().isIndex(expr.getField().numSegments() - 1)) {
                arrayField = expr.getField().prefix(-1);
                insertionIndex = expr.getField().getIndex(expr.getField().numSegments() - 1);
            } else {
                throw new EvaluationError(CrudConstants.ERR_REQUIRED_INSERTION_INDEX + expr.getField());
            }
        } else {
            arrayField = expr.getField();
            insertionIndex = -1;
        }
        if (arrayField.nAnys() > 0) {
            throw new EvaluationError(CrudConstants.ERR_PATTERN_NOT_EXPECTED + arrayField);
        }
        FieldTreeNode ftn = context.resolve(arrayField);
        if (ftn instanceof ArrayField) {
            fieldMd = (ArrayField) ftn;
            // Array size field should be at the same level as the array field
            MutablePath abs = new MutablePath();
            fieldMd.getFullPath(abs);
            absArrayField = abs.mutableCopy();
            abs.setLast(abs.getLast() + "#");
            // At this point, arraySizeField is derived from metadata,
            // so it has * as array indexes
            arraySizeField = abs.immutableCopy();
            values = new ArrayList<>(expr.getValues().size());
            initializeArrayField(context, expr);
        } else {
            throw new EvaluationError(CrudConstants.ERR_REQUIRED_ARRAY + arrayField);
        }
    }

    private void initializeArrayField(FieldTreeNode context, ArrayAddExpression expr) {
        for (RValueExpression rvalue : expr.getValues()) {
            Path refPath = null;
            FieldTreeNode refMd = null;
            if (rvalue.getType() == RValueExpression.RValueType._dereference) {
                refPath = rvalue.getPath();
                refMd = context.resolve(refPath);
                if (refMd == null) {
                    throw new EvaluationError(CrudConstants.ERR_INVALID_DEREFERENCE + refPath);
                }
            }

            ArrayElement element = fieldMd.getElement();
            validateArrayElement(element, refMd, rvalue, refPath);

            values.add(new RValueData(refPath, refMd == null ? null : refMd.getType(), rvalue.getValue(), rvalue.getType()));
        }
    }

    private void validateArrayElement(ArrayElement element, FieldTreeNode refMd, RValueExpression rvalue, Path refPath) {
        if (element instanceof ObjectArrayElement) {
            if (refMd != null && !refMd.getType().equals(element.getType())) {
                throw new EvaluationError(CrudConstants.ERR_INVALID_ASSIGNMENT + arrayField + " <- " + refPath);
            } else if (rvalue.getType() == RValueExpression.RValueType._value) {
                Value v = rvalue.getValue();
                if (!(v.getValue() instanceof ObjectNode)
                        && !(v.getValue() instanceof ArrayNode)) {
                    throw new EvaluationError(CrudConstants.ERR_EXPECTED_OBJECT_VALUE + arrayField);
                }
            }
        } else if (refMd != null && !refMd.getType().equals(element.getType())) {
            throw new EvaluationError(CrudConstants.ERR_INVALID_ASSIGNMENT + arrayField + "<-" + refPath);
        } else if (rvalue.getType() == RValueExpression.RValueType._value) {
            Value v = rvalue.getValue();
            if (v.getValue() instanceof ObjectNode
                    || v.getValue() instanceof ArrayNode) {
                throw new EvaluationError(CrudConstants.ERR_EXPECTED_VALUE + arrayField);
            }
        }
    }

    @Override
    public void getUpdateFields(Set<Path> fields) {
        fields.add(absArrayField);
        fields.add(arraySizeField);
    }

    @Override
    public boolean update(JsonDoc doc, FieldTreeNode contextMd, Path contextPath) {
        boolean ret = false;
        Path absPath = new Path(contextPath, arrayField);
        JsonNode node = doc.get(absPath);
        int insertTo = insertionIndex;
        if (node == null || node instanceof NullNode) {
            doc.modify(absPath, node = factory.arrayNode(), true);
        }
        if (node instanceof ArrayNode) {
            ArrayNode arrayNode = (ArrayNode) node;
            for (RValueData rvalueData : values) {
                LOGGER.debug("add element to {} rvalue:{}", absPath, rvalueData);
                Object newValue = null;
                Type newValueType = null;
                JsonNode newValueNode = null;
                if (rvalueData.refPath != null) {
                    JsonNode refNode = doc.get(new Path(contextPath, rvalueData.refPath));
                    if (refNode != null) {
                        newValueNode = refNode.deepCopy();
                        newValue = rvalueData.refType.fromJson(newValueNode);
                        newValueType = rvalueData.refType;
                    }
                } else if (rvalueData.value != null) {
                    newValue = rvalueData.value.getValue();
                    newValueNode = newValue instanceof JsonNode ? (JsonNode) newValue : fieldMd.getElement().getType().toJson(factory, newValue);
                    newValueType = fieldMd.getElement().getType();
                } else if (rvalueData.rvalueType == RValueExpression.RValueType._null) {
                    newValueNode = factory.nullNode();
                }
                LOGGER.debug("newValueType:{}, newValue:{}, newValueNode:{} ", newValueType, newValue, newValueNode);

                if (insertTo >= 0) {
                    // If we're inserting, make sure we have that many elements
                    while (arrayNode.size() < insertTo) {
                        arrayNode.addNull();
                    }

                    if (arrayNode.size() > insertTo) {
                        arrayNode.insert(insertTo, newValueNode);
                    } else {
                        arrayNode.add(newValueNode);
                    }
                    insertTo++;
                } else {
                    arrayNode.add(newValueNode);
                }
                ret = true;
            }
            if (ret) {
                // We have to rewrite the array indexes in arraySizeField using
                // the context path
                MutablePath p = new MutablePath(arraySizeField);
                p.rewriteIndexes(absPath);
                LOGGER.debug("Setting {} = {}", p, arrayNode.size());
                doc.modify(p, factory.numberNode(arrayNode.size()), false);
            }
        }
        return ret;
    }
}
