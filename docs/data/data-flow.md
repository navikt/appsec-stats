# Data Flow Documentation

## Overview Diagram

```mermaid
flowchart TD
    subgraph "Data Sources"
        GH[GitHub API] --> |Repository data| APP
        NAIS[NAIS API] --> |Team data & SLSA coverage| APP
        TC[Teamcatalog API] --> |Product area data| APP
        BQ[BigQuery] --> |Deployment data| APP
    end

    subgraph "Application Processing"
        APP[appsec-stats]
    end

    subgraph "Data Storage"
        APP --> |Repository statistics| REPOS[BigQuery: github_repo_stats]
        APP --> |Team statistics| TEAMS[BigQuery: github_team_stats]
    end
```

## Data Field Mapping

This document shows the exact fields collected from each data source and how they map to our final data models.

### Source: GitHub API

```mermaid
classDiagram
    class GitHubRepository {
        name: String
        isArchived: Boolean
        pushedAt: DateTime
        hasVulnerabilityAlertsEnabled: Boolean
        vulnerabilityAlerts: Int
    }
```

### Source: NAIS API

```mermaid
classDiagram
    class NaisTeam {
        naisTeam: String
        slsaCoverage: Int
        hasDeployedResources: Boolean
        hasGithubRepositories: Boolean
        repositories: List~String~
    }
```

### Source: Teamcatalog API

```mermaid
classDiagram
    class ProductArea {
        id: String
        name: String
    }
    
    class TeamCatalogTeam {
        productAreaId: String
        name: String
        naisTeams: List~String~
    }
```

### Source: BigQuery (Existing Data)

```mermaid
classDiagram
    class Deployment {
        platform: String
        cluster: String
        namespace: String
        application: String
        latestDeploy: Instant
    }
```

### Target Model: Repository Statistics

```mermaid
classDiagram
    class BQRepoStat {
        owners: List~String~ ← From NAIS API (team repositories)
        lastPush: String ← From GitHub (pushedAt)
        repositoryName: String ← From GitHub (name)
        vulnerabilityAlertsEnabled: Boolean ← From GitHub (hasVulnerabilityAlertsEnabled)
        vulnerabilityCount: Int ← From GitHub (vulnerabilityAlerts)
        isArchived: Boolean ← From GitHub (isArchived)
        productArea: String ← From Teamcatalog (ProductArea.name)
        isDeployed: Boolean ← Derived from BigQuery deployment data
        deployDate: String ← From BigQuery (latestDeploy)
        deployedTo: String ← From BigQuery (cluster)
    }
```

### Target Model: Team Statistics

```mermaid
classDiagram
    class BQNaisTeam {
        naisTeam: String ← From NAIS API (naisTeam)
        slsaCoverage: Int ← From NAIS API (slsaCoverage)
        hasDeployedResources: Boolean ← From NAIS API (hasDeployedResources)
        hasGithubRepositories: Boolean ← From NAIS API (hasGithubRepositories)
    }
```

## Data Processing Flow

1. **Collection Phase**:
   - Fetch repository data from GitHub GraphQL API
   - Fetch team data from NAIS API
   - Identify repository owners by matching repositories to teams

2. **Enrichment Phase**:
   - Query Teamcatalog to get product area information for teams
   - Fetch deployment data from BigQuery
   - Match deployments to repositories based on application name

3. **Transformation Phase**:
   - Convert GitHub repositories to `BQRepoStat` objects
   - Add team ownership information to repositories
   - Add product area information to repositories with owners
   - Add deployment information to repositories that have been deployed
   - Convert NAIS teams to `BQNaisTeam` objects

4. **Storage Phase**:
   - Insert `BQRepoStat` objects into BigQuery `github_repo_stats` table
   - Insert `BQNaisTeam` objects into BigQuery `github_team_stats` table
