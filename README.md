# appsec-stats

![workflow](https://github.com/navikt/appsec-stats/actions/workflows/main.yaml/badge.svg)

## Overview
This application fetches and processes data from various sources to generate statistics and insights. The data is then stored in BigQuery for further analysis. Uses this data to create a ["Dataprodukt"](https://docs.knada.io/dataprodukter/dataprodukt/).


Runs as a [Naisjob](https://doc.nais.io/explanation/workloads/job/?h=job) on a schedule set in `.nais/nais.yaml`.

Ensure that the required environment variables are set (found in nais console) :
* GCP_TEAM_PROJECT_ID
* NAIS_ANALYSE_PROJECT_ID
* GITHUB_TOKEN
* NAIS_API_TOKEN

## Data Sources


### GitHub
- Endpoint: https://api.github.com/graphql
- Data Fetched: Repository information including repository name, vulnerability alerts, archival status, and last push date.

### NAIS API
- Endpoint: https://console.nav.cloud.nais.io/graphql
- Data Fetched: Team information including team slug, vulnerability summary, inventory counts, and repository ownership information.

### Teamkatalogen
- Endpoint: Teamcatalog API (Service discovery)
- Data Fetched: Product area information for teams.

### BigQuery
- Endpoint: BigQuery API
- Data Fetched: Deployment information.


## ⚖️ License
[MIT](LICENSE).

## 👥 Contact

This project is maintained by [@appsec](https://github.com/orgs/navikt/teams/appsec).

Questions and/or feature requests? Please create an [issue](https://github.com/navikt/appsec-stats/issues).

If you work in [@navikt](https://github.com/navikt) you can reach us at the Slack channel [#appsec](https://nav-it.slack.com/archives/C06P91VN27M).


