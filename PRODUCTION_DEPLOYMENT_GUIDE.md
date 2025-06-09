# SailPoint IIQ Accessio RACF Connector - Production Deployment Guide

## Overview

This guide provides comprehensive instructions for deploying the SailPoint IdentityIQ Accessio RACF connector to production environments. The connector enables full lifecycle management of RACF users and roles through the Garancy API integration.

## Prerequisites

### System Requirements

- **SailPoint IdentityIQ**: Version 8.0 or higher
- **Java**: JDK 11 or higher
- **Database**: PostgreSQL 12+ or Oracle 12c+ (for SailPoint IIQ)
- **Memory**: Minimum 8GB RAM for SailPoint IIQ server
- **Storage**: Minimum 50GB available space

### Network Requirements

- **Garancy API Access**: HTTPS connectivity to Garancy endpoints
- **SailPoint IIQ**: Web access for administration
- **SMTP**: Email server for notifications (optional)
- **Firewall**: Ports 8080/8443 for SailPoint IIQ

### Access Requirements

- **SailPoint Administrator**: Full administrative access to IIQ
- **Garancy API Credentials**: Service account with appropriate permissions
- **Database Access**: Connection credentials for SailPoint IIQ database
- **File System Access**: Write permissions to SailPoint IIQ directories

## Pre-Deployment Checklist

### 1. Environment Validation

```bash
# Verify SailPoint IIQ installation
ls -la $IIQ_HOME/WEB-INF/lib/identityiq.jar

# Check Java version
java -version

# Test database connectivity
# (Use appropriate database client)

# Verify Garancy API connectivity
curl -k https://your-garancy-endpoint/api/health
```

### 2. Backup Creation

```bash
# Create comprehensive backup
mkdir -p /backup/sailpoint/$(date +%Y%m%d_%H%M%S)
BACKUP_DIR="/backup/sailpoint/$(date +%Y%m%d_%H%M%S)"

# Backup SailPoint IIQ
cp -r $IIQ_HOME/WEB-INF/lib $BACKUP_DIR/
cp -r $IIQ_HOME/WEB-INF/config $BACKUP_DIR/

# Backup database (PostgreSQL example)
pg_dump -h localhost -U sailpoint identityiq > $BACKUP_DIR/identityiq_backup.sql

# Document current configuration
cp /path/to/current/connector/config/* $BACKUP_DIR/config/
```

### 3. Configuration Preparation

Update the following configuration files with production values:

#### `config/deployment.properties`
```properties
# Production Environment Configuration
environment=production
sailpoint.iiq.url=https://prod-sailpoint.company.com
sailpoint.iiq.username=spadmin
sailpoint.iiq.password=${ENCRYPTED_PASSWORD}

# Garancy API Configuration
garancy.endpoint=https://prod-garancy.company.com/api
garancy.username=${GARANCY_PROD_USER}
garancy.password=${ENCRYPTED_GARANCY_PASSWORD}
garancy.timeout=60000

# Database Configuration
database.url=jdbc:postgresql://prod-db.company.com:5432/identityiq
database.username=${DB_PROD_USER}
database.password=${ENCRYPTED_DB_PASSWORD}

# Security Configuration
security.encryption.key=${PROD_ENCRYPTION_KEY}
security.ssl.enabled=true
security.ssl.keystore=/path/to/prod/keystore.jks
security.ssl.truststore=/path/to/prod/truststore.jks

# Monitoring Configuration
monitoring.enabled=true
monitoring.metrics.endpoint=https://monitoring.company.com/metrics
monitoring.alerts.email=sailpoint-alerts@company.com

# Logging Configuration
logging.level=INFO
logging.audit.enabled=true
logging.file.path=/var/log/sailpoint/accessio-racf.log
logging.file.max.size=100MB
logging.file.max.files=10
```

#### `src/main/resources/config/application.properties`
```properties
# Production Application Configuration
app.environment=production
app.version=1.0.0
app.build.timestamp=${BUILD_TIMESTAMP}

# Performance Configuration
connector.pool.size=20
connector.timeout=300000
connector.retry.attempts=3
connector.retry.delay=5000

# Approval Workflow Configuration
approval.timeout.hours=72
approval.escalation.enabled=true
approval.notification.enabled=true

# SoD Configuration
sod.validation.enabled=true
sod.conflict.action=REJECT
sod.high.risk.auto.reject=true

# Recertification Configuration
recertification.enabled=true
recertification.periodic.schedule=0 0 2 1 * ?
recertification.guardian.schedule=0 0 2 15 * ?
recertification.notification.days.before=7,3,1

# Audit Configuration
audit.enabled=true
audit.level=DETAILED
audit.retention.days=2555  # 7 years
audit.export.enabled=true
audit.export.schedule=0 0 3 * * ?
```

## Deployment Process

### Phase 1: Connector Deployment

1. **Build the Connector**
```bash
cd /path/to/sailpoint-racf-connector
mvn clean package -Pprod
```

2. **Deploy Using Automated Script**
```bash
# Set environment variables
export IIQ_HOME=/opt/sailpoint/identityiq
export ENVIRONMENT=production
export BACKUP_ENABLED=true
export VALIDATION_ENABLED=true

# Run deployment script
./scripts/deploy.sh deploy
```

3. **Manual Deployment (Alternative)**
```bash
# Copy connector JAR
cp target/accessio-racf-connector-1.0.0.jar $IIQ_HOME/WEB-INF/lib/

# Copy configuration files
cp -r src/main/resources/config/* $IIQ_HOME/WEB-INF/config/custom/

# Set permissions
chown -R sailpoint:sailpoint $IIQ_HOME/WEB-INF/lib/accessio-racf-connector-1.0.0.jar
chown -R sailpoint:sailpoint $IIQ_HOME/WEB-INF/config/custom/
```

### Phase 2: SailPoint IIQ Configuration Import

1. **Import XML Configurations**
```bash
# Navigate to SailPoint IIQ console
cd $IIQ_HOME/WEB-INF/bin

# Import in correct order
./iiq import ../../../src/main/resources/rules/*.xml
./iiq import ../../../src/main/resources/applications/AccessioRACF.xml
./iiq import ../../../src/main/resources/policies/*.xml
./iiq import ../../../src/main/resources/workflows/*.xml
./iiq import ../../../src/main/resources/tasks/*.xml
```

2. **Verify Imports**
```bash
# Check for import errors
./iiq list Rule | grep -i accessio
./iiq list Application | grep -i accessio
./iiq list Policy | grep -i accessio
./iiq list Workflow | grep -i accessio
./iiq list TaskDefinition | grep -i accessio
```

### Phase 3: Application Configuration

1. **Configure Accessio RACF Application**
   - Login to SailPoint IIQ as administrator
   - Navigate to **Applications** → **Accessio RACF**
   - Update connection parameters:
     - Garancy Endpoint URL
     - Authentication credentials
     - Timeout settings
     - Connection pool configuration

2. **Test Connection**
   - Click **Test Connection** button
   - Verify successful connection to Garancy API
   - Check logs for any connection issues

3. **Configure Aggregation**
   - Set up aggregation schedule
   - Configure incremental vs full aggregation
   - Set performance parameters

### Phase 4: Workflow Configuration

1. **Approval Workflow Setup**
   - Configure approval chains for each user type
   - Set up notification templates
   - Configure escalation rules

2. **SoD Policy Configuration**
   - Define role conflict rules
   - Configure risk levels
   - Set up automatic rejection criteria

3. **Recertification Setup**
   - Configure certification campaigns
   - Set up schedules for different user types
   - Configure notification settings

## Post-Deployment Validation

### 1. Functional Testing

```bash
# Test user creation workflow
# (Execute through SailPoint IIQ interface)

# Test role assignment workflow
# (Execute through SailPoint IIQ interface)

# Test aggregation
./iiq task AccessioRACFAggregationTask

# Test recertification
./iiq task AccessioRACFRecertificationTask
```

### 2. Performance Testing

```bash
# Run performance tests
mvn test -Dtest=AccessioRACFIntegrationTest -Dintegration.tests.enabled=true

# Monitor system resources during aggregation
top -p $(pgrep -f identityiq)
iostat -x 1 60
```

### 3. Security Validation

- Verify SSL/TLS connections to Garancy API
- Check audit log generation
- Validate approval workflow security
- Test SoD conflict detection

### 4. Monitoring Setup

1. **Configure Health Checks**
```bash
# Add health check endpoint monitoring
curl -k https://sailpoint.company.com/identityiq/health
```

2. **Set Up Alerts**
   - Connection failures to Garancy API
   - Workflow approval timeouts
   - SoD violations
   - Performance degradation

3. **Log Monitoring**
```bash
# Monitor application logs
tail -f /var/log/sailpoint/accessio-racf.log

# Monitor SailPoint IIQ logs
tail -f $IIQ_HOME/WEB-INF/logs/sailpoint.log
```

## Rollback Procedures

### Emergency Rollback

```bash
# Use automated rollback
./scripts/deploy.sh rollback

# Or manual rollback
# 1. Stop SailPoint IIQ
systemctl stop sailpoint

# 2. Restore from backup
BACKUP_DIR="/backup/sailpoint/YYYYMMDD_HHMMSS"
cp $BACKUP_DIR/lib/* $IIQ_HOME/WEB-INF/lib/
cp -r $BACKUP_DIR/config/* $IIQ_HOME/WEB-INF/config/

# 3. Restore database
psql -h localhost -U sailpoint identityiq < $BACKUP_DIR/identityiq_backup.sql

# 4. Start SailPoint IIQ
systemctl start sailpoint
```

### Partial Rollback

```bash
# Remove only connector components
rm $IIQ_HOME/WEB-INF/lib/accessio-racf-connector-*.jar
rm -rf $IIQ_HOME/WEB-INF/config/custom/accessio-racf/

# Remove imported configurations via IIQ console
./iiq delete Application "Accessio RACF"
./iiq delete Workflow "AccessioRACFUserCreationWorkflow"
# ... (remove other imported objects)
```

## Maintenance Procedures

### Regular Maintenance

1. **Weekly Tasks**
   - Review audit logs
   - Check system performance
   - Validate workflow approvals
   - Monitor error rates

2. **Monthly Tasks**
   - Update connector statistics
   - Review recertification results
   - Analyze SoD violations
   - Performance tuning

3. **Quarterly Tasks**
   - Security review
   - Configuration validation
   - Disaster recovery testing
   - Documentation updates

### Troubleshooting

#### Common Issues

1. **Connection Failures**
```bash
# Check network connectivity
telnet garancy-server 443

# Verify SSL certificates
openssl s_client -connect garancy-server:443

# Check credentials
# (Verify in Garancy system)
```

2. **Performance Issues**
```bash
# Check database performance
SELECT * FROM pg_stat_activity WHERE application_name LIKE '%identityiq%';

# Monitor JVM heap usage
jstat -gc $(pgrep -f identityiq) 5s

# Check connector pool usage
# (Monitor through SailPoint IIQ interface)
```

3. **Workflow Issues**
```bash
# Check workflow status
./iiq list WorkflowCase | grep -i accessio

# Review workflow logs
grep -i "workflow" /var/log/sailpoint/accessio-racf.log
```

## Security Considerations

### Production Security Checklist

- [ ] All passwords encrypted and stored securely
- [ ] SSL/TLS enabled for all connections
- [ ] Audit logging enabled and monitored
- [ ] Access controls properly configured
- [ ] Network security rules in place
- [ ] Regular security scans performed
- [ ] Incident response procedures documented

### Compliance Requirements

- **SOX Compliance**: Audit trails for all access changes
- **GDPR Compliance**: Data privacy and retention policies
- **PCI DSS**: Secure handling of sensitive data
- **HIPAA**: Healthcare data protection (if applicable)

## Support and Escalation

### Support Contacts

- **Level 1 Support**: helpdesk@company.com
- **Level 2 Support**: sailpoint-support@company.com
- **Level 3 Support**: identity-architects@company.com

### Escalation Procedures

1. **Severity 1 (Critical)**: Immediate escalation to Level 3
2. **Severity 2 (High)**: Escalate to Level 2 within 2 hours
3. **Severity 3 (Medium)**: Standard support queue
4. **Severity 4 (Low)**: Next business day response

### Documentation

- **Technical Documentation**: `/docs/technical/`
- **User Guides**: `/docs/user/`
- **API Documentation**: `/docs/api/`
- **Troubleshooting**: `/docs/troubleshooting/`

---

**Document Version**: 1.0  
**Last Updated**: 2024-06-09  
**Next Review**: 2024-09-09  
**Owner**: Identity Management Team
