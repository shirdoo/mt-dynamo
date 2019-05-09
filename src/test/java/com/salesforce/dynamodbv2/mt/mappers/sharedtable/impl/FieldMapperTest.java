/*
 * Copyright (c) 2018, salesforce.com, inc.
 * All rights reserved.
 * Licensed under the BSD 3-Clause license.
 * For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.dynamodbv2.mt.mappers.sharedtable.impl;

import static com.amazonaws.services.dynamodbv2.model.ScalarAttributeType.B;
import static com.amazonaws.services.dynamodbv2.model.ScalarAttributeType.N;
import static com.amazonaws.services.dynamodbv2.model.ScalarAttributeType.S;
import static com.salesforce.dynamodbv2.mt.mappers.sharedtable.impl.FieldMapping.IndexType.SECONDARY_INDEX;
import static com.salesforce.dynamodbv2.mt.mappers.sharedtable.impl.FieldMapping.IndexType.TABLE;
import static java.util.UUID.randomUUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType;
import com.salesforce.dynamodbv2.mt.context.MtAmazonDynamoDbContextProvider;
import com.salesforce.dynamodbv2.mt.mappers.sharedtable.impl.FieldMapping.Field;
import com.salesforce.dynamodbv2.mt.mappers.sharedtable.impl.FieldMapping.IndexType;

import java.nio.charset.Charset;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/**
 * TODO: write Javadoc.
 *
 * @author msgroi
 */
class FieldMapperTest {

    private static final char DELIMITER = '.';

    @Test
    void applyTableIndex() {
        MtAmazonDynamoDbContextProvider mtContext = buildMtContext();
        String value = generateValue();
        assertMapper(S,
            TABLE,
            () -> new AttributeValue().withS(value),
            mtContext.getContext() + DELIMITER + "virtualTable" + DELIMITER + value,
            mtContext);
    }

    @Test
    void applySecondaryIndex() {
        MtAmazonDynamoDbContextProvider mtContext = buildMtContext();
        String value = generateValue();
        assertMapper(S,
            SECONDARY_INDEX,
            () -> new AttributeValue().withS(value),
            mtContext.getContext() + DELIMITER + "virtualTable" + DELIMITER + value,
            mtContext);
    }

    @Test
    void applyTableIndexNumber() {
        MtAmazonDynamoDbContextProvider mtContext = buildMtContext();
        assertMapper(N,
            TABLE,
            () -> new AttributeValue().withN("123"),
            mtContext.getContext() + DELIMITER + "virtualTable" + DELIMITER + "123",
            mtContext);
    }

    @Test
    void applyTableIndexByteArray() {
        MtAmazonDynamoDbContextProvider mtContext = buildMtContext();
        assertMapper(B,
            TABLE,
            () -> new AttributeValue().withB(Charset.defaultCharset().encode("byte_buffer")),
            mtContext.getContext() + DELIMITER + "virtualTable" + DELIMITER + "byte_buffer",
            mtContext);
    }

    @Test
    void applyValueNotFound() {
        try {
            buildFieldMapper(buildMtContext()).apply(buildFieldMapping(N, TABLE), new AttributeValue().withS("value"));
        } catch (NullPointerException e) {
            // expected
            assertEquals("attributeValue={S: value,} of type=N could not be converted", e.getMessage());
        }
    }

    @Test
    void invalidType() {
        try {
            buildFieldMapper(buildMtContext())
                    .apply(buildFieldMapping(null, TABLE), new AttributeValue().withS(generateValue()));
            fail("Expected NullPointerException not thrown");
        } catch (NullPointerException e) {
            // expected
            assertEquals("null attribute type", e.getMessage());
        }
    }

    private void assertMapper(ScalarAttributeType fieldType,
                              IndexType indexType,
                              Supplier<AttributeValue> attributeValue,
                              String expectedStringValue,
                              MtAmazonDynamoDbContextProvider mtContext) {
        FieldMapping fieldMapping = buildFieldMapping(fieldType, indexType);
        FieldMapper fieldMapper = buildFieldMapper(mtContext);
        AttributeValue qualifiedAttributeValue = fieldMapper.apply(fieldMapping, attributeValue.get());
        assertEquals(expectedStringValue, qualifiedAttributeValue.getS());
        AttributeValue actualAttributeValue = fieldMapper
                .reverse(reverseFieldMapping(fieldMapping), qualifiedAttributeValue);
        assertEquals(attributeValue.get(), actualAttributeValue);
    }

    private FieldMapping buildFieldMapping(ScalarAttributeType sourceFieldType, IndexType indexType) {
        return new FieldMapping(
            new Field("sourceField", sourceFieldType),
            new Field("targetField", S),
            "virtualIndex",
            "physicalIndex",
            indexType,
            true);
    }


    private FieldMapping reverseFieldMapping(FieldMapping fieldMapping) {
        return new FieldMapping(
            fieldMapping.getTarget(),
            fieldMapping.getSource(),
            fieldMapping.getVirtualIndexName(),
            fieldMapping.getPhysicalIndexName(),
            fieldMapping.getIndexType(),
            fieldMapping.isContextAware());
    }

    private FieldMapper buildFieldMapper(MtAmazonDynamoDbContextProvider mtContext) {
        return new FieldMapper(mtContext,
            "virtualTable",
            new FieldPrefixFunction(DELIMITER));
    }

    private static String random() {
        return randomUUID().toString();
    }

    private MtAmazonDynamoDbContextProvider buildMtContext() {
        return new MtAmazonDynamoDbContextProvider() {
            final String context = random();

            @Override
            public Optional<String> getContextOpt() {
                return Optional.of(this.context);
            }

            @Override
            public void withContext(String org, Runnable runnable) {
            }
        };
    }

    private String generateValue() {
        return random();
    }

}