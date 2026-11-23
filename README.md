# appsec-stats

![workflow](https://github.com/navikt/appsec-stats/actions/workflows/main.yaml/badge.svg)

## Overview
Application that collects security and deployment statistics for Nav's GitHub repositories, enriches the data with team and product area information, and stores it in BigQuery for analysis.



Runs as a [Naisjob](https://doc.nais.io/explanation/workloads/job/) on a schedule set in `.nais/nais.yaml`.

## Usage

The application collects repository statistics:
1. `./gradlew installDist` creates the distribution in `build/install/app/`
2. You can run either with the shell script `bin/app` or directly with `java -cp`
3. Arguments work the same way with both approaches

```bash
java -cp "build/install/app/lib/*" no.nav.security.MainKt
```

## Required Environment Variables
* `GCP_TEAM_PROJECT_ID` - GCP project ID for BigQuery operations
* `NAIS_ANALYSE_PROJECT_ID` - Project ID containing deployment data
* `GITHUB_APP_ID` - Github App ID
* `GITHUB_APP_PRIVATE_KEY` - Private key generated for the GitHub App
* `GITHUB_APP_INSTALLATION_ID` - Github App installation ID, found after installing the app in organization
* `NAIS_API_TOKEN` - Token for NAIS API access

## Data Flow
1. **GitHub Collection**: Fetches all repositories from the `navikt` organization using GraphQL API, including repository metadata, vulnerability alerts, and admin team permissions
2. **NAIS Team Data**: Retrieves repository ownership mappings for each team
3. **Ownership Mapping**: Links repositories to teams by combining NAIS team repository lists with GitHub admin team permissions
4. **Product Area Enrichment**: Queries Teamcatalog API to map teams to their product areas for organizational context
5. **Deployment Integration**: 
   - Fetches deployment history from BigQuery `dev_rapid` tables
   - Queries NAIS API for current deployment status
   - Matches deployments to repositories by application name
6. **BigQuery Storage**: Inserts processed data into the `appsec.github_repo_stats` table

## Data Model
### Repository Stats (`appsec.github_repo_stats`)
* **Repository information** - Name, archive status, last push date
* **Vulnerability data** - Vulnerability count from GitHub, alerts status
* **Ownership data** - Team ownership, product area association
* **Deployment status** - Deployment dates, target environment

## Data Sources
* **GitHub GraphQL API** - Repository information, vulnerability alerts, team permissions, and archival status
* **NAIS API** - Repository ownership and deployment status
* **Teamcatalog** - Product area information for teams
* **BigQuery** - Deployment information from existing tables

## ⚖️ License
[MIT](LICENSE)

## 👥 Contact
Maintained by [@appsec](https://github.com/orgs/navikt/teams/appsec)

Questions? Create an [issue](https://github.com/navikt/appsec-stats/issues) or reach us on Slack at [#appsec](https://nav-it.slack.com/archives/C06P91VN27M) if you work at Nav.
