# appsec-stats

![workflow](https://github.com/navikt/appsec-stats/actions/workflows/main.yaml/badge.svg)

## Overview
Application that collects security and deployment statistics for Nav's GitHub repositories, enriches the data with team and product area information, and stores it in BigQuery for analysis.

Runs as a [Naisjob](https://doc.nais.io/explanation/workloads/job/) on a schedule set in `.nais/nais.yaml`.

## Usage

### Standard Operation
By default, the application collects repository and team statistics:
```bash
java -jar app.jar
```

### Vulnerability Data Collection
To collect detailed vulnerability data from both NAIS API and GitHub, use the `--fetch-vulnerabilities` argument:
```bash
java -jar app.jar --fetch-vulnerabilities
```

**Note**: When run with `--fetch-vulnerabilities`, the application will:
- Collect vulnerability data from both NAIS API and GitHub GraphQL API
- Store the data in the `appsec.github_repo_vulnerability_stats` table
- Exit after vulnerability collection (skips standard repository/team stats collection)

This mode is designed for dedicated vulnerability scanning runs separate from regular statistics collection.

## Required Environment Variables
* `GCP_TEAM_PROJECT_ID` - GCP project ID for BigQuery operations
* `NAIS_ANALYSE_PROJECT_ID` - Project ID containing deployment data
* `GITHUB_APP_ID` - Github App ID
* `GITHUB_APP_PRIVATE_KEY` - Private key generated for the GitHub App
* `GITHUB_APP_INSTALLATION_ID` - Github App installation ID, found after installing the app in organization
* `NAIS_API_TOKEN` - Token for NAIS API access

## Data Flow
1. **Collection**: Fetches data from GitHub, NAIS API, and Teamcatalog
2. **Enrichment**: Links repositories to teams and product areas, adds deployment information
3. **Storage**: Stores processed data in BigQuery tables

### Standard Mode
- Collects repository statistics, team information, and deployment data
- Stores in `appsec.github_repo_stats` and `appsec.github_team_stats` tables

### Vulnerability Mode (`--fetch-vulnerabilities`)
- Collects detailed vulnerability data from NAIS workload images and GitHub security alerts
- Combines data from both sources with proper source attribution
- Stores in `appsec.github_repo_vulnerability_stats` table
- Handles pagination to ensure complete data collection (critical for repositories with >100 vulnerabilities)

## Data Model
The application maintains three main data collections:

### Repository Stats (`appsec.github_repo_stats`)
* **Repository information** - Name, archive status, last push date
* **Vulnerability data** - Vulnerability count from GitHub, alerts status
* **Ownership data** - Team ownership, product area association
* **Deployment status** - Deployment dates, target environment

### Team Stats (`appsec.github_team_stats`)
* **Team information** - NAIS team identifier
* **Security metrics** - SLSA coverage percentage from NAIS API
* **Resource status** - Deployment status, GitHub repository ownership

### Vulnerability Stats (`appsec.github_repo_vulnerability_stats`) *New*
* **Source attribution** - NAIS or GitHub origin
* **Repository mapping** - Links vulnerabilities to specific repositories
* **Vulnerability details** - Multiple identifiers (CVE, vendor IDs), severity levels, suppression status
* **Temporal data** - Collection timestamp for trend analysis

## Data Sources
* **GitHub GraphQL API** - Repository information, vulnerability alerts, team permissions, and archival status
* **NAIS API** - Team data, SLSA coverage, repository ownership, and container vulnerability scans
* **Teamcatalog** - Product area information for teams
* **BigQuery** - Deployment information from existing tables

## ⚖️ License
[MIT](LICENSE)

## 👥 Contact
Maintained by [@appsec](https://github.com/orgs/navikt/teams/appsec)

Questions? Create an [issue](https://github.com/navikt/appsec-stats/issues) or reach us on Slack at [#appsec](https://nav-it.slack.com/archives/C06P91VN27M) if you work at Nav.
