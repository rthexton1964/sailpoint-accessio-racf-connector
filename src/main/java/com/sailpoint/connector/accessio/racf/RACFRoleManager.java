package com.sailpoint.connector.accessio.racf;

import sailpoint.object.Configuration;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.*;

/**
 * RACF Role Manager for Accessio Integration
 * 
 * This class handles role-related operations including:
 * - Role discovery and listing
 * - Role metadata management
 * - Role-to-user relationship management
 * - Guardian and monitor role handling
 * 
 * @author SailPoint Professional Services
 * @version 1.0.0
 */
public class RACFRoleManager {
    
    private static final Log log = LogFactory.getLog(RACFRoleManager.class);
    
    // Special role types
    public static final String ROLE_TYPE_GUARDIAN = "ISO_GUARDIAN";
    public static final String ROLE_TYPE_MONITOR = "MONITOR";
    public static final String ROLE_TYPE_STANDARD = "STANDARD";
    
    private final GarancyAPIClient apiClient;
    private final Configuration configuration;
    
    /**
     * Constructor
     */
    public RACFRoleManager(GarancyAPIClient apiClient, Configuration configuration) {
        this.apiClient = apiClient;
        this.configuration = configuration;
    }
    
    /**
     * Get all available roles
     */
    public List<Map<String, Object>> getAllRoles() throws Exception {
        log.debug("Retrieving all RACF roles");
        
        List<Map<String, Object>> roles = apiClient.listRoles();
        
        // Enrich roles with additional metadata
        for (Map<String, Object> role : roles) {
            enrichRoleData(role);
        }
        
        log.info("Retrieved " + roles.size() + " RACF roles");
        return roles;
    }
    
    /**
     * Get role by ID
     */
    public Map<String, Object> getRoleById(String roleId) throws Exception {
        log.debug("Retrieving role: " + roleId);
        
        List<Map<String, Object>> roles = getAllRoles();
        
        for (Map<String, Object> role : roles) {
            if (roleId.equals(role.get("BASEUSRC_ROLE"))) {
                return role;
            }
        }
        
        return null;
    }
    
    /**
     * Get roles by type (Guardian, Monitor, Standard)
     */
    public List<Map<String, Object>> getRolesByType(String roleType) throws Exception {
        log.debug("Retrieving roles of type: " + roleType);
        
        List<Map<String, Object>> allRoles = getAllRoles();
        List<Map<String, Object>> filteredRoles = new ArrayList<>();
        
        for (Map<String, Object> role : allRoles) {
            String detectedType = (String) role.get("roleType");
            if (roleType.equals(detectedType)) {
                filteredRoles.add(role);
            }
        }
        
        log.info("Found " + filteredRoles.size() + " roles of type " + roleType);
        return filteredRoles;
    }
    
    /**
     * Get users assigned to a specific role
     */
    public List<String> getUsersForRole(String roleId) throws Exception {
        log.debug("Getting users for role: " + roleId);
        
        List<Map<String, Object>> connections = apiClient.listRoleUserConnections();
        List<String> users = new ArrayList<>();
        
        for (Map<String, Object> connection : connections) {
            if (roleId.equals(connection.get("BASEUSRC_ROLE"))) {
                String userId = (String) connection.get("BASEUS_SAM_ID");
                if (userId != null) {
                    users.add(userId);
                }
            }
        }
        
        log.debug("Found " + users.size() + " users for role " + roleId);
        return users;
    }
    
    /**
     * Get roles assigned to a specific user
     */
    public List<String> getRolesForUser(String userId) throws Exception {
        log.debug("Getting roles for user: " + userId);
        
        List<Map<String, Object>> connections = apiClient.listRoleUserConnections();
        List<String> roles = new ArrayList<>();
        
        for (Map<String, Object> connection : connections) {
            if (userId.equals(connection.get("BASEUS_SAM_ID"))) {
                String roleId = (String) connection.get("BASEUSRC_ROLE");
                if (roleId != null) {
                    roles.add(roleId);
                }
            }
        }
        
        log.debug("Found " + roles.size() + " roles for user " + userId);
        return roles;
    }
    
    /**
     * Get role statistics
     */
    public Map<String, Object> getRoleStatistics() throws Exception {
        log.debug("Calculating role statistics");
        
        List<Map<String, Object>> roles = getAllRoles();
        List<Map<String, Object>> connections = apiClient.listRoleUserConnections();
        
        Map<String, Object> stats = new HashMap<>();
        
        // Count roles by type
        int guardianRoles = 0;
        int monitorRoles = 0;
        int standardRoles = 0;
        
        for (Map<String, Object> role : roles) {
            String roleType = (String) role.get("roleType");
            if (ROLE_TYPE_GUARDIAN.equals(roleType)) {
                guardianRoles++;
            } else if (ROLE_TYPE_MONITOR.equals(roleType)) {
                monitorRoles++;
            } else {
                standardRoles++;
            }
        }
        
        stats.put("totalRoles", roles.size());
        stats.put("guardianRoles", guardianRoles);
        stats.put("monitorRoles", monitorRoles);
        stats.put("standardRoles", standardRoles);
        stats.put("totalConnections", connections.size());
        
        // Calculate average roles per user
        Set<String> uniqueUsers = new HashSet<>();
        for (Map<String, Object> connection : connections) {
            String userId = (String) connection.get("BASEUS_SAM_ID");
            if (userId != null) {
                uniqueUsers.add(userId);
            }
        }
        
        stats.put("uniqueUsersWithRoles", uniqueUsers.size());
        if (uniqueUsers.size() > 0) {
            double avgRolesPerUser = (double) connections.size() / uniqueUsers.size();
            stats.put("averageRolesPerUser", Math.round(avgRolesPerUser * 100.0) / 100.0);
        } else {
            stats.put("averageRolesPerUser", 0.0);
        }
        
        log.info("Role statistics calculated: " + stats);
        return stats;
    }
    
    /**
     * Get roles requiring recertification
     */
    public List<Map<String, Object>> getRolesRequiringRecertification() throws Exception {
        log.debug("Getting roles requiring recertification");
        
        List<Map<String, Object>> allRoles = getAllRoles();
        List<Map<String, Object>> recertificationRoles = new ArrayList<>();
        
        for (Map<String, Object> role : allRoles) {
            // Guardian and Monitor roles always require recertification
            String roleType = (String) role.get("roleType");
            if (ROLE_TYPE_GUARDIAN.equals(roleType) || ROLE_TYPE_MONITOR.equals(roleType)) {
                recertificationRoles.add(role);
            }
            
            // Check if role has high-privilege indicators
            String roleId = (String) role.get("BASEUSRC_ROLE");
            if (isHighPrivilegeRole(roleId)) {
                recertificationRoles.add(role);
            }
        }
        
        log.info("Found " + recertificationRoles.size() + " roles requiring recertification");
        return recertificationRoles;
    }
    
    /**
     * Get role ownership information
     */
    public Map<String, Object> getRoleOwnership(String roleId) throws Exception {
        log.debug("Getting ownership information for role: " + roleId);
        
        Map<String, Object> role = getRoleById(roleId);
        if (role == null) {
            return null;
        }
        
        Map<String, Object> ownership = new HashMap<>();
        ownership.put("roleId", roleId);
        ownership.put("roleName", role.get("TECHDSP_NAME"));
        ownership.put("ownerEmail", role.get("BASEUS_C_C_78_001"));
        ownership.put("deputyEmail", role.get("BASEUS_C_C_78_002"));
        ownership.put("ownerUserId", role.get("BASEUS_C_C_78_004"));
        ownership.put("deputyUserId", role.get("BASEUS_C_C_78_005"));
        ownership.put("description", role.get("BASEUS_C_C_78_003"));
        
        return ownership;
    }
    
    /**
     * Validate role assignment eligibility
     */
    public boolean isRoleAssignmentValid(String userId, String roleId) throws Exception {
        log.debug("Validating role assignment: " + roleId + " to user " + userId);
        
        // Get role information
        Map<String, Object> role = getRoleById(roleId);
        if (role == null) {
            log.warn("Role not found: " + roleId);
            return false;
        }
        
        // Check if user already has the role
        List<String> userRoles = getRolesForUser(userId);
        if (userRoles.contains(roleId)) {
            log.debug("User already has role: " + roleId);
            return false;
        }
        
        // Check for conflicting roles (SoD violations)
        if (hasConflictingRoles(userId, roleId)) {
            log.warn("Role assignment would create SoD conflict: " + roleId + " for user " + userId);
            return false;
        }
        
        // Special validation for Guardian and Monitor roles
        String roleType = (String) role.get("roleType");
        if (ROLE_TYPE_GUARDIAN.equals(roleType) || ROLE_TYPE_MONITOR.equals(roleType)) {
            return validateSpecialRoleAssignment(userId, roleId, roleType);
        }
        
        return true;
    }
    
    /**
     * Enrich role data with additional metadata
     */
    private void enrichRoleData(Map<String, Object> role) {
        String roleId = (String) role.get("BASEUSRC_ROLE");
        String roleName = (String) role.get("TECHDSP_NAME");
        
        // Determine role type based on name patterns
        String roleType = ROLE_TYPE_STANDARD;
        if (roleName != null) {
            if (roleName.contains("GUARDIAN") || roleName.contains("ISO_GUARDIAN")) {
                roleType = ROLE_TYPE_GUARDIAN;
            } else if (roleName.contains("MONITOR") || roleName.contains("MON_")) {
                roleType = ROLE_TYPE_MONITOR;
            }
        }
        
        role.put("roleType", roleType);
        role.put("isHighPrivilege", isHighPrivilegeRole(roleId));
        role.put("requiresRecertification", 
                ROLE_TYPE_GUARDIAN.equals(roleType) || 
                ROLE_TYPE_MONITOR.equals(roleType) || 
                isHighPrivilegeRole(roleId));
    }
    
    /**
     * Check if role is high privilege
     */
    private boolean isHighPrivilegeRole(String roleId) {
        if (roleId == null) return false;
        
        // Define patterns for high-privilege roles
        String[] highPrivilegePatterns = {
            "ADMIN", "SUPER", "ROOT", "SYSPROG", "SECURITY", 
            "AUDIT", "BACKUP", "RESTORE", "SPECIAL"
        };
        
        String upperRoleId = roleId.toUpperCase();
        for (String pattern : highPrivilegePatterns) {
            if (upperRoleId.contains(pattern)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Check for conflicting roles (Segregation of Duties)
     */
    private boolean hasConflictingRoles(String userId, String newRoleId) throws Exception {
        List<String> userRoles = getRolesForUser(userId);
        
        // Define SoD conflict rules
        Map<String, List<String>> conflictRules = getSoDConflictRules();
        
        for (String existingRole : userRoles) {
            if (isConflictingRole(existingRole, newRoleId, conflictRules)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Get Segregation of Duties conflict rules
     */
    private Map<String, List<String>> getSoDConflictRules() {
        Map<String, List<String>> conflictRules = new HashMap<>();
        
        // Example SoD rules - customize based on organization requirements
        conflictRules.put("FINANCIAL_APPROVER", Arrays.asList("FINANCIAL_PROCESSOR", "PAYMENT_PROCESSOR"));
        conflictRules.put("SECURITY_ADMIN", Arrays.asList("AUDIT_REVIEWER", "COMPLIANCE_CHECKER"));
        conflictRules.put("BACKUP_OPERATOR", Arrays.asList("RESTORE_OPERATOR"));
        
        return conflictRules;
    }
    
    /**
     * Check if two roles conflict
     */
    private boolean isConflictingRole(String role1, String role2, Map<String, List<String>> conflictRules) {
        List<String> conflicts1 = conflictRules.get(role1);
        if (conflicts1 != null && conflicts1.contains(role2)) {
            return true;
        }
        
        List<String> conflicts2 = conflictRules.get(role2);
        if (conflicts2 != null && conflicts2.contains(role1)) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Validate special role assignment (Guardian/Monitor)
     */
    private boolean validateSpecialRoleAssignment(String userId, String roleId, String roleType) {
        // Additional validation logic for special roles
        // This could include checking user qualifications, training records, etc.
        
        log.debug("Validating special role assignment: " + roleType + " for user " + userId);
        
        // For now, allow all special role assignments
        // In production, implement specific business rules
        return true;
    }
}
