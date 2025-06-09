package com.sailpoint.connector.accessio.racf;

import sailpoint.connector.AbstractConnector;
import sailpoint.connector.ConnectorException;
import sailpoint.connector.Connector;
import sailpoint.object.*;
import sailpoint.tools.Util;
import sailpoint.tools.xml.XMLObjectFactory;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.*;

/**
 * Accessio RACF Connector for SailPoint IdentityIQ
 * 
 * This connector provides comprehensive integration with IBM RACF systems
 * through the Garancy API platform, supporting user lifecycle management,
 * role-based access control, and compliance requirements.
 * 
 * Key Features:
 * - Multi-user type support (AARID, STC, Technical)
 * - Approval workflow integration
 * - User-Role recertification
 * - Org Unit management
 * - Garancy API integration
 * 
 * @author SailPoint Professional Services
 * @version 1.0.0
 */
public class AccessioRACFConnector extends AbstractConnector {
    
    private static final Log log = LogFactory.getLog(AccessioRACFConnector.class);
    
    // Connector configuration keys
    public static final String CONFIG_SERVER_URL = "serverUrl";
    public static final String CONFIG_USERNAME = "username";
    public static final String CONFIG_PASSWORD = "password";
    public static final String CONFIG_TIMEOUT = "timeout";
    public static final String CONFIG_RETRY_ATTEMPTS = "retryAttempts";
    public static final String CONFIG_DEFAULT_ORG_UNIT = "defaultOrgUnit";
    public static final String CONFIG_API_VERSION = "apiVersion";
    
    // Default values
    public static final String DEFAULT_ORG_UNIT = "$F000UDF";
    public static final int DEFAULT_TIMEOUT = 30000;
    public static final int DEFAULT_RETRY_ATTEMPTS = 3;
    public static final String DEFAULT_API_VERSION = "1.0";
    
    // User types
    public static final String USER_TYPE_AARID = "A";
    public static final String USER_TYPE_STC = "B";
    public static final String USER_TYPE_TECHNICAL = "T";
    
    // API clients and managers
    private GarancyAPIClient apiClient;
    private RACFUserManager userManager;
    private RACFRoleManager roleManager;
    private ApprovalWorkflowHandler workflowHandler;
    private RecertificationManager recertificationManager;
    
    /**
     * Default constructor
     */
    public AccessioRACFConnector() {
        super();
    }
    
    /**
     * Constructor with application parameter
     */
    public AccessioRACFConnector(Application application) {
        super(application);
    }
    
    /**
     * Test the connection to the RACF system via Garancy API
     */
    @Override
    public void testConfiguration() throws ConnectorException {
        log.info("Testing Accessio RACF connector configuration");
        
        try {
            initializeComponents();
            
            // Test API connectivity
            boolean connected = apiClient.testConnection();
            if (!connected) {
                throw new ConnectorException("Failed to connect to Garancy API");
            }
            
            // Test basic operations
            List<Map<String, Object>> orgUnits = apiClient.listOrgUnits();
            log.info("Successfully retrieved " + orgUnits.size() + " organizational units");
            
            log.info("Accessio RACF connector configuration test completed successfully");
            
        } catch (Exception e) {
            log.error("Configuration test failed", e);
            throw new ConnectorException("Configuration test failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Discover the schema for accounts and entitlements
     */
    @Override
    public Schema discoverSchema() throws ConnectorException {
        log.info("Discovering Accessio RACF schema");
        
        try {
            initializeComponents();
            
            Schema schema = new Schema();
            
            // Account schema for RACF users
            ObjectConfig accountConfig = createAccountSchema();
            schema.setObjectConfig(accountConfig);
            
            // Entitlement schema for RACF roles
            ObjectConfig entitlementConfig = createEntitlementSchema();
            schema.addObjectConfig(entitlementConfig);
            
            log.info("Schema discovery completed successfully");
            return schema;
            
        } catch (Exception e) {
            log.error("Schema discovery failed", e);
            throw new ConnectorException("Schema discovery failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Iterate over all accounts in the RACF system
     */
    @Override
    public Iterator<ResourceObject> iterateObjects(String objectType, Filter filter, Map<String, Object> options) 
            throws ConnectorException {
        
        log.info("Starting iteration for object type: " + objectType);
        
        try {
            initializeComponents();
            
            if (ObjectConfig.TYPE_ACCOUNT.equals(objectType)) {
                return iterateAccounts(filter, options);
            } else if (ObjectConfig.TYPE_GROUP.equals(objectType)) {
                return iterateRoles(filter, options);
            } else {
                throw new ConnectorException("Unsupported object type: " + objectType);
            }
            
        } catch (Exception e) {
            log.error("Object iteration failed for type: " + objectType, e);
            throw new ConnectorException("Object iteration failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Create a new RACF user account
     */
    @Override
    public Result create(String objectType, Attributes attributes, Map<String, Object> options) 
            throws ConnectorException {
        
        log.info("Creating " + objectType + " with attributes: " + attributes.getKeys());
        
        try {
            initializeComponents();
            
            if (ObjectConfig.TYPE_ACCOUNT.equals(objectType)) {
                return userManager.createUser(attributes, options);
            } else {
                throw new ConnectorException("Create operation not supported for object type: " + objectType);
            }
            
        } catch (Exception e) {
            log.error("Create operation failed", e);
            throw new ConnectorException("Create operation failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Update an existing RACF user account
     */
    @Override
    public Result update(String objectType, String identity, Attributes attributes, Map<String, Object> options) 
            throws ConnectorException {
        
        log.info("Updating " + objectType + " " + identity + " with attributes: " + attributes.getKeys());
        
        try {
            initializeComponents();
            
            if (ObjectConfig.TYPE_ACCOUNT.equals(objectType)) {
                return userManager.updateUser(identity, attributes, options);
            } else {
                throw new ConnectorException("Update operation not supported for object type: " + objectType);
            }
            
        } catch (Exception e) {
            log.error("Update operation failed", e);
            throw new ConnectorException("Update operation failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Delete a RACF user account
     */
    @Override
    public Result delete(String objectType, String identity, Map<String, Object> options) 
            throws ConnectorException {
        
        log.info("Deleting " + objectType + " " + identity);
        
        try {
            initializeComponents();
            
            if (ObjectConfig.TYPE_ACCOUNT.equals(objectType)) {
                return userManager.deleteUser(identity, options);
            } else {
                throw new ConnectorException("Delete operation not supported for object type: " + objectType);
            }
            
        } catch (Exception e) {
            log.error("Delete operation failed", e);
            throw new ConnectorException("Delete operation failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Enable or disable a RACF user account
     */
    @Override
    public Result enable(String objectType, String identity, Map<String, Object> options) 
            throws ConnectorException {
        
        log.info("Enabling " + objectType + " " + identity);
        
        try {
            initializeComponents();
            
            if (ObjectConfig.TYPE_ACCOUNT.equals(objectType)) {
                return userManager.enableUser(identity, options);
            } else {
                throw new ConnectorException("Enable operation not supported for object type: " + objectType);
            }
            
        } catch (Exception e) {
            log.error("Enable operation failed", e);
            throw new ConnectorException("Enable operation failed: " + e.getMessage(), e);
        }
    }
    
    @Override
    public Result disable(String objectType, String identity, Map<String, Object> options) 
            throws ConnectorException {
        
        log.info("Disabling " + objectType + " " + identity);
        
        try {
            initializeComponents();
            
            if (ObjectConfig.TYPE_ACCOUNT.equals(objectType)) {
                return userManager.disableUser(identity, options);
            } else {
                throw new ConnectorException("Disable operation not supported for object type: " + objectType);
            }
            
        } catch (Exception e) {
            log.error("Disable operation failed", e);
            throw new ConnectorException("Disable operation failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Authenticate a user (not typically used for RACF)
     */
    @Override
    public Result authenticate(String username, String password, Map<String, Object> options) 
            throws ConnectorException {
        
        log.debug("Authentication not supported for RACF connector");
        throw new ConnectorException("Authentication operation not supported");
    }
    
    /**
     * Initialize connector components
     */
    private void initializeComponents() throws ConnectorException {
        if (apiClient == null) {
            log.debug("Initializing Accessio RACF connector components");
            
            // Get configuration
            String serverUrl = getConfiguration().getString(CONFIG_SERVER_URL);
            String username = getConfiguration().getString(CONFIG_USERNAME);
            String password = getConfiguration().getString(CONFIG_PASSWORD);
            int timeout = getConfiguration().getInt(CONFIG_TIMEOUT, DEFAULT_TIMEOUT);
            int retryAttempts = getConfiguration().getInt(CONFIG_RETRY_ATTEMPTS, DEFAULT_RETRY_ATTEMPTS);
            
            if (Util.isNullOrEmpty(serverUrl)) {
                throw new ConnectorException("Server URL is required");
            }
            if (Util.isNullOrEmpty(username)) {
                throw new ConnectorException("Username is required");
            }
            if (Util.isNullOrEmpty(password)) {
                throw new ConnectorException("Password is required");
            }
            
            // Initialize API client
            apiClient = new GarancyAPIClient(serverUrl, username, password, timeout, retryAttempts);
            
            // Initialize managers
            userManager = new RACFUserManager(apiClient, getConfiguration());
            roleManager = new RACFRoleManager(apiClient, getConfiguration());
            workflowHandler = new ApprovalWorkflowHandler(apiClient, getConfiguration());
            recertificationManager = new RecertificationManager(apiClient, getConfiguration());
            
            log.debug("Connector components initialized successfully");
        }
    }
    
    /**
     * Create account schema for RACF users
     */
    private ObjectConfig createAccountSchema() throws ConnectorException {
        log.debug("Creating account schema");
        
        ObjectConfig accountConfig = new ObjectConfig(ObjectConfig.TYPE_ACCOUNT);
        accountConfig.setDisplayAttribute("BASEUS_SAM_ID");
        accountConfig.setIdentityAttribute("BASEUS_SAM_ID");
        accountConfig.setGroupAttribute("roles");
        
        // Core user attributes
        accountConfig.addAttributeDefinition(createAttribute("BASEUS_SAM_ID", AttributeDefinition.TYPE_STRING, true, false, true));
        accountConfig.addAttributeDefinition(createAttribute("BASEORG_ID", AttributeDefinition.TYPE_STRING, false, false, false));
        accountConfig.addAttributeDefinition(createAttribute("BASEUS_C_C01_001", AttributeDefinition.TYPE_STRING, false, false, false));
        accountConfig.addAttributeDefinition(createAttribute("BASEUS_C_C01_009", AttributeDefinition.TYPE_STRING, false, false, false));
        accountConfig.addAttributeDefinition(createAttribute("BASEUS_C_C01_010", AttributeDefinition.TYPE_STRING, false, false, false));
        accountConfig.addAttributeDefinition(createAttribute("BASEUS_C_C01_011", AttributeDefinition.TYPE_DATE, false, false, false));
        accountConfig.addAttributeDefinition(createAttribute("BASEUS_C_C01_004", AttributeDefinition.TYPE_DATE, false, false, false));
        
        // Multi-valued attributes
        accountConfig.addAttributeDefinition(createAttribute("roles", AttributeDefinition.TYPE_STRING, false, true, false));
        accountConfig.addAttributeDefinition(createAttribute("groups", AttributeDefinition.TYPE_STRING, false, true, false));
        
        // Custom attributes for approval workflows
        accountConfig.addAttributeDefinition(createAttribute("lineManager", AttributeDefinition.TYPE_STRING, false, false, false));
        accountConfig.addAttributeDefinition(createAttribute("orgUnitOwner", AttributeDefinition.TYPE_STRING, false, false, false));
        accountConfig.addAttributeDefinition(createAttribute("accountOwner", AttributeDefinition.TYPE_STRING, false, false, false));
        
        return accountConfig;
    }
    
    /**
     * Create entitlement schema for RACF roles
     */
    private ObjectConfig createEntitlementSchema() throws ConnectorException {
        log.debug("Creating entitlement schema");
        
        ObjectConfig entitlementConfig = new ObjectConfig(ObjectConfig.TYPE_GROUP);
        entitlementConfig.setDisplayAttribute("TECHDSP_NAME");
        entitlementConfig.setIdentityAttribute("BASEUSRC_ROLE");
        
        // Core role attributes
        entitlementConfig.addAttributeDefinition(createAttribute("BASEUSRC_ROLE", AttributeDefinition.TYPE_STRING, true, false, true));
        entitlementConfig.addAttributeDefinition(createAttribute("TECHDSP_NAME", AttributeDefinition.TYPE_STRING, false, false, false));
        entitlementConfig.addAttributeDefinition(createAttribute("BASEUS_C_C_08_004", AttributeDefinition.TYPE_STRING, false, false, false));
        entitlementConfig.addAttributeDefinition(createAttribute("BASEUS_C_C_78_001", AttributeDefinition.TYPE_STRING, false, false, false));
        entitlementConfig.addAttributeDefinition(createAttribute("BASEUS_C_C_78_002", AttributeDefinition.TYPE_STRING, false, false, false));
        entitlementConfig.addAttributeDefinition(createAttribute("BASEUS_C_C_78_003", AttributeDefinition.TYPE_STRING, false, false, false));
        entitlementConfig.addAttributeDefinition(createAttribute("BASEUS_C_C_78_004", AttributeDefinition.TYPE_STRING, false, false, false));
        entitlementConfig.addAttributeDefinition(createAttribute("BASEUS_C_C_78_005", AttributeDefinition.TYPE_STRING, false, false, false));
        
        return entitlementConfig;
    }
    
    /**
     * Create attribute definition helper
     */
    private AttributeDefinition createAttribute(String name, String type, boolean required, boolean multiValued, boolean identity) {
        AttributeDefinition attr = new AttributeDefinition();
        attr.setName(name);
        attr.setType(type);
        attr.setRequired(required);
        attr.setMultiValued(multiValued);
        if (identity) {
            attr.setIdentity(true);
        }
        return attr;
    }
    
    /**
     * Iterate over RACF user accounts
     */
    private Iterator<ResourceObject> iterateAccounts(Filter filter, Map<String, Object> options) 
            throws ConnectorException {
        
        log.debug("Iterating RACF user accounts");
        
        try {
            List<Map<String, Object>> users = apiClient.listUsers();
            List<ResourceObject> accounts = new ArrayList<>();
            
            for (Map<String, Object> user : users) {
                ResourceObject account = convertUserToResourceObject(user);
                if (account != null && (filter == null || filter.matches(account))) {
                    accounts.add(account);
                }
            }
            
            log.info("Retrieved " + accounts.size() + " user accounts");
            return accounts.iterator();
            
        } catch (Exception e) {
            log.error("Failed to iterate accounts", e);
            throw new ConnectorException("Failed to iterate accounts: " + e.getMessage(), e);
        }
    }
    
    /**
     * Iterate over RACF roles
     */
    private Iterator<ResourceObject> iterateRoles(Filter filter, Map<String, Object> options) 
            throws ConnectorException {
        
        log.debug("Iterating RACF roles");
        
        try {
            List<Map<String, Object>> roles = apiClient.listRoles();
            List<ResourceObject> entitlements = new ArrayList<>();
            
            for (Map<String, Object> role : roles) {
                ResourceObject entitlement = convertRoleToResourceObject(role);
                if (entitlement != null && (filter == null || filter.matches(entitlement))) {
                    entitlements.add(entitlement);
                }
            }
            
            log.info("Retrieved " + entitlements.size() + " roles");
            return entitlements.iterator();
            
        } catch (Exception e) {
            log.error("Failed to iterate roles", e);
            throw new ConnectorException("Failed to iterate roles: " + e.getMessage(), e);
        }
    }
    
    /**
     * Convert user data to ResourceObject
     */
    private ResourceObject convertUserToResourceObject(Map<String, Object> user) {
        if (user == null) return null;
        
        ResourceObject account = new ResourceObject();
        account.setObjectType(ObjectConfig.TYPE_ACCOUNT);
        
        // Set identity
        String userId = (String) user.get("BASEUS_SAM_ID");
        account.setIdentity(userId);
        account.setDisplayName(userId);
        
        // Set attributes
        Attributes attributes = new Attributes();
        for (Map.Entry<String, Object> entry : user.entrySet()) {
            if (entry.getValue() != null) {
                attributes.put(entry.getKey(), entry.getValue());
            }
        }
        
        account.setAttributes(attributes);
        return account;
    }
    
    /**
     * Convert role data to ResourceObject
     */
    private ResourceObject convertRoleToResourceObject(Map<String, Object> role) {
        if (role == null) return null;
        
        ResourceObject entitlement = new ResourceObject();
        entitlement.setObjectType(ObjectConfig.TYPE_GROUP);
        
        // Set identity
        String roleId = (String) role.get("BASEUSRC_ROLE");
        entitlement.setIdentity(roleId);
        
        String roleName = (String) role.get("TECHDSP_NAME");
        entitlement.setDisplayName(Util.isNotNullOrEmpty(roleName) ? roleName : roleId);
        
        // Set attributes
        Attributes attributes = new Attributes();
        for (Map.Entry<String, Object> entry : role.entrySet()) {
            if (entry.getValue() != null) {
                attributes.put(entry.getKey(), entry.getValue());
            }
        }
        
        entitlement.setAttributes(attributes);
        return entitlement;
    }
    
    /**
     * Get connector version
     */
    public String getConnectorVersion() {
        return "1.0.0";
    }
    
    /**
     * Clean up resources
     */
    @Override
    public void close() {
        log.debug("Closing Accessio RACF connector");
        
        if (apiClient != null) {
            try {
                apiClient.close();
            } catch (Exception e) {
                log.warn("Error closing API client", e);
            }
        }
        
        super.close();
    }
}
