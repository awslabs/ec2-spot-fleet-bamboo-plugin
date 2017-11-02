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

import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import com.atlassian.bamboo.collections.ActionParametersMap;
import com.atlassian.bamboo.configuration.AdministrationConfigurationAccessor;
import com.atlassian.bamboo.utils.error.ErrorCollection;
import com.atlassian.bamboo.utils.error.SimpleErrorCollection;
import com.google.common.collect.ImmutableMap;

import AwsSpotFleetBambooPlugin.SpotFleetConfigurator;
import AwsSpotFleetBambooPlugin.StringConstants;

@RunWith(MockitoJUnitRunner.class)
public class ConfiguratorUnitTest {

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
        when(params.getString(StringConstants.REGION)).thenReturn("us-east-1");
        when(params.getBoolean(StringConstants.ENABLE_AUTOSCALING)).thenReturn(true);
        when(params.getBoolean(StringConstants.TERMINATE_FLEET)).thenReturn(true);
        when(params.getInt(StringConstants.QUEUED_BUILDS, -1)).thenReturn(6);
        when(params.getInt(StringConstants.AVERAGE_QUEUE_TIME, -1)).thenReturn(5);
        when(params.getInt(StringConstants.IDLE_INSTANCES, -1)).thenReturn(4);
        when(params.getInt(StringConstants.MAXIMUM_UNITS_PER_SCALE, -1)).thenReturn(2);
    }

    @Test
    public void generateTaskConfigMapPullsParamsCorrectlyTest() {
        Map<String, String> actualConfigMap = spotFleetConfigurator.generateTaskConfigMap(params, null);
        Map<String, String> expectedConfigMap = new HashMap<String, String>();
        expectedConfigMap = ImmutableMap.<String, String>builder().put(StringConstants.ACCESS_KEY, "access")
                .put(StringConstants.SECRET_KEY, "secret").put(StringConstants.FLEET_ID, "fleetid")
                .put(StringConstants.REGION, "us-east-1").put(StringConstants.ENABLE_AUTOSCALING, "true")
                .put(StringConstants.TERMINATE_FLEET, "true").put(StringConstants.QUEUED_BUILDS, "6")
                .put(StringConstants.AVERAGE_QUEUE_TIME, "5").put(StringConstants.IDLE_INSTANCES, "4")
                .put(StringConstants.MAXIMUM_UNITS_PER_SCALE, "2").build();
        assertEquals(expectedConfigMap, actualConfigMap);
    }
}
