# Change analysis benchmark

Groundwork's deterministic change-risk analysis is protected by a versioned, 30-scenario benchmark at
`src/test/resources/evidence/change-scenarios.json`. The cases cover intent, test and check evidence,
database rollback plans, CODEOWNERS approval, OpenAPI compatibility, changelog evidence, missing provider
data, frontend/backend/configuration/infrastructure changes, and compound release risk.

Run only the benchmark:

```bash
./mvnw -Dtest=ChangeScenarioBenchmarkTest test
```

The benchmark passes only when every scenario produces exactly the expected missing and unknown evidence
categories. It is intentionally deterministic and runs without network or model access. Grounded AI output
has a separate citation-validation boundary and cannot override these findings.
