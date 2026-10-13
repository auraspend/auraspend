# Runtime evaluation reports

Raw output from `FusedAiEval`, the evaluator that runs the golden corpus through
the **production pipeline** (`SmsAutoClassifier` → `BankMessageParser` →
`AiSignalFusion.fuse` → the unresolved-fields gate) rather than scoring a model
in isolation.

These files are kept as the evidence behind model decisions. They are not
fixtures: the only one a test reads is `minilm-2026-09-29.json`, because
`FusedAiEvalTest` asserts that the shipped runtime destroys zero transactions.
Everything else is a rejected candidate, retained so a future proposal has to
argue against a number rather than a summary line.

| report | model | size | exact | destroyed | verdict |
|---|---|---|---|---|---|
| `smollm2-135m-2026-09-29.json` | SmolLM2-135M | 100 MB | 29.2 % | 43/46 | catastrophic |
| `qwen2.5-0.5b-2026-09-29.json` | Qwen2.5-0.5B | 468 MB | 29.2 % | 8/46 | shipped, net harmful |
| `functiongemma-270m-2026-09-29.json` | FunctionGemma-270M | 241 MB | 24.6 % | 5/46 | best generative, still deletes |
| `minilm-2026-09-29.json` | MiniLM-L6-v2 (encoder) | 22 MB | 16.9 % | **0/46** | **shipped** |
| `needle-v1/v2-2026-09-29.json` | Needle 3 (schema variants) | 34 MB | 0/65 | — | rejected in 0011 |

"destroyed" is the decisive column. A model that calls a real debit "not a
transaction" has its amount and type nulled in `AiSignalFusion` and then dropped
by the pipeline's unresolved-fields gate, so the error *deletes* a transaction
rather than mis-filing it. Standalone and fused scores can point opposite ways
— Qwen and SmolLM2 both score 29.2 % standalone and reach opposite conclusions
— which is why only the fused view is recorded here.

## Reproducing

The encoder candidate was measured host-side:

```bash
python3 -m venv .zcode/enc-venv
.zcode/enc-venv/bin/pip install onnxruntime tokenizers numpy
.zcode/enc-venv/bin/python tools/encoder_eval.py --model <encoder.onnx>
```

The generative candidates need `tools/gguf_eval.py`, which drives
`llama-server` built from `third_party/llama.cpp` — removed in 0013 along with
the llama.cpp runtime, so those reports are now historical rather than
reproducible without reintroducing the dependency. That is a deliberate
trade: the tooling measured four candidates, and keeping a 165 MB vendored C++
tree alive to re-measure a decision that is already made is not worth it.

## `probabilitySemantics` — read this before adding a report

A report may declare how its `isTransactionProbability` field is meant:

- `"p_transaction"` — the runtime's raw probability of the transaction class
  (what `tools/encoder_eval.py` produces). `FusedAiEval` converts it with
  `SmsExtraction.verdictConfidence` on load, the same conversion the runtime
  applies.
- `"verdict_confidence"` (or absent) — already the value
  `SmsExtraction.isTransactionProbability` is defined to carry.

The distinction is not cosmetic. `AiSignalFusion.mayDiscard` reads that field as
confidence in the answer *given*, so replaying raw `p_transaction` on a negative
verdict puts it below 0.5 and makes the 0.9 discard gate unreachable — the
encoder's "0 destroyed" is then produced by a gate that never runs. That is
exactly how the shipped encoder's result was recorded before 2026-10-11, and
`FusedAiEvalTest` now fails if a report is replayed in the wrong protocol.

`minilm-2026-09-29.json` therefore carries `"probabilitySemantics":
"p_transaction"` with its measured values untouched; the other reports have no
probability field at all (a generative runtime has no calibrated head), which
keeps the legacy veto behaviour.
