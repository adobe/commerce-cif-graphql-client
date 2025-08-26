/*******************************************************************************
 *
 *    Copyright 2025 Adobe. All rights reserved.
 *    This file is licensed to you under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License. You may obtain a copy
 *    of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software distributed under
 *    the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
 *    OF ANY KIND, either express or implied. See the License for the specific language
 *    governing permissions and limitations under the License.
 *
 ******************************************************************************/

package com.adobe.cq.commerce.graphql.client;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public class GraphqlResponseTest {

    // Test constants
    private static final String TEST_DATA = "test-data";
    private static final String ERROR_MESSAGE = "test-error";
    private static final long DURATION_MS = 150L;
    private static final long ZERO_DURATION = 0L;

    // Test data classes
    static class TestData {
        String value;
        TestData(String value) { this.value = value; }
    }

    static class TestError {
        String message;
        TestError(String message) { this.message = message; }
    }

    @Test
    public void testDefaultValues() {
        GraphqlResponse<String, String> response = new GraphqlResponse<>();
        
        assertNull("Default data should be null", response.getData());
        assertNull("Default errors should be null", response.getErrors());
        assertEquals("Default duration should be 0", ZERO_DURATION, response.getDuration());
    }

    @Test
    public void testDataGetterSetter() {
        GraphqlResponse<String, String> response = new GraphqlResponse<>();
        
        response.setData(TEST_DATA);
        assertEquals("Data should be set and retrieved correctly", TEST_DATA, response.getData());
        
        response.setData(null);
        assertNull("Data should be null after setting to null", response.getData());
    }

    @Test
    public void testErrorsGetterSetter() {
        GraphqlResponse<String, String> response = new GraphqlResponse<>();
        List<String> errors = Arrays.asList(ERROR_MESSAGE, "second-error");
        
        response.setErrors(errors);
        assertEquals("Errors should be set and retrieved correctly", errors, response.getErrors());
        assertEquals("Error list size should be correct", 2, response.getErrors().size());
        assertEquals("First error should match", ERROR_MESSAGE, response.getErrors().get(0));
        
        response.setErrors(null);
        assertNull("Errors should be null after setting to null", response.getErrors());
    }

    @Test
    public void testDurationGetterSetter() {
        GraphqlResponse<String, String> response = new GraphqlResponse<>();
        
        response.setDuration(DURATION_MS);
        assertEquals("Duration should be set and retrieved correctly", DURATION_MS, response.getDuration());
        
        response.setDuration(ZERO_DURATION);
        assertEquals("Duration should be zero", ZERO_DURATION, response.getDuration());
    }

    @Test
    public void testCompleteResponseWithGenerics() {
        GraphqlResponse<TestData, TestError> response = new GraphqlResponse<>();
        TestData testData = new TestData("sample-value");
        List<TestError> testErrors = Arrays.asList(new TestError("error1"), new TestError("error2"));
        
        response.setData(testData);
        response.setErrors(testErrors);
        response.setDuration(DURATION_MS);
        
        assertNotNull("Data should not be null", response.getData());
        assertEquals("Data value should match", "sample-value", response.getData().value);
        assertEquals("Errors size should be 2", 2, response.getErrors().size());
        assertEquals("First error message should match", "error1", response.getErrors().get(0).message);
        assertEquals("Duration should match", DURATION_MS, response.getDuration());
    }

    @Test
    public void testResponseWithEmptyCollections() {
        GraphqlResponse<String, String> response = new GraphqlResponse<>();
        List<String> emptyErrors = Arrays.asList();
        
        response.setData("");
        response.setErrors(emptyErrors);
        response.setDuration(ZERO_DURATION);
        
        assertEquals("Empty string data should be preserved", "", response.getData());
        assertNotNull("Empty errors list should not be null", response.getErrors());
        assertTrue("Errors list should be empty", response.getErrors().isEmpty());
        assertEquals("Zero duration should be preserved", ZERO_DURATION, response.getDuration());
    }
}
