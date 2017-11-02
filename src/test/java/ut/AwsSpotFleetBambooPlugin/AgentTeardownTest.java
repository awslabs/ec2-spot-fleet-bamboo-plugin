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
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.runners.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;
import org.powermock.reflect.Whitebox;

import com.atlassian.bamboo.build.BuildExecutionManager;
import com.atlassian.bamboo.buildqueue.manager.AgentManager;
import com.atlassian.bamboo.plan.PlanResultKey;
import com.atlassian.bamboo.v2.build.BuildIdentifier;
import com.atlassian.bamboo.v2.build.CurrentlyBuilding;
import com.atlassian.bamboo.v2.build.agent.BuildAgent;
import com.google.common.collect.ImmutableList;

import AwsSpotFleetBambooPlugin.SpotFleetTaskExecution;

@RunWith(MockitoJUnitRunner.class)
public class AgentTeardownTest {

    @Mock
    private SpotFleetTaskExecution taskExecutor;
    @Mock
    private AgentManager agentManager;
    private List<BuildAgent> agentList;
    @Mock
    private BuildAgent fleetAgent1;
    @Mock
    private BuildAgent fleetAgent2;
    @Mock
    private BuildAgent agent1;
    @Mock
    private BuildExecutionManager buildExecutionManager;
    private List<CurrentlyBuilding> buildList;
    @Mock
    CurrentlyBuilding build1;
    @Mock
    CurrentlyBuilding build2;
    @Mock
    BuildIdentifier buildIdentifier1;
    @Mock
    BuildIdentifier buildIdentifier2;
    @Mock
    PlanResultKey resultKey1;
    @Mock
    PlanResultKey resultKey2;
    private Map<String, String> activeInstanceDnsNamesToInstanceIdsMap;

    @Before
    public void setup() throws TimeoutException {
        taskExecutor = new SpotFleetTaskExecution(null, null, agentManager, buildExecutionManager);
        agentList = new LinkedList<BuildAgent>();
        buildList = new LinkedList<CurrentlyBuilding>();
        when(agentManager.getAllNonElasticAgents()).thenReturn(agentList);
        agentList.addAll(ImmutableList.of(fleetAgent1, fleetAgent2, agent1));
        when(fleetAgent1.getName()).thenReturn("ip-12-345-67-890.ec2.internal");
        when(fleetAgent2.getName()).thenReturn("ip-172-31-30-164.eu-central-1.compute.internal");
        activeInstanceDnsNamesToInstanceIdsMap = new HashMap<String, String>();
        Whitebox.setInternalState(taskExecutor, "activeInstanceDnsNamesToInstanceIdsMap",
                activeInstanceDnsNamesToInstanceIdsMap);
        when(agent1.getName()).thenReturn("remoteagent");
        when(fleetAgent1.isUnresponsive()).thenReturn(true);
        when(fleetAgent2.isUnresponsive()).thenReturn(true);
        when(agent1.isUnresponsive()).thenReturn(true);
        when(fleetAgent1.getId()).thenReturn(1l);
        when(fleetAgent2.getId()).thenReturn(2l);
        when(agent1.getId()).thenReturn(3l);
        Mockito.doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                Object[] args = invocation.getArguments();
                agentList.remove(fleetAgent1);
                return null;
            }
        }).when(agentManager).removeAgent(1l);
        Mockito.doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                Object[] args = invocation.getArguments();
                agentList.remove(fleetAgent2);
                return null;
            }
        }).when(agentManager).removeAgent(2l);
        // also mock removal of agent1 so we have visibility if logic doesnt work as expected
        Mockito.doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                Object[] args = invocation.getArguments();
                agentList.remove(agent1);
                return null;
            }
        }).when(agentManager).removeAgent(3l);
        when(buildExecutionManager.getCurrentlyExecuting()).thenReturn(buildList);
        buildList.add(build1);
        buildList.add(build2);
        when(build1.getBuildAgentId()).thenReturn(1l);
        when(build2.getBuildAgentId()).thenReturn(3l);
        when(build1.getBuildIdentifier()).thenReturn(buildIdentifier1);
        when(build2.getBuildIdentifier()).thenReturn(buildIdentifier2);
        when(buildIdentifier1.getPlanResultKey()).thenReturn(resultKey1);
        when(buildIdentifier2.getPlanResultKey()).thenReturn(resultKey2);
        Mockito.doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                Object[] args = invocation.getArguments();
                buildList.remove(build1);
                return null;
            }
        }).when(buildExecutionManager).finishBuild(resultKey1);
        Mockito.doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                Object[] args = invocation.getArguments();
                buildList.remove(build2);
                return null;
            }
        }).when(buildExecutionManager).finishBuild(resultKey2);
    }

    @Test
    public void agentTeardownOnlyRemovesFleetAgentsAndOnlyTerminatesFleetBuildsTest() {
        // simulate instance termination by making this list empty
        activeInstanceDnsNamesToInstanceIdsMap = new HashMap<String, String>();
        List<BuildAgent> expectedRemainingAgents = new LinkedList<BuildAgent>();
        expectedRemainingAgents.add(agent1);
        List<CurrentlyBuilding> expectedRemainingBuilds = new LinkedList<CurrentlyBuilding>();
        expectedRemainingBuilds.add(build2);
        try {
            org.powermock.reflect.Whitebox.invokeMethod(taskExecutor, "agentTeardown");
        } catch (Exception e) {
            e.printStackTrace();
            fail();
        }
        assertEquals(expectedRemainingAgents, agentList);
        assertEquals(expectedRemainingBuilds, buildList);
    }

    @Test
    public void agentTeardownWithNoAgentsPresentTest() {
        when(agentManager.getAllAgents()).thenReturn(new LinkedList<BuildAgent>());

        try {
            org.powermock.reflect.Whitebox.invokeMethod(taskExecutor, "agentTeardown");
        } catch (Exception e) {
            e.printStackTrace();
            fail();
        }
    }

    @Test
    public void agentTeardownRemovesOnlyOfflineAgentsTest() {
        List<BuildAgent> expectedRemainingAgents = new LinkedList<BuildAgent>();
        expectedRemainingAgents.add(fleetAgent2);
        expectedRemainingAgents.add(agent1);
        // fleetAgent2 should be detected as online
        activeInstanceDnsNamesToInstanceIdsMap.put("ip-172-31-30-164.eu-central-1.compute.internal", "i-11232312487");
        Whitebox.setInternalState(taskExecutor, "activeInstanceDnsNamesToInstanceIdsMap",
                activeInstanceDnsNamesToInstanceIdsMap);
        List<CurrentlyBuilding> expectedRemainingBuilds = new LinkedList<CurrentlyBuilding>();
        expectedRemainingBuilds.add(build2);
        try {
            org.powermock.reflect.Whitebox.invokeMethod(taskExecutor, "agentTeardown");
        } catch (Exception e) {
            e.printStackTrace();
            fail();
        }
        assertEquals(expectedRemainingAgents, agentList);
        assertEquals(expectedRemainingBuilds, buildList);
    }

}
