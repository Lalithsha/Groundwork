# Evaluation status

No verified quality or latency benchmark is currently claimed.

Run `run_eval.py` against the release configuration. The runner records the dataset version, timestamp, workspace, per-case answers, expected-term recall, citation coverage, and observed request latency for both retrieval modes. Generated JSON belongs under `eval/reports/` and should be reviewed before it is used in release claims.

Expected-term recall is a deterministic smoke metric, not a substitute for human review or RAGAS-style faithfulness evaluation. Provider model/version, embedding model, reranker state, hardware, corpus, and commit SHA should accompany any published benchmark.
