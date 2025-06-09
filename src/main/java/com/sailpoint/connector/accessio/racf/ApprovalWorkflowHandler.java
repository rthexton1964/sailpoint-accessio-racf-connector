package com.sailpoint.connector.accessio.racf;

import sailpoint.object.Attributes;
import sailpoint.object.Configuration;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Approval Workflow Handler for Accessio RACF Integration
 * 
 * This class manages approval workflows for RACF operations including:
 * - User creation, modification, and deletion approvals
 * - Role assignment and removal approvals
 * - Org unit transfer approvals
 * - Multi-level approval chains
 * 
 * Approval Types:
 * - Line Manager approval for AARID users
 * - Org Unit Owner approval for org transfers
 * - Account Owner approval for STC/Technical users
 * 
 * @author SailPoint Professional Services
 * @version 1.0.0
 */
public class ApprovalWorkflowHandler {
    
    private static final Log log = LogFactory.getLog(ApprovalWorkflowHandler.class);
    
    // Workflow status constants
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_CANCELLED = "CANCELLED";
    public static final String STATUS_EXPIRED = "EXPIRED";
    
    // Operation types
    public static final String OP_CREATE_USER = "CREATE_USER";
    public static final String OP_MODIFY_USER = "MODIFY_USER";
    public static final String OP_DELETE_USER = "DELETE_USER";
    public static final String OP_ADD_ROLE = "ADD_ROLE";
    public static final String OP_REMOVE_ROLE = "REMOVE_ROLE";
    public static final String OP_TRANSFER_ORG = "TRANSFER_ORG";
    
    // Default timeout for approvals (in hours)
    private static final int DEFAULT_APPROVAL_TIMEOUT = 72;
    
    private final GarancyAPIClient apiClient;
    private final Configuration configuration;
    
    // In-memory workflow tracking (in production, use database)
    private final Map<String, WorkflowInstance> activeWorkflows = new ConcurrentHashMap<>();
    
    /**
     * Constructor
     */
    public ApprovalWorkflowHandler(GarancyAPIClient apiClient, Configuration configuration) {
        this.apiClient = apiClient;
        this.configuration = configuration;
    }
    
    /**
     * Submit request for approval
     */
    public String submitForApproval(String operationType, String targetId, Attributes attributes, List<String> approvers) {
        log.info("Submitting " + operationType + " for approval: " + targetId);
        
        String workflowId = generateWorkflowId();
        
        WorkflowInstance workflow = new WorkflowInstance();
        workflow.setWorkflowId(workflowId);
        workflow.setOperationType(operationType);
        workflow.setTargetId(targetId);
        workflow.setAttributes(attributes);
        workflow.setApprovers(approvers);
        workflow.setStatus(STATUS_PENDING);
        workflow.setCreatedDate(new Date());
        workflow.setExpiryDate(calculateExpiryDate());
        
        activeWorkflows.put(workflowId, workflow);
        
        // Send approval notifications
        sendApprovalNotifications(workflow);
        
        log.info("Workflow submitted with ID: " + workflowId);
        return workflowId;
    }
    
    /**
     * Process approval response
     */
    public boolean processApproval(String workflowId, String approverId, boolean approved, String comments) {
        log.info("Processing approval for workflow " + workflowId + " by " + approverId + ": " + approved);
        
        WorkflowInstance workflow = activeWorkflows.get(workflowId);
        if (workflow == null) {
            log.warn("Workflow not found: " + workflowId);
            return false;
        }
        
        if (!STATUS_PENDING.equals(workflow.getStatus())) {
            log.warn("Workflow is not in pending status: " + workflowId);
            return false;
        }
        
        if (workflow.isExpired()) {
            log.warn("Workflow has expired: " + workflowId);
            workflow.setStatus(STATUS_EXPIRED);
            return false;
        }
        
        // Record approval decision
        ApprovalDecision decision = new ApprovalDecision();
        decision.setApproverId(approverId);
        decision.setApproved(approved);
        decision.setComments(comments);
        decision.setDecisionDate(new Date());
        
        workflow.addApprovalDecision(decision);
        
        if (!approved) {
            // Any rejection cancels the workflow
            workflow.setStatus(STATUS_REJECTED);
            log.info("Workflow rejected by " + approverId + ": " + workflowId);
            
            // Send rejection notifications
            sendRejectionNotifications(workflow, approverId, comments);
            
            return true;
        }
        
        // Check if all required approvals are received
        if (workflow.hasAllApprovals()) {
            workflow.setStatus(STATUS_APPROVED);
            log.info("Workflow fully approved: " + workflowId);
            
            // Execute the approved operation
            executeApprovedOperation(workflow);
            
            // Send approval completion notifications
            sendApprovalCompletionNotifications(workflow);
            
            return true;
        }
        
        log.info("Partial approval received for workflow: " + workflowId);
        return true;
    }
    
    /**
     * Get workflow status
     */
    public WorkflowInstance getWorkflowStatus(String workflowId) {
        return activeWorkflows.get(workflowId);
    }
    
    /**
     * Get pending workflows for approver
     */
    public List<WorkflowInstance> getPendingWorkflows(String approverId) {
        List<WorkflowInstance> pendingWorkflows = new ArrayList<>();
        
        for (WorkflowInstance workflow : activeWorkflows.values()) {
            if (STATUS_PENDING.equals(workflow.getStatus()) && 
                workflow.getApprovers().contains(approverId) &&
                !workflow.hasApprovalFrom(approverId)) {
                pendingWorkflows.add(workflow);
            }
        }
        
        return pendingWorkflows;
    }
    
    /**
     * Cancel workflow
     */
    public boolean cancelWorkflow(String workflowId, String reason) {
        log.info("Cancelling workflow " + workflowId + ": " + reason);
        
        WorkflowInstance workflow = activeWorkflows.get(workflowId);
        if (workflow == null) {
            return false;
        }
        
        workflow.setStatus(STATUS_CANCELLED);
        workflow.setCancellationReason(reason);
        
        // Send cancellation notifications
        sendCancellationNotifications(workflow, reason);
        
        return true;
    }
    
    /**
     * Clean up expired workflows
     */
    public void cleanupExpiredWorkflows() {
        log.debug("Cleaning up expired workflows");
        
        Date now = new Date();
        List<String> expiredWorkflows = new ArrayList<>();
        
        for (Map.Entry<String, WorkflowInstance> entry : activeWorkflows.entrySet()) {
            WorkflowInstance workflow = entry.getValue();
            if (STATUS_PENDING.equals(workflow.getStatus()) && 
                workflow.getExpiryDate().before(now)) {
                
                workflow.setStatus(STATUS_EXPIRED);
                expiredWorkflows.add(entry.getKey());
                
                log.info("Workflow expired: " + entry.getKey());
                sendExpirationNotifications(workflow);
            }
        }
        
        log.info("Cleaned up " + expiredWorkflows.size() + " expired workflows");
    }
    
    /**
     * Execute approved operation
     */
    private void executeApprovedOperation(WorkflowInstance workflow) {
        log.info("Executing approved operation: " + workflow.getOperationType());
        
        try {
            switch (workflow.getOperationType()) {
                case OP_CREATE_USER:
                    executeUserCreation(workflow);
                    break;
                case OP_MODIFY_USER:
                    executeUserModification(workflow);
                    break;
                case OP_DELETE_USER:
                    executeUserDeletion(workflow);
                    break;
                case OP_ADD_ROLE:
                    executeRoleAddition(workflow);
                    break;
                case OP_REMOVE_ROLE:
                    executeRoleRemoval(workflow);
                    break;
                default:
                    log.warn("Unknown operation type: " + workflow.getOperationType());
            }
            
        } catch (Exception e) {
            log.error("Failed to execute approved operation", e);
            workflow.setExecutionError(e.getMessage());
        }
    }
    
    /**
     * Execute user creation
     */
    private void executeUserCreation(WorkflowInstance workflow) throws Exception {
        Map<String, Object> userAttributes = new HashMap<>();
        for (String key : workflow.getAttributes().getKeys()) {
            userAttributes.put(key, workflow.getAttributes().get(key));
        }
        
        Map<String, Object> result = apiClient.createUser(userAttributes);
        workflow.setExecutionResult(result);
        
        log.info("User created successfully: " + workflow.getTargetId());
    }
    
    /**
     * Execute user modification
     */
    private void executeUserModification(WorkflowInstance workflow) throws Exception {
        Map<String, Object> userAttributes = new HashMap<>();
        for (String key : workflow.getAttributes().getKeys()) {
            userAttributes.put(key, workflow.getAttributes().get(key));
        }
        
        Map<String, Object> result = apiClient.modifyUser(workflow.getTargetId(), userAttributes);
        workflow.setExecutionResult(result);
        
        log.info("User modified successfully: " + workflow.getTargetId());
    }
    
    /**
     * Execute user deletion
     */
    private void executeUserDeletion(WorkflowInstance workflow) throws Exception {
        Map<String, Object> result = apiClient.deleteUser(workflow.getTargetId());
        workflow.setExecutionResult(result);
        
        log.info("User deleted successfully: " + workflow.getTargetId());
    }
    
    /**
     * Execute role addition
     */
    private void executeRoleAddition(WorkflowInstance workflow) throws Exception {
        String roleId = workflow.getAttributes().getString("roleId");
        Map<String, Object> result = apiClient.addRoleConnection(workflow.getTargetId(), roleId);
        workflow.setExecutionResult(result);
        
        log.info("Role added successfully: " + roleId + " to user " + workflow.getTargetId());
    }
    
    /**
     * Execute role removal
     */
    private void executeRoleRemoval(WorkflowInstance workflow) throws Exception {
        String roleId = workflow.getAttributes().getString("roleId");
        Map<String, Object> result = apiClient.removeRoleConnection(workflow.getTargetId(), roleId);
        workflow.setExecutionResult(result);
        
        log.info("Role removed successfully: " + roleId + " from user " + workflow.getTargetId());
    }
    
    /**
     * Send approval notifications
     */
    private void sendApprovalNotifications(WorkflowInstance workflow) {
        log.debug("Sending approval notifications for workflow: " + workflow.getWorkflowId());
        
        for (String approver : workflow.getApprovers()) {
            sendNotification(approver, "Approval Required", 
                "Please review and approve " + workflow.getOperationType() + 
                " for " + workflow.getTargetId());
        }
    }
    
    /**
     * Send rejection notifications
     */
    private void sendRejectionNotifications(WorkflowInstance workflow, String rejector, String comments) {
        log.debug("Sending rejection notifications for workflow: " + workflow.getWorkflowId());
        
        sendNotification(workflow.getRequestor(), "Request Rejected", 
            "Your request for " + workflow.getOperationType() + 
            " has been rejected by " + rejector + ". Comments: " + comments);
    }
    
    /**
     * Send approval completion notifications
     */
    private void sendApprovalCompletionNotifications(WorkflowInstance workflow) {
        log.debug("Sending completion notifications for workflow: " + workflow.getWorkflowId());
        
        sendNotification(workflow.getRequestor(), "Request Approved", 
            "Your request for " + workflow.getOperationType() + 
            " has been approved and executed successfully.");
    }
    
    /**
     * Send cancellation notifications
     */
    private void sendCancellationNotifications(WorkflowInstance workflow, String reason) {
        log.debug("Sending cancellation notifications for workflow: " + workflow.getWorkflowId());
        
        for (String approver : workflow.getApprovers()) {
            sendNotification(approver, "Request Cancelled", 
                "The approval request for " + workflow.getOperationType() + 
                " has been cancelled. Reason: " + reason);
        }
    }
    
    /**
     * Send expiration notifications
     */
    private void sendExpirationNotifications(WorkflowInstance workflow) {
        log.debug("Sending expiration notifications for workflow: " + workflow.getWorkflowId());
        
        sendNotification(workflow.getRequestor(), "Request Expired", 
            "Your request for " + workflow.getOperationType() + 
            " has expired due to lack of approval within the required timeframe.");
    }
    
    /**
     * Send notification (placeholder implementation)
     */
    private void sendNotification(String recipient, String subject, String message) {
        // In production, integrate with email system or notification service
        log.info("Notification sent to " + recipient + ": " + subject);
    }
    
    /**
     * Generate unique workflow ID
     */
    private String generateWorkflowId() {
        return "WF-" + System.currentTimeMillis() + "-" + 
               Integer.toHexString(new Random().nextInt());
    }
    
    /**
     * Calculate expiry date for workflow
     */
    private Date calculateExpiryDate() {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.HOUR, DEFAULT_APPROVAL_TIMEOUT);
        return cal.getTime();
    }
    
    /**
     * Workflow Instance inner class
     */
    public static class WorkflowInstance {
        private String workflowId;
        private String operationType;
        private String targetId;
        private Attributes attributes;
        private List<String> approvers;
        private String status;
        private Date createdDate;
        private Date expiryDate;
        private String requestor;
        private String cancellationReason;
        private String executionError;
        private Map<String, Object> executionResult;
        private List<ApprovalDecision> approvalDecisions = new ArrayList<>();
        
        // Getters and setters
        public String getWorkflowId() { return workflowId; }
        public void setWorkflowId(String workflowId) { this.workflowId = workflowId; }
        
        public String getOperationType() { return operationType; }
        public void setOperationType(String operationType) { this.operationType = operationType; }
        
        public String getTargetId() { return targetId; }
        public void setTargetId(String targetId) { this.targetId = targetId; }
        
        public Attributes getAttributes() { return attributes; }
        public void setAttributes(Attributes attributes) { this.attributes = attributes; }
        
        public List<String> getApprovers() { return approvers; }
        public void setApprovers(List<String> approvers) { this.approvers = approvers; }
        
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        
        public Date getCreatedDate() { return createdDate; }
        public void setCreatedDate(Date createdDate) { this.createdDate = createdDate; }
        
        public Date getExpiryDate() { return expiryDate; }
        public void setExpiryDate(Date expiryDate) { this.expiryDate = expiryDate; }
        
        public String getRequestor() { return requestor; }
        public void setRequestor(String requestor) { this.requestor = requestor; }
        
        public String getCancellationReason() { return cancellationReason; }
        public void setCancellationReason(String cancellationReason) { this.cancellationReason = cancellationReason; }
        
        public String getExecutionError() { return executionError; }
        public void setExecutionError(String executionError) { this.executionError = executionError; }
        
        public Map<String, Object> getExecutionResult() { return executionResult; }
        public void setExecutionResult(Map<String, Object> executionResult) { this.executionResult = executionResult; }
        
        public List<ApprovalDecision> getApprovalDecisions() { return approvalDecisions; }
        public void addApprovalDecision(ApprovalDecision decision) { this.approvalDecisions.add(decision); }
        
        public boolean isExpired() {
            return expiryDate != null && expiryDate.before(new Date());
        }
        
        public boolean hasAllApprovals() {
            if (approvers == null || approvers.isEmpty()) {
                return true;
            }
            
            Set<String> approvedBy = new HashSet<>();
            for (ApprovalDecision decision : approvalDecisions) {
                if (decision.isApproved()) {
                    approvedBy.add(decision.getApproverId());
                }
            }
            
            return approvedBy.containsAll(approvers);
        }
        
        public boolean hasApprovalFrom(String approverId) {
            for (ApprovalDecision decision : approvalDecisions) {
                if (approverId.equals(decision.getApproverId())) {
                    return true;
                }
            }
            return false;
        }
    }
    
    /**
     * Approval Decision inner class
     */
    public static class ApprovalDecision {
        private String approverId;
        private boolean approved;
        private String comments;
        private Date decisionDate;
        
        // Getters and setters
        public String getApproverId() { return approverId; }
        public void setApproverId(String approverId) { this.approverId = approverId; }
        
        public boolean isApproved() { return approved; }
        public void setApproved(boolean approved) { this.approved = approved; }
        
        public String getComments() { return comments; }
        public void setComments(String comments) { this.comments = comments; }
        
        public Date getDecisionDate() { return decisionDate; }
        public void setDecisionDate(Date decisionDate) { this.decisionDate = decisionDate; }
    }
}
