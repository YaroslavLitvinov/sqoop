/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.sqoop.shell;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.OptionBuilder;
import org.apache.sqoop.model.MPrincipal;
import org.apache.sqoop.model.MPrivilege;
import org.apache.sqoop.model.MResource;
import org.apache.sqoop.shell.core.Constants;
import org.apache.sqoop.validation.Status;

import java.io.IOException;
import java.util.Arrays;

import static org.apache.sqoop.shell.ShellEnvironment.client;
import static org.apache.sqoop.shell.ShellEnvironment.printlnResource;
import static org.apache.sqoop.shell.ShellEnvironment.resourceString;

import static org.apache.sqoop.shell.utils.ConfigFiller.errorMessage;

public class RevokePrivilegeFunction extends SqoopFunction {
  private static final long serialVersionUID = 1L;

  @SuppressWarnings("static-access")
  public RevokePrivilegeFunction() {
    this.addOption(OptionBuilder
        .withLongOpt(Constants.OPT_RESOURCE_TYPE)
        .withDescription(resourceString(Constants.RES_PROMPT_RESOURCE_TYPE))
        .hasArg()
        .create()
    );
    this.addOption(OptionBuilder
        .withLongOpt(Constants.OPT_RESOURCE)
        .withDescription(resourceString(Constants.RES_PROMPT_RESOURCE))
        .hasArg()
        .create()
    );
    this.addOption(OptionBuilder
        .withLongOpt(Constants.OPT_ACTION)
        .withDescription(resourceString(Constants.RES_PROMPT_ACTION))
        .hasArg()
        .create(Constants.OPT_ACTION_CHAR)
    );
    this.addOption(OptionBuilder
        .withLongOpt(Constants.OPT_PRINCIPAL)
        .withDescription(resourceString(Constants.RES_PROMPT_PRINCIPAL))
        .isRequired()
        .hasArg()
        .create()
    );
    this.addOption(OptionBuilder
        .withLongOpt(Constants.OPT_PRINCIPAL_TYPE)
        .withDescription(resourceString(Constants.RES_PROMPT_PRINCIPAL_TYPE))
        .isRequired()
        .hasArg()
        .create()
    );
    this.addOption(OptionBuilder
        .withLongOpt(Constants.OPT_WITH_GRANT)
        .withDescription(resourceString(Constants.RES_PROMPT_WITH_GRANT))
        .create(Constants.OPT_WITH_GRANT_CHAR)
    );
  }

  @Override
  @SuppressWarnings("unchecked")
  public Object executeFunction(CommandLine line, boolean isInteractive) throws IOException {
    return revokePrivilege(
      line.getOptionValue(Constants.OPT_ACTION),
      line.getOptionValue(Constants.OPT_RESOURCE_TYPE),
      line.getOptionValue(Constants.OPT_RESOURCE),
      line.getOptionValue(Constants.OPT_PRINCIPAL_TYPE),
      line.getOptionValue(Constants.OPT_PRINCIPAL),
      line.hasOption(Constants.OPT_WITH_GRANT));
  }

  private Status revokePrivilege(String action, String resourceType, String resource,
                                 String principalType, String principal, boolean withGrant)
    throws IOException {
    MPrivilege privilegeObject = null;
    if (resource != null && !resource.isEmpty()
        && resourceType != null && !resourceType.isEmpty()
        && action != null && !action.isEmpty()) {
      MResource resourceObject = new MResource(resource, resourceType);
      privilegeObject = new MPrivilege(resourceObject, action, withGrant);
    } else if ((resource == null || resource.isEmpty())
        && (resourceType == null || resourceType.isEmpty())
        && (action == null || action.isEmpty())) {
      // revoke all privileges on the principal
      privilegeObject = null;
    } else if (resource == null || resource.isEmpty()) {
      errorMessage("--resource isn't specified.");
      return Status.ERROR;
    } else if (resourceType == null || resourceType.isEmpty()) {
      errorMessage("--resource-type isn't specified.");
      return Status.ERROR;
    } else if (action == null || action.isEmpty()) {
      errorMessage("--action isn't specified.");
      return Status.ERROR;
    }
    MPrincipal principalObject = new MPrincipal(principal, principalType);

    client.revokePrivilege(
      Arrays.asList(principalObject),
      privilegeObject == null ? null : Arrays.asList(privilegeObject));

    client.clearCache();

    if (privilegeObject != null) {
      printlnResource(Constants.RES_REVOKE_PRIVILEGE_SUCCESSFUL,
        action, resourceType + " " + resource,
        ((withGrant) ? " " + resourceString(Constants.RES_REVOKE_PRIVILEGE_SUCCESSFUL_WITH_GRANT) : ""),
        principalType + " " + principal);
    } else {
      printlnResource(Constants.RES_REVOKE_ALL_PRIVILEGES_SUCCESSFUL,
        principalType + " " + principal);
    }

    return Status.OK;
  }
}
