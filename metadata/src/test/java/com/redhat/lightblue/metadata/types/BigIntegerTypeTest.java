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
import com.redhat.lightblue.metadata.Type;
import com.redhat.lightblue.util.Error;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.math.BigInteger;

import static org.junit.Assert.*;

public class BigIntegerTypeTest {

    Type bigIntegerType;

    @Before
    public void setUp() throws Exception {
        bigIntegerType = BigIntegerType.TYPE;
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void testIsAContainerType() {
        assertTrue(ContainerType.class.isAssignableFrom(ArrayType.class));
    }

    @Test
    public void testGetName() {
        assertEquals(bigIntegerType.getName(), BigIntegerType.NAME);
    }

    @Test
    public void testSupportsEq() {
        assertTrue(bigIntegerType.supportsEq());
    }

    @Test
    public void testSupportsOrdering() {
        assertFalse(bigIntegerType.supportsOrdering());
    }

    @Test
    public void testToJson() {
        JsonNodeFactory jsonNodeFactory = new JsonNodeFactory(true);
        JsonNode jsonNode = bigIntegerType.toJson(jsonNodeFactory, BigInteger.ZERO);
        assertTrue(new BigInteger(jsonNode.asText()).equals(BigInteger.ZERO));
    }

    @Test
    public void testFromJson() {
        JsonNode jsonNode = JsonNodeFactory.withExactBigDecimals(false).numberNode(BigInteger.TEN);
        Object fromJson = bigIntegerType.fromJson(jsonNode);
        assertTrue(fromJson instanceof BigInteger);
    }

    @Test(expected = Error.class)
    public void testFromJsonWithIncompatibleValue() {
        JsonNode jsonNode = JsonNodeFactory.withExactBigDecimals(false).objectNode();
        bigIntegerType.fromJson(jsonNode);
    }

    @Test
    public void testFromJsonStr() {
        JsonNode jsonNode = JsonNodeFactory.instance.textNode("100");
        Object fromJson = bigIntegerType.fromJson(jsonNode);
        assertEquals(new BigInteger("100"), fromJson);
    }

    @Test
    public void testCastNull() {
        assertNull(bigIntegerType.cast(null));
    }

    @Test
    public void testCastBigInteger() {
        assertTrue(bigIntegerType.cast(BigInteger.ONE) instanceof BigInteger);
    }

    @Test
    public void testCastLong() {
        assertTrue(bigIntegerType.cast(Long.MAX_VALUE) instanceof BigInteger);
    }

    @Test
    public void testCastBooleanTrue() {
        assertTrue(bigIntegerType.cast(Boolean.TRUE) instanceof BigInteger);
    }

    @Test
    public void testCastBooleanFalse() {
        assertTrue(bigIntegerType.cast(Boolean.FALSE) instanceof BigInteger);
    }

    @Test
    public void testCastGoodString() {
        assertTrue(bigIntegerType.cast(String.valueOf(BigInteger.TEN)) instanceof BigInteger);
    }

    @Test(expected = Error.class)
    public void testCastBadString() {
        bigIntegerType.cast("string");
    }

    @Test(expected = Error.class)
    public void testCastOther() {
        Object object = new Object();
        bigIntegerType.cast(object);
    }

    @Test
    public void testCompareBothNull() {
        assertEquals(bigIntegerType.compare(null, null), 0);
    }

    @Test
    public void testCompareV1Null() {
        assertEquals(bigIntegerType.compare(null, new Object()), -1);
    }

    @Test
    public void testCompareV2Null() {
        assertEquals(bigIntegerType.compare(new Object(), null), 1);
    }

    @Test
    public void testCompareEqual() {
        assertEquals(bigIntegerType.compare((Object) BigInteger.ONE, (Object) BigInteger.ONE), 0);
    }

    @Test
    public void testCompareNotEqual() {
        assertEquals(bigIntegerType.compare((Object) BigInteger.ZERO, (Object) BigInteger.ONE), -1);
    }

    @Test
    public void testEqualsTrue() {
        assertTrue(bigIntegerType.equals(BigIntegerType.TYPE));
    }

    @Test
    public void testEqualsFalse() {
        assertFalse(bigIntegerType.equals(Double.MAX_VALUE));
    }

    @Test
    public void testHashCode() {
        assertEquals(bigIntegerType.hashCode(), 5);
    }

    @Test
    public void testToString() {
        assertEquals(bigIntegerType.toString(), BigIntegerType.NAME);
    }

}
