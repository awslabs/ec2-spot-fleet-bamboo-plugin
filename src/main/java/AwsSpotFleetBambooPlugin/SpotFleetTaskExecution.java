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
package AwsSpotFleetBambooPlugin;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.ActiveInstance;
import com.amazonaws.services.ec2.model.CancelSpotFleetRequestsRequest;
import com.amazonaws.services.ec2.model.CancelSpotFleetRequestsResult;
import com.amazonaws.services.ec2.model.CreateTagsRequest;
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
import com.amazonaws.services.ec2.model.SpotFleetRequestConfigData;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;
import com.atlassian.bamboo.build.BuildExecutionManager;
import com.atlassian.bamboo.build.logger.BuildLogger;
import com.atlassian.bamboo.buildqueue.manager.AgentManager;
import com.atlassian.bamboo.configuration.AdministrationConfigurationAccessor;
import com.atlassian.bamboo.plan.PlanResultKey;
import com.atlassian.bamboo.task.TaskContext;
import com.atlassian.bamboo.task.TaskException;
import com.atlassian.bamboo.task.TaskResult;
import com.atlassian.bamboo.task.TaskResultBuilder;
import com.atlassian.bamboo.task.TaskType;
import com.atlassian.bamboo.v2.build.BuildIdentifier;
import com.atlassian.bamboo.v2.build.CurrentlyBuilding;
import com.atlassian.bamboo.v2.build.agent.BuildAgent;
import com.atlassian.bamboo.v2.build.queue.BuildQueueManager;
import com.atlassian.bamboo.v2.build.queue.BuildQueueManager.QueuedResultKey;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.google.common.collect.Sets.SetView;

/**
 * Task execution class allows Bamboo managers (which allow access to Bamboo components) to be injected via constructor.
 * Defines SpotFleet task behavior during build.
 *
 * @author kalteu
 *
 */
@Component
public class SpotFleetTaskExecution implements TaskType {
    // Bamboo component to access server URL. Used in configurator class.
    private AdministrationConfigurationAccessor administrationConfigurationAccessor;
    // Bamboo component to access Build queue
    private BuildQueueManager buildQueueManager;
    // Bamboo component to access agents and their info
    private AgentManager agentManager;
    // Bamboo component to access currently executing builds
    private BuildExecutionManager buildExecutionManager;
    private AmazonEC2 EC2Client = null;
    private BuildLogger buildLogger = null;
    private Map<String, String> activeInstanceDnsNamesToInstanceIdsMap;
    // cap for target capacity (according to spot fleet console)
    public static final int MAXIMUM_TARGET_CAPACITY = 250;
    public static final int MINIMUM_TARGET_CAPACITY = 1;
    public static final int MILLISECONDS_PER_MINUTE = 60000;

    /**
     * must include the component imports here to make available to configurator class
     *
     * @param administrationConfigurationAccessor
     * @param buildQueueManager
     * @param agentManager
     * @param buildExecutionManager
     */
    @Autowired
    public SpotFleetTaskExecution(
            @ComponentImport AdministrationConfigurationAccessor administrationConfigurationAccessor,
            @ComponentImport BuildQueueManager buildQueueManager, @ComponentImport AgentManager agentManager,
            @ComponentImport BuildExecutionManager buildExecutionManager) {
        this.administrationConfigurationAccessor = administrationConfigurationAccessor;
        this.agentManager = agentManager;
        this.buildQueueManager = buildQueueManager;
        this.buildExecutionManager = buildExecutionManager;
    }

    /**
     * method is called at time of task execution. Handles Monitoring and Teardown phases of plugin
     */
    @Override
    public TaskResult execute(final TaskContext taskContext) throws TaskException {
        TaskResultBuilder builder = TaskResultBuilder.newBuilder(taskContext);
        buildLogger = taskContext.getBuildLogger();
        // retrieve user-inputted values
        // error validation from configurator has run on these
        final String accessKeyInput = taskContext.getConfigurationMap().get(StringConstants.ACCESS_KEY);
        final String secretKeyInput = taskContext.getConfigurationMap().get(StringConstants.SECRET_KEY);
        final String fleetIdInput = taskContext.getConfigurationMap().get(StringConstants.FLEET_ID);
        final String regionInput = taskContext.getConfigurationMap().get(StringConstants.REGION);
        final boolean terminateFleetInput = Boolean
                .parseBoolean(taskContext.getConfigurationMap().get(StringConstants.TERMINATE_FLEET));
        final boolean enableAutoscalingInput = Boolean
                .parseBoolean(taskContext.getConfigurationMap().get(StringConstants.ENABLE_AUTOSCALING));
        final int queuedBuildsInput = Integer
                .parseInt(taskContext.getConfigurationMap().get(StringConstants.QUEUED_BUILDS));
        final int averageQueueTimeInput = Integer
                .parseInt(taskContext.getConfigurationMap().get(StringConstants.AVERAGE_QUEUE_TIME));
        final int idleInstancesInput = Integer
                .parseInt(taskContext.getConfigurationMap().get(StringConstants.IDLE_INSTANCES));
        final int maxUnitsPerScaleInput = Integer
                .parseInt(taskContext.getConfigurationMap().get(StringConstants.MAXIMUM_UNITS_PER_SCALE));
        BasicAWSCredentials awsCredentials = new BasicAWSCredentials(accessKeyInput, secretKeyInput);
        EC2Client = new AmazonEC2Client(awsCredentials).withRegion(Regions.fromName(regionInput));
        // handle autoscaling and instance cleanup
        makeScalingDecision(fleetIdInput, queuedBuildsInput, averageQueueTimeInput, idleInstancesInput,
                maxUnitsPerScaleInput, enableAutoscalingInput);
        tagInstances();
        // remove offline spot instances
        agentTeardown();
        // cancel fleet if all builds have finished
        if (terminateFleetInput) {
            terminateFleet(fleetIdInput);
        }
        return builder.success().build();
    }

    /**
     * Check current fleet status, obtaining fulfilled/target capacity and list of active agent names Then check Bamboo
     * metrics and compare against user-provided scaling thresholds to make scaling decision
     *
     * @param fleetID
     * @param maxQueuedBuilds
     * @param maxAverageQueueTime
     * @param maxIdleInstances
     * @param unitsPerScale
     */
    private void makeScalingDecision(String fleetID, int maxQueuedBuilds, int maxAverageQueueTime, int maxIdleInstances,
            int unitsPerScale, boolean enableAutoscalingInput) {
        // check current status of fleet
        DescribeSpotFleetRequestsRequest describeSpotFleetRequestsRequest = new DescribeSpotFleetRequestsRequest()
                .withSpotFleetRequestIds(fleetID);
        try {
            DescribeSpotFleetRequestsResult describeSpotFleetRequestsResult = EC2Client
                    .describeSpotFleetRequests(describeSpotFleetRequestsRequest);
            SpotFleetRequestConfigData bambooFleetConfig = describeSpotFleetRequestsResult.getSpotFleetRequestConfigs()
                    .get(0).getSpotFleetRequestConfig();
            int currentTargetCapacity = bambooFleetConfig.getTargetCapacity();
            // TODO: If dependency issues are resolved refactor this into a SpotFleetRequestConfigData method call
            DescribeSpotFleetInstancesRequest describeSpotfleetInstancesRequest = new DescribeSpotFleetInstancesRequest()
                    .withSpotFleetRequestId(fleetID);
            DescribeSpotFleetInstancesResult describeSpotFleetInstancesResult = EC2Client
                    .describeSpotFleetInstances(describeSpotfleetInstancesRequest);
            List<ActiveInstance> instanceList = describeSpotFleetInstancesResult.getActiveInstances();
            // agents are named after their private DNS e.g. ip-172-31-68-230.ec2.internal
            // with multiple agents per instance this name will act as a shared prefix
            // e.g. ip-172-31-68-230.ec2.internal (2)
            activeInstanceDnsNamesToInstanceIdsMap = new HashMap<String, String>();
            List<String> instanceIds = new ArrayList<String>();
            for (ActiveInstance activeInstance : instanceList) {
                instanceIds.add(activeInstance.getInstanceId());
            }
            DescribeInstancesRequest describeInstancesRequest = new DescribeInstancesRequest()
                    .withInstanceIds(instanceIds);
            DescribeInstancesResult describeInstancesResult = EC2Client.describeInstances(describeInstancesRequest);
            for (Reservation reservation : describeInstancesResult.getReservations()) {
                for (Instance instance : reservation.getInstances()) {
                    activeInstanceDnsNamesToInstanceIdsMap.put(instance.getPrivateDnsName(), instance.getInstanceId());
                }
            }
            int activeCapacity = instanceList.size();
            // check bamboo metrics against user-provided autoscaling settings
            if (enableAutoscalingInput) {
                int newTargetCapacity = determineTargetCapacity(currentTargetCapacity, activeCapacity,
                        maxQueuedBuilds, maxAverageQueueTime, maxIdleInstances, unitsPerScale,
                        activeInstanceDnsNamesToInstanceIdsMap);
                // modify fleet request if scaling decision was made
                if (currentTargetCapacity != newTargetCapacity) {
                    ModifySpotFleetRequestRequest modifySpotFleetRequestRequest = new ModifySpotFleetRequestRequest()
                            .withSpotFleetRequestId(fleetID).withTargetCapacity(newTargetCapacity);
                    ModifySpotFleetRequestResult modifySpotFleetRequestResult = EC2Client
                            .modifySpotFleetRequest(modifySpotFleetRequestRequest);
                    if (!modifySpotFleetRequestResult.isReturn()) {
                        // request did not succeed
                        buildLogger.addBuildLogEntry("modify spot fleet request failed");
                    } else {
                        buildLogger
                                .addBuildLogEntry("modify spot fleet request succeeded, target capacity changed from "
                                        + Integer.toString(currentTargetCapacity) + " to "
                                        + Integer.toString(newTargetCapacity));
                    }
                } else {
                    buildLogger.addBuildLogEntry("No scaling decision made");
                }
            } else {
                buildLogger.addBuildLogEntry("WARNING: autoscaling was not enabled");
            }
        } catch (AmazonServiceException e) {
            buildLogger.addBuildLogEntry("ERROR occurred during Fleet monitoring");
            if (e.getErrorCode().equals("AuthFailure")) {
                buildLogger.addBuildLogEntry("provided credentials could not be validated");
            } else if (e.getErrorCode().equals("UnauthorizedOperation")) {
                buildLogger.addBuildLogEntry("provided credentials lack permissions for operation");
            } else if (e.getErrorCode().equals("InvalidParameterValue")) {
                buildLogger.addBuildLogEntry("provided fleet ID does not match regex: " + e.getErrorMessage());
            } else if (e.getErrorCode().equals("InvalidSpotFleetRequestId.NotFound")) {
                buildLogger.addBuildLogEntry("fleet ID not valid in current region: " + e.getErrorMessage());
            } else {
                buildLogger.addBuildLogEntry("amazon service exception code: " + e.getErrorCode());
                buildLogger.addBuildLogEntry("with message: " + e.getErrorMessage());
            }
        } catch (AmazonClientException e) {
            buildLogger.addBuildLogEntry("amazon client exception: " + e.getMessage());
        }
    }

    /**
     * Method to calculate the new target capacity by comparing user-provided inputs against bamboo metrics and
     *
     * @param currentTargetCapacity
     * @param activeFulfilledCapacity
     * @param maxQueuedBuilds
     * @param maxAverageQueueTime
     * @param maxIdleInstances
     * @param unitsPerScale
     * @param activeInstanceDnsNamesToInstanceIdsMap.keySet()
     * @return
     */
    private int determineTargetCapacity(int currentTargetCapacity, int activeFulfilledCapacity, int maxQueuedBuilds,
            int maxAverageQueueTime, int maxIdleInstances, int unitsPerScale,
            Map<String, String> instanceDnsNamesToInstanceIdsMap) {
        int modifier = 0;
        int queuedBuildCounter = 0;
        double totalQueueTime = 0;
        Date now = new Date();
        Iterator<QueuedResultKey> queueIterator = buildQueueManager.getQueuedExecutables().iterator();
        QueuedResultKey currentBuild;
        while (queueIterator.hasNext()) {
            queuedBuildCounter++;
            currentBuild = queueIterator.next();
            totalQueueTime += (now.getTime() - buildQueueManager.peekContext(currentBuild.getResultKey()).getView()
                    .getCurrentResult().getTasksStartDate().getTime());
        }
        int averageQueueTime = ((int) (totalQueueTime / queuedBuildCounter / MILLISECONDS_PER_MINUTE));
        // convert milliseconds to minutes and compute average queue time (truncated to nearest minute)
        if (queuedBuildCounter > maxQueuedBuilds || averageQueueTime > maxAverageQueueTime) {
            // scale up
            modifier = 1;
        }
        int fleetAgentCounter = 0;
        // avoid iterating over local/elastic agents to avoid similar names and save time
        Iterator<BuildAgent> agentIterator = agentManager.getAllNonElasticAgents().iterator();
        Set<String> fleetBusyInstanceSet = new HashSet<String>();
        while (agentIterator.hasNext()) {
            BuildAgent currentAgent = agentIterator.next();
            String agentName = currentAgent.getName();
            // in the case of multiple agents per instance
            String trueAgentName = agentName.split("\\.internal")[0] + ".internal";
            if (instanceDnsNamesToInstanceIdsMap.keySet().contains(trueAgentName)) {
                fleetAgentCounter++;
                // map instance to number of idle agents
                if (currentAgent.isBusy()) {
                    fleetBusyInstanceSet.add(trueAgentName);
                }
            }
        }
        if (fleetAgentCounter < instanceDnsNamesToInstanceIdsMap.keySet().size()) {
            buildLogger.addBuildLogEntry(
                    "WARNING: detected fewer fleet agents than instances. Something may have gone wrong during "
                    + "installation or this task schedule may not be allowing for enough time between executions.");
        }
        int agentsPerInstance = Integer.MAX_VALUE;
        // this number is only correct if user doesn't initially go over their allowed instances
        // as if they do, it's possible to have varying numbers of agents per instance
        if (instanceDnsNamesToInstanceIdsMap.keySet().size() > 0) {
            agentsPerInstance = fleetAgentCounter / instanceDnsNamesToInstanceIdsMap.keySet().size();
        }
        SetView<String> instanceTerminationCandidates = com.google.common.collect.Sets
                .difference(instanceDnsNamesToInstanceIdsMap.keySet(), fleetBusyInstanceSet);
        int numberOfInstancesToTerminate = 0;
        // scale down if we exceed maxIdleInstances threshold
        if (instanceTerminationCandidates.size() > maxIdleInstances) {
            numberOfInstancesToTerminate = Math.min(instanceTerminationCandidates.size(), unitsPerScale);
            int terminationCounter = 0;
            Iterator<String> terminateInstancesIterator = instanceTerminationCandidates.iterator();
            LinkedList<String> instancesToTerminate = new LinkedList<String>();
            LinkedList<String> entriesToRemoveFromMap = new LinkedList<String>();

            // will terminate unitsPerScale instances, unless there aren't enough idle instances to terminate
            while (terminateInstancesIterator.hasNext() && terminationCounter < numberOfInstancesToTerminate) {
                String instanceName = terminateInstancesIterator.next();
                instancesToTerminate.add(instanceDnsNamesToInstanceIdsMap.get(instanceName));
                //this will indicate the instance is terminated in teardown
                entriesToRemoveFromMap.add(instanceName);
                terminationCounter++;
            }
            TerminateInstancesRequest terminateInstancesRequest = new TerminateInstancesRequest()
                    .withInstanceIds(instancesToTerminate);
            for (String key: entriesToRemoveFromMap) {
                instanceDnsNamesToInstanceIdsMap.remove(key);
            }
            EC2Client.terminateInstances(terminateInstancesRequest);
            modifier = -1;
        }
        int newCapacity = currentTargetCapacity;
        if (modifier > 0) {
            // If active > target, then a scale down has recently occurred. In this case, rather than scaling up
            // we should instead no-op
            if (activeFulfilledCapacity <= currentTargetCapacity) {
                int scalingCounter = 1;
                while (scalingCounter <= unitsPerScale
                        && agentManager.allowNewRemoteAgents(agentsPerInstance * scalingCounter)) {
                    // only scale up within the license restrictions. dont change target capacity if changing
                    // activeFulfilledCapacity would not result in a change relative to the currentTargetCapacity
                    newCapacity = Math.max(currentTargetCapacity, activeFulfilledCapacity + scalingCounter);
                    scalingCounter++;
                }
            } else {
                buildLogger.addBuildLogEntry("WARNING: Tried to scale up when active capacity > target capacity. You may need to increase the time between task executions.");
            }
            // if target is already greater than what we would scale to based on active, then don't scale
            // scale down case.
        } else if (modifier < 0) {
            // use subListEndIndex as it's possible the idleInstance threshold was exceeded but there weren't enough
            // idle instances to meet the maxUnitsPerScaleValue
            newCapacity = activeFulfilledCapacity - numberOfInstancesToTerminate;
        }
        // enforce lower bound of 1
        newCapacity = Math.max(newCapacity, MINIMUM_TARGET_CAPACITY);
        // enforce upper bound of 250, the maximum amount of Remote agent allowed by Bamboo
        newCapacity = Math.min(newCapacity, MAXIMUM_TARGET_CAPACITY);
        return newCapacity;
    }

    /**
     * Tag instances as being used with Bamboo Spot Fleet Plugin
     */
    private void tagInstances() {
        // tag instances as being used with bamboo
        if (activeInstanceDnsNamesToInstanceIdsMap != null && activeInstanceDnsNamesToInstanceIdsMap.size() > 0) {
            try {
                Tag tag = new Tag().withKey("Bamboo-Spot-Fleet-Plugin-0811").withValue("");
                CreateTagsRequest createTagsRequest = new CreateTagsRequest()
                        .withResources(activeInstanceDnsNamesToInstanceIdsMap.values()).withTags(tag);
                EC2Client.createTags(createTagsRequest);
            } catch (AmazonServiceException e) {
                buildLogger.addBuildLogEntry(
                        "CreateTagsRequest failed with amazon service exception code: " + e.getErrorCode());
            } catch (AmazonClientException e) {
                buildLogger
                        .addBuildLogEntry("CreateTagsRequest failed with amazon client exception: " + e.getMessage());
            }
        }
    }

    /**
     * remove offline fleet agents from Bamboo agent pool. Terminate builds executing on offline fleet agents.
     */
    private void agentTeardown() {
        List<Long> agentIdsToRemove = new LinkedList<Long>();
        HashSet<Long> activeAgentIds = new HashSet<Long>();
        Iterator<BuildAgent> agentIterator = agentManager.getAllNonElasticAgents().iterator();
        while (agentIterator.hasNext()) {
            BuildAgent currentAgent = agentIterator.next();
            String agentName = currentAgent.getName();
            // in case of multiple agents per instance we only want to consider the agent's name prefix
            // e.g we dont want to consider the " (2)" in "ip-172-31-66-67.ec2.internal (2)"
            String trueAgentName = agentName.split("\\.internal")[0] + ".internal";
            // regex to find agent names beginning with "ip-" and ending with ".internal". All supported regions
            // have instances with private DNS following this pattern
            if (!activeInstanceDnsNamesToInstanceIdsMap.keySet().contains(trueAgentName)
                    && Pattern.matches("(ip-)(.*)(\\.internal)", trueAgentName)) {
                agentIdsToRemove.add(currentAgent.getId());
            } else {
                activeAgentIds.add(currentAgent.getId());
            }
        }
        for (CurrentlyBuilding build : buildExecutionManager.getCurrentlyExecuting()) {
            if (!activeAgentIds.contains(build.getBuildAgentId())) {
                BuildIdentifier currentBI = build.getBuildIdentifier();
                PlanResultKey planResultKey = currentBI.getPlanResultKey();
                buildExecutionManager.finishBuild(planResultKey);
                try {
                    agentManager.removeAgent(build.getBuildAgentId());
                } catch (TimeoutException e) {
                    buildLogger.addBuildLogEntry(
                            "timed out removing agent with id: " + Long.toString(build.getBuildAgentId()));
                }
            }
        }
        for (Long id : agentIdsToRemove) {
            try {
                agentManager.removeAgent(id);
            } catch (TimeoutException e) {
                buildLogger.addBuildLogEntry("failed to remove agent with id: " + Long.toString(id));
            }
        }
    }

    /**
     * terminates fleet request if build queue is empty
     *
     * @param fleetID
     */
    private void terminateFleet(String fleetID) {
        // check for empty build queue
        if (!buildQueueManager.getQueuedExecutables().iterator().hasNext()) {
            CancelSpotFleetRequestsRequest cancelSpotFleetRequestsRequest = new CancelSpotFleetRequestsRequest()
                    .withSpotFleetRequestIds(fleetID).withTerminateInstances(true);
            try {
                CancelSpotFleetRequestsResult cancelSpotFleetRequestsResult = EC2Client
                        .cancelSpotFleetRequests(cancelSpotFleetRequestsRequest);
                if (cancelSpotFleetRequestsResult.getSuccessfulFleetRequests().isEmpty()) {
                    buildLogger.addBuildLogEntry("Cancellation of fleet failed");
                } else {
                    buildLogger.addBuildLogEntry("Cancellation of fleet succeeded");
                }
            } catch (AmazonServiceException e) {
                if (e.getErrorCode().equals("AuthFailure")) {
                } else if (e.getErrorCode().equals("UnauthorizedOperation")) {
                    buildLogger.addBuildLogEntry("provided credentials could not be validated");
                } else if (e.getErrorCode().equals("InvalidParameterValue")) {
                    buildLogger.addBuildLogEntry("provided fleet ID does not match regex: " + e.getErrorMessage());
                } else if (e.getErrorCode().equals("InvalidSpotFleetRequestId.NotFound")) {
                    buildLogger.addBuildLogEntry("fleet ID not valid in current region: " + e.getErrorMessage());
                } else {
                    buildLogger.addBuildLogEntry("amazon service exception: " + e.getErrorMessage());
                }
            } catch (AmazonClientException e) {
                buildLogger.addBuildLogEntry("amazon client exception: " + e.getMessage());
            }
        }
    }
}
