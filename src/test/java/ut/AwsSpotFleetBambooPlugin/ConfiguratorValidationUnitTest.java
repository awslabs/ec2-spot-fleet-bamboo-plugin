/*
 * Copyright 2017 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package ut.AwsSpotFleetBambooPlugin;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import com.atlassian.bamboo.collections.ActionParametersMap;
import com.atlassian.bamboo.configuration.AdministrationConfigurationAccessor;
import com.atlassian.bamboo.utils.error.ErrorCollection;
import com.atlassian.bamboo.utils.error.SimpleErrorCollection;

import AwsSpotFleetBambooPlugin.SpotFleetConfigurator;
import AwsSpotFleetBambooPlugin.StringConstants;

@RunWith(MockitoJUnitRunner.class)
public class ConfiguratorValidationUnitTest {

    @Mock
    private AdministrationConfigurationAccessor administrationConfigurationAccessor;
    @Mock
    private ActionParametersMap params;
    private ErrorCollection actualErrorCollection;
    private ErrorCollection expectedErrorCollection;
    private SpotFleetConfigurator spotFleetConfigurator;

    @Before
    public void setup() {
        actualErrorCollection = new SimpleErrorCollection();
        expectedErrorCollection = new SimpleErrorCollection();
        spotFleetConfigurator = new SpotFleetConfigurator(administrationConfigurationAccessor);
        when(params.getString(StringConstants.ACCESS_KEY)).thenReturn("access");
        when(params.getString(StringConstants.SECRET_KEY)).thenReturn("secret");
        when(params.getString(StringConstants.FLEET_ID)).thenReturn("fleetid");
        when(params.getString(StringConstants.REGION)).thenReturn("us-west-2 (Orgeon)");
        when(params.getString(StringConstants.ENABLE_AUTOSCALING)).thenReturn("true");
        when(params.getString(StringConstants.QUEUED_BUILDS)).thenReturn("5");
        when(params.getString(StringConstants.AVERAGE_QUEUE_TIME)).thenReturn("5");
        when(params.getString(StringConstants.IDLE_INSTANCES)).thenReturn("4");
        when(params.getString(StringConstants.MAXIMUM_UNITS_PER_SCALE)).thenReturn("2");
    }

    @Test
    public void emptyAWSAccessKeyThrowsErrorTest() {
        when(params.getString(StringConstants.ACCESS_KEY)).thenReturn(null);
        spotFleetConfigurator.validate(params, actualErrorCollection);
        expectedErrorCollection.addError(StringConstants.ACCESS_KEY, "Can't be empty");
        assertEquals(expectedErrorCollection.getErrors(), actualErrorCollection.getErrors());
    }

    @Test
    public void emptyAWSSecretKeyThrowsErrorTest() {
        when(params.getString(StringConstants.SECRET_KEY)).thenReturn(null);
        spotFleetConfigurator.validate(params, actualErrorCollection);
        expectedErrorCollection.addError(StringConstants.SECRET_KEY, "Can't be empty");
        assertEquals(expectedErrorCollection.getErrors(), actualErrorCollection.getErrors());
    }

    @Test
    public void emptyAWSFleetIDThrowsErrorTest() {
        when(params.getString(StringConstants.FLEET_ID)).thenReturn(null);
        spotFleetConfigurator.validate(params, actualErrorCollection);
        expectedErrorCollection.addError(StringConstants.FLEET_ID, "Can't be empty");
        assertEquals(expectedErrorCollection.getErrors(), actualErrorCollection.getErrors());
    }

    @Test
    public void emptyQueuedBuildValueThrowErrorsTest() {
        when(params.getString(StringConstants.QUEUED_BUILDS)).thenReturn(null);
        spotFleetConfigurator.validate(params, actualErrorCollection);
        expectedErrorCollection.addError(StringConstants.ENABLE_AUTOSCALING,
                "Autoscaling inputs must be positive integers");
        assertEquals(expectedErrorCollection.getErrors(), actualErrorCollection.getErrors());
    }

    @Test
    public void emptyAverageQueueTimeValueThrowErrorsTest() {
        when(params.getString(StringConstants.AVERAGE_QUEUE_TIME)).thenReturn(null);
        spotFleetConfigurator.validate(params, actualErrorCollection);
        expectedErrorCollection.addError(StringConstants.ENABLE_AUTOSCALING,
                "Autoscaling inputs must be positive integers");
        assertEquals(expectedErrorCollection.getErrors(), actualErrorCollection.getErrors());
    }

    @Test
    public void emptyIdleInstancesValueThrowErrorsTest() {
        when(params.getString(StringConstants.IDLE_INSTANCES)).thenReturn(null);
        spotFleetConfigurator.validate(params, actualErrorCollection);
        expectedErrorCollection.addError(StringConstants.ENABLE_AUTOSCALING,
                "Autoscaling inputs must be positive integers");
        assertEquals(expectedErrorCollection.getErrors(), actualErrorCollection.getErrors());
    }

    @Test
    public void emptyUnitsPerScaleValueThrowErrorsTest() {
        when(params.getString(StringConstants.MAXIMUM_UNITS_PER_SCALE)).thenReturn(null);
        spotFleetConfigurator.validate(params, actualErrorCollection);
        expectedErrorCollection.addError(StringConstants.ENABLE_AUTOSCALING,
                "Autoscaling inputs must be positive integers");
        assertEquals(expectedErrorCollection.getErrors(), actualErrorCollection.getErrors());
    }

    @Test
    public void negativeQueuedBuildValueThrowErrorsTest() {
        when(params.getString(StringConstants.QUEUED_BUILDS)).thenReturn("-1");
        spotFleetConfigurator.validate(params, actualErrorCollection);
        expectedErrorCollection.addError(StringConstants.QUEUED_BUILDS, "Integer value must be positive");
        assertEquals(expectedErrorCollection.getErrors(), actualErrorCollection.getErrors());
    }

    @Test
    public void negativeAverageQueueTimeValueThrowErrors() {
        when(params.getString(StringConstants.AVERAGE_QUEUE_TIME)).thenReturn("-1");
        spotFleetConfigurator.validate(params, actualErrorCollection);
        expectedErrorCollection.addError(StringConstants.AVERAGE_QUEUE_TIME, "Integer value must be positive");
        assertEquals(expectedErrorCollection.getErrors(), actualErrorCollection.getErrors());
    }

    @Test
    public void negativeIdleInstancesValueThrowErrorsTest() {
        when(params.getString(StringConstants.IDLE_INSTANCES)).thenReturn("-1");
        spotFleetConfigurator.validate(params, actualErrorCollection);
        expectedErrorCollection.addError(StringConstants.IDLE_INSTANCES, "Integer value must be positive");
        assertEquals(expectedErrorCollection.getErrors(), actualErrorCollection.getErrors());
    }

    @Test
    public void negativeUnitsPerScaleValueThrowErrorsTest() {
        when(params.getString(StringConstants.MAXIMUM_UNITS_PER_SCALE)).thenReturn("-1");
        spotFleetConfigurator.validate(params, actualErrorCollection);
        expectedErrorCollection.addError(StringConstants.MAXIMUM_UNITS_PER_SCALE, "Integer value must be positive");
        assertEquals(expectedErrorCollection.getErrors(), actualErrorCollection.getErrors());
    }
}
