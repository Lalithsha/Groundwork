# Product validation and external evidence gates

Software completion is not customer validation. This file distinguishes what the repository proves from what only real users and a deployed environment can prove.

## Hypothesis

Small-to-medium engineering teams shipping frequent human- and AI-authored changes lose review time and release confidence because requirements, design decisions, ownership, tests, API impact, incidents, and rollback evidence are distributed across tools. Groundwork should reduce context gathering and prevent evidence gaps without becoming a noisy AI reviewer.

## Ideal design partners

Recruit engineering leads, senior developers, release owners, or platform engineers from teams with 5–50 developers, GitHub pull requests, Jira or an equivalent issue system, and recurring API/database changes. Prefer teams already feeling review-context or audit pain; avoid recruiting only friends who will give hypothetical praise.

## Interview protocol

For at least ten interviews, ask for a recent real change and walk through it:

- Where was its reason, decision, ownership, test proof, rollout/rollback plan, and incident context?
- Which facts were missing or stale, and who had to be interrupted?
- What escaped review or delayed release?
- Which checks would be useful, and which would be noise?
- What repository/integration permission would block a trial?
- Would the team connect one non-sensitive repository for a two-week pilot?

Record role, team shape, concrete example, current time/cost, repeated pain, security objections, desired workflow, and commitment. Do not treat feature compliments as validation.

## Pilot design

Run three two-week pilots on separate workspaces/repositories. Start advisory-only. For each repository:

1. capture a one-week baseline of review context-gathering time and missing-evidence incidents;
2. configure only high-confidence deterministic policies;
3. review false positives/negatives twice weekly with users;
4. measure check latency, evidence completeness, finding acceptance/dismissal, repeated use, and time saved;
5. conduct an exit interview and ask for continued voluntary use.

## Success criteria

- at least 3 design partners connect a repository;
- at least 2 teams use Groundwork repeatedly after onboarding;
- at least 60% of surfaced deterministic gaps are accepted/actioned during the learning pilot, then tighten toward 80% precision;
- median context-gathering time improves by at least 30% against each team's own baseline;
- native check p95 is under the agreed workflow tolerance;
- no cross-tenant/source-ACL incident occurs;
- at least one team requests continued use or a concrete next integration.

These are hypotheses/targets, not current results.

## Evidence log template

| Date | Participant/team | Real change observed | Pain frequency/impact | Commitment | Security concern | Follow-up |
|---|---|---|---|---|---|---|
| _uncollected_ | | | | | | |

## Experiment register

| Hypothesis | Metric | Baseline | Target | Result | Decision |
|---|---|---:|---:|---:|---|
| Evidence aggregation reduces context gathering | minutes/change | unmeasured | -30% | unmeasured | pending pilot |
| Deterministic findings are useful | accepted / reviewed | unmeasured | >=60% initial | unmeasured | pending pilot |
| Users return without prompting | active weeks/team | 0 | >=2 | 0 | pending pilot |
| Permissions are acceptable | connected / invited | 0 | >=3 | 0 | pending recruitment |

## External gates that cannot be implemented in code

- completed interviews with consented notes;
- live GitHub/Atlassian credentials and permission review;
- staging/production infrastructure and TLS/domain ownership;
- repeated pilots and outcome measurements;
- independent penetration/tenant-isolation review;
- target-environment load and chaos reports;
- backup restore/replay exercise;
- demo video and public case study using non-sensitive data.

Until those exist, keep resume bullets factual and implementation-based. Replace every numeric placeholder only with a committed report produced from a named environment and revision.
