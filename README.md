# appsec-stats

![workflow](https://github.com/navikt/appsec-stats/actions/workflows/main.yaml/badge.svg)

## Overview
Application that collects security and deployment statistics for NAV's GitHub repositories, enriches the data with team and product area information, and stores it in BigQuery for analysis.

Runs as a [Naisjob](https://doc.nais.io/explanation/workloads/job/) on a schedule set in `.nais/nais.yaml`.

## Required Environment Variables
* `GCP_TEAM_PROJECT_ID` - GCP project ID for BigQuery operations
* `NAIS_ANALYSE_PROJECT_ID` - Project ID containing deployment data
* `GITHUB_TOKEN` - Token for GitHub API access
* `NAIS_API_TOKEN` - Token for NAIS API access

## Data Flow
1. **Collection**: Fetches data from GitHub, NAIS API, and Teamcatalog
2. **Enrichment**: Links repositories to teams and product areas, adds deployment information
3. **Storage**: Stores processed data in BigQuery tables (`appsec.github_repo_stats` and `appsec.github_team_stats`)

## Data Model
The application maintains two main data collections:

### Repository Stats (`appsec.github_repo_stats`)
* **Repository information** - Name, archive status, last push date
* **Vulnerability data** - Vulnerability count from GitHub, alerts status
* **Ownership data** - Team ownership, product area association
* **Deployment status** - Deployment dates, target environment

### Team Stats (`appsec.github_team_stats`)
* **Team information** - NAIS team identifier
* **Security metrics** - SLSA coverage percentage from NAIS API
* **Resource status** - Deployment status, GitHub repository ownership

## Data Sources
* **GitHub** - Repository information, vulnerability alerts, and archival status
* **NAIS API** - Team data, SLSA coverage, and repository ownership
* **Teamcatalog** - Product area information for teams
* **BigQuery** - Deployment information from existing tables

## ⚖️ License
[MIT](LICENSE)

## 👥 Contact
Maintained by [@appsec](https://github.com/orgs/navikt/teams/appsec)

Questions? Create an [issue](https://github.com/navikt/appsec-stats/issues) or reach us on Slack at [#appsec](https://nav-it.slack.com/archives/C06P91VN27M) if you work at NAV.
