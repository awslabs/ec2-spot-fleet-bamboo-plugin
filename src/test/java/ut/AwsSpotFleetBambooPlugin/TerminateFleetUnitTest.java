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

import static org.junit.Assert.fail;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.LinkedList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations.Mock;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.reflect.Whitebox;

import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.model.CancelSpotFleetRequestsRequest;
import com.amazonaws.services.ec2.model.CancelSpotFleetRequestsResult;
import com.amazonaws.services.ec2.model.CancelSpotFleetRequestsSuccessItem;
import com.atlassian.bamboo.build.BuildExecutionManager;
import com.atlassian.bamboo.build.logger.BuildLogger;
import com.atlassian.bamboo.buildqueue.manager.AgentManager;
import com.atlassian.bamboo.v2.build.queue.BuildQueueManager;
import com.atlassian.bamboo.v2.build.queue.BuildQueueManager.QueuedResultKey;
import com.google.common.collect.ImmutableList;

import AwsSpotFleetBambooPlugin.SpotFleetTaskExecution;

@RunWith(PowerMockRunner.class)
@PrepareForTest(SpotFleetTaskExecution.class)

public class TerminateFleetUnitTest {

    private SpotFleetTaskExecution taskExecutor;
    @Mock
    private AgentManager agentManager;
    @Mock
    private BuildExecutionManager buildExecutionManager;
    @Mock
    private CancelSpotFleetRequestsRequest cancelSpotFleetRequestsRequest;
    @Mock
    private AmazonEC2 EC2Client;
    @Mock
    private BuildQueueManager buildQueueManager;
    @Mock
    private CancelSpotFleetRequestsResult cancelSpotFleetRequestsResult;
    private List<CancelSpotFleetRequestsSuccessItem> cancelRequestsList;
    @Mock
    private CancelSpotFleetRequestsSuccessItem cancelSpotFleetRequestsSuccessItem;
    @Mock
    BuildLogger buildLogger;

    @Before
    public void setup() throws Exception {
        PowerMockito.whenNew(CancelSpotFleetRequestsRequest.class).withNoArguments()
                .thenReturn(cancelSpotFleetRequestsRequest);
        when(cancelSpotFleetRequestsRequest.withSpotFleetRequestIds(anyString()))
                .thenReturn(cancelSpotFleetRequestsRequest);
        when(cancelSpotFleetRequestsRequest.withTerminateInstances(true))
            .thenReturn(cancelSpotFleetRequestsRequest);
        when(EC2Client.cancelSpotFleetRequests(cancelSpotFleetRequestsRequest))
                .thenReturn(cancelSpotFleetRequestsResult);
        // create empty build queue
        when(buildQueueManager.getQueuedExecutables()).thenReturn(new LinkedList<QueuedResultKey>());
        cancelRequestsList = ImmutableList.of(cancelSpotFleetRequestsSuccessItem);
        when(cancelSpotFleetRequestsResult.getSuccessfulFleetRequests()).thenReturn(cancelRequestsList);
        taskExecutor = new SpotFleetTaskExecution(null, buildQueueManager, agentManager, buildExecutionManager);
        Whitebox.setInternalState(taskExecutor, "buildLogger", buildLogger);
        Whitebox.setInternalState(taskExecutor, "EC2Client", EC2Client);
    }

    @Test
    public void terminationRequestSucceedsTest() {
        try {
            org.powermock.reflect.Whitebox.invokeMethod(taskExecutor, "terminateFleet", "fleet");
        } catch (Exception e) {
            e.printStackTrace();
            fail("Test failed due to unexpected exception");
        }
        // fail if fleet cancellation success message isn't logged
        verify(buildLogger).addBuildLogEntry("Cancellation of fleet succeeded");
    }

    @Test
    public void terminationRequestFailsTest() {
        cancelRequestsList = new LinkedList<CancelSpotFleetRequestsSuccessItem>();
        when(cancelSpotFleetRequestsResult.getSuccessfulFleetRequests()).thenReturn(cancelRequestsList);

        try {
            org.powermock.reflect.Whitebox.invokeMethod(taskExecutor, "terminateFleet", "fleet");
        } catch (Exception e) {
            e.printStackTrace();
            fail("Test failed due to unexpected exception");
        }
        // fail if fleet cancellation failure message isn't logged
        verify(buildLogger).addBuildLogEntry("Cancellation of fleet failed");
    }
}
