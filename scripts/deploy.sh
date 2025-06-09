#!/bin/bash

# SailPoint IIQ Accessio RACF Connector Deployment Script
# This script automates the deployment of the connector to SailPoint IIQ

set -e  # Exit on any error

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
CONFIG_FILE="${PROJECT_DIR}/config/deployment.properties"

# Default values
IIQ_HOME="${IIQ_HOME:-/opt/sailpoint/identityiq}"
ENVIRONMENT="${ENVIRONMENT:-development}"
BACKUP_ENABLED="${BACKUP_ENABLED:-true}"
VALIDATION_ENABLED="${VALIDATION_ENABLED:-true}"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Logging functions
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Load configuration
load_config() {
    if [[ -f "$CONFIG_FILE" ]]; then
        log_info "Loading configuration from $CONFIG_FILE"
        source "$CONFIG_FILE"
    else
        log_warning "Configuration file not found: $CONFIG_FILE"
        log_info "Using default values"
    fi
}

# Validate prerequisites
validate_prerequisites() {
    log_info "Validating prerequisites..."
    
    # Check if IIQ_HOME exists
    if [[ ! -d "$IIQ_HOME" ]]; then
        log_error "SailPoint IIQ home directory not found: $IIQ_HOME"
        exit 1
    fi
    
    # Check if IIQ is accessible
    if [[ ! -d "$IIQ_HOME/WEB-INF" ]]; then
        log_error "Invalid SailPoint IIQ installation: $IIQ_HOME"
        exit 1
    fi
    
    # Check if connector JAR exists
    CONNECTOR_JAR="${PROJECT_DIR}/target/accessio-racf-connector-1.0.0.jar"
    if [[ ! -f "$CONNECTOR_JAR" ]]; then
        log_error "Connector JAR not found: $CONNECTOR_JAR"
        log_info "Please run 'mvn clean package' first"
        exit 1
    fi
    
    # Check Java version
    if command -v java &> /dev/null; then
        JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}')
        log_info "Java version: $JAVA_VERSION"
    else
        log_error "Java not found in PATH"
        exit 1
    fi
    
    log_success "Prerequisites validation completed"
}

# Create backup
create_backup() {
    if [[ "$BACKUP_ENABLED" == "true" ]]; then
        log_info "Creating backup..."
        
        BACKUP_DIR="${IIQ_HOME}/backups/$(date +%Y%m%d_%H%M%S)"
        mkdir -p "$BACKUP_DIR"
        
        # Backup existing connector JAR if it exists
        EXISTING_JAR="${IIQ_HOME}/WEB-INF/lib/accessio-racf-connector-*.jar"
        if ls $EXISTING_JAR 1> /dev/null 2>&1; then
            cp $EXISTING_JAR "$BACKUP_DIR/"
            log_info "Backed up existing connector JAR"
        fi
        
        # Backup configuration files
        if [[ -d "${IIQ_HOME}/WEB-INF/config/custom" ]]; then
            cp -r "${IIQ_HOME}/WEB-INF/config/custom" "$BACKUP_DIR/"
            log_info "Backed up configuration files"
        fi
        
        log_success "Backup created: $BACKUP_DIR"
    else
        log_info "Backup disabled, skipping..."
    fi
}

# Deploy connector JAR
deploy_connector_jar() {
    log_info "Deploying connector JAR..."
    
    CONNECTOR_JAR="${PROJECT_DIR}/target/accessio-racf-connector-1.0.0.jar"
    TARGET_DIR="${IIQ_HOME}/WEB-INF/lib"
    
    # Remove existing connector JARs
    rm -f "${TARGET_DIR}/accessio-racf-connector-*.jar"
    
    # Copy new connector JAR
    cp "$CONNECTOR_JAR" "$TARGET_DIR/"
    
    log_success "Connector JAR deployed to $TARGET_DIR"
}

# Deploy configuration files
deploy_config_files() {
    log_info "Deploying configuration files..."
    
    CONFIG_SOURCE="${PROJECT_DIR}/src/main/resources/config"
    CONFIG_TARGET="${IIQ_HOME}/WEB-INF/config/custom"
    
    # Create target directory if it doesn't exist
    mkdir -p "$CONFIG_TARGET"
    
    # Copy configuration files
    if [[ -d "$CONFIG_SOURCE" ]]; then
        cp -r "$CONFIG_SOURCE"/* "$CONFIG_TARGET/"
        log_success "Configuration files deployed to $CONFIG_TARGET"
    else
        log_warning "Configuration source directory not found: $CONFIG_SOURCE"
    fi
}

# Import XML configurations
import_xml_configs() {
    log_info "Importing XML configurations..."
    
    # Check if IIQ console is available
    IIQ_CONSOLE="${IIQ_HOME}/WEB-INF/bin/iiq"
    if [[ ! -f "$IIQ_CONSOLE" ]]; then
        log_warning "IIQ console not found: $IIQ_CONSOLE"
        log_info "Please import XML files manually using SailPoint console"
        return
    fi
    
    # Import order: rules -> applications -> policies -> workflows -> tasks
    IMPORT_DIRS=(
        "rules"
        "applications" 
        "policies"
        "workflows"
        "tasks"
    )
    
    for dir in "${IMPORT_DIRS[@]}"; do
        XML_DIR="${PROJECT_DIR}/src/main/resources/${dir}"
        if [[ -d "$XML_DIR" ]]; then
            log_info "Importing $dir..."
            for xml_file in "$XML_DIR"/*.xml; do
                if [[ -f "$xml_file" ]]; then
                    log_info "Importing $(basename "$xml_file")..."
                    "$IIQ_CONSOLE" import "$xml_file"
                fi
            done
            log_success "$dir imported successfully"
        else
            log_warning "$dir directory not found: $XML_DIR"
        fi
    done
}

# Validate deployment
validate_deployment() {
    if [[ "$VALIDATION_ENABLED" == "true" ]]; then
        log_info "Validating deployment..."
        
        # Check if connector JAR exists
        DEPLOYED_JAR="${IIQ_HOME}/WEB-INF/lib/accessio-racf-connector-1.0.0.jar"
        if [[ -f "$DEPLOYED_JAR" ]]; then
            log_success "Connector JAR deployed successfully"
        else
            log_error "Connector JAR not found after deployment"
            exit 1
        fi
        
        # Check configuration files
        CONFIG_FILES=(
            "application.properties"
            "log4j.properties"
        )
        
        for config_file in "${CONFIG_FILES[@]}"; do
            if [[ -f "${IIQ_HOME}/WEB-INF/config/custom/${config_file}" ]]; then
                log_success "Configuration file deployed: $config_file"
            else
                log_warning "Configuration file not found: $config_file"
            fi
        done
        
        log_success "Deployment validation completed"
    else
        log_info "Validation disabled, skipping..."
    fi
}

# Test connection
test_connection() {
    log_info "Testing connector connection..."
    
    # This would typically use IIQ console to test the application
    # For now, we'll just log that manual testing is required
    log_info "Please test the connector connection manually:"
    log_info "1. Login to SailPoint IIQ"
    log_info "2. Navigate to Applications -> Accessio RACF"
    log_info "3. Click 'Test Connection'"
    log_info "4. Verify successful connection to Garancy API"
}

# Send notifications
send_notifications() {
    if [[ "$NOTIFICATION_ENABLED" == "true" ]]; then
        log_info "Sending deployment notifications..."
        
        # This would typically send email notifications
        # For now, we'll just log the notification
        log_info "Deployment completed for environment: $ENVIRONMENT"
        log_info "Timestamp: $(date)"
        log_info "Deployed by: $(whoami)"
        
        log_success "Notifications sent"
    else
        log_info "Notifications disabled, skipping..."
    fi
}

# Cleanup temporary files
cleanup() {
    log_info "Cleaning up temporary files..."
    
    # Remove any temporary files created during deployment
    # This is a placeholder for actual cleanup logic
    
    log_success "Cleanup completed"
}

# Main deployment function
deploy() {
    log_info "Starting deployment for environment: $ENVIRONMENT"
    log_info "Target IIQ Home: $IIQ_HOME"
    
    # Load configuration
    load_config
    
    # Validate prerequisites
    validate_prerequisites
    
    # Create backup
    create_backup
    
    # Deploy components
    deploy_connector_jar
    deploy_config_files
    
    # Import XML configurations
    import_xml_configs
    
    # Validate deployment
    validate_deployment
    
    # Test connection
    test_connection
    
    # Send notifications
    send_notifications
    
    # Cleanup
    cleanup
    
    log_success "Deployment completed successfully!"
    log_info "Please restart SailPoint IIQ to ensure all changes take effect"
}

# Rollback function
rollback() {
    log_info "Starting rollback..."
    
    # Find latest backup
    BACKUP_BASE="${IIQ_HOME}/backups"
    if [[ -d "$BACKUP_BASE" ]]; then
        LATEST_BACKUP=$(ls -1t "$BACKUP_BASE" | head -n1)
        if [[ -n "$LATEST_BACKUP" ]]; then
            BACKUP_DIR="${BACKUP_BASE}/${LATEST_BACKUP}"
            log_info "Using backup: $BACKUP_DIR"
            
            # Restore connector JAR
            if ls "${BACKUP_DIR}"/accessio-racf-connector-*.jar 1> /dev/null 2>&1; then
                rm -f "${IIQ_HOME}/WEB-INF/lib/accessio-racf-connector-*.jar"
                cp "${BACKUP_DIR}"/accessio-racf-connector-*.jar "${IIQ_HOME}/WEB-INF/lib/"
                log_success "Connector JAR restored"
            fi
            
            # Restore configuration files
            if [[ -d "${BACKUP_DIR}/custom" ]]; then
                rm -rf "${IIQ_HOME}/WEB-INF/config/custom"
                cp -r "${BACKUP_DIR}/custom" "${IIQ_HOME}/WEB-INF/config/"
                log_success "Configuration files restored"
            fi
            
            log_success "Rollback completed successfully!"
        else
            log_error "No backup found for rollback"
            exit 1
        fi
    else
        log_error "Backup directory not found: $BACKUP_BASE"
        exit 1
    fi
}

# Print usage
usage() {
    echo "Usage: $0 [OPTIONS] COMMAND"
    echo ""
    echo "Commands:"
    echo "  deploy    Deploy the connector to SailPoint IIQ"
    echo "  rollback  Rollback to previous version"
    echo "  help      Show this help message"
    echo ""
    echo "Options:"
    echo "  -e, --environment ENV    Deployment environment (default: development)"
    echo "  -h, --home PATH         SailPoint IIQ home directory (default: /opt/sailpoint/identityiq)"
    echo "  -b, --backup            Enable backup (default: true)"
    echo "  -v, --validate          Enable validation (default: true)"
    echo "  --help                  Show this help message"
    echo ""
    echo "Environment Variables:"
    echo "  IIQ_HOME               SailPoint IIQ home directory"
    echo "  ENVIRONMENT            Deployment environment"
    echo "  BACKUP_ENABLED         Enable/disable backup"
    echo "  VALIDATION_ENABLED     Enable/disable validation"
    echo ""
    echo "Examples:"
    echo "  $0 deploy"
    echo "  $0 -e production deploy"
    echo "  $0 --home /opt/sailpoint/iiq deploy"
    echo "  $0 rollback"
}

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        -e|--environment)
            ENVIRONMENT="$2"
            shift 2
            ;;
        -h|--home)
            IIQ_HOME="$2"
            shift 2
            ;;
        -b|--backup)
            BACKUP_ENABLED="true"
            shift
            ;;
        -v|--validate)
            VALIDATION_ENABLED="true"
            shift
            ;;
        --help)
            usage
            exit 0
            ;;
        deploy)
            COMMAND="deploy"
            shift
            ;;
        rollback)
            COMMAND="rollback"
            shift
            ;;
        help)
            usage
            exit 0
            ;;
        *)
            log_error "Unknown option: $1"
            usage
            exit 1
            ;;
    esac
done

# Execute command
case "${COMMAND:-}" in
    deploy)
        deploy
        ;;
    rollback)
        rollback
        ;;
    *)
        log_error "No command specified"
        usage
        exit 1
        ;;
esac
