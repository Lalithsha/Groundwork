# Security model and threat analysis

## Assets and trust boundaries

Protected assets are workspace evidence, source ACL metadata, connector refresh/access tokens, GitHub App credentials, JWT signing material, release approvals, audit history, and provider prompts/responses. Untrusted boundaries include browsers, uploaded files, GitHub/Atlassian webhooks and APIs, repository content, retrieved evidence text, and model output.

## Implemented controls

- BCrypt cost 12 passwords; registration cannot choose a privileged system role.
- Signed JWT access tokens validate issuer, audience, signature, and expiry. Random refresh tokens are hash-stored, rotated on use, and revocable.
- Workspace membership enforces `OWNER > ADMIN > EDITOR > VIEWER`; repositories scope reads and writes by workspace.
- Connector tokens use AES-256-GCM with random nonces and connection-bound authenticated context. Ciphertexts carry a key version; one previous key can remain available during rotation.
- GitHub webhooks require SHA-256 HMAC, a supported event, a delivery ID, and a bounded body before persistence. Delivery and event constraints make replay idempotent.
- Atlassian OAuth state is random, workspace/user-bound, single-use, and expires after ten minutes. Only selected Jira projects and Confluence spaces synchronize.
- Revocation erases connector ciphertext and makes connector-owned artifacts inaccessible while preserving audit evidence.
- Uploads allow PDF/TXT/Markdown only, validate PDF magic, reject encrypted/oversized/empty inputs, cap pages, sanitize names, and normalize extracted text.
- Redis-backed rate limits protect auth, upload, and chat paths with an in-process fallback.
- User/repository/evidence text is untrusted data. It cannot authorize tools; grounded model output must cite retrieved IDs and cannot override deterministic release gates.
- Production fails closed for disabled security, wildcard CORS, weak/placeholders secrets, local embeddings, incomplete enabled integrations, and billing.
- Browser headers cover CSP, frame denial, MIME sniffing, referrer, and permissions policy. Dynamic UI content is rendered through React.
- Mutation audit events, finding feedback, request IDs, metrics, and traces support incident investigation.

## Threat matrix

| Threat | Control | Residual risk / next operational proof |
|---|---|---|
| Cross-tenant IDOR | Membership checks and workspace-scoped SQL | Run an independent adversarial tenant-isolation review before enterprise claims. |
| Webhook spoof/replay | Raw-body HMAC, unique delivery ID, digest conflict detection | Rotate webhook secret and exercise replay in staging. |
| OAuth token theft | AES-GCM ciphertext, secrets outside Git, response DTOs omit credentials | Infrastructure/database administrators remain privileged; use managed KMS/HSM for regulated deployments. |
| Prompt injection | Untrusted-evidence delimiters, structured output, citation validation, no AI gates/tools | Provider output can still be misleading; keep human review and monitor feedback. |
| Source permission drift | Selected scope metadata, reconciliation, revocation/inaccessible state | Near-real-time ACL webhooks are not implemented; define sync interval appropriate to sensitivity. |
| Malicious PDF | type/size/page/encryption limits and non-root container | No malware scanner or isolated parser service; add both for hostile public uploads. |
| Tampered release record | Canonical SHA-256 evidence digests and verification endpoint | Not externally signed/notarized; an administrator with DB access can alter history. |
| Denial of service | body limits, rate limits, worker queues, bounded retries | Load thresholds must be measured on the target environment. |
| Dependency compromise | locked npm tree, Maven BOM, Dependabot, npm audit, CodeQL, Trivy, secret scan | CI findings require triage; no independent supply-chain attestation yet. |

## Connector key rotation

1. Generate a new random secret of at least 48 characters.
2. Move the active key/version into `CONNECTOR_PREVIOUS_CREDENTIAL_KEY(_VERSION)`.
3. Set the new `CONNECTOR_CREDENTIAL_KEY` and increment `CONNECTOR_CREDENTIAL_KEY_VERSION`.
4. Deploy. New/updated credentials use the new version; old versions remain decryptable.
5. Reauthorize or refresh every active connector so its ciphertext is rewritten.
6. Verify no active row uses the previous `credential_key_version`, remove the previous key, and deploy again.

Never reuse a version for different key material. Losing both configured keys makes existing credentials unrecoverable.

## Incident response

Revoke the affected connection, rotate provider and application secrets, retain database/audit snapshots, search by request/trace/delivery IDs, and determine exposed workspaces. Do not delete evidence required for investigation. Notify affected operators according to the deployment's legal and contractual obligations.

## Honest limitations

Raw evidence and document text are not application-level encrypted. Use encrypted storage/backups and a retention/deletion policy. No penetration test, SOC 2, compliance certification, production pilot, or security guarantee is claimed. Report vulnerabilities privately to the repository owner.
