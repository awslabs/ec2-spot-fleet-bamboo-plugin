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
import static org.mockito.Matchers.anyCollectionOf;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations.Mock;
import org.mockito.internal.util.reflection.Whitebox;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.model.ActiveInstance;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.DescribeSpotFleetInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeSpotFleetInstancesResult;
import com.amazonaws.services.ec2.model.DescribeSpotFleetRequestsRequest;
import com.amazonaws.services.ec2.model.DescribeSpotFleetRequestsResult;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.ModifySpotFleetRequestRequest;
import com.amazonaws.services.ec2.model.ModifySpotFleetRequestResult;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.SpotFleetRequestConfig;
import com.amazonaws.services.ec2.model.SpotFleetRequestConfigData;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;
import com.amazonaws.services.ec2.model.TerminateInstancesResult;
import com.atlassian.bamboo.ResultKey;
import com.atlassian.bamboo.build.BuildExecutionManager;
import com.atlassian.bamboo.build.logger.BuildLogger;
import com.atlassian.bamboo.buildqueue.manager.AgentManager;
import com.atlassian.bamboo.configuration.AdministrationConfigurationAccessor;
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
public class MakeScalingDecisionUnitTest {
    @Mock
    private AgentManager agentManager;
    @Mock
    private AmazonEC2 EC2Client;
    @Mock
    private BuildLogger buildLogger;
    @Mock
    private DescribeSpotFleetRequestsRequest describeSpotFleetRequestsRequest;
    @Mock
    private DescribeSpotFleetRequestsResult describeSpotFleetRequestsResult;
    @Mock
    SpotFleetRequestConfig spotFleetRequestConfig;
    @Mock
    SpotFleetRequestConfigData spotFleetRequestConfigData;
    @Mock
    private DescribeSpotFleetInstancesRequest describeSpotfleetInstancesRequest;
    @Mock
    private DescribeSpotFleetInstancesResult describeSpotFleetInstancesResult;
    List<ActiveInstance> instanceList;
    @Mock
    ActiveInstance instance1;
    @Mock
    ActiveInstance instance2;
    @Mock
    ActiveInstance instance3;
    @Mock
    private DescribeInstancesRequest describeInstancesRequest;
    @Mock
    private DescribeInstancesResult describeInstancesResult;
    private List<Reservation> reservations;
    @Mock
    Reservation reservation;
    private List<Instance> reservationInstanceList;
    @Mock
    Instance reservationInstance1;
    @Mock
    Instance reservationInstance2;
    @Mock
    Instance reservationInstance3;
    @Mock
    private ModifySpotFleetRequestRequest modifySpotFleetRequestRequest;
    @Mock
    private ModifySpotFleetRequestResult modifySpotFleetRequestResult;
    @Mock
    private AdministrationConfigurationAccessor administrationConfigurationAccessor;
    @Mock
    private BuildQueueManager buildQueueManager;
    @Mock
    private Iterator<QueuedResultKey> buildQueueIterator;
    @Mock
    private BuildExecutionManager buildExecutionManager;
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
    TerminateInstancesResult terminateInstancesResult;
    @Mock
    TerminateInstancesRequest terminateInstancesRequest;

    private int currentTargetCapacity = 3;
    private int maxQueuedBuilds = 3;
    private int maxAverageQueueTime = 5;
    private int maxIdleAgents = 2;
    private int unitsPerScalingAction = 2;
    private Set<String> activeInstanceDNSNames;
    private List<QueuedResultKey> buildQueueResults;
    private List<BuildAgent> buildAgents;
    private SpotFleetTaskExecution taskExecutor;
    private boolean enableAutoscale = true;

    @Before
    public void setup() throws Exception {
        when(agentManager.allowNewElasticAgents(anyInt())).thenReturn(true);
        PowerMockito.whenNew(DescribeSpotFleetRequestsRequest.class).withNoArguments()
                .thenReturn(describeSpotFleetRequestsRequest);
        when(describeSpotFleetRequestsRequest.withSpotFleetRequestIds(anyString()))
                .thenReturn(describeSpotFleetRequestsRequest);
        PowerMockito.whenNew(DescribeSpotFleetInstancesRequest.class).withNoArguments()
                .thenReturn(describeSpotfleetInstancesRequest);
        when(describeSpotfleetInstancesRequest.withSpotFleetRequestId(anyString()))
                .thenReturn(describeSpotfleetInstancesRequest);
        PowerMockito.whenNew(DescribeInstancesRequest.class).withNoArguments().thenReturn(describeInstancesRequest);
        when(describeInstancesRequest.withInstanceIds(anyCollectionOf(String.class)))
                .thenReturn(describeInstancesRequest);
        when(EC2Client.describeSpotFleetRequests(describeSpotFleetRequestsRequest))
                .thenReturn(describeSpotFleetRequestsResult);
        when(describeSpotFleetRequestsResult.getSpotFleetRequestConfigs())
                .thenReturn(ImmutableList.of(spotFleetRequestConfig));
        when(spotFleetRequestConfig.getSpotFleetRequestConfig()).thenReturn(spotFleetRequestConfigData);
        when(spotFleetRequestConfigData.getTargetCapacity()).thenReturn(currentTargetCapacity);
        when(EC2Client.describeSpotFleetInstances(describeSpotfleetInstancesRequest))
                .thenReturn(describeSpotFleetInstancesResult);
        instanceList = ImmutableList.of(instance1, instance2, instance3);
        when(instance1.getInstanceId()).thenReturn("i-0111605f74c7ab82a");
        when(instance2.getInstanceId()).thenReturn("i-0155104068b16f6a1");
        when(instance3.getInstanceId()).thenReturn("i-0acd407a8d0c055b8");
        when(describeSpotFleetInstancesResult.getActiveInstances()).thenReturn(instanceList);
        when(EC2Client.describeInstances(describeInstancesRequest)).thenReturn(describeInstancesResult);
        reservations = ImmutableList.of(reservation);
        when(describeInstancesResult.getReservations()).thenReturn(reservations);
        reservationInstanceList = ImmutableList.of(reservationInstance1, reservationInstance2, reservationInstance3);
        when(reservationInstance1.getPrivateDnsName()).thenReturn("ip-172-31-66-67.ec2.internal");
        when(reservationInstance2.getPrivateDnsName()).thenReturn("ip-172-31-64-253.ec2.internal");
        when(reservationInstance3.getPrivateDnsName()).thenReturn("ip-172-31-73-206.ec2.internal");
        when(reservation.getInstances()).thenReturn(reservationInstanceList);
        // begin mocking for determineNewTargetCapacity
        when(agentManager.allowNewRemoteAgents(anyInt())).thenReturn(true);
        buildQueueResults = new LinkedList<QueuedResultKey>();
        activeInstanceDNSNames = new HashSet<String>();
        buildAgents = new LinkedList<BuildAgent>();
        QueuedResultKey qKey1 = new QueuedResultKey(resultKey1, 0);
        buildQueueResults.add(qKey1);
        QueuedResultKey qKey2 = new QueuedResultKey(resultKey2, 0);
        buildQueueResults.add(qKey2);
        activeInstanceDNSNames.add("ip-172-31-66-67.ec2.internal");
        activeInstanceDNSNames.add("ip-172-31-64-253.ec2.internal");
        activeInstanceDNSNames.add("ip-172-31-73-206.ec2.internal");
        activeInstanceDNSNames.add("ip-162-32-68-252.ec2.internal");
        activeInstanceDNSNames.add("ip-189-21-39-112.ec2.internal");
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
        Whitebox.setInternalState(taskExecutor, "buildLogger", buildLogger);

        PowerMockito.whenNew(ModifySpotFleetRequestRequest.class).withNoArguments()
                .thenReturn(modifySpotFleetRequestRequest);
        when(modifySpotFleetRequestRequest.withSpotFleetRequestId(anyString()))
                .thenReturn(modifySpotFleetRequestRequest);
        when(modifySpotFleetRequestRequest.withTargetCapacity(anyInt())).thenReturn(modifySpotFleetRequestRequest);
        when(EC2Client.modifySpotFleetRequest(modifySpotFleetRequestRequest)).thenReturn(modifySpotFleetRequestResult);
    }

    @Test
    public void noScalingDecisionDoesntMakeModifyFleetRequestTest() throws Exception {
        // if a modify spot fleet request is made then the decision to scale was made
        when(EC2Client.modifySpotFleetRequest(modifySpotFleetRequestRequest)).thenThrow(new IllegalStateException());
        try {
            org.powermock.reflect.Whitebox.invokeMethod(taskExecutor, "makeScalingDecision", "fleet", maxQueuedBuilds,
                    maxAverageQueueTime, maxIdleAgents, unitsPerScalingAction, enableAutoscale);
        } catch (IllegalStateException e) {
            fail("makeScalingDecision attempted to scale when no scaling decision should have been made");
        } catch (Exception e) {
            e.printStackTrace();
            fail("Test failed due to unknown exception");
        }
    }

    @Test
    public void scalingDecisionDoesMakeModifySpotFleetRequestTest() throws Exception {
        // with 2 queued build this will trigger a scale up
        maxQueuedBuilds = 1;
        when(modifySpotFleetRequestResult.isReturn()).thenReturn(true);
        try {
            org.powermock.reflect.Whitebox.invokeMethod(taskExecutor, "makeScalingDecision", "fleet", maxQueuedBuilds,
                    maxAverageQueueTime, maxIdleAgents, unitsPerScalingAction, enableAutoscale);
        } catch (Exception e) {
            e.printStackTrace();
            fail("Test failed due to unexpected exception");
        }
        verify(modifySpotFleetRequestResult, times(1)).isReturn();
    }

    @Test
    public void activeCapacityLowerThanFulfilledDuringScaleUpTest() {
        // empty instance list => fulfilled capacity of 0
        instanceList = new LinkedList<ActiveInstance>();
        // have to override previous getActiveInstances call
        when(describeSpotFleetInstancesResult.getActiveInstances()).thenReturn(instanceList);
        // with 2 queued build this would trigger a scale up, but since active/fulfilled + unitsPerScale <
        // targetCapacity
        // no scaling decision should be made
        maxQueuedBuilds = 1;
        when(EC2Client.modifySpotFleetRequest(modifySpotFleetRequestRequest)).thenThrow(new IllegalStateException());
        try {
            org.powermock.reflect.Whitebox.invokeMethod(taskExecutor, "makeScalingDecision", "fleet", maxQueuedBuilds,
                    maxAverageQueueTime, maxIdleAgents, unitsPerScalingAction, enableAutoscale);
        } catch (IllegalStateException e) {
            fail("makeScalingDecision attempted to scale when no scaling decision should have been made");
        } catch (Exception e) {
            e.printStackTrace();
            fail("Test failed due to unknown exception");
        }
    }
}
