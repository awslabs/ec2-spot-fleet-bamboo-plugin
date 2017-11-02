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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.Request;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.CancelSpotFleetRequestsRequest;
import com.amazonaws.services.ec2.model.CreateTagsRequest;
import com.amazonaws.services.ec2.model.DescribeSpotFleetInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeSpotFleetInstancesResult;
import com.amazonaws.services.ec2.model.DescribeSpotFleetRequestsRequest;
import com.amazonaws.services.ec2.model.DescribeSpotFleetRequestsResult;
import com.amazonaws.services.ec2.model.DryRunResult;
import com.amazonaws.services.ec2.model.DryRunSupportedRequest;
import com.amazonaws.services.ec2.model.ModifySpotFleetRequestRequest;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;
import com.amazonaws.services.ec2.model.transform.CancelSpotFleetRequestsRequestMarshaller;
import com.amazonaws.services.ec2.model.transform.CreateTagsRequestMarshaller;
import com.amazonaws.services.ec2.model.transform.ModifySpotFleetRequestRequestMarshaller;
import com.amazonaws.services.ec2.model.transform.TerminateInstancesRequestMarshaller;
import com.atlassian.bamboo.collections.ActionParametersMap;
import com.atlassian.bamboo.configuration.AdministrationConfigurationAccessor;
import com.atlassian.bamboo.task.AbstractTaskConfigurator;
import com.atlassian.bamboo.task.TaskDefinition;
import com.atlassian.bamboo.utils.error.ErrorCollection;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.sal.api.user.UserManager;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.opensymphony.xwork.TextProvider;

/**
 * Configurator class sits between task UI (specified in ../resources/ftl/editSpotFleet.ftl) and task execution
 * codecontext deals with the UI elements, config with the mapping of keys to inputs for use in task. params are form
 * elements after submission
 *
 * @author kalteu
 *
 */
public class SpotFleetConfigurator extends AbstractTaskConfigurator {

    public final int EC2REQUEST_SUCCESS_CODE = 412;
    public final int EC2REQUEST_ERROR_CODE = 403;
    public final int EMPTY_INTEGER_VALUE = -1;

    private TextProvider textProvider;
    private AdministrationConfigurationAccessor administrationConfigurationAccessor;
    private Set<String> regionCodeAndNamesSet = ImmutableSet.of("us-east-1 (N. Virginia)", "us-west-1 (N. California)",
            "us-west-2 (Oregon)", "eu-west-1 (Ireland)", "eu-central-1 (Frankfurt)", "ap-southeast-1 (Singapore)",
            "ap-southeast-2 (Sydney)", "ap-northeast-1 (Tokyo)", "ap-northeast-2 (Seoul)", "sa-east-1 (São Paulo)");

    // any components imported here must be present in main class as well
    @Autowired
    public SpotFleetConfigurator(
            @ComponentImport AdministrationConfigurationAccessor administrationConfigurationAccessor) {
        this.administrationConfigurationAccessor = administrationConfigurationAccessor;
    }

    /**
     * method to map values of user-inputted data into config map accessible via task and task configuration. Executes
     * on form submission if validation passes without errors
     */
    @NotNull
    @Override
    public Map<String, String> generateTaskConfigMap(@NotNull final ActionParametersMap params,
            @Nullable final TaskDefinition previousTaskDefinition) {
        final Map<String, String> config = super.generateTaskConfigMap(params, previousTaskDefinition);
        config.put(StringConstants.ACCESS_KEY, params.getString(StringConstants.ACCESS_KEY));
        config.put(StringConstants.SECRET_KEY, params.getString(StringConstants.SECRET_KEY));
        config.put(StringConstants.FLEET_ID, params.getString(StringConstants.FLEET_ID));
        String regionName = params.getString(StringConstants.REGION);
        String regionCode = regionName.split(" \\(")[0];
        config.put(StringConstants.REGION, regionCode);
        config.put(StringConstants.TERMINATE_FLEET,
                Boolean.toString(params.getBoolean(StringConstants.TERMINATE_FLEET)));
        config.put(StringConstants.ENABLE_AUTOSCALING,
                Boolean.toString(params.getBoolean(StringConstants.ENABLE_AUTOSCALING)));
        config.put(StringConstants.QUEUED_BUILDS,
                Integer.toString(params.getInt(StringConstants.QUEUED_BUILDS, EMPTY_INTEGER_VALUE)));
        config.put(StringConstants.AVERAGE_QUEUE_TIME,
                Integer.toString(params.getInt(StringConstants.AVERAGE_QUEUE_TIME, EMPTY_INTEGER_VALUE)));
        config.put(StringConstants.IDLE_INSTANCES,
                Integer.toString(params.getInt(StringConstants.IDLE_INSTANCES, EMPTY_INTEGER_VALUE)));
        config.put(StringConstants.MAXIMUM_UNITS_PER_SCALE,
                Integer.toString(params.getInt(StringConstants.MAXIMUM_UNITS_PER_SCALE, EMPTY_INTEGER_VALUE)));
        return config;
    }

    /**
     * populates UI elements during task creation. Currently populates user data text box with agent install script
     */
    @Override
    public void populateContextForCreate(@NotNull final Map<String, Object> context) {
        super.populateContextForCreate(context);
        context.put(StringConstants.USER_DATA, generateUserDataScript());
    }

    /**
     * retrieve previously-entered data from config map, enter it into text boxes when a task is edited
     */
    @Override
    public void populateContextForEdit(@NotNull final Map<String, Object> context,
            @NotNull final TaskDefinition taskDefinition) {
        super.populateContextForEdit(context, taskDefinition);
        context.put(StringConstants.USER_DATA, generateUserDataScript());
        context.put(StringConstants.ACCESS_KEY, taskDefinition.getConfiguration().get(StringConstants.ACCESS_KEY));
        context.put(StringConstants.SECRET_KEY, taskDefinition.getConfiguration().get(StringConstants.SECRET_KEY));
        context.put(StringConstants.FLEET_ID, taskDefinition.getConfiguration().get(StringConstants.FLEET_ID));
        String regionCodeName = taskDefinition.getConfiguration().get(StringConstants.REGION);
        String regionFullName = getRegionNameFromCodeName(regionCodeName);
        context.put(StringConstants.REGION, regionFullName);
        context.put(StringConstants.TERMINATE_FLEET,
                taskDefinition.getConfiguration().get(StringConstants.TERMINATE_FLEET));
        context.put(StringConstants.ENABLE_AUTOSCALING,
                taskDefinition.getConfiguration().get(StringConstants.ENABLE_AUTOSCALING));
        context.put(StringConstants.QUEUED_BUILDS,
                taskDefinition.getConfiguration().get(StringConstants.QUEUED_BUILDS));
        context.put(StringConstants.AVERAGE_QUEUE_TIME,
                taskDefinition.getConfiguration().get(StringConstants.AVERAGE_QUEUE_TIME));
        context.put(StringConstants.IDLE_INSTANCES,
                taskDefinition.getConfiguration().get(StringConstants.IDLE_INSTANCES));
        context.put(StringConstants.MAXIMUM_UNITS_PER_SCALE,
                taskDefinition.getConfiguration().get(StringConstants.MAXIMUM_UNITS_PER_SCALE));
    }

    /**
     * retrieve previously-entered data from config map, enter it into text boxes when a task is viewed
     */
    @Override
    public void populateContextForView(@NotNull final Map<String, Object> context,
            @NotNull final TaskDefinition taskDefinition) {
        super.populateContextForView(context, taskDefinition);
        context.put(StringConstants.USER_DATA, generateUserDataScript());
        context.put(StringConstants.ACCESS_KEY, taskDefinition.getConfiguration().get(StringConstants.ACCESS_KEY));
        context.put(StringConstants.SECRET_KEY, taskDefinition.getConfiguration().get(StringConstants.SECRET_KEY));
        context.put(StringConstants.FLEET_ID, taskDefinition.getConfiguration().get(StringConstants.FLEET_ID));
        String regionCodeName = taskDefinition.getConfiguration().get(StringConstants.REGION);
        String regionFullName = getRegionNameFromCodeName(regionCodeName);
        context.put(StringConstants.REGION, regionFullName);
        context.put(StringConstants.TERMINATE_FLEET,
                taskDefinition.getConfiguration().get(StringConstants.TERMINATE_FLEET));
        context.put(StringConstants.ENABLE_AUTOSCALING,
                taskDefinition.getConfiguration().get(StringConstants.ENABLE_AUTOSCALING));
        context.put(StringConstants.QUEUED_BUILDS,
                taskDefinition.getConfiguration().get(StringConstants.QUEUED_BUILDS));
        context.put(StringConstants.AVERAGE_QUEUE_TIME,
                taskDefinition.getConfiguration().get(StringConstants.AVERAGE_QUEUE_TIME));
        context.put(StringConstants.IDLE_INSTANCES,
                taskDefinition.getConfiguration().get(StringConstants.IDLE_INSTANCES));
        context.put(StringConstants.MAXIMUM_UNITS_PER_SCALE,
                taskDefinition.getConfiguration().get(StringConstants.MAXIMUM_UNITS_PER_SCALE));
    }

    /**
     * errorCollection must be empty upon completion of this method for submission of data to succeed
     */
    @Override
    public void validate(@NotNull final ActionParametersMap params, @NotNull final ErrorCollection errorCollection) {
        super.validate(params, errorCollection);
        final String accessKeyInput = params.getString(StringConstants.ACCESS_KEY);
        final String secretKeyInput = params.getString(StringConstants.SECRET_KEY);
        final String fleetIdInput = params.getString(StringConstants.FLEET_ID);
        String regionName = params.getString(StringConstants.REGION);
        final String regionInput = regionName.split(" \\(")[0];
        final boolean terminateFleetInput = Boolean.parseBoolean(params.getString(StringConstants.TERMINATE_FLEET));
        final boolean enableAutoscalingInput = Boolean
                .parseBoolean(params.getString(StringConstants.ENABLE_AUTOSCALING));
        // client-side validation. Ensure all required inputs are present, of
        // correct type, and within allowed bounds
        if (StringUtils.isEmpty(accessKeyInput)) {
            errorCollection.addError(StringConstants.ACCESS_KEY, "Can't be empty");
        }
        if (StringUtils.isEmpty(secretKeyInput)) {
            errorCollection.addError(StringConstants.SECRET_KEY, "Can't be empty");
        }
        if (StringUtils.isEmpty(fleetIdInput)) {
            errorCollection.addError(StringConstants.FLEET_ID, "Can't be empty");
        }
        if (enableAutoscalingInput) {
            try {
                final int queuedBuildsInput = Integer.parseInt(params.getString(StringConstants.QUEUED_BUILDS));
                // TODO verify no upper bound
                if (queuedBuildsInput <= 0) {
                    errorCollection.addError(StringConstants.QUEUED_BUILDS, "Integer value must be positive");
                }
                final int averageQueueTimeInput = Integer
                        .parseInt(params.getString(StringConstants.AVERAGE_QUEUE_TIME));
                if (averageQueueTimeInput <= 0) {
                    errorCollection.addError(StringConstants.AVERAGE_QUEUE_TIME, "Integer value must be positive");
                }
                final int idleInstancesInput = Integer.parseInt(params.getString(StringConstants.IDLE_INSTANCES));
                if (idleInstancesInput <= 0) {
                    errorCollection.addError(StringConstants.IDLE_INSTANCES, "Integer value must be positive");
                }
                final int unitsPerScaleInput = Integer
                        .parseInt(params.getString(StringConstants.MAXIMUM_UNITS_PER_SCALE));
                if (unitsPerScaleInput <= 0) {
                    errorCollection.addError(StringConstants.MAXIMUM_UNITS_PER_SCALE, "Integer value must be positive");
                }
            } catch (NumberFormatException e) {
                errorCollection.addError(StringConstants.ENABLE_AUTOSCALING,
                        "Autoscaling inputs must be positive integers");
            }
        }
        if (!errorCollection.hasAnyErrors()) {
            // server-side validation. Test that user-provided credentials/fleet ID
            // are valid, active, and have necessary permissions
            BasicAWSCredentials awsCredentials = new BasicAWSCredentials(accessKeyInput, secretKeyInput);
            AmazonEC2 EC2Client = new AmazonEC2Client(awsCredentials).withRegion(Regions.fromName(regionInput));
            // TODO: refactor into a single IAM policy call.
            DescribeSpotFleetRequestsRequest describeSpotFleetRequestsRequest = new DescribeSpotFleetRequestsRequest();
            describeSpotFleetRequestsRequest.withSpotFleetRequestIds(fleetIdInput);
            try {
                DescribeSpotFleetRequestsResult describeSpotFleetRequestsResult = EC2Client
                        .describeSpotFleetRequests(describeSpotFleetRequestsRequest);
                String fleetRequestState = describeSpotFleetRequestsResult.getSpotFleetRequestConfigs().get(0)
                        .getSpotFleetRequestState();
                // ensure fleet request is active
                if (!fleetRequestState.contains("active")) {
                    errorCollection.addError(StringConstants.FLEET_ID,
                            "Provided fleet needs to be active, fleet status is: " + fleetRequestState);
                }
                // check user has DescribeSpotFleetInstances permissions
                DescribeSpotFleetInstancesRequest describeSpotfleetInstancesRequest = new DescribeSpotFleetInstancesRequest()
                        .withSpotFleetRequestId(fleetIdInput);
                DescribeSpotFleetInstancesResult describeSpotFleetInstancesResult = EC2Client
                        .describeSpotFleetInstances(describeSpotfleetInstancesRequest);
                // check user has modify spot fleet permissions
                DryRunSupportedRequest<ModifySpotFleetRequestRequest> tryFleetModification = new DryRunSupportedRequest<ModifySpotFleetRequestRequest>() {
                    // create dry run request - check for modify spot fleet
                    // permissions without actually placing request
                    @Override
                    public Request<ModifySpotFleetRequestRequest> getDryRunRequest() {
                        ModifySpotFleetRequestRequest modifySpotFleetRequestRequest = new ModifySpotFleetRequestRequest()
                                .withSpotFleetRequestId(fleetIdInput);
                        Request<ModifySpotFleetRequestRequest> request = new ModifySpotFleetRequestRequestMarshaller()
                                .marshall(modifySpotFleetRequestRequest);
                        request.addParameter("DryRun", Boolean.toString(true));
                        return request;
                    }
                };
                DryRunResult modifyDryRunResult = EC2Client.dryRun(tryFleetModification);
                if (modifyDryRunResult.getDryRunResponse().getStatusCode() == EC2REQUEST_ERROR_CODE) {
                    errorCollection.addError(StringConstants.ACCESS_KEY, "ModifySpotFleetRequest failed with error: "
                            + modifyDryRunResult.getDryRunResponse().getErrorMessage());
                }
                // check user has cancel spot fleet permissions if they want fleet to terminate after builds finish
                if (terminateFleetInput) {
                    DryRunSupportedRequest<CancelSpotFleetRequestsRequest> tryFleetCancellation = new DryRunSupportedRequest<CancelSpotFleetRequestsRequest>() {
                        @Override
                        public Request<CancelSpotFleetRequestsRequest> getDryRunRequest() {
                            CancelSpotFleetRequestsRequest cancelSpotFleetRequestRequest = new CancelSpotFleetRequestsRequest()
                                    .withSpotFleetRequestIds(fleetIdInput);
                            Request<CancelSpotFleetRequestsRequest> request = new CancelSpotFleetRequestsRequestMarshaller()
                                    .marshall(cancelSpotFleetRequestRequest);
                            request.addParameter("DryRun", Boolean.toString(true));
                            return request;
                        }
                    };
                    DryRunResult cancelDryRunResult = EC2Client.dryRun(tryFleetCancellation);
                    if (cancelDryRunResult.getDryRunResponse().getStatusCode() == EC2REQUEST_ERROR_CODE) {
                        errorCollection.addError(StringConstants.ACCESS_KEY,
                                "CancelSpotFleetRequest failed with error: "
                                        + cancelDryRunResult.getDryRunResponse().getErrorMessage());
                    }
                }
            } catch (AmazonServiceException e) {
                if (e.getErrorCode().equals("AuthFailure")) {
                    errorCollection.addError(StringConstants.ACCESS_KEY, "Provided credentials could not be validated in selected Region");
                } else if (e.getErrorCode().equals("UnauthorizedOperation")) {
                    errorCollection.addError(StringConstants.ACCESS_KEY,
                            "These credentials do not have ec2: DescribeSpotFleetRequest permissions: "
                                    + e.getErrorMessage());
                } else if (e.getErrorCode().equals("InvalidParameterValue")) {
                    errorCollection.addError(StringConstants.FLEET_ID,
                            "Provided fleet ID does not match regex: " + e.getErrorMessage());
                } else if (e.getErrorCode().equals("InvalidSpotFleetRequestId.NotFound")) {
                    errorCollection.addError(StringConstants.FLEET_ID,
                            "Fleet ID not valid in current region: " + e.getErrorMessage());
                } else {
                    errorCollection.addError(StringConstants.ACCESS_KEY, e.getErrorMessage());
                    errorCollection.addError(StringConstants.SECRET_KEY, e.getErrorMessage());
                }
            } catch (AmazonClientException e) {
                errorCollection.addError(StringConstants.ACCESS_KEY, e.getMessage());
                errorCollection.addError(StringConstants.SECRET_KEY, e.getMessage());
            }
        }
    }

    /**
     * method to generate bash script for agent installation on instance launch via user data.
     *
     * @return agent installation script
     * @throws IOException
     */
    public String generateUserDataScript() {
        // retrieve bamboo server URL
        String serverURL = administrationConfigurationAccessor.getAdministrationConfiguration().getBaseUrl();
        // get current file path
        // (AwsSpotFleetBambooPlugin/target/container/tomcat8x/cargo-bamboo-home)
        String filePath = new File("").getAbsolutePath();
        StringBuilder script = new StringBuilder();
        try {
            BufferedReader br = new BufferedReader(
                    new FileReader(filePath + "/../../../classes/scripts/UserDataLaunchScript"));
            String currentLine;
            while ((currentLine = br.readLine()) != null) {
                // replace placeholder with server's URL
                currentLine = currentLine.replace("SERVER_URL", serverURL);
                script.append(currentLine + System.lineSeparator());
            }
        } catch (FileNotFoundException e) {
            return "User data script not found";
        } catch (IOException e) {
            return "IO error while reading User data script";
        }
        return script.toString();
    }
    /**
     * Retrieve a region's full name from its code name
     */
    private String getRegionNameFromCodeName(String codeName) {
        String regionFullName = null;
        for (String regionCodeName : regionCodeAndNamesSet) {
            if (regionCodeName.contains(codeName)) {
                regionFullName = regionCodeName;
            }
        }
        return regionFullName;
    }
}
