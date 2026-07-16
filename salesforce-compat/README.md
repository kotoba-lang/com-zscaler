# Salesforce Clean Room Actor

This actor provides a clean-room, API-compatible implementation of the Salesforce CRM platform.

## Architecture
- **State:** Backed by Datomic for immutable, time-travel-capable record keeping.
- **Schema:** Defined in `schema/sforce.kotoba` (translates Salesforce standard objects like Account, Contact, Opportunity).
- **Execution:** Runs in `Py Kotodama WASM`, intercepting inbound REST requests and SOQL queries.
