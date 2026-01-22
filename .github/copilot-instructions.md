# Copilot Instructions

## Tech Stack
- Kotlin
- Ktor (HTTP client, GraphQL)
- Kafka (message producer)
- BigQuery (data warehouse)
- JUnit 5 (testing)
- kotlinx.serialization (JSON)

## Code Principles
- **Zero unnecessary code**: No comments, no documentation, no unused code
- **Self-documenting**: Clear names, obvious logic
- **Test everything**: Unit tests for logic, integration tests for data flow
- **Never assume**: Always verify with user before making changes not explicitly requested
- **Protect tests**: If test breaks, verify change was intentional before modifying test

## Clean Code & Best Practices
- Small, focused functions with single responsibility
- Immutable data by default (val over var, data classes)
- Fail fast with meaningful error messages
- No magic numbers or strings (use constants or enums)
- Prefer functional style (map, filter, fold) over imperative loops
- No null checks (use nullable types explicitly or default values)
- DRY: Extract duplicated logic immediately

## Testing Strategy
- **Unit tests**: Test individual components and data transformations
- **Integration tests**: Mock external APIs (GitHub, NAIS), verify end-to-end data format in Kafka/BigQuery
- **Purpose**: Catch breaking changes in data pipelines before production

## Key Patterns
- Use data classes for DTOs
- kotlinx.serialization for JSON
- Suspend functions for async operations
- Result types for error handling
- Extension functions for utilities

## Test Requirements
When modifying data flow:
1. Update unit tests for changed mappings
2. Update integration tests with realistic API responses
3. Verify Kafka message format matches expectations
4. Verify BigQuery payload format matches schema

## Repository Name Handling
- **BigQuery**: Store `name` only (e.g., "appsec-stats")
- **Kafka**: Store `nameWithOwner` (e.g., "navikt/appsec-stats")
