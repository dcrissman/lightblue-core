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

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.redhat.lightblue.metadata.EntityMetadata;
import com.redhat.lightblue.query.UpdateExpression;
import com.redhat.lightblue.util.JsonDoc;
import com.redhat.lightblue.util.Path;
import com.redhat.lightblue.util.test.AbstractJsonNodeTest;

public class ArrayAddExpressionEvaluatorTest extends AbstractJsonNodeTest {

    private EntityMetadata md;
    private JsonDoc doc;

    @Before
    public void setUp() throws Exception {
        md = EvalTestContext.getMd("./testMetadata.json");
        doc = EvalTestContext.getDoc("./sample1.json");
    }

    @Test(expected = com.redhat.lightblue.eval.EvaluationError.class)
    public void expression_evaluator_not_created_when_specified_field_is_not_an_array() throws Exception {
        UpdateExpression expr = EvalTestContext.updateExpressionFromJson("{ '$append' : { 'field5' : [ 'five','six',{'$valueof':'field2' }] } }");
        Updater.getInstance(JSON_NODE_FACTORY, md, expr);
    }

    @Test(expected = com.redhat.lightblue.eval.EvaluationError.class)
    public void array_insert_without_index_results_in_evaluation_error() throws Exception {
        UpdateExpression expr = EvalTestContext.updateExpressionFromJson("{ '$insert' : { 'field6.nf6.$parent.nf8' : [ 'five','six',{'$valueof':'field2' }] } }");
        Updater.getInstance(JSON_NODE_FACTORY, md, expr);
    }

    @Test(expected = com.redhat.lightblue.eval.EvaluationError.class)
    public void assignment_of_invalid_type_results_in_evaluation_error() throws Exception {
        UpdateExpression expr = EvalTestContext.updateExpressionFromJson("{ '$insert' : { 'field6.nf7.nnf1.$parent.$parent.nf11.5' : [ 'five','six',{'$valueof':'field2' }] } }");
        Updater updater = Updater.getInstance(JSON_NODE_FACTORY, md, expr);

        updater.update(doc, md.getFieldTreeRoot(), new Path());
    }

    @Test
    public void string_array_append() throws Exception {
        String[] expectedValues = {"four", "three", "two", "one", "five", "six", "value2"};
        JsonNode expectedNode = stringArrayNode(expectedValues);
        UpdateExpression expr = EvalTestContext.updateExpressionFromJson("{ '$append' : { 'field6.nf8' : [ 'five','six',{'$valueof':'field2' }] } }");
        Updater updater = Updater.getInstance(JSON_NODE_FACTORY, md, expr);

        updater.update(doc, md.getFieldTreeRoot(), new Path());

        Assert.assertEquals(expectedNode, doc.get(new Path("field6.nf8")));
    }

    @Test
    public void int_array_append() throws Exception {
        Integer[] expectedValues = {5, 10, 15, 20, 1, 2, 3};
        UpdateExpression expr = EvalTestContext.updateExpressionFromJson("{ '$append' : { 'field6.nf5' : [ 1,2,{'$valueof':'field3' }] } }");
        Updater updater = Updater.getInstance(JSON_NODE_FACTORY, md, expr);

        updater.update(doc, md.getFieldTreeRoot(), new Path());

        Assert.assertTrue(arrayNodesHaveSameValues(intArrayNode(expectedValues), doc.get(new Path("field6.nf5"))));
    }

    @Test
    public void double_array_append() throws Exception {
        Double[] expectedValues = {20.1, 15.2, 10.3, 5.4, 1.5, 2.6, 4.7};
        UpdateExpression expr = EvalTestContext.updateExpressionFromJson("{ '$append' : { 'field6.nf10' : [ 1.5,2.6,{'$valueof':'field4' }] } }");
        Updater updater = Updater.getInstance(JSON_NODE_FACTORY, md, expr);

        updater.update(doc, md.getFieldTreeRoot(), new Path());

        Assert.assertTrue(arrayNodesHaveSameValues(doubleArrayNode(expectedValues), doc.get(new Path("field6.nf10"))));
    }

    @Test
    public void string_array_insert() throws Exception {
        String[] expectedValues = {"four", "three", "five", "six", "value2", "two", "one"};
        UpdateExpression expr = EvalTestContext.updateExpressionFromJson("{ '$insert' : { 'field6.nf8.2' : [ 'five','six',{'$valueof':'field2' }] } }");
        Updater updater = Updater.getInstance(JSON_NODE_FACTORY, md, expr);

        updater.update(doc, md.getFieldTreeRoot(), new Path());

        Assert.assertTrue(arrayNodesHaveSameValues(stringArrayNode(expectedValues), doc.get(new Path("field6.nf8"))));
    }

    @Test
    public void string_array_insert_and_array_expansion() throws Exception {
        String[] expectedValues = {"four", "three", "two", "one", null, "five", "six", "value2"};
        UpdateExpression expr = EvalTestContext.updateExpressionFromJson("{ '$insert' : { 'field6.nf8.5' : [ 'five','six',{'$valueof':'field2' }] } }");
        Updater updater = Updater.getInstance(JSON_NODE_FACTORY, md, expr);

        updater.update(doc, md.getFieldTreeRoot(), new Path());

        Assert.assertEquals(stringArrayNode(expectedValues), doc.get(new Path("field6.nf8")));
    }

    @Test
    public void string_array_append_with_1_$parent_relative_path() throws Exception {
        String[] expectedValues = {"four", "three", "two", "one", "five", "six", "value2"};
        JsonNode expectedNode = stringArrayNode(expectedValues);
        UpdateExpression expr = EvalTestContext.updateExpressionFromJson("{ '$append' : { 'field6.nf6.$parent.nf8' : [ 'five','six',{'$valueof':'field2' }] } }");
        Updater updater = Updater.getInstance(JSON_NODE_FACTORY, md, expr);

        updater.update(doc, md.getFieldTreeRoot(), new Path());

        Assert.assertEquals(expectedNode, doc.get(new Path("field6.nf8")));
    }

    @Test
    public void string_array_append_with_2_$parent_relative_path() throws Exception {
        String[] expectedValues = {"four", "three", "two", "one", "five", "six", "value2"};
        UpdateExpression expr = EvalTestContext.updateExpressionFromJson("{ '$append' : { 'field6.nf7.nnf1.$parent.$parent.nf8' : [ 'five','six',{'$valueof':'field2' }] } }");
        Updater updater = Updater.getInstance(JSON_NODE_FACTORY, md, expr);

        updater.update(doc, md.getFieldTreeRoot(), new Path());

        Assert.assertEquals(stringArrayNode(expectedValues), doc.get(new Path("field6.nf8")));
    }

    @Test
    public void int_array_append_with_1_$parent_relative_path() throws Exception {
        Integer[] expectedValues = {5, 10, 15, 20, 1, 2, 3};
        UpdateExpression expr = EvalTestContext.updateExpressionFromJson("{ '$append' : { 'field6.nf6.$parent.nf5' : [ 1,2,{'$valueof':'field3' }] } }");
        Updater updater = Updater.getInstance(JSON_NODE_FACTORY, md, expr);

        updater.update(doc, md.getFieldTreeRoot(), new Path());

        Assert.assertTrue(arrayNodesHaveSameValues(intArrayNode(expectedValues), doc.get(new Path("field6.nf5"))));
    }

    @Test
    public void double_array_append_with_1_$parent_relative_path() throws Exception {
        Double[] expectedValues = {20.1, 15.2, 10.3, 5.4, 1.5, 2.6, 4.7};
        UpdateExpression expr = EvalTestContext.updateExpressionFromJson("{ '$append' : { 'field6.nf6.$parent.nf10' : [ 1.5,2.6,{'$valueof':'field4' }] } }");
        Updater updater = Updater.getInstance(JSON_NODE_FACTORY, md, expr);

        updater.update(doc, md.getFieldTreeRoot(), new Path());

        Assert.assertTrue(arrayNodesHaveSameValues(doubleArrayNode(expectedValues), doc.get(new Path("field6.nf10"))));
    }

    @Test
    public void string_array_insert_with_1_$parent_relative_path() throws Exception {
        String[] expectedValues = {"four", "three", "five", "six", "value2", "two", "one"};
        UpdateExpression expr = EvalTestContext.updateExpressionFromJson("{ '$insert' : { 'field6.nf6.$parent.nf8.2' : [ 'five','six',{'$valueof':'field2' }] } }");
        Updater updater = Updater.getInstance(JSON_NODE_FACTORY, md, expr);

        updater.update(doc, md.getFieldTreeRoot(), new Path());

        Assert.assertTrue(arrayNodesHaveSameValues(stringArrayNode(expectedValues), doc.get(new Path("field6.nf8"))));
    }

    @Test
    public void string_array_insert_with_2_$parent_relative_path() throws Exception {
        String[] expectedValues = {"four", "three", "five", "six", "value2", "two", "one"};
        UpdateExpression expr = EvalTestContext.updateExpressionFromJson("{ '$insert' : { 'field6.nf7.nnf1.$parent.$parent.nf8.2' : [ 'five','six',{'$valueof':'field2' }] } }");
        Updater updater = Updater.getInstance(JSON_NODE_FACTORY, md, expr);

        updater.update(doc, md.getFieldTreeRoot(), new Path());

        Assert.assertEquals(stringArrayNode(expectedValues), doc.get(new Path("field6.nf8")));
    }

    @Test
    public void string_array_insert_with_2_$parent_relative_path_and_array_expansion() throws Exception {
        String[] expectedValues = {"four", "three", "two", "one", null, "five", "six", "value2"};
        UpdateExpression expr = EvalTestContext.updateExpressionFromJson("{ '$insert' : { 'field6.nf7.nnf1.$parent.$parent.nf8.5' : [ 'five','six',{'$valueof':'field2' }] } }");
        Updater updater = Updater.getInstance(JSON_NODE_FACTORY, md, expr);

        updater.update(doc, md.getFieldTreeRoot(), new Path());

        Assert.assertEquals(stringArrayNode(expectedValues), doc.get(new Path("field6.nf8")));
    }

    @Test
    public void string_array_insert_null() throws Exception {
        String[] expectedValues = {"four", "three", null, null, "two", "one"};
        UpdateExpression expr = EvalTestContext.updateExpressionFromJson("{ '$insert' : { 'field6.nf8.2' : [ '$null', '$null'] } }");
        Updater updater = Updater.getInstance(JSON_NODE_FACTORY, md, expr);

        updater.update(doc, md.getFieldTreeRoot(), new Path());

        Assert.assertEquals(stringArrayNode(expectedValues), doc.get(new Path("field6.nf8")));
    }

    @Test
    public void string_array_append_null() throws Exception {
        String[] expectedValues = {"four", "three", "two", "one", null, null};
        UpdateExpression expr = EvalTestContext.updateExpressionFromJson("{ '$append' : { 'field6.nf8' : [ '$null', '$null'] } }");
        Updater updater = Updater.getInstance(JSON_NODE_FACTORY, md, expr);

        updater.update(doc, md.getFieldTreeRoot(), new Path());

        Assert.assertEquals(stringArrayNode(expectedValues), doc.get(new Path("field6.nf8")));
    }

    @Test
    public void object_array_append() throws Exception {
        UpdateExpression expr = EvalTestContext.updateExpressionFromJson(" [ {'$append' : { 'field7' : {} } }, {'$set': {'field7.-1.elemf1':'value'}} ]");
        Updater updater = Updater.getInstance(JSON_NODE_FACTORY, md, expr);
        updater.update(doc, md.getFieldTreeRoot(), new Path());
        JsonNode f7 = doc.get(new Path("field7"));
        Assert.assertEquals(5, f7.size());
        Assert.assertEquals("value", f7.get(4).get("elemf1").asText());
    }

    @Test
    public void append_object_to_array() throws Exception {
        UpdateExpression expr = EvalTestContext.updateExpressionFromJson("{'$append': {'field7':{'elemf1':'value'}}}");
        Updater updater = Updater.getInstance(JSON_NODE_FACTORY, md, expr);
        updater.update(doc, md.getFieldTreeRoot(), new Path());
        JsonNode f7 = doc.get(new Path("field7"));
        Assert.assertEquals(5, f7.size());
        Assert.assertEquals("value", f7.get(4).get("elemf1").asText());

    }

    @Test
    public void insert_object_to_array() throws Exception {
        UpdateExpression expr = EvalTestContext.updateExpressionFromJson("{'$insert': {'field7.0':{'elemf1':'value'}}}");
        Updater updater = Updater.getInstance(JSON_NODE_FACTORY, md, expr);
        updater.update(doc, md.getFieldTreeRoot(), new Path());
        JsonNode f7 = doc.get(new Path("field7"));
        Assert.assertEquals(5, f7.size());
        Assert.assertEquals("value", f7.get(0).get("elemf1").asText());

    }

}
