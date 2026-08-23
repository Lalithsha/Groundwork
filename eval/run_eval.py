#!/usr/bin/env python3
"""Versioned, reproducible Groundwork retrieval smoke evaluation."""

import argparse
import json
import time
from datetime import datetime, timezone
from pathlib import Path

import requests

ROOT = Path(__file__).resolve().parent


def request(session, method, url, **kwargs):
    response = session.request(method, url, timeout=120, **kwargs)
    response.raise_for_status()
    return response.json() if response.content else None


def upload_fixture(session, api_url, workspace_id, fixture):
    with fixture.open("rb") as handle:
        result = request(session, "POST", f"{api_url}/api/documents/upload",
                         params={"workspaceId": workspace_id}, files={"file": (fixture.name, handle, "text/markdown")})
    job_id = result.get("jobId")
    if not job_id:
        return result
    deadline = time.monotonic() + 180
    while time.monotonic() < deadline:
        job = request(session, "GET", f"{api_url}/api/documents/jobs/{job_id}")
        if job["status"] == "COMPLETED":
            return result
        if job["status"] in {"FAILED", "CANCELLED"}:
            raise RuntimeError(job.get("errorMessage") or f"Ingestion {job['status'].lower()}")
        time.sleep(1)
    raise TimeoutError("Fixture ingestion did not complete within 180 seconds")


def evaluate_mode(session, api_url, workspace_id, mode, cases):
    rows = []
    for case in cases:
        started = time.monotonic()
        result = request(session, "POST", f"{api_url}/api/chat", json={
            "question": case["question"], "retrievalMode": mode, "workspaceId": workspace_id
        })
        answer = result.get("answer", "")
        matched = [term for term in case["expectedTerms"] if term.casefold() in answer.casefold()]
        rows.append({
            "id": case["id"],
            "latencyMs": round((time.monotonic() - started) * 1000),
            "expectedTermRecall": len(matched) / len(case["expectedTerms"]),
            "citationCount": len(result.get("citations", [])),
            "evidenceStatus": result.get("evidenceStatus"),
            "matchedTerms": matched,
            "answer": answer,
        })
    return {
        "mode": mode,
        "caseCount": len(rows),
        "meanExpectedTermRecall": sum(row["expectedTermRecall"] for row in rows) / len(rows),
        "citationCoverage": sum(bool(row["citationCount"]) for row in rows) / len(rows),
        "meanLatencyMs": round(sum(row["latencyMs"] for row in rows) / len(rows)),
        "cases": rows,
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--api-url", default="http://localhost:8080")
    parser.add_argument("--workspace-id", required=True)
    parser.add_argument("--token", help="JWT access token when security is enabled")
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()

    dataset = json.loads((ROOT / "eval_set.json").read_text())
    session = requests.Session()
    if args.token:
        session.headers["Authorization"] = f"Bearer {args.token}"
    upload_fixture(session, args.api_url.rstrip("/"), args.workspace_id, ROOT / dataset["fixture"])
    report = {
        "datasetVersion": dataset["datasetVersion"],
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "apiUrl": args.api_url,
        "workspaceId": args.workspace_id,
        "results": [evaluate_mode(session, args.api_url.rstrip("/"), args.workspace_id, mode, dataset["cases"])
                    for mode in ("naive", "hybrid_rerank")],
    }
    output = args.output or ROOT / "reports" / f"eval-{datetime.now(timezone.utc):%Y%m%dT%H%M%SZ}.json"
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, indent=2) + "\n")
    print(output)


if __name__ == "__main__":
    main()
