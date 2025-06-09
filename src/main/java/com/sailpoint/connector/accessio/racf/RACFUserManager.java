package com.sailpoint.connector.accessio.racf;

import sailpoint.object.Attributes;
import sailpoint.object.Configuration;
import sailpoint.object.Result;
import sailpoint.tools.Util;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.*;

/**
 * RACF User Manager for Accessio Integration
 * 
 * This class handles all user lifecycle operations including:
 * - User creation with approval workflows
 * - User modification and org unit transfers
 * - User suspension and deletion
 * - Role assignment and removal
 * 
 * Supports multiple user types:
 * - AARID: Business users requiring line manager approval
 * - STC: System accounts with account owner approval
 * - Technical: Technical service accounts
 * 
 * @author SailPoint Professional Services
 * @version 1.0.0
 */
public class RACFUserManager {
    
    private static final Log log = LogFactory.getLog(RACFUserManager.class);
    
    // User status constants
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_SUSPENDED = "SUSPENDED";
    public static final String STATUS_DELETED = "DELETED";
    public static final String STATUS_PENDING = "PENDING";
    
    // Operation result constants
    public static final String RESULT_SUCCESS = "SUCCESS";
    public static final String RESULT_PENDING_APPROVAL = "PENDING_APPROVAL";
    public static final String RESULT_FAILED = "FAILED";
    
    private final GarancyAPIClient apiClient;
    private final Configuration configuration;
    private final ApprovalWorkflowHandler workflowHandler;
    
    /**
     * Constructor
     */
    public RACFUserManager(GarancyAPIClient apiClient, Configuration configuration) {
        this.apiClient = apiClient;
        this.configuration = configuration;
        this.workflowHandler = new ApprovalWorkflowHandler(apiClient, configuration);
    }
    
    /**
     * Create a new RACF user account
     */
    public Result createUser(Attributes attributes, Map<String, Object> options) {
        log.info("Creating RACF user account");
        
        try {
            // Validate required attributes
            String userId = attributes.getString("BASEUS_SAM_ID");
            if (Util.isNullOrEmpty(userId)) {
                return Result.failed("User ID (BASEUS_SAM_ID) is required");
            }
            
            // Determine user type
            String userType = attributes.getString("BASEUS_C_C01_001");
            if (Util.isNullOrEmpty(userType)) {
                userType = AccessioRACFConnector.USER_TYPE_AARID; // Default to AARID
                attributes.put("BASEUS_C_C01_001", userType);
            }
            
            // Set default org unit if not specified
            String orgUnitId = attributes.getString("BASEORG_ID");
            if (Util.isNullOrEmpty(orgUnitId)) {
                orgUnitId = configuration.getString(AccessioRACFConnector.CONFIG_DEFAULT_ORG_UNIT, 
                                                  AccessioRACFConnector.DEFAULT_ORG_UNIT);
                attributes.put("BASEORG_ID", orgUnitId);
            }
            
            // Set creation timestamp
            attributes.put("BASEUS_C_C01_011", new Date());
            attributes.put("BASEUS_C_C01_004", new Date());
            attributes.put("BASEUS_C_C01_010", STATUS_PENDING);
            
            // Check if approval is required
            if (requiresApproval(userType, "CREATE", attributes)) {
                log.debug("User creation requires approval");
                
                // Submit for approval workflow
                String workflowId = workflowHandler.submitForApproval(
                    "CREATE_USER", userId, attributes, getApprovers(userType, "CREATE", attributes)
                );
                
                Result result = new Result();
                result.setStatus(RESULT_PENDING_APPROVAL);
                result.setAttribute("workflowId", workflowId);
                result.setAttribute("message", "User creation submitted for approval");
                return result;
                
            } else {
                // Create user directly
                return executeUserCreation(attributes);
            }
            
        } catch (Exception e) {
            log.error("Failed to create user", e);
            return Result.failed("Failed to create user: " + e.getMessage());
        }
    }
    
    /**
     * Update an existing RACF user account
     */
    public Result updateUser(String identity, Attributes attributes, Map<String, Object> options) {
        log.info("Updating RACF user account: " + identity);
        
        try {
            // Get current user data
            Map<String, Object> currentUser = getCurrentUser(identity);
            if (currentUser == null) {
                return Result.failed("User not found: " + identity);
            }
            
            String userType = (String) currentUser.get("BASEUS_C_C01_001");
            String currentOrgUnit = (String) currentUser.get("BASEORG_ID");
            String newOrgUnit = attributes.getString("BASEORG_ID");
            
            // Check if this is an org unit transfer
            boolean isOrgUnitTransfer = !Util.isNullOrEmpty(newOrgUnit) && 
                                      !newOrgUnit.equals(currentOrgUnit);
            
            // Set modification timestamp
            attributes.put("BASEUS_C_C01_004", new Date());
            
            // Check if approval is required
            if (requiresApproval(userType, "MODIFY", attributes) || isOrgUnitTransfer) {
                log.debug("User modification requires approval");
                
                // For org unit transfers, need approval from both current and new org unit owners
                List<String> approvers = getApprovers(userType, "MODIFY", attributes);
                if (isOrgUnitTransfer) {
                    approvers.addAll(getOrgUnitOwners(currentOrgUnit));
                    approvers.addAll(getOrgUnitOwners(newOrgUnit));
                }
                
                String workflowId = workflowHandler.submitForApproval(
                    "MODIFY_USER", identity, attributes, approvers
                );
                
                Result result = new Result();
                result.setStatus(RESULT_PENDING_APPROVAL);
                result.setAttribute("workflowId", workflowId);
                result.setAttribute("message", "User modification submitted for approval");
                return result;
                
            } else {
                // Update user directly
                return executeUserModification(identity, attributes);
            }
            
        } catch (Exception e) {
            log.error("Failed to update user", e);
            return Result.failed("Failed to update user: " + e.getMessage());
        }
    }
    
    /**
     * Delete a RACF user account
     */
    public Result deleteUser(String identity, Map<String, Object> options) {
        log.info("Deleting RACF user account: " + identity);
        
        try {
            // Get current user data
            Map<String, Object> currentUser = getCurrentUser(identity);
            if (currentUser == null) {
                return Result.failed("User not found: " + identity);
            }
            
            String userType = (String) currentUser.get("BASEUS_C_C01_001");
            String orgUnitId = (String) currentUser.get("BASEORG_ID");
            
            // Check if approval is required
            if (requiresApproval(userType, "DELETE", null)) {
                log.debug("User deletion requires approval");
                
                List<String> approvers = getOrgUnitOwners(orgUnitId);
                
                String workflowId = workflowHandler.submitForApproval(
                    "DELETE_USER", identity, new Attributes(), approvers
                );
                
                Result result = new Result();
                result.setStatus(RESULT_PENDING_APPROVAL);
                result.setAttribute("workflowId", workflowId);
                result.setAttribute("message", "User deletion submitted for approval");
                return result;
                
            } else {
                // Delete user directly
                return executeUserDeletion(identity);
            }
            
        } catch (Exception e) {
            log.error("Failed to delete user", e);
            return Result.failed("Failed to delete user: " + e.getMessage());
        }
    }
    
    /**
     * Enable (resume) a RACF user account
     */
    public Result enableUser(String identity, Map<String, Object> options) {
        log.info("Enabling RACF user account: " + identity);
        
        try {
            Map<String, Object> result = apiClient.resumeUser(identity);
            
            if (RESULT_SUCCESS.equals(result.get("status"))) {
                Result enableResult = new Result();
                enableResult.setStatus(RESULT_SUCCESS);
                enableResult.setAttribute("message", "User enabled successfully");
                enableResult.setAttribute("requestId", result.get("requestId"));
                return enableResult;
            } else {
                return Result.failed("Failed to enable user: " + result.get("message"));
            }
            
        } catch (Exception e) {
            log.error("Failed to enable user", e);
            return Result.failed("Failed to enable user: " + e.getMessage());
        }
    }
    
    /**
     * Disable (suspend) a RACF user account
     */
    public Result disableUser(String identity, Map<String, Object> options) {
        log.info("Disabling RACF user account: " + identity);
        
        try {
            Map<String, Object> result = apiClient.suspendUser(identity);
            
            if (RESULT_SUCCESS.equals(result.get("status"))) {
                Result disableResult = new Result();
                disableResult.setStatus(RESULT_SUCCESS);
                disableResult.setAttribute("message", "User disabled successfully");
                disableResult.setAttribute("requestId", result.get("requestId"));
                return disableResult;
            } else {
                return Result.failed("Failed to disable user: " + result.get("message"));
            }
            
        } catch (Exception e) {
            log.error("Failed to disable user", e);
            return Result.failed("Failed to disable user: " + e.getMessage());
        }
    }
    
    /**
     * Add role to user
     */
    public Result addRole(String userId, String roleId, Map<String, Object> options) {
        log.info("Adding role " + roleId + " to user " + userId);
        
        try {
            // Get user and role information
            Map<String, Object> user = getCurrentUser(userId);
            if (user == null) {
                return Result.failed("User not found: " + userId);
            }
            
            String userType = (String) user.get("BASEUS_C_C01_001");
            
            // Check if approval is required for role assignment
            if (requiresApproval(userType, "ADD_ROLE", null)) {
                log.debug("Role assignment requires approval");
                
                Attributes attributes = new Attributes();
                attributes.put("roleId", roleId);
                
                List<String> approvers = getApprovers(userType, "ADD_ROLE", attributes);
                
                String workflowId = workflowHandler.submitForApproval(
                    "ADD_ROLE", userId, attributes, approvers
                );
                
                Result result = new Result();
                result.setStatus(RESULT_PENDING_APPROVAL);
                result.setAttribute("workflowId", workflowId);
                result.setAttribute("message", "Role assignment submitted for approval");
                return result;
                
            } else {
                // Add role directly
                return executeRoleAddition(userId, roleId);
            }
            
        } catch (Exception e) {
            log.error("Failed to add role", e);
            return Result.failed("Failed to add role: " + e.getMessage());
        }
    }
    
    /**
     * Remove role from user
     */
    public Result removeRole(String userId, String roleId, Map<String, Object> options) {
        log.info("Removing role " + roleId + " from user " + userId);
        
        try {
            // Get user information
            Map<String, Object> user = getCurrentUser(userId);
            if (user == null) {
                return Result.failed("User not found: " + userId);
            }
            
            String userType = (String) user.get("BASEUS_C_C01_001");
            
            // Check if approval is required for role removal
            if (requiresApproval(userType, "REMOVE_ROLE", null)) {
                log.debug("Role removal requires approval");
                
                Attributes attributes = new Attributes();
                attributes.put("roleId", roleId);
                
                List<String> approvers = getApprovers(userType, "REMOVE_ROLE", attributes);
                
                String workflowId = workflowHandler.submitForApproval(
                    "REMOVE_ROLE", userId, attributes, approvers
                );
                
                Result result = new Result();
                result.setStatus(RESULT_PENDING_APPROVAL);
                result.setAttribute("workflowId", workflowId);
                result.setAttribute("message", "Role removal submitted for approval");
                return result;
                
            } else {
                // Remove role directly
                return executeRoleRemoval(userId, roleId);
            }
            
        } catch (Exception e) {
            log.error("Failed to remove role", e);
            return Result.failed("Failed to remove role: " + e.getMessage());
        }
    }
    
    /**
     * Execute user creation (after approval if required)
     */
    private Result executeUserCreation(Attributes attributes) throws Exception {
        log.debug("Executing user creation");
        
        Map<String, Object> userAttributes = new HashMap<>();
        for (String key : attributes.getKeys()) {
            userAttributes.put(key, attributes.get(key));
        }
        
        Map<String, Object> result = apiClient.createUser(userAttributes);
        
        if (RESULT_SUCCESS.equals(result.get("status"))) {
            Result createResult = new Result();
            createResult.setStatus(RESULT_SUCCESS);
            createResult.setAttribute("message", "User created successfully");
            createResult.setAttribute("requestId", result.get("requestId"));
            return createResult;
        } else {
            return Result.failed("Failed to create user: " + result.get("message"));
        }
    }
    
    /**
     * Execute user modification (after approval if required)
     */
    private Result executeUserModification(String identity, Attributes attributes) throws Exception {
        log.debug("Executing user modification");
        
        Map<String, Object> userAttributes = new HashMap<>();
        for (String key : attributes.getKeys()) {
            userAttributes.put(key, attributes.get(key));
        }
        
        Map<String, Object> result = apiClient.modifyUser(identity, userAttributes);
        
        if (RESULT_SUCCESS.equals(result.get("status"))) {
            Result modifyResult = new Result();
            modifyResult.setStatus(RESULT_SUCCESS);
            modifyResult.setAttribute("message", "User modified successfully");
            modifyResult.setAttribute("requestId", result.get("requestId"));
            return modifyResult;
        } else {
            return Result.failed("Failed to modify user: " + result.get("message"));
        }
    }
    
    /**
     * Execute user deletion (after approval if required)
     */
    private Result executeUserDeletion(String identity) throws Exception {
        log.debug("Executing user deletion");
        
        Map<String, Object> result = apiClient.deleteUser(identity);
        
        if (RESULT_SUCCESS.equals(result.get("status"))) {
            Result deleteResult = new Result();
            deleteResult.setStatus(RESULT_SUCCESS);
            deleteResult.setAttribute("message", "User deleted successfully");
            deleteResult.setAttribute("requestId", result.get("requestId"));
            return deleteResult;
        } else {
            return Result.failed("Failed to delete user: " + result.get("message"));
        }
    }
    
    /**
     * Execute role addition (after approval if required)
     */
    private Result executeRoleAddition(String userId, String roleId) throws Exception {
        log.debug("Executing role addition");
        
        Map<String, Object> result = apiClient.addRoleConnection(userId, roleId);
        
        if (RESULT_SUCCESS.equals(result.get("status"))) {
            Result addResult = new Result();
            addResult.setStatus(RESULT_SUCCESS);
            addResult.setAttribute("message", "Role added successfully");
            addResult.setAttribute("requestId", result.get("requestId"));
            return addResult;
        } else {
            return Result.failed("Failed to add role: " + result.get("message"));
        }
    }
    
    /**
     * Execute role removal (after approval if required)
     */
    private Result executeRoleRemoval(String userId, String roleId) throws Exception {
        log.debug("Executing role removal");
        
        Map<String, Object> result = apiClient.removeRoleConnection(userId, roleId);
        
        if (RESULT_SUCCESS.equals(result.get("status"))) {
            Result removeResult = new Result();
            removeResult.setStatus(RESULT_SUCCESS);
            removeResult.setAttribute("message", "Role removed successfully");
            removeResult.setAttribute("requestId", result.get("requestId"));
            return removeResult;
        } else {
            return Result.failed("Failed to remove role: " + result.get("message"));
        }
    }
    
    /**
     * Get current user data from API
     */
    private Map<String, Object> getCurrentUser(String userId) throws Exception {
        List<Map<String, Object>> users = apiClient.listUsers();
        
        for (Map<String, Object> user : users) {
            if (userId.equals(user.get("BASEUS_SAM_ID"))) {
                return user;
            }
        }
        
        return null;
    }
    
    /**
     * Check if operation requires approval based on user type and operation
     */
    private boolean requiresApproval(String userType, String operation, Attributes attributes) {
        // AARID users always require approval for all operations
        if (AccessioRACFConnector.USER_TYPE_AARID.equals(userType)) {
            return true;
        }
        
        // STC and Technical users require approval for create/delete/role operations
        if (AccessioRACFConnector.USER_TYPE_STC.equals(userType) || 
            AccessioRACFConnector.USER_TYPE_TECHNICAL.equals(userType)) {
            return "CREATE".equals(operation) || "DELETE".equals(operation) || 
                   "ADD_ROLE".equals(operation) || "REMOVE_ROLE".equals(operation);
        }
        
        return false;
    }
    
    /**
     * Get list of approvers based on user type and operation
     */
    private List<String> getApprovers(String userType, String operation, Attributes attributes) {
        List<String> approvers = new ArrayList<>();
        
        if (AccessioRACFConnector.USER_TYPE_AARID.equals(userType)) {
            // AARID users require line manager + org unit owner approval
            String lineManager = attributes != null ? attributes.getString("lineManager") : null;
            if (!Util.isNullOrEmpty(lineManager)) {
                approvers.add(lineManager);
            }
            
            String orgUnitId = attributes != null ? attributes.getString("BASEORG_ID") : null;
            if (!Util.isNullOrEmpty(orgUnitId)) {
                approvers.addAll(getOrgUnitOwners(orgUnitId));
            }
            
        } else if (AccessioRACFConnector.USER_TYPE_STC.equals(userType) || 
                   AccessioRACFConnector.USER_TYPE_TECHNICAL.equals(userType)) {
            // STC/Technical users require account owner approval
            String accountOwner = attributes != null ? attributes.getString("accountOwner") : null;
            if (!Util.isNullOrEmpty(accountOwner)) {
                approvers.add(accountOwner);
            }
        }
        
        return approvers;
    }
    
    /**
     * Get org unit owners for approval
     */
    private List<String> getOrgUnitOwners(String orgUnitId) {
        List<String> owners = new ArrayList<>();
        
        try {
            List<Map<String, Object>> orgUnits = apiClient.listOrgUnits();
            
            for (Map<String, Object> orgUnit : orgUnits) {
                if (orgUnitId.equals(orgUnit.get("BASEORG_ID"))) {
                    String primaryOwner = (String) orgUnit.get("BASEORG_C_C32_05");
                    String secondaryOwner = (String) orgUnit.get("BASEORG_C_C78_01");
                    
                    if (!Util.isNullOrEmpty(primaryOwner)) {
                        owners.add(primaryOwner);
                    }
                    if (!Util.isNullOrEmpty(secondaryOwner)) {
                        owners.add(secondaryOwner);
                    }
                    break;
                }
            }
            
        } catch (Exception e) {
            log.warn("Failed to get org unit owners for " + orgUnitId, e);
        }
        
        return owners;
    }
}
