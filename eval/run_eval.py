import json
import requests
from ragas import evaluate
from ragas.metrics import faithfulness, answer_relevancy, context_precision, context_recall
from datasets import Dataset

API_URL = "http://localhost:8080/api/chat"

def load_eval_set():
    with open("eval_set.json", "r") as f:
        return json.load(f)

def run_against_mode(mode: str, eval_set: list):
    questions = []
    answers = []
    contexts = []
    ground_truths = []

    for item in eval_set:
        resp = requests.post(API_URL, json={
            "question": item["question"],
            "retrievalMode": mode
        }).json()

        questions.append(item["question"])
        answers.append(resp.get("answer", ""))
        retrieved_chunks = [c.get("content", "") for c in resp.get("retrievedContexts", [])]
        contexts.append(retrieved_chunks)
        ground_truths.append(item["ground_truth_answer"])

    dataset = Dataset.from_dict({
        "question": questions,
        "answer": answers,
        "contexts": contexts,
        "ground_truth": ground_truths
    })

    return evaluate(dataset, metrics=[faithfulness, answer_relevancy, context_precision, context_recall])

if __name__ == "__main__":
    eval_set = load_eval_set()
    print("Running Naive Vector Search Evaluation...")
    naive_results = run_against_mode("naive", eval_set)
    print("Naive Results:", naive_results)

    print("\nRunning Hybrid RRF + Rerank Evaluation...")
    hybrid_results = run_against_mode("hybrid_rerank", eval_set)
    print("Hybrid Results:", hybrid_results)
