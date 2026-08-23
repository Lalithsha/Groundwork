# Security model and threat notes

## Controls implemented

- BCrypt cost 12 passwords; public registration cannot choose an elevated system role.
- Signed JWTs validate signature, expiry, issuer, and audience. Refresh tokens are random, stored only as hashes, rotated at refresh, and revocable at logout.
- Workspace membership checks enforce `OWNER > ADMIN > EDITOR > VIEWER` on tenant operations.
- Production refuses disabled security, wildcard CORS, placeholder/short JWT secrets, local embeddings, missing provider keys, and enabled placeholder billing.
- Uploads allow PDF/TXT/Markdown only, validate PDF magic, reject encrypted/oversized/empty documents, cap PDF pages, sanitize filenames, and normalize extracted text.
- Redis-backed rate limits protect auth, upload, and chat paths with an in-process fallback.
- Request IDs and mutation audit events support incident tracing.
- The production frontend sets CSP, clickjacking, MIME-sniffing, referrer, and permissions headers and escapes dynamic markup.
- Retrieved document text is marked untrusted in the grounded prompt and cannot authorize tools or external actions.

## Known residual risks

- Raw extracted text is not application-level encrypted. Use encrypted disks/backups and add field encryption/retention controls for regulated data.
- JWT revocation affects refresh tokens; already-issued access tokens remain valid for their 15-minute lifetime.
- Malware scanning/OCR sandboxing is not included. PDFBox runs inside the constrained application container; high-risk deployments should add an isolated scanning/extraction service.
- AI provider data handling depends on the configured vendor and contract.
- No security certification or penetration-test claim is made.

## Secret handling

Keep `.env` out of version control, prefer an OS/cloud secret manager, rotate provider/JWT/database credentials, and terminate TLS at a hardened reverse proxy. Never enable billing until a provider adapter verifies raw-body webhook signatures, event idempotency, amount/currency, and account ownership.

Report suspected vulnerabilities privately to the repository owner rather than opening a public issue with exploit details.
