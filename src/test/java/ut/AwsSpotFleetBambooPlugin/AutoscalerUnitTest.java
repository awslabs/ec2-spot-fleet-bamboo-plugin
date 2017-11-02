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
import static org.junit.Assert.fail;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.when;

import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations.Mock;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.reflect.Whitebox;

import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;
import com.amazonaws.services.ec2.model.TerminateInstancesResult;
import com.atlassian.bamboo.ResultKey;
import com.atlassian.bamboo.build.BuildExecutionManager;
import com.atlassian.bamboo.buildqueue.manager.AgentManager;
import com.atlassian.bamboo.chains.ChainExecutionManager;
import com.atlassian.bamboo.configuration.AdministrationConfigurationAccessor;
import com.atlassian.bamboo.plan.PlanExecutionManager;
import com.atlassian.bamboo.v2.build.CommonContext;
import com.atlassian.bamboo.v2.build.CurrentResult;
import com.atlassian.bamboo.v2.build.agent.BuildAgent;
import com.atlassian.bamboo.v2.build.queue.BuildQueueManager;
import com.atlassian.bamboo.v2.build.queue.BuildQueueManager.QueueItemView;
import com.atlassian.bamboo.v2.build.queue.BuildQueueManager.QueuedResultKey;
import com.google.common.collect.ImmutableList;

import AwsSpotFleetBambooPlugin.SpotFleetTaskExecution;

@RunWith(PowerMockRunner.class)
@PrepareForTest(SpotFleetTaskExecution.class)
public class AutoscalerUnitTest {

    @Mock
    private AdministrationConfigurationAccessor administrationConfigurationAccessor;
    @Mock
    private AgentManager agentManager;
    @Mock
    private BuildQueueManager buildQueueManager;
    @Mock
    private Iterator<QueuedResultKey> buildQueueIterator;
    @Mock
    private BuildExecutionManager buildExecutionManager;
    @Mock
    private ChainExecutionManager chainExecutionManager;
    @Mock
    private PlanExecutionManager planExecutionManager;
    @Mock
    BuildAgent agent1;
    @Mock
    BuildAgent agent2;
    @Mock
    BuildAgent agent3;
    @Mock
    BuildAgent agent4;
    @Mock
    BuildAgent agent5;
    @Mock
    BuildAgent agent6;
    @Mock
    BuildAgent agent7;
    @Mock
    BuildAgent agent8;
    @Mock
    BuildAgent agent9;
    @Mock
    BuildAgent agent10;
    // queue items require these three objects to be mocked
    @Mock
    ResultKey resultKey1;
    @Mock
    CommonContext commonContext1;
    @Mock
    CurrentResult currentResult1;
    @Mock
    ResultKey resultKey2;
    @Mock
    CommonContext commonContext2;
    @Mock
    CurrentResult currentResult2;
    @Mock
    ResultKey resultKey3;
    @Mock
    CommonContext commonContext3;
    @Mock
    CurrentResult currentResult3;
    @Mock
    AmazonEC2 EC2Client;
    @Mock
    TerminateInstancesResult terminateInstancesResult;
    @Mock
    TerminateInstancesRequest terminateInstancesRequest;

    private int currentTargetCapacity = 5;
    private int activeFulfilledCapacity = 5;
    private int maxQueuedBuilds = 3;
    private int maxAverageQueueTime = 5;
    private int maxIdleInstances = 2;
    private int maxUnitsPerScalingAction = 2;
    private Map<String, String> activeInstanceDNSNamesToInstanceIdsMap;
    private List<QueuedResultKey> buildQueueResults;
    private List<BuildAgent> buildAgents;
    private SpotFleetTaskExecution taskExecutor;

    @Before
    public void setup() throws Exception {
        when(agentManager.allowNewRemoteAgents(anyInt())).thenReturn(true);
        buildQueueResults = new LinkedList<QueuedResultKey>();
        activeInstanceDNSNamesToInstanceIdsMap = new HashMap<String, String>();
        buildAgents = new LinkedList<BuildAgent>();
        QueuedResultKey qKey1 = new QueuedResultKey(resultKey1, 0);
        buildQueueResults.add(qKey1);
        QueuedResultKey qKey2 = new QueuedResultKey(resultKey2, 0);
        buildQueueResults.add(qKey2);
        activeInstanceDNSNamesToInstanceIdsMap.put("ip-172-31-66-67.ec2.internal", "id");
        activeInstanceDNSNamesToInstanceIdsMap.put("ip-172-31-64-253.ec2.internal", "id");
        activeInstanceDNSNamesToInstanceIdsMap.put("ip-172-31-73-206.ec2.internal", "id");
        activeInstanceDNSNamesToInstanceIdsMap.put("ip-162-32-68-252.ec2.internal", "id");
        activeInstanceDNSNamesToInstanceIdsMap.put("ip-189-21-39-112.ec2.internal", "id");
        buildAgents.addAll(
                ImmutableList.of(agent1, agent2, agent3, agent4, agent5, agent6, agent7, agent8, agent9, agent10));
        when(agentManager.getAllNonElasticAgents()).thenReturn(buildAgents);
        when(buildQueueManager.getQueuedExecutables()).thenReturn(buildQueueResults);
        when(agent1.getName()).thenReturn("ip-172-31-66-67.ec2.internal");
        when(agent2.getName()).thenReturn("ip-172-31-66-67.ec2.internal (2)");
        when(agent3.getName()).thenReturn("ip-172-31-64-253.ec2.internal");
        when(agent4.getName()).thenReturn("ip-172-31-64-253.ec2.internal (2)");
        when(agent5.getName()).thenReturn("ip-172-31-73-206.ec2.internal");
        when(agent6.getName()).thenReturn("ip-172-31-73-206.ec2.internal (2)");
        when(agent7.getName()).thenReturn("ip-162-32-68-252.ec2.internal");
        when(agent8.getName()).thenReturn("ip-162-32-68-252.ec2.internal (2)");
        when(agent9.getName()).thenReturn("ip-189-21-39-112.ec2.internal");
        when(agent10.getName()).thenReturn("ip-189-21-39-112.ec2.internal (2)");
        when(agent1.isBusy()).thenReturn(true);
        when(agent2.isBusy()).thenReturn(false);
        when(agent3.isBusy()).thenReturn(false);
        when(agent4.isBusy()).thenReturn(false);
        when(agent5.isBusy()).thenReturn(true);
        when(agent6.isBusy()).thenReturn(true);
        when(agent7.isBusy()).thenReturn(true);
        when(agent8.isBusy()).thenReturn(true);
        when(agent9.isBusy()).thenReturn(true);
        when(agent10.isBusy()).thenReturn(true);
        QueueItemView<CommonContext> queueItemView1 = new QueueItemView<CommonContext>(qKey1, commonContext1);
        when(buildQueueManager.peekContext(qKey1.getResultKey())).thenReturn(queueItemView1);
        when(commonContext1.getCurrentResult()).thenReturn(currentResult1);
        Date now = new Date();
        // set queue time of item to 1 minute ago
        when(currentResult1.getTasksStartDate()).thenReturn(new Date(now.getTime() - 60000));
        QueueItemView<CommonContext> queueItemView2 = new QueueItemView<CommonContext>(qKey2, commonContext2);
        when(buildQueueManager.peekContext(qKey2.getResultKey())).thenReturn(queueItemView2);
        when(commonContext2.getCurrentResult()).thenReturn(currentResult2);
        // set queue time of item to 7 minutes ago
        when(currentResult2.getTasksStartDate()).thenReturn(new Date(now.getTime() - 420000));
        taskExecutor = new SpotFleetTaskExecution(administrationConfigurationAccessor, buildQueueManager, agentManager,
                buildExecutionManager);
        PowerMockito.whenNew(TerminateInstancesRequest.class).withNoArguments().thenReturn(terminateInstancesRequest);
        when(terminateInstancesRequest.withInstanceIds(anyString())).thenReturn(terminateInstancesRequest);
        when(EC2Client.terminateInstances(terminateInstancesRequest)).thenReturn(terminateInstancesResult);
        Whitebox.setInternalState(taskExecutor, "EC2Client", EC2Client);

    }

    // active == target cases
    @Test
    public void noScalingChangeWhenMetricsArentMetTest() {
        int newTargetCapacity = 0;
        try {
            newTargetCapacity = org.powermock.reflect.Whitebox.invokeMethod(taskExecutor, "determineTargetCapacity",
                    currentTargetCapacity, activeFulfilledCapacity, maxQueuedBuilds, maxAverageQueueTime,
                    maxIdleInstances, maxUnitsPerScalingAction, activeInstanceDNSNamesToInstanceIdsMap);
        } catch (Exception e) {
            e.printStackTrace();
            fail();
        }
        assertEquals(currentTargetCapacity, newTargetCapacity);
    }

    @Test
    public void noScalingChangeWhenMetricsArentMetEdgesTest() {
        maxQueuedBuilds = 2;
        maxAverageQueueTime = 4;
        maxIdleInstances = 1;
        int newTargetCapacity = 0;
        try {
            newTargetCapacity = org.powermock.reflect.Whitebox.invokeMethod(taskExecutor, "determineTargetCapacity",
                    currentTargetCapacity, activeFulfilledCapacity, maxQueuedBuilds, maxAverageQueueTime,
                    maxIdleInstances, maxUnitsPerScalingAction, activeInstanceDNSNamesToInstanceIdsMap);
        } catch (Exception e) {
            e.printStackTrace();
            fail();
        }
        assertEquals(currentTargetCapacity, newTargetCapacity);
    }

    @Test
    public void scaleUpWhenMaxQueuedBuildsExceededTest() {
        maxQueuedBuilds = 2;
        QueuedResultKey qKey3 = new QueuedResultKey(resultKey3, 0);
        buildQueueResults.add(qKey3);
        QueueItemView<CommonContext> queueItemView3 = new QueueItemView<CommonContext>(qKey3, commonContext3);
        when(buildQueueManager.peekContext(qKey3.getResultKey())).thenReturn(queueItemView3);
        when(commonContext3.getCurrentResult()).thenReturn(currentResult3);
        Date now = new Date();
        // set queue time of item to 3 minutes ago
        when(currentResult3.getTasksStartDate()).thenReturn(new Date(now.getTime() - 180000));
        int newTargetCapacity = 0;
        try {
            newTargetCapacity = org.powermock.reflect.Whitebox.invokeMethod(taskExecutor, "determineTargetCapacity",
                    currentTargetCapacity, activeFulfilledCapacity, maxQueuedBuilds, maxAverageQueueTime,
                    maxIdleInstances, maxUnitsPerScalingAction, activeInstanceDNSNamesToInstanceIdsMap);
        } catch (Exception e) {
            e.printStackTrace();
            fail();
        }
        assertEquals(currentTargetCapacity + maxUnitsPerScalingAction, newTargetCapacity);
    }

    @Test
    public void scaleUpWhenMaxAverageQueueTimeExceededTest() {
        // set to 3, expect 4
        maxAverageQueueTime = 3;
        int newTargetCapacity = 0;
        try {
            newTargetCapacity = org.powermock.reflect.Whitebox.invokeMethod(taskExecutor, "determineTargetCapacity",
                    currentTargetCapacity, activeFulfilledCapacity, maxQueuedBuilds, maxAverageQueueTime,
                    maxIdleInstances, maxUnitsPerScalingAction, activeInstanceDNSNamesToInstanceIdsMap);
        } catch (Exception e) {
            e.printStackTrace();
            fail();
        }
        assertEquals(currentTargetCapacity + maxUnitsPerScalingAction, newTargetCapacity);
    }

    @Test
    public void scaleDownWhenMaxIdleAgentsExceededTest() {
        maxIdleInstances = 2;
        // set agents to idle
        when(agent1.isBusy()).thenReturn(false);
        when(agent5.isBusy()).thenReturn(false);
        when(agent6.isBusy()).thenReturn(false);
        when(agent7.isBusy()).thenReturn(false);
        when(agent8.isBusy()).thenReturn(false);
        when(agent9.isBusy()).thenReturn(false);
        when(agent10.isBusy()).thenReturn(false);
        int newTargetCapacity = 0;
        try {
            newTargetCapacity = org.powermock.reflect.Whitebox.invokeMethod(taskExecutor, "determineTargetCapacity",
                    currentTargetCapacity, activeFulfilledCapacity, maxQueuedBuilds, maxAverageQueueTime,
                    maxIdleInstances, maxUnitsPerScalingAction, activeInstanceDNSNamesToInstanceIdsMap);
        } catch (Exception e) {
            e.printStackTrace();
            fail();
        }
        assertEquals(currentTargetCapacity - maxUnitsPerScalingAction, newTargetCapacity);
    }

    // active != target cases
    @Test
    public void dontScaleUpWhenActiveLessThanTargetDuringScaleUpTest() {
        maxQueuedBuilds = 2;
        QueuedResultKey qKey3 = new QueuedResultKey(resultKey3, 0);
        buildQueueResults.add(qKey3);
        QueueItemView<CommonContext> queueItemView3 = new QueueItemView<CommonContext>(qKey3, commonContext3);
        when(buildQueueManager.peekContext(qKey3.getResultKey())).thenReturn(queueItemView3);
        when(commonContext3.getCurrentResult()).thenReturn(currentResult3);
        Date now = new Date();
        // set queue time of item to 3 minutes ago
        when(currentResult3.getTasksStartDate()).thenReturn(new Date(now.getTime() - 180000));
        int newTargetCapacity = 0;
        // this should cause no scale up despite maxQueued builds being exceeded
        activeFulfilledCapacity = currentTargetCapacity - maxUnitsPerScalingAction - 1;
        try {
            newTargetCapacity = org.powermock.reflect.Whitebox.invokeMethod(taskExecutor, "determineTargetCapacity",
                    currentTargetCapacity, activeFulfilledCapacity, maxQueuedBuilds, maxAverageQueueTime,
                    maxIdleInstances, maxUnitsPerScalingAction, activeInstanceDNSNamesToInstanceIdsMap);
        } catch (Exception e) {
            e.printStackTrace();
            fail();
        }
        assertEquals(currentTargetCapacity, newTargetCapacity);
    }

    @Test
    public void scaleDownBasedOnActiveCapacityTest() {
        maxIdleInstances = 4;
        when(agent1.isBusy()).thenReturn(false);
        when(agent5.isBusy()).thenReturn(false);
        when(agent6.isBusy()).thenReturn(false);
        when(agent7.isBusy()).thenReturn(false);
        when(agent8.isBusy()).thenReturn(false);
        when(agent9.isBusy()).thenReturn(false);
        when(agent10.isBusy()).thenReturn(false);
        int newTargetCapacity = 0;
        activeFulfilledCapacity = currentTargetCapacity - 2;
        try {
            newTargetCapacity = org.powermock.reflect.Whitebox.invokeMethod(taskExecutor, "determineTargetCapacity",
                    currentTargetCapacity, activeFulfilledCapacity, maxQueuedBuilds, maxAverageQueueTime,
                    maxIdleInstances, maxUnitsPerScalingAction, activeInstanceDNSNamesToInstanceIdsMap);
        } catch (Exception e) {
            e.printStackTrace();
            fail();
        }
        // since active < target scaling should be based off of active
        assertEquals(activeFulfilledCapacity - maxUnitsPerScalingAction, newTargetCapacity);
    }

    // upper/lower bounds verification
    public void scalingNeverExceedsUpperBoundTest() {
        maxQueuedBuilds = 2;
        QueuedResultKey qKey3 = new QueuedResultKey(resultKey3, 0);
        buildQueueResults.add(qKey3);
        QueueItemView<CommonContext> queueItemView3 = new QueueItemView<CommonContext>(qKey3, commonContext3);
        when(buildQueueManager.peekContext(qKey3.getResultKey())).thenReturn(queueItemView3);
        when(commonContext3.getCurrentResult()).thenReturn(currentResult3);
        Date now = new Date();
        // set queue time of item to 3 minutes ago
        when(currentResult3.getTasksStartDate()).thenReturn(new Date(now.getTime() - 180000));
        int newTargetCapacity = 0;
        currentTargetCapacity = 2999;
        try {
            newTargetCapacity = org.powermock.reflect.Whitebox.invokeMethod(taskExecutor, "determineTargetCapacity",
                    currentTargetCapacity, activeFulfilledCapacity, maxQueuedBuilds, maxAverageQueueTime,
                    maxIdleInstances, maxUnitsPerScalingAction, activeInstanceDNSNamesToInstanceIdsMap);
        } catch (Exception e) {
            e.printStackTrace();
            fail();
        }
        assertEquals(SpotFleetTaskExecution.MAXIMUM_TARGET_CAPACITY, newTargetCapacity);
    }

    @Test
    public void scalingNeverExceedsLowerBoundTest() {
        maxIdleInstances = 4;
        when(agent1.isBusy()).thenReturn(false);
        when(agent5.isBusy()).thenReturn(false);
        when(agent6.isBusy()).thenReturn(false);
        when(agent7.isBusy()).thenReturn(false);
        when(agent8.isBusy()).thenReturn(false);
        when(agent9.isBusy()).thenReturn(false);
        when(agent10.isBusy()).thenReturn(false);
        int newTargetCapacity = 0;
        currentTargetCapacity = 2;
        activeFulfilledCapacity = 2;
        try {
            newTargetCapacity = org.powermock.reflect.Whitebox.invokeMethod(taskExecutor, "determineTargetCapacity",
                    currentTargetCapacity, activeFulfilledCapacity, maxQueuedBuilds, maxAverageQueueTime,
                    maxIdleInstances, maxUnitsPerScalingAction, activeInstanceDNSNamesToInstanceIdsMap);
        } catch (Exception e) {
            e.printStackTrace();
            fail();
        }
        assertEquals(SpotFleetTaskExecution.MINIMUM_TARGET_CAPACITY, newTargetCapacity);
    }

    // License restriction tests
    public void autoscalingDoesntExceedLicenseRestrictionsTest() {
        // set to 3, expect 4
        maxAverageQueueTime = 3;
        when(agentManager.allowNewRemoteAgents(anyInt())).thenReturn(true);
        int newTargetCapacity = 0;
        try {
            newTargetCapacity = org.powermock.reflect.Whitebox.invokeMethod(taskExecutor, "determineTargetCapacity",
                    currentTargetCapacity, activeFulfilledCapacity, maxQueuedBuilds, maxAverageQueueTime,
                    maxIdleInstances, maxUnitsPerScalingAction, activeInstanceDNSNamesToInstanceIdsMap);
        } catch (Exception e) {
            e.printStackTrace();
            fail();
        }
        assertEquals(currentTargetCapacity + maxUnitsPerScalingAction, newTargetCapacity);
    }
}
