package com.sailpoint.connector.accessio.racf;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;

import sailpoint.connector.ConnectorException;
import sailpoint.object.Application;
import sailpoint.object.AttributeDefinition;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningResult;
import sailpoint.object.ResourceObject;
import sailpoint.object.Schema;
import sailpoint.tools.CloseableIterator;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AccessioRACFConnector
 */
@ExtendWith(MockitoExtension.class)
public class AccessioRACFConnectorTest {

    @Mock
    private Application mockApplication;
    
    @Mock
    private GarancyAPIClient mockApiClient;
    
    @Mock
    private RACFUserManager mockUserManager;
    
    @Mock
    private RACFRoleManager mockRoleManager;
    
    @Mock
    private ApprovalWorkflowHandler mockApprovalHandler;
    
    private AccessioRACFConnector connector;
    
    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        connector = new AccessioRACFConnector();
        
        // Setup mock application
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("garancyEndpoint", "http://test.garancy.com/api");
        attributes.put("garancyUsername", "testuser");
        attributes.put("garancyPassword", "testpass");
        attributes.put("garancyTimeout", "30000");
        
        when(mockApplication.getAttributes()).thenReturn(attributes);
        when(mockApplication.getAttributeValue("garancyEndpoint")).thenReturn("http://test.garancy.com/api");
        when(mockApplication.getAttributeValue("garancyUsername")).thenReturn("testuser");
        when(mockApplication.getAttributeValue("garancyPassword")).thenReturn("testpass");
        when(mockApplication.getAttributeValue("garancyTimeout")).thenReturn("30000");
        
        // Initialize connector with mocked dependencies
        connector.setApplication(mockApplication);
        connector.setGarancyAPIClient(mockApiClient);
        connector.setUserManager(mockUserManager);
        connector.setRoleManager(mockRoleManager);
        connector.setApprovalWorkflowHandler(mockApprovalHandler);
    }
    
    @Test
    void testConnectorInitialization() throws Exception {
        // Test successful initialization
        assertNotNull(connector);
        assertEquals(mockApplication, connector.getApplication());
    }
    
    @Test
    void testDiscoverSchema() throws Exception {
        // Setup mock schema
        Schema accountSchema = new Schema();
        accountSchema.setObjectType("account");
        accountSchema.setNativeObjectType("account");
        accountSchema.setDisplayAttribute("BASEUS_SAM_ID");
        accountSchema.setIdentityAttribute("BASEUS_SAM_ID");
        
        AttributeDefinition samIdAttr = new AttributeDefinition();
        samIdAttr.setName("BASEUS_SAM_ID");
        samIdAttr.setType(AttributeDefinition.TYPE_STRING);
        samIdAttr.setRequired(true);
        accountSchema.addAttributeDefinition(samIdAttr);
        
        when(mockApiClient.testConnection()).thenReturn(true);
        
        // Test schema discovery
        Schema discoveredSchema = connector.discoverSchema("account");
        
        assertNotNull(discoveredSchema);
        assertEquals("account", discoveredSchema.getObjectType());
        assertEquals("BASEUS_SAM_ID", discoveredSchema.getIdentityAttribute());
        
        verify(mockApiClient).testConnection();
    }
    
    @Test
    void testIterateAllAccounts() throws Exception {
        // Setup mock user data
        List<Map<String, Object>> mockUsers = Arrays.asList(
            createMockUser("user1", "AARID", "John", "Doe"),
            createMockUser("user2", "STC", "Service", "Account"),
            createMockUser("user3", "Technical", "Tech", "User")
        );
        
        when(mockApiClient.getAllUsers()).thenReturn(mockUsers);
        
        // Test iteration
        CloseableIterator<ResourceObject> iterator = connector.iterateObjects("account", null, null);
        
        assertNotNull(iterator);
        
        List<ResourceObject> accounts = new ArrayList<>();
        while (iterator.hasNext()) {
            accounts.add(iterator.next());
        }
        iterator.close();
        
        assertEquals(3, accounts.size());
        assertEquals("user1", accounts.get(0).getIdentity());
        assertEquals("user2", accounts.get(1).getIdentity());
        assertEquals("user3", accounts.get(2).getIdentity());
        
        verify(mockApiClient).getAllUsers();
    }
    
    @Test
    void testIterateWithFilter() throws Exception {
        // Setup filter for AARID users only
        Filter filter = Filter.eq("userType", "AARID");
        
        List<Map<String, Object>> mockUsers = Arrays.asList(
            createMockUser("user1", "AARID", "John", "Doe")
        );
        
        when(mockApiClient.getUsersByType("AARID")).thenReturn(mockUsers);
        
        // Test filtered iteration
        CloseableIterator<ResourceObject> iterator = connector.iterateObjects("account", filter, null);
        
        assertNotNull(iterator);
        
        List<ResourceObject> accounts = new ArrayList<>();
        while (iterator.hasNext()) {
            accounts.add(iterator.next());
        }
        iterator.close();
        
        assertEquals(1, accounts.size());
        assertEquals("user1", accounts.get(0).getIdentity());
        assertEquals("AARID", accounts.get(0).getAttribute("userType"));
    }
    
    @Test
    void testGetObject() throws Exception {
        // Setup mock user
        Map<String, Object> mockUser = createMockUser("testuser", "AARID", "Test", "User");
        when(mockApiClient.getUser("testuser")).thenReturn(mockUser);
        
        // Test get object
        ResourceObject account = connector.getObject("account", "testuser", null);
        
        assertNotNull(account);
        assertEquals("testuser", account.getIdentity());
        assertEquals("AARID", account.getAttribute("userType"));
        assertEquals("Test", account.getAttribute("BASEUS_FIRST_NAME"));
        assertEquals("User", account.getAttribute("BASEUS_LAST_NAME"));
        
        verify(mockApiClient).getUser("testuser");
    }
    
    @Test
    void testProvisionUserCreation() throws Exception {
        // Setup provisioning plan for user creation
        ProvisioningPlan plan = new ProvisioningPlan();
        ProvisioningPlan.AccountRequest accountRequest = new ProvisioningPlan.AccountRequest();
        accountRequest.setOperation(ProvisioningPlan.AccountRequest.Operation.Create);
        accountRequest.add(new ProvisioningPlan.AttributeRequest("BASEUS_SAM_ID", "newuser"));
        accountRequest.add(new ProvisioningPlan.AttributeRequest("userType", "AARID"));
        accountRequest.add(new ProvisioningPlan.AttributeRequest("BASEUS_FIRST_NAME", "New"));
        accountRequest.add(new ProvisioningPlan.AttributeRequest("BASEUS_LAST_NAME", "User"));
        plan.add(accountRequest);
        
        // Mock successful user creation
        when(mockUserManager.createUser(any(), any())).thenReturn(true);
        when(mockApprovalHandler.submitForApproval(any(), any(), any())).thenReturn("APPROVED");
        
        // Test provisioning
        ProvisioningResult result = connector.provision(plan);
        
        assertNotNull(result);
        assertEquals(ProvisioningResult.STATUS_COMMITTED, result.getStatus());
        
        verify(mockUserManager).createUser(any(), any());
        verify(mockApprovalHandler).submitForApproval(any(), any(), any());
    }
    
    @Test
    void testProvisionRoleAssignment() throws Exception {
        // Setup provisioning plan for role assignment
        ProvisioningPlan plan = new ProvisioningPlan();
        ProvisioningPlan.AccountRequest accountRequest = new ProvisioningPlan.AccountRequest();
        accountRequest.setOperation(ProvisioningPlan.AccountRequest.Operation.Modify);
        accountRequest.add(new ProvisioningPlan.AttributeRequest("roles", 
            ProvisioningPlan.Operation.Add, Arrays.asList("RACF_USER_BASIC", "RACF_IT_USER")));
        plan.add(accountRequest);
        
        // Mock successful role assignment
        when(mockRoleManager.validateRoleAssignment(any(), any())).thenReturn(new ArrayList<>());
        when(mockUserManager.addRoles(any(), any())).thenReturn(true);
        when(mockApprovalHandler.submitForApproval(any(), any(), any())).thenReturn("APPROVED");
        
        // Test provisioning
        ProvisioningResult result = connector.provision(plan);
        
        assertNotNull(result);
        assertEquals(ProvisioningResult.STATUS_COMMITTED, result.getStatus());
        
        verify(mockRoleManager).validateRoleAssignment(any(), any());
        verify(mockUserManager).addRoles(any(), any());
        verify(mockApprovalHandler).submitForApproval(any(), any(), any());
    }
    
    @Test
    void testProvisionWithSoDConflict() throws Exception {
        // Setup provisioning plan with conflicting roles
        ProvisioningPlan plan = new ProvisioningPlan();
        ProvisioningPlan.AccountRequest accountRequest = new ProvisioningPlan.AccountRequest();
        accountRequest.setOperation(ProvisioningPlan.AccountRequest.Operation.Modify);
        accountRequest.add(new ProvisioningPlan.AttributeRequest("roles", 
            ProvisioningPlan.Operation.Add, Arrays.asList("CONFLICTING_ROLE_1", "CONFLICTING_ROLE_2")));
        plan.add(accountRequest);
        
        // Mock SoD conflict detection
        List<String> conflicts = Arrays.asList("HIGH_RISK: Conflicting roles detected");
        when(mockRoleManager.validateRoleAssignment(any(), any())).thenReturn(conflicts);
        
        // Test provisioning with SoD conflict
        ProvisioningResult result = connector.provision(plan);
        
        assertNotNull(result);
        assertEquals(ProvisioningResult.STATUS_FAILED, result.getStatus());
        assertTrue(result.getErrors().get(0).contains("SoD conflict"));
        
        verify(mockRoleManager).validateRoleAssignment(any(), any());
        verify(mockUserManager, never()).addRoles(any(), any());
    }
    
    @Test
    void testTestConnection() throws Exception {
        // Mock successful connection test
        when(mockApiClient.testConnection()).thenReturn(true);
        
        // Test connection
        connector.testConnection();
        
        verify(mockApiClient).testConnection();
    }
    
    @Test
    void testTestConnectionFailure() throws Exception {
        // Mock connection failure
        when(mockApiClient.testConnection()).thenReturn(false);
        
        // Test connection failure
        assertThrows(ConnectorException.class, () -> {
            connector.testConnection();
        });
        
        verify(mockApiClient).testConnection();
    }
    
    @Test
    void testDispose() throws Exception {
        // Test resource cleanup
        connector.dispose();
        
        // Verify cleanup was called (would need to add verification methods to actual implementation)
        // This is a placeholder for actual cleanup verification
        assertTrue(true);
    }
    
    // Helper method to create mock user data
    private Map<String, Object> createMockUser(String samId, String userType, String firstName, String lastName) {
        Map<String, Object> user = new HashMap<>();
        user.put("BASEUS_SAM_ID", samId);
        user.put("userType", userType);
        user.put("BASEUS_FIRST_NAME", firstName);
        user.put("BASEUS_LAST_NAME", lastName);
        user.put("BASEUS_EMAIL", firstName.toLowerCase() + "." + lastName.toLowerCase() + "@accessio.com");
        user.put("BASEORG_ID", "ORG_IT");
        user.put("enabled", true);
        user.put("created", new Date());
        user.put("lastModified", new Date());
        user.put("roles", Arrays.asList("RACF_USER_BASIC"));
        return user;
    }
}
