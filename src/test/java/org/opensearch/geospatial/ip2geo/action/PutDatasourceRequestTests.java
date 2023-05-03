/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package org.opensearch.geospatial.ip2geo.action;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;

import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.common.Randomness;
import org.opensearch.common.Strings;
import org.opensearch.common.io.stream.BytesStreamInput;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.geospatial.GeospatialTestHelper;
import org.opensearch.geospatial.ip2geo.Ip2GeoTestCase;

public class PutDatasourceRequestTests extends Ip2GeoTestCase {

    public void testValidateWithInvalidUrl() {
        String datasourceName = GeospatialTestHelper.randomLowerCaseString();
        PutDatasourceRequest request = new PutDatasourceRequest(datasourceName);
        request.setEndpoint("invalidUrl");
        request.setUpdateInterval(TimeValue.timeValueDays(1));
        ActionRequestValidationException exception = request.validate();
        assertEquals(1, exception.validationErrors().size());
        assertEquals("Invalid URL format is provided", exception.validationErrors().get(0));
    }

    public void testValidateWithInvalidManifestFile() {
        String datasourceName = GeospatialTestHelper.randomLowerCaseString();
        String domain = GeospatialTestHelper.randomLowerCaseString();
        PutDatasourceRequest request = new PutDatasourceRequest(datasourceName);
        request.setEndpoint(String.format(Locale.ROOT, "https://%s.com", domain));
        request.setUpdateInterval(TimeValue.timeValueDays(1));
        ActionRequestValidationException exception = request.validate();
        assertEquals(1, exception.validationErrors().size());
        assertEquals(
            String.format(Locale.ROOT, "Error occurred while reading a file from %s", request.getEndpoint()),
            exception.validationErrors().get(0)
        );
    }

    public void testValidate() throws Exception {
        String datasourceName = GeospatialTestHelper.randomLowerCaseString();
        PutDatasourceRequest request = new PutDatasourceRequest(datasourceName);
        request.setEndpoint(sampleManifestUrl());
        request.setUpdateInterval(TimeValue.timeValueDays(1));
        assertNull(request.validate());
    }

    public void testValidateWithZeroUpdateInterval() throws Exception {
        String datasourceName = GeospatialTestHelper.randomLowerCaseString();
        PutDatasourceRequest request = new PutDatasourceRequest(datasourceName);
        request.setEndpoint(sampleManifestUrl());
        request.setUpdateInterval(TimeValue.timeValueDays(0));

        // Run
        ActionRequestValidationException exception = request.validate();

        // Verify
        assertEquals(1, exception.validationErrors().size());
        assertEquals(
            String.format(Locale.ROOT, "Update interval should be equal to or larger than 1 day"),
            exception.validationErrors().get(0)
        );
    }

    public void testValidateWithLargeUpdateInterval() throws Exception {
        String datasourceName = GeospatialTestHelper.randomLowerCaseString();
        PutDatasourceRequest request = new PutDatasourceRequest(datasourceName);
        request.setEndpoint(sampleManifestUrl());
        request.setUpdateInterval(TimeValue.timeValueDays(30));

        // Run
        ActionRequestValidationException exception = request.validate();

        // Verify
        assertEquals(1, exception.validationErrors().size());
        assertTrue(exception.validationErrors().get(0).contains("should be smaller"));
    }

    public void testValidateWithInvalidUrlInsideManifest() throws Exception {
        String datasourceName = GeospatialTestHelper.randomLowerCaseString();
        PutDatasourceRequest request = new PutDatasourceRequest(datasourceName);
        request.setEndpoint(sampleManifestUrlWithInvalidUrl());
        request.setUpdateInterval(TimeValue.timeValueDays(1));

        // Run
        ActionRequestValidationException exception = request.validate();

        // Verify
        assertEquals(1, exception.validationErrors().size());
        assertTrue(exception.validationErrors().get(0).contains("Invalid URL format"));
    }

    public void testValidateDatasourceNames() throws Exception {
        String validDatasourceName = GeospatialTestHelper.randomLowerCaseString();
        String domain = GeospatialTestHelper.randomLowerCaseString();
        PutDatasourceRequest request = new PutDatasourceRequest(validDatasourceName);
        request.setEndpoint(sampleManifestUrl());
        request.setUpdateInterval(TimeValue.timeValueDays(Randomness.get().nextInt(30) + 1));

        // Run
        ActionRequestValidationException exception = request.validate();

        // Verify
        assertNull(exception);

        String fileNameChar = validDatasourceName + Strings.INVALID_FILENAME_CHARS.stream()
            .skip(Randomness.get().nextInt(Strings.INVALID_FILENAME_CHARS.size() - 1))
            .findFirst();
        String startsWith = Arrays.asList("_", "-", "+").get(Randomness.get().nextInt(3)) + validDatasourceName;
        String empty = "";
        String hash = validDatasourceName + "#";
        String colon = validDatasourceName + ":";
        StringBuilder longName = new StringBuilder();
        while (longName.length() < 256) {
            longName.append(GeospatialTestHelper.randomLowerCaseString());
        }
        String point = Arrays.asList(".", "..").get(Randomness.get().nextInt(2));
        Map<String, String> nameToError = Map.of(
            fileNameChar,
            "not contain the following characters",
            empty,
            "must not be empty",
            hash,
            "must not contain '#'",
            colon,
            "must not contain ':'",
            startsWith,
            "must not start with",
            longName.toString(),
            "name is too long",
            point,
            "must not be '.' or '..'"
        );

        for (Map.Entry<String, String> entry : nameToError.entrySet()) {
            request.setDatasourceName(entry.getKey());

            // Run
            exception = request.validate();

            // Verify
            assertEquals(1, exception.validationErrors().size());
            assertTrue(exception.validationErrors().get(0).contains(entry.getValue()));
        }
    }

    public void testStreamInOut() throws Exception {
        String datasourceName = GeospatialTestHelper.randomLowerCaseString();
        String domain = GeospatialTestHelper.randomLowerCaseString();
        PutDatasourceRequest request = new PutDatasourceRequest(datasourceName);
        request.setEndpoint(String.format(Locale.ROOT, "https://%s.com", domain));
        request.setUpdateInterval(TimeValue.timeValueDays(Randomness.get().nextInt(30) + 1));

        // Run
        BytesStreamOutput output = new BytesStreamOutput();
        request.writeTo(output);
        BytesStreamInput input = new BytesStreamInput(output.bytes().toBytesRef().bytes);
        PutDatasourceRequest copiedRequest = new PutDatasourceRequest(input);

        // Verify
        assertEquals(request, copiedRequest);
    }
}