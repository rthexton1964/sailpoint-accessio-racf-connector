package com.sailpoint.connector.accessio.racf;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.soap.*;
import javax.xml.transform.stream.StreamSource;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;

/**
 * Garancy API Client for Accessio RACF Integration
 * 
 * This client handles all communication with the Garancy API system
 * for RACF user and role management operations.
 * 
 * Supported Operations:
 * - List organizational units
 * - List users and roles
 * - Create, modify, delete users
 * - Add/remove role assignments
 * - User lifecycle management
 * 
 * @author SailPoint Professional Services
 * @version 1.0.0
 */
public class GarancyAPIClient {
    
    private static final Log log = LogFactory.getLog(GarancyAPIClient.class);
    
    // API endpoints
    private static final String ENDPOINT_LIST_ORGUNIT = "/listOrgUnit";
    private static final String ENDPOINT_LIST_ROLE = "/listRole";
    private static final String ENDPOINT_LIST_USER = "/listUser";
    private static final String ENDPOINT_LIST_ROLE_USER_CONNECTION = "/listRoleToUserConnection";
    private static final String ENDPOINT_CREATE_USER = "/createUser";
    private static final String ENDPOINT_MODIFY_USER = "/modifyUser";
    private static final String ENDPOINT_SUSPEND_USER = "/suspendUser";
    private static final String ENDPOINT_RESUME_USER = "/resumeUser";
    private static final String ENDPOINT_DELETE_USER = "/deleteUser";
    private static final String ENDPOINT_ADD_ROLE_CONNECTION = "/addRoleConnection";
    private static final String ENDPOINT_REMOVE_ROLE_CONNECTION = "/removeRoleConnection";
    
    // SOAP namespaces
    private static final String SOAP_NAMESPACE = "http://schemas.xmlsoap.org/soap/envelope/";
    private static final String GARANCY_NAMESPACE = "http://garancy.api.accessio.com/";
    
    // Configuration
    private final String serverUrl;
    private final String username;
    private final String password;
    private final int timeout;
    private final int retryAttempts;
    
    // Connection management
    private SOAPConnectionFactory connectionFactory;
    private MessageFactory messageFactory;
    
    /**
     * Constructor
     */
    public GarancyAPIClient(String serverUrl, String username, String password, int timeout, int retryAttempts) {
        this.serverUrl = serverUrl;
        this.username = username;
        this.password = password;
        this.timeout = timeout;
        this.retryAttempts = retryAttempts;
        
        try {
            this.connectionFactory = SOAPConnectionFactory.newInstance();
            this.messageFactory = MessageFactory.newInstance();
        } catch (Exception e) {
            log.error("Failed to initialize SOAP factories", e);
            throw new RuntimeException("Failed to initialize SOAP factories", e);
        }
    }
    
    /**
     * Test connection to Garancy API
     */
    public boolean testConnection() {
        log.debug("Testing connection to Garancy API");
        
        try {
            // Try to list org units as a connectivity test
            List<Map<String, Object>> orgUnits = listOrgUnits();
            log.info("Connection test successful - retrieved " + orgUnits.size() + " org units");
            return true;
            
        } catch (Exception e) {
            log.error("Connection test failed", e);
            return false;
        }
    }
    
    /**
     * List all organizational units
     */
    public List<Map<String, Object>> listOrgUnits() throws Exception {
        log.debug("Listing organizational units");
        
        SOAPMessage request = createSOAPRequest("listOrgUnit", new HashMap<>());
        SOAPMessage response = sendSOAPRequest(ENDPOINT_LIST_ORGUNIT, request);
        
        return parseOrgUnitResponse(response);
    }
    
    /**
     * List all roles
     */
    public List<Map<String, Object>> listRoles() throws Exception {
        log.debug("Listing roles");
        
        SOAPMessage request = createSOAPRequest("listRole", new HashMap<>());
        SOAPMessage response = sendSOAPRequest(ENDPOINT_LIST_ROLE, request);
        
        return parseRoleResponse(response);
    }
    
    /**
     * List all users
     */
    public List<Map<String, Object>> listUsers() throws Exception {
        log.debug("Listing users");
        
        SOAPMessage request = createSOAPRequest("listUser", new HashMap<>());
        SOAPMessage response = sendSOAPRequest(ENDPOINT_LIST_USER, request);
        
        return parseUserResponse(response);
    }
    
    /**
     * List role to user connections
     */
    public List<Map<String, Object>> listRoleUserConnections() throws Exception {
        log.debug("Listing role to user connections");
        
        SOAPMessage request = createSOAPRequest("listRoleToUserConnection", new HashMap<>());
        SOAPMessage response = sendSOAPRequest(ENDPOINT_LIST_ROLE_USER_CONNECTION, request);
        
        return parseRoleUserConnectionResponse(response);
    }
    
    /**
     * Create a new user
     */
    public Map<String, Object> createUser(Map<String, Object> userAttributes) throws Exception {
        log.debug("Creating user: " + userAttributes.get("BASEUS_SAM_ID"));
        
        SOAPMessage request = createSOAPRequest("createUser", userAttributes);
        SOAPMessage response = sendSOAPRequest(ENDPOINT_CREATE_USER, request);
        
        return parseOperationResponse(response);
    }
    
    /**
     * Modify an existing user
     */
    public Map<String, Object> modifyUser(String userId, Map<String, Object> userAttributes) throws Exception {
        log.debug("Modifying user: " + userId);
        
        Map<String, Object> parameters = new HashMap<>(userAttributes);
        parameters.put("BASEUS_SAM_ID", userId);
        
        SOAPMessage request = createSOAPRequest("modifyUser", parameters);
        SOAPMessage response = sendSOAPRequest(ENDPOINT_MODIFY_USER, request);
        
        return parseOperationResponse(response);
    }
    
    /**
     * Suspend a user
     */
    public Map<String, Object> suspendUser(String userId) throws Exception {
        log.debug("Suspending user: " + userId);
        
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("BASEUS_SAM_ID", userId);
        
        SOAPMessage request = createSOAPRequest("suspendUser", parameters);
        SOAPMessage response = sendSOAPRequest(ENDPOINT_SUSPEND_USER, request);
        
        return parseOperationResponse(response);
    }
    
    /**
     * Resume a user
     */
    public Map<String, Object> resumeUser(String userId) throws Exception {
        log.debug("Resuming user: " + userId);
        
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("BASEUS_SAM_ID", userId);
        
        SOAPMessage request = createSOAPRequest("resumeUser", parameters);
        SOAPMessage response = sendSOAPRequest(ENDPOINT_RESUME_USER, request);
        
        return parseOperationResponse(response);
    }
    
    /**
     * Delete a user
     */
    public Map<String, Object> deleteUser(String userId) throws Exception {
        log.debug("Deleting user: " + userId);
        
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("BASEUS_SAM_ID", userId);
        
        SOAPMessage request = createSOAPRequest("deleteUser", parameters);
        SOAPMessage response = sendSOAPRequest(ENDPOINT_DELETE_USER, request);
        
        return parseOperationResponse(response);
    }
    
    /**
     * Add role connection to user
     */
    public Map<String, Object> addRoleConnection(String userId, String roleId) throws Exception {
        log.debug("Adding role " + roleId + " to user " + userId);
        
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("BASEUS_SAM_ID", userId);
        parameters.put("BASEUSRC_ROLE", roleId);
        
        SOAPMessage request = createSOAPRequest("addRoleConnection", parameters);
        SOAPMessage response = sendSOAPRequest(ENDPOINT_ADD_ROLE_CONNECTION, request);
        
        return parseOperationResponse(response);
    }
    
    /**
     * Remove role connection from user
     */
    public Map<String, Object> removeRoleConnection(String userId, String roleId) throws Exception {
        log.debug("Removing role " + roleId + " from user " + userId);
        
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("BASEUS_SAM_ID", userId);
        parameters.put("BASEUSRC_ROLE", roleId);
        
        SOAPMessage request = createSOAPRequest("removeRoleConnection", parameters);
        SOAPMessage response = sendSOAPRequest(ENDPOINT_REMOVE_ROLE_CONNECTION, request);
        
        return parseOperationResponse(response);
    }
    
    /**
     * Create SOAP request message
     */
    private SOAPMessage createSOAPRequest(String operation, Map<String, Object> parameters) throws Exception {
        SOAPMessage message = messageFactory.createMessage();
        SOAPPart soapPart = message.getSOAPPart();
        SOAPEnvelope envelope = soapPart.getEnvelope();
        
        // Add namespaces
        envelope.addNamespaceDeclaration("gar", GARANCY_NAMESPACE);
        
        // Create SOAP header with authentication
        SOAPHeader header = envelope.getHeader();
        if (header == null) {
            header = envelope.addHeader();
        }
        
        SOAPElement authElement = header.addChildElement("Authentication", "gar");
        authElement.addChildElement("Username", "gar").addTextNode(username);
        authElement.addChildElement("Password", "gar").addTextNode(password);
        
        // Log operation safely without exposing credentials
        logSafeOperation(operation, parameters);
        
        // Create SOAP body
        SOAPBody body = envelope.getBody();
        SOAPElement operationElement = body.addChildElement(operation, "gar");
        
        // Add parameters
        for (Map.Entry<String, Object> entry : parameters.entrySet()) {
            if (entry.getValue() != null) {
                SOAPElement paramElement = operationElement.addChildElement(entry.getKey(), "gar");
                paramElement.addTextNode(entry.getValue().toString());
            }
        }
        
        message.saveChanges();
        return message;
    }
    
    /**
     * Log operation safely without exposing sensitive data
     */
    private void logSafeOperation(String operation, Map<String, Object> parameters) {
        Map<String, Object> safeParams = new HashMap<>(parameters);
        // Remove sensitive fields that could contain passwords or PII
        safeParams.remove("password");
        safeParams.remove("Password");
        safeParams.remove("BASEUS_PASSWORD");
        safeParams.remove("BASEUS_C_C_78_002"); // Deputy password field
        safeParams.remove("BASEUS_C_C_78_004"); // Owner password field
        
        log.debug("SOAP operation: " + operation + " for user: " + username + " with parameters: " + safeParams.keySet());
    }
    
    /**
     * Send SOAP request with retry logic
     */
    private SOAPMessage sendSOAPRequest(String endpoint, SOAPMessage request) throws Exception {
        Exception lastException = null;
        
        for (int attempt = 1; attempt <= retryAttempts; attempt++) {
            try {
                log.debug("Sending SOAP request to " + endpoint + " (attempt " + attempt + ")");
                
                SOAPConnection connection = connectionFactory.createConnection();
                try {
                    String fullUrl = serverUrl + endpoint;
                    SOAPMessage response = connection.call(request, fullUrl);
                    
                    // Check for SOAP faults
                    if (response.getSOAPBody().hasFault()) {
                        SOAPFault fault = response.getSOAPBody().getFault();
                        throw new Exception("SOAP Fault: " + fault.getFaultString());
                    }
                    
                    return response;
                    
                } finally {
                    connection.close();
                }
                
            } catch (Exception e) {
                lastException = e;
                log.warn("Request attempt " + attempt + " failed: " + e.getMessage());
                
                if (attempt < retryAttempts) {
                    try {
                        Thread.sleep(1000 * attempt); // Exponential backoff
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new Exception("Request interrupted", ie);
                    }
                }
            }
        }
        
        throw new Exception("All " + retryAttempts + " attempts failed", lastException);
    }
    
    /**
     * Parse organizational unit response
     */
    private List<Map<String, Object>> parseOrgUnitResponse(SOAPMessage response) throws Exception {
        List<Map<String, Object>> orgUnits = new ArrayList<>();
        
        Document doc = response.getSOAPBody().extractContentAsDocument();
        NodeList orgUnitNodes = doc.getElementsByTagName("OrgUnit");
        
        for (int i = 0; i < orgUnitNodes.getLength(); i++) {
            Element orgUnitElement = (Element) orgUnitNodes.item(i);
            Map<String, Object> orgUnit = new HashMap<>();
            
            orgUnit.put("BASEORG_ID", getElementText(orgUnitElement, "BASEORG_ID"));
            orgUnit.put("BASEORG_KEY", getElementText(orgUnitElement, "BASEORG_KEY"));
            orgUnit.put("BASEORG_ACTSTA", getElementText(orgUnitElement, "BASEORG_ACTSTA"));
            orgUnit.put("BASEORG_C_C32_05", getElementText(orgUnitElement, "BASEORG_C_C32_05"));
            orgUnit.put("BASEORG_C_C78_01", getElementText(orgUnitElement, "BASEORG_C_C78_01"));
            orgUnit.put("BASEORG_EMAIL", getElementText(orgUnitElement, "BASEORG_EMAIL"));
            
            orgUnits.add(orgUnit);
        }
        
        log.debug("Parsed " + orgUnits.size() + " organizational units");
        return orgUnits;
    }
    
    /**
     * Parse role response
     */
    private List<Map<String, Object>> parseRoleResponse(SOAPMessage response) throws Exception {
        List<Map<String, Object>> roles = new ArrayList<>();
        
        Document doc = response.getSOAPBody().extractContentAsDocument();
        NodeList roleNodes = doc.getElementsByTagName("Role");
        
        for (int i = 0; i < roleNodes.getLength(); i++) {
            Element roleElement = (Element) roleNodes.item(i);
            Map<String, Object> role = new HashMap<>();
            
            role.put("BASEUSRC_ROLE", getElementText(roleElement, "BASEUSRC_ROLE"));
            role.put("TECHDSP_NAME", getElementText(roleElement, "TECHDSP_NAME"));
            role.put("BASEUS_C_C_08_004", getElementText(roleElement, "BASEUS_C_C_08_004"));
            role.put("BASEUS_C_C_78_001", getElementText(roleElement, "BASEUS_C_C_78_001"));
            role.put("BASEUS_C_C_78_002", getElementText(roleElement, "BASEUS_C_C_78_002"));
            role.put("BASEUS_C_C_78_003", getElementText(roleElement, "BASEUS_C_C_78_003"));
            role.put("BASEUS_C_C_78_004", getElementText(roleElement, "BASEUS_C_C_78_004"));
            role.put("BASEUS_C_C_78_005", getElementText(roleElement, "BASEUS_C_C_78_005"));
            
            roles.add(role);
        }
        
        log.debug("Parsed " + roles.size() + " roles");
        return roles;
    }
    
    /**
     * Parse user response
     */
    private List<Map<String, Object>> parseUserResponse(SOAPMessage response) throws Exception {
        List<Map<String, Object>> users = new ArrayList<>();
        
        Document doc = response.getSOAPBody().extractContentAsDocument();
        NodeList userNodes = doc.getElementsByTagName("User");
        
        for (int i = 0; i < userNodes.getLength(); i++) {
            Element userElement = (Element) userNodes.item(i);
            Map<String, Object> user = new HashMap<>();
            
            user.put("BASEUS_SAM_ID", getElementText(userElement, "BASEUS_SAM_ID"));
            user.put("BASEORG_ID", getElementText(userElement, "BASEORG_ID"));
            user.put("BASEUS_C_C01_001", getElementText(userElement, "BASEUS_C_C01_001"));
            user.put("BASEUS_C_C01_009", getElementText(userElement, "BASEUS_C_C01_009"));
            user.put("BASEUS_C_C01_010", getElementText(userElement, "BASEUS_C_C01_010"));
            user.put("BASEUS_C_C01_011", getElementText(userElement, "BASEUS_C_C01_011"));
            user.put("BASEUS_C_C01_004", getElementText(userElement, "BASEUS_C_C01_004"));
            
            users.add(user);
        }
        
        log.debug("Parsed " + users.size() + " users");
        return users;
    }
    
    /**
     * Parse role-user connection response
     */
    private List<Map<String, Object>> parseRoleUserConnectionResponse(SOAPMessage response) throws Exception {
        List<Map<String, Object>> connections = new ArrayList<>();
        
        Document doc = response.getSOAPBody().extractContentAsDocument();
        NodeList connectionNodes = doc.getElementsByTagName("Connection");
        
        for (int i = 0; i < connectionNodes.getLength(); i++) {
            Element connectionElement = (Element) connectionNodes.item(i);
            Map<String, Object> connection = new HashMap<>();
            
            connection.put("BASEUS_SAM_ID", getElementText(connectionElement, "BASEUS_SAM_ID"));
            connection.put("BASEUSRC_ROLE", getElementText(connectionElement, "BASEUSRC_ROLE"));
            connection.put("CONNECTION_STATUS", getElementText(connectionElement, "CONNECTION_STATUS"));
            connection.put("ASSIGNED_DATE", getElementText(connectionElement, "ASSIGNED_DATE"));
            
            connections.add(connection);
        }
        
        log.debug("Parsed " + connections.size() + " role-user connections");
        return connections;
    }
    
    /**
     * Parse operation response
     */
    private Map<String, Object> parseOperationResponse(SOAPMessage response) throws Exception {
        Map<String, Object> result = new HashMap<>();
        
        Document doc = response.getSOAPBody().extractContentAsDocument();
        NodeList resultNodes = doc.getElementsByTagName("Result");
        
        if (resultNodes.getLength() > 0) {
            Element resultElement = (Element) resultNodes.item(0);
            
            result.put("status", getElementText(resultElement, "Status"));
            result.put("message", getElementText(resultElement, "Message"));
            result.put("requestId", getElementText(resultElement, "RequestId"));
            result.put("timestamp", getElementText(resultElement, "Timestamp"));
        }
        
        return result;
    }
    
    /**
     * Get text content from XML element
     */
    private String getElementText(Element parent, String tagName) {
        NodeList nodeList = parent.getElementsByTagName(tagName);
        if (nodeList.getLength() > 0) {
            return nodeList.item(0).getTextContent();
        }
        return null;
    }
    
    /**
     * Close API client and cleanup resources
     */
    public void close() {
        log.debug("Closing Garancy API client");
        // Cleanup any resources if needed
    }
}
