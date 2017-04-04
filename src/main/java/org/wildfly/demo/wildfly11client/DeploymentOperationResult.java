/******************************************************************************* 
 * Copyright (c) 2011 Red Hat, Inc. 
 * Distributed under license by Red Hat, Inc. All rights reserved. 
 * This program is made available under the terms of the 
 * Eclipse Public License v1.0 which accompanies this distribution, 
 * and is available at http://www.eclipse.org/legal/epl-v10.html 
 * 
 * Contributors: 
 * Red Hat, Inc. - initial API and implementation 
 ******************************************************************************/ 

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.jboss.as.controller.client.helpers.standalone.DeploymentAction;
import org.jboss.as.controller.client.helpers.standalone.ServerDeploymentActionResult;
import org.jboss.as.controller.client.helpers.standalone.ServerDeploymentPlanResult;

/**
 * A class that holds the status of a deployment operation.
 * 
 * @author André Dietisheim
 *
 */
public class DeploymentOperationResult {
	
	private Future<ServerDeploymentPlanResult> planResult;
	private DeploymentAction action;
	private long timeout;
	private TimeUnit unit;

	DeploymentOperationResult(DeploymentAction action, Future<ServerDeploymentPlanResult> planResult) {
		this.action = action;
		this.planResult = planResult;
	}
	DeploymentOperationResult(DeploymentAction action, Future<ServerDeploymentPlanResult> planResult, long timeout, TimeUnit unit) {
		this(action, planResult);
		this.timeout = timeout;
		this.unit = unit;
	}

	public ServerDeploymentActionResult getStatus() throws Exception {
		try {
			ServerDeploymentActionResult actionResult = null;
			if( unit == null )
				actionResult = planResult.get().getDeploymentActionResult(action.getId());
			else
				actionResult = planResult.get(timeout, unit).getDeploymentActionResult(action.getId());
			return actionResult;
		} catch (Exception e) {
			throw e;
		}
	}

	/*
	 * Candidate for API
	 */
	public boolean isDone() {
		return planResult.isDone();
	}
	
	/*
	 * Candidate for API
	 */
	public void cancel() {
		planResult.cancel(true);
	}
}