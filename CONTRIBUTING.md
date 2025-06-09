# Contributing to SailPoint IdentityIQ Accessio RACF Connector

Thank you for your interest in contributing to the SailPoint IdentityIQ Accessio RACF Connector! This document provides guidelines for contributing to this project.

## Code of Conduct

This project adheres to a code of conduct. By participating, you are expected to uphold this code.

## How to Contribute

### Reporting Issues

Before creating bug reports, please check the existing issues as you might find out that you don't need to create one. When you are creating a bug report, please include as many details as possible:

- Use a clear and descriptive title
- Describe the exact steps which reproduce the problem
- Provide specific examples to demonstrate the steps
- Describe the behavior you observed after following the steps
- Explain which behavior you expected to see instead and why
- Include screenshots and animated GIFs if possible

### Suggesting Enhancements

Enhancement suggestions are tracked as GitHub issues. When creating an enhancement suggestion, please include:

- Use a clear and descriptive title
- Provide a step-by-step description of the suggested enhancement
- Provide specific examples to demonstrate the steps
- Describe the current behavior and explain which behavior you expected to see instead
- Explain why this enhancement would be useful

### Pull Requests

1. Fork the repo and create your branch from `main`
2. If you've added code that should be tested, add tests
3. If you've changed APIs, update the documentation
4. Ensure the test suite passes
5. Make sure your code lints
6. Issue that pull request!

## Development Process

### Setting Up Development Environment

1. Clone the repository
2. Install Java 11 or higher
3. Install Maven 3.6+
4. Run `mvn clean install` to build the project
5. Run `mvn test` to execute tests

### Code Style

- Follow Java coding conventions
- Use meaningful variable and method names
- Add appropriate comments and documentation
- Ensure proper error handling
- Follow security best practices

### Testing

- Write unit tests for new functionality
- Ensure all tests pass before submitting PR
- Maintain test coverage above 90%
- Include integration tests for API interactions

### Security Considerations

- Never commit sensitive information (passwords, API keys, etc.)
- Follow secure coding practices
- Validate all inputs
- Use parameterized queries to prevent SQL injection
- Implement proper authentication and authorization

### Documentation

- Update README.md for significant changes
- Add inline code documentation
- Update configuration guides if needed
- Include examples for new features

## Project Structure

```
src/
├── main/
│   ├── java/com/sailpoint/connector/accessio/racf/
│   │   ├── AccessioRACFConnector.java
│   │   ├── GarancyAPIClient.java
│   │   ├── RACFUserManager.java
│   │   ├── RACFRoleManager.java
│   │   ├── ApprovalWorkflowHandler.java
│   │   ├── RecertificationManager.java
│   │   └── security/InputValidator.java
│   └── resources/
│       ├── applications/
│       ├── workflows/
│       ├── policies/
│       ├── rules/
│       ├── tasks/
│       └── config/
└── test/
    └── java/com/sailpoint/connector/accessio/racf/
```

## Release Process

1. Update version in `pom.xml`
2. Update `CHANGELOG.md`
3. Create release branch
4. Run full test suite
5. Create pull request to main
6. After merge, create GitHub release
7. Deploy to production environment

## Questions?

Feel free to contact the maintainers if you have any questions or need clarification on any aspect of contributing to this project.
