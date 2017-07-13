package org.ekstep.graph.service.request.validator;

import static com.ilimi.graph.dac.util.Neo4jGraphUtil.getNodeByUniqueId;

import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.ekstep.graph.service.common.DACConfigurationConstants;
import org.ekstep.graph.service.common.DACErrorCodeConstants;
import org.ekstep.graph.service.common.DACErrorMessageConstants;
import org.ekstep.graph.service.common.NodeUpdateMode;
import org.ekstep.graph.service.util.DefinitionNodeUtil;
import org.ekstep.graph.service.util.PassportUtil;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Transaction;

import com.ilimi.common.dto.Request;
import com.ilimi.common.exception.ClientException;
import com.ilimi.common.logger.PlatformLogger;
import com.ilimi.graph.common.DateUtils;
import com.ilimi.graph.dac.enums.GraphDACParams;
import com.ilimi.graph.dac.enums.SystemNodeTypes;
import com.ilimi.graph.dac.model.Node;
import com.ilimi.graph.dac.util.Neo4jGraphFactory;

public class Neo4JEmbeddedDataVersionKeyValidator {

	public boolean validateUpdateOperation(String graphId, org.neo4j.graphdb.Node neo4jNode,
			com.ilimi.graph.dac.model.Node node, Request request) {
		PlatformLogger.log("Graph Engine Node: ", node);
		PlatformLogger.log("Neo4J Node: ", neo4jNode);

		boolean isValidUpdateOperation = false;

		// Fetching Version Check Mode ('OFF', 'STRICT', 'LINIENT')
		String versionCheckMode = DefinitionNodeUtil.getMetadataValue(graphId, node.getObjectType(),
				GraphDACParams.versionCheckMode.name(), request);
		PlatformLogger.log("Version Check Mode in Definition Node: " + versionCheckMode + " for Object Type: "
				+ node.getObjectType());

		// Checking if the 'versionCheckMode' Property is not specified,
		// then default Mode is OFF
		if (StringUtils.isBlank(versionCheckMode)
				|| StringUtils.equalsIgnoreCase(SystemNodeTypes.DEFINITION_NODE.name(), node.getNodeType()))
			versionCheckMode = NodeUpdateMode.OFF.name();

		// Checking of Node Update Version Checking is either 'STRICT'
		// or 'LENIENT'.
		// If Number of Modes are increasing then the Condition should
		// be checked for 'OFF' Mode Only.
		if (StringUtils.equalsIgnoreCase(NodeUpdateMode.STRICT.name(), versionCheckMode)
				|| StringUtils.equalsIgnoreCase(NodeUpdateMode.LENIENT.name(), versionCheckMode)) {
			boolean isValidVersionKey = isValidVersionKey(neo4jNode, node);
			PlatformLogger.log("Is Valid Version Key ? " + isValidVersionKey);

			if (!isValidVersionKey) {
				// Checking for Strict Mode
				PlatformLogger.log("Checking for Node Update Operation Mode is 'STRICT' for Node Id: " + node.getIdentifier());
				if (StringUtils.equalsIgnoreCase(NodeUpdateMode.STRICT.name(), versionCheckMode))
					throw new ClientException(DACErrorCodeConstants.ERR_STALE_VERSION_KEY.name(),
							DACErrorMessageConstants.INVALID_VERSION_KEY_ERROR + " | [Unable to Update the Data.]");

				// Checking for Lenient Mode
				PlatformLogger.log(
						"Checking for Node Update Operation Mode is 'LENIENT' for Node Id: " + node.getIdentifier());
				if (StringUtils.equalsIgnoreCase(NodeUpdateMode.LENIENT.name(), versionCheckMode))
					node.getMetadata().put(GraphDACParams.NODE_UPDATE_STATUS.name(),
							GraphDACParams.STALE_DATA_UPDATED.name());

				// Update Operation is Valid
				isValidUpdateOperation = true;
				PlatformLogger.log("Update Operation is Valid for Node Id: " + node.getIdentifier());
			}
		}

		PlatformLogger.log("Is Valid Update Operation ? " + isValidUpdateOperation);
		return isValidUpdateOperation;
	}

	public boolean validateUpdateOperation(String graphId, Node node, Request request) {
		PlatformLogger.log("Graph Engine Node: ", node);

		boolean isValidUpdateOperation = false;

		// Fetching Version Check Mode ('OFF', 'STRICT', 'LINIENT')
		String versionCheckMode = DefinitionNodeUtil.getMetadataValue(graphId, node.getObjectType(),
				GraphDACParams.versionCheckMode.name(), request);
		PlatformLogger.log("Version Check Mode in Definition Node: " + versionCheckMode + " for Object Type: "
				+ node.getObjectType());

		// Checking if the 'versionCheckMode' Property is not specified,
		// then default Mode is OFF
		if (StringUtils.isBlank(versionCheckMode))
			versionCheckMode = NodeUpdateMode.OFF.name();

		// Checking of Node Update Version Checking is either 'STRICT'
		// or 'LENIENT'.
		// If Number of Modes are increasing then the Condition should
		// be checked for 'OFF' Mode Only.
		if (StringUtils.equalsIgnoreCase(NodeUpdateMode.STRICT.name(), versionCheckMode)
				|| StringUtils.equalsIgnoreCase(NodeUpdateMode.LENIENT.name(), versionCheckMode)) {
			boolean isValidVersionKey = isValidVersionKey(graphId, node, request);
			PlatformLogger.log("Is Valid Version Key ? " + isValidVersionKey);

			if (!isValidVersionKey) {
				// Checking for Strict Mode
				PlatformLogger.log("Checking for Node Update Operation Mode is 'STRICT' for Node Id: " + node.getIdentifier());
				if (StringUtils.equalsIgnoreCase(NodeUpdateMode.STRICT.name(), versionCheckMode))
					throw new ClientException(DACErrorCodeConstants.ERR_STALE_VERSION_KEY.name(),
							DACErrorMessageConstants.INVALID_VERSION_KEY_ERROR + " | [Unable to Update the Data.]");

				// Checking for Lenient Mode
				PlatformLogger.log(
						"Checking for Node Update Operation Mode is 'LENIENT' for Node Id: " + node.getIdentifier());
				if (StringUtils.equalsIgnoreCase(NodeUpdateMode.LENIENT.name(), versionCheckMode))
					node.getMetadata().put(GraphDACParams.NODE_UPDATE_STATUS.name(),
							GraphDACParams.STALE_DATA_UPDATED.name());

				// Update Operation is Valid
				isValidUpdateOperation = true;
				PlatformLogger.log("Update Operation is Valid for Node Id: " + node.getIdentifier());
			}
		}
		PlatformLogger.log("Is Valid Update Operation ? " + isValidUpdateOperation);

		return isValidUpdateOperation;
	}

	private boolean isValidVersionKey(String graphId, Node node, Request request) {
		PlatformLogger.log("Node: ", node);

		boolean isValidVersionKey = false;

		String versionKey = (String) node.getMetadata().get(GraphDACParams.versionKey.name());
		PlatformLogger.log("Data Node Version Key Value: " + versionKey + " | [Node Id: '" + node.getIdentifier() + "']");

		// Fetching Neo4J Node
		org.neo4j.graphdb.Node neo4jNode = getNeo4jNode(graphId, node.getIdentifier(), request);
		PlatformLogger.log("Fetched the Neo4J Node Id: " + neo4jNode.getId() + " | [Node Id: '" + node.getIdentifier() + "']");

		// Reading Last Updated On time stamp from Neo4J Node
		String lastUpdateOn = (String) neo4jNode.getProperty(GraphDACParams.lastUpdatedOn.name());
		PlatformLogger.log("Fetched 'lastUpdatedOn' Property from the Neo4J Node Id: " + neo4jNode.getId()
				+ " as 'lastUpdatedOn': " + lastUpdateOn + " | [Node Id: '" + node.getIdentifier() + "']");
		if (StringUtils.isBlank(lastUpdateOn))
			throw new ClientException(DACErrorCodeConstants.INVALID_TIMESTAMP.name(),
					DACErrorMessageConstants.INVALID_LAST_UPDATED_ON_TIMESTAMP + " | [Node Id: " + node.getIdentifier()
							+ "]");

		// Converting 'lastUpdatedOn' to milli seconds of type Long
		String graphVersionKey = (String) neo4jNode.getProperty(GraphDACParams.versionKey.name());
		if (StringUtils.isBlank(graphVersionKey))
			graphVersionKey = String.valueOf(DateUtils.parse(lastUpdateOn).getTime());
		PlatformLogger.log("'lastUpdatedOn' Time Stamp: " + graphVersionKey + " | [Node Id: '" + node.getIdentifier() + "']");

		// Compare both the Time Stamp
		if (StringUtils.equals(versionKey, graphVersionKey))
			isValidVersionKey = true;

		// Remove 'SYS_INTERNAL_LAST_UPDATED_ON' property
		node.getMetadata().remove(GraphDACParams.SYS_INTERNAL_LAST_UPDATED_ON.name());

		// May be the Given 'versionKey' is a Passport Key.
		// Check for the Valid Passport Key
		if (BooleanUtils.isFalse(isValidVersionKey)
				&& BooleanUtils.isTrue(DACConfigurationConstants.IS_PASSPORT_AUTHENTICATION_ENABLED)) {
			isValidVersionKey = PassportUtil.isValidPassportKey(versionKey);
			if (BooleanUtils.isTrue(isValidVersionKey))
				node.getMetadata().put(GraphDACParams.SYS_INTERNAL_LAST_UPDATED_ON.name(),
						DateUtils.formatCurrentDate());
		}

		return isValidVersionKey;
	}

	private boolean isValidVersionKey(org.neo4j.graphdb.Node neo4jNode, com.ilimi.graph.dac.model.Node node) {
		PlatformLogger.log("Graph Node: ", node);
		PlatformLogger.log("Neo4J Node: ", neo4jNode);

		boolean isValidVersionKey = false;

		String versionKey = (String) node.getMetadata().get(GraphDACParams.versionKey.name());
		PlatformLogger.log("Data Node Version Key Value: " + versionKey + " | [Node Id: '" + node.getIdentifier() + "']");

		if (StringUtils.isBlank(versionKey))
			throw new ClientException(DACErrorCodeConstants.BLANK_VERSION.name(),
					DACErrorMessageConstants.BLANK_VERSION_KEY_ERROR + " | [Node Id: " + node.getIdentifier() + "]");

		PlatformLogger.log("Fetched the Neo4J Node Id: " + neo4jNode.getId() + " | [Node Id: '" + node.getIdentifier() + "']");

		// Reading Last Updated On time stamp from Neo4J Node
		String lastUpdateOn = (String) neo4jNode.getProperty(GraphDACParams.lastUpdatedOn.name());
		PlatformLogger.log("Fetched 'lastUpdatedOn' Property from the Neo4J Node Id: " + neo4jNode.getId()
				+ " as 'lastUpdatedOn': " + lastUpdateOn + " | [Node Id: '" + node.getIdentifier() + "']");
		if (StringUtils.isBlank(lastUpdateOn))
			throw new ClientException(DACErrorCodeConstants.INVALID_TIMESTAMP.name(),
					DACErrorMessageConstants.INVALID_LAST_UPDATED_ON_TIMESTAMP + " | [Node Id: " + node.getIdentifier()
							+ "]");

		// Converting 'lastUpdatedOn' to milli seconds of type Long
		String graphVersionKey = (String) neo4jNode.getProperty(GraphDACParams.versionKey.name());
		if (StringUtils.isBlank(graphVersionKey))
			graphVersionKey = String.valueOf(DateUtils.parse(lastUpdateOn).getTime());
		PlatformLogger.log("'lastUpdatedOn' Time Stamp: " + graphVersionKey + " | [Node Id: '" + node.getIdentifier() + "']");

		// Compare both the Time Stamp
		if (StringUtils.equals(versionKey, graphVersionKey))
			isValidVersionKey = true;

		// Remove 'SYS_INTERNAL_LAST_UPDATED_ON' property
		node.getMetadata().remove(GraphDACParams.SYS_INTERNAL_LAST_UPDATED_ON.name());

		// May be the Given 'versionKey' is a Passport Key.
		// Check for the Valid Passport Key
		if (BooleanUtils.isFalse(isValidVersionKey)
				&& BooleanUtils.isTrue(DACConfigurationConstants.IS_PASSPORT_AUTHENTICATION_ENABLED)) {
			isValidVersionKey = PassportUtil.isValidPassportKey(versionKey);
			if (BooleanUtils.isTrue(isValidVersionKey))
				node.getMetadata().put(GraphDACParams.SYS_INTERNAL_LAST_UPDATED_ON.name(),
						DateUtils.formatCurrentDate());
		}

		return isValidVersionKey;
	}

	private org.neo4j.graphdb.Node getNeo4jNode(String graphId, String identifier, Request request) {
		GraphDatabaseService graphDb = Neo4jGraphFactory.getGraphDb(graphId, request);
		try (Transaction tx = graphDb.beginTx()) {
			PlatformLogger.log("Transaction Started For 'getNeo4jNode' Operation. | [Node ID: '" + identifier + "']");
			org.neo4j.graphdb.Node neo4jNode = getNodeByUniqueId(graphDb, identifier);

			tx.success();
			PlatformLogger.log("Transaction For Operation 'getNeo4jNode' Completed Successfully. | [Node ID: '" + identifier
					+ "']");

			PlatformLogger.log("Returning the Neo4J Node. | [Node ID: '" + identifier + "']");
			return neo4jNode;
		}
	}

}