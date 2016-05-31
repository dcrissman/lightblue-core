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
package com.redhat.lightblue.crud.validator;

import java.util.List;
import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.redhat.lightblue.crud.ConstraintValidator;
import com.redhat.lightblue.crud.CrudConstants;
import com.redhat.lightblue.crud.FieldConstraintDocChecker;
import com.redhat.lightblue.metadata.FieldConstraint;
import com.redhat.lightblue.metadata.FieldTreeNode;
import com.redhat.lightblue.metadata.constraints.RequiredConstraint;
import com.redhat.lightblue.util.Error;
import com.redhat.lightblue.util.JsonDoc;
import com.redhat.lightblue.util.KeyValueCursor;
import com.redhat.lightblue.util.Path;

public class RequiredChecker implements FieldConstraintDocChecker {

    private static final Logger LOGGER = LoggerFactory.getLogger(RequiredChecker.class);

    @Override
    public void checkConstraint(ConstraintValidator validator,
                                FieldTreeNode fieldMetadata,
                                Path fieldMetadataPath,
                                FieldConstraint constraint,
                                JsonDoc doc) {
        if (((RequiredConstraint) constraint).getValue()) {
            List<Path> errors = getMissingFields(fieldMetadataPath, doc);
            for (Path x : errors) {
                validator.addDocError(Error.get(CrudConstants.ERR_REQUIRED, x.toString()));
            }
        }
    }

    /**
     * Returns the list of fields that are missing in the doc
     *
     * @param fieldMetadataPath Path of the required field
     * @param doc The document
     *
     * @return List of field instances that are not present in the doc.
     */
    public static List<Path> getMissingFields(Path fieldMetadataPath,
                                              JsonDoc doc) {
        LOGGER.debug("Checking {}", fieldMetadataPath);
        int nAnys = fieldMetadataPath.nAnys();
        List<Path> errors = new ArrayList<>();
        if (nAnys == 0) {
            JsonNode fieldNode = doc.get(fieldMetadataPath);
            if (fieldNode == null) {
                // Field does not exist. If the parent object does not exist either, constraint is ok
                if (fieldMetadataPath.numSegments() > 1) {
                    JsonNode parent = doc.get(fieldMetadataPath.prefix(-1));
                    if (parent == null || parent instanceof NullNode) {
                        // Parent does not exist as well. Let it pass
                        ;
                    } else {
                        errors.add(fieldMetadataPath);
                    }
                } else {
                    errors.add(fieldMetadataPath);
                }
            } else if (fieldNode instanceof NullNode) {
                errors.add(fieldMetadataPath);
            }
        } else {
            // The required field is a member of an object that's an element of an array
            // If the array element exists, then the member must exist in that object
            Path parent = fieldMetadataPath.prefix(-1);
            String fieldName = fieldMetadataPath.tail(0);
            LOGGER.debug("Checking {} under {}", fieldName, parent);
            KeyValueCursor<Path, JsonNode> cursor = doc.getAllNodes(parent);
            while (cursor.hasNext()) {
                cursor.next();
                JsonNode parentObject = cursor.getCurrentValue();
                if (!(parentObject instanceof NullNode)) {
                    LOGGER.debug("Checking {}", cursor.getCurrentKey());
                    JsonNode fieldNode = parentObject.get(fieldName);
                    if (fieldNode == null || fieldNode instanceof NullNode) {
                        errors.add(new Path(cursor.getCurrentKey() + "." + fieldName));
                    }
                }
            }
        }
        LOGGER.debug("Errors:{}", errors);
        return errors;
    }
}
