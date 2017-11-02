[@ww.textarea label="User data script" name="userData" required='false'/]
[@ww.textfield label="AWS User Access Key" name="accessKey" required='true'/]
[@ww.password label="AWS User Secret Key" name="secretKey" required='true'/]
[@ww.select
	label="Region"
	name="region"
	list = ["us-east-1 (N. Virginia)", "us-west-1 (N. California)", "us-west-2 (Oregon)", "eu-west-1 (Ireland)",
	 		"eu-central-1 (Frankfurt)", "ap-southeast-1 (Singapore)", "ap-southeast-2 (Sydney)", "ap-northeast-1 (Tokyo)", "ap-northeast-2 (Seoul)",
	 		"sa-east-1 (São Paulo)"
			]
	required="true"
/]
[@ww.textfield label="Fleet Request ID" name="fleetID" required='true'/]
[@ww.checkbox label="Terminate fleet upon completing all builds" name="terminateFleet" required='false'/]
[@ww.label label="Autoscaling options" name="autoscale"/]
[@ww.checkbox label="Enable autoscaling" name="enableAutoscaling" required='false'/]
[@ww.textfield label="Maximum queued builds before scaleup" name="queuedBuilds" required='true'/]
[@ww.textfield label="Average queue time in minutes before scaleup" name="averageQueueTime" required='true'/]
[@ww.textfield label="Maximum idle instances before scaledown" name="idleInstances" required='true'/]
[@ww.textfield label="Maximum units per scaling action" name="maxUnitsPerScale" required='true'/]
