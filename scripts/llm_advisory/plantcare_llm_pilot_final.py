#!/usr/bin/env python3
"""PlantCareAI tomato pilot: offline, read-only LLM context + deterministic advisory.

This is the LLM-side Python reference, not Android application code. It does NOT
load a .pte, run a vision model, retrain, or change the provided SQLite database.

Clinical/agronomic caveat: 'extent' is FARMER-REPORTED symptom spread, never a
model-derived severity measurement. English/Hindi records in Stage 1B have not
been professionally reviewed; regional-language records require documented review.

Examples:
  python plantcare_llm_pilot_final.py --db 'plantcare_knowledge_stage1b(1).db' --self-test
  python plantcare_llm_pilot_final.py --db 'plantcare_knowledge_stage1b(1).db' --task disease --class-id 30 --lang hi --extent high --region IN-MH --question 'अब क्या करूँ?'
"""
from __future__ import annotations

import argparse
import csv
import json
import re
import sqlite3
from pathlib import Path
from typing import Any, Callable

MODEL_NAME = "Llama-3.2-1B-Instruct-XNNPACK-INT4"
MAX_TOTAL_TOKENS = 512
MAX_NEW_TOKENS = 32
MAX_ACTIONS_TO_LLM = 3
MAX_QUESTION_CHARS = 240
PILOT_DISEASE_CLASSES = {28, 29, 30, 37}
PILOT_PEST_CLASS = 71  # Trialeurodes vaporariorum; requires export mapping validation
PILOT_CROP_ID = "tomato"
PEST_MODEL_ID = "plantcare.pest.yolo26n.ip102"
SUPPORTED_LANGS = ("en", "hi", "mr", "bn", "ta", "te", "kn", "gu")
DRAFT_IN_DB = {"hi": "draft_hi", "en": "editorial_en"}
LANGS_FOR_LLM_SELECTION = {"en", "hi"}
REPORTED_EXTENT = ("unknown", "low", "moderate", "high")
REGION_PATTERN = re.compile(r"^IN(?:-[A-Z0-9]+)*$")
SELECTOR_SYSTEM = (
    "You are PlantCareAI's action selector. From the supplied IDs select only the ones "
    "relevant to the question. Reply EXACTLY IDS:ID1,ID2 or IDS:NONE. "
    "Never write treatment, dosage, translations or any other text. "
    "Do not select an ID absent from the list."
)


def open_database(path: str) -> sqlite3.Connection:
    """Open as read-only, never mutate the supplied agricultural knowledge base."""
    uri = Path(path).resolve().as_uri() + "?mode=ro"
    db = sqlite3.connect(uri, uri=True)
    db.row_factory = sqlite3.Row
    db.execute("PRAGMA query_only=ON")
    return db


def get_fact(db: sqlite3.Connection, fact_id: str | None, lang: str) -> dict:
    if not fact_id:
        return {"text": None, "language_review": "missing"}
    r = db.execute(
        "SELECT text, language_review FROM fact_text WHERE fact_id=? AND lang=?",
        (fact_id, lang),
    ).fetchone()
    return dict(r) if r else {"text": None, "language_review": "missing"}


def load_reviewed_locales(csv_path: str | None) -> dict[tuple[str, str], dict]:
    """Only imports non-empty translations with documented REVIEWED status.

    CSV columns: fact_id,lang,translated_text,review_status,reviewer,review_date,
                 en_reference,hi_reference.
    Staged/unreviewed text is deliberately ignored so it cannot enter advisory.
    """
    if not csv_path:
        return {}
    out: dict[tuple[str, str], dict] = {}
    with open(csv_path, "r", encoding="utf-8-sig", newline="") as f:
        for line, row in enumerate(csv.DictReader(f), 2):
            fact_id = (row.get("fact_id") or "").strip()
            lang = (row.get("lang") or "").strip()
            content = (row.get("translated_text") or "").strip()
            status = (row.get("review_status") or "").strip().lower()
            reviewer = (row.get("reviewer") or "").strip()
            reviewed_on = (row.get("review_date") or "").strip()
            if lang not in SUPPORTED_LANGS or not fact_id:
                raise ValueError(f"Invalid localized record at CSV row {line}")
            if not content or status != "reviewed" or not reviewer or not reviewed_on:
                continue
            key = (fact_id, lang)
            if key in out:
                raise ValueError(f"Duplicate reviewed translation: {key}")
            out[key] = {"text": content, "language_review": "reviewed_external",
                        "reviewer": reviewer, "review_date": reviewed_on}
    return out


def localize(db: sqlite3.Connection, fact_id: str | None, lang: str,
             locales: dict[tuple[str, str], dict]) -> dict:
    if not fact_id:
        return {"text": None, "language_review": "missing"}
    if lang in ("en", "hi"):
        return get_fact(db, fact_id, lang)
    return locales.get((fact_id, lang), {"text": None, "language_review": "missing"})


def geography_chain(db: sqlite3.Connection, region: str) -> tuple[list[str], list[str]]:
    """Returns DB-known chain from most specific to BASE; never fabricates a state rule."""
    if not REGION_PATTERN.fullmatch(region):
        raise ValueError("Use 'IN' or an ISO-style Indian region such as 'IN-MH'")
    known = {r["region_id"]: r["parent_id"] for r in db.execute(
        "SELECT region_id,parent_id FROM restrictions")}
    warnings: list[str] = []
    if region in known:
        chain, seen, node = [], set(), region
        while node and node not in seen:
            seen.add(node)
            chain.append(node)
            node = known.get(node)
        if "BASE" not in chain:
            chain.append("BASE")
        return chain, warnings
    # Unknown state: IN-wide + BASE are the only defensible fallback scopes.
    warnings.append("no_verified_state_specific_rules_falling_back_to_IN_BASE")
    return ["IN", "BASE"], warnings


def database_actions(db: sqlite3.Connection, task: str, issue_id: str,
                     crop_id: str, regions: list[str]) -> list[dict]:
    marks = ",".join("?" for _ in regions)
    if task == "disease":
        sql = f"""SELECT ca.action_id,ca.region_id,ca.rank AS ordinal,
                          ma.category,ma.polarity,ca.phase
                   FROM condition_actions ca JOIN management_actions ma ON ma.id=ca.action_id
                   WHERE ca.condition_id=? AND ca.region_id IN ({marks})
                     AND ca.phase='any'
                   ORDER BY ca.rank,ca.action_id"""
        rows = db.execute(sql, (issue_id, *regions)).fetchall()
    else:
        sql = f"""SELECT pa.action_id,pa.region_id,pa.display_order AS ordinal,
                          ma.category,ma.polarity,'any' AS phase
                   FROM pest_management_actions pa JOIN management_actions ma ON ma.id=pa.action_id
                   WHERE pa.pest_id=? AND pa.crop_id=? AND pa.region_id IN ({marks})
                   ORDER BY pa.display_order,pa.action_id"""
        rows = db.execute(sql, (issue_id, crop_id, *regions)).fetchall()
    found: dict[str, dict] = {}
    for row in rows:
        action_id = row["action_id"]
        if task == "disease":
            evidence_rows = db.execute(
                "SELECT DISTINCT source_id FROM applicability_evidence WHERE "
                "condition_id=? AND action_id=? AND region_id=? AND phase=?",
                (issue_id, action_id, row["region_id"], row["phase"]),
            )
        else:
            evidence_rows = db.execute(
                "SELECT DISTINCT source_id FROM pest_action_evidence WHERE "
                "pest_id=? AND crop_id=? AND action_id=? AND region_id=?",
                (issue_id, crop_id, action_id, row["region_id"]),
            )
        sources = sorted({r["source_id"] for r in evidence_rows})
        english = get_fact(db, action_id, "en")
        if not sources or not english["text"]:
            continue
        record = {
            "id": action_id,
            "english": english["text"],
            "english_review": english["language_review"],
            "source_ids": sources,
            "category": row["category"],
            "polarity": row["polarity"],
            "region_id": row["region_id"],
            "order": row["ordinal"],
        }
        # For duplicate actions prefer explicitly more-specific region if present.
        old = found.get(action_id)
        if old is None or regions.index(record["region_id"]) < regions.index(old["region_id"]):
            found[action_id] = record
    return sorted(found.values(), key=lambda x: (x["order"], x["id"]))


def model_label_for(db: sqlite3.Connection, task: str, class_id: int) -> dict | None:
    if task == "disease":
        row = db.execute(
            """SELECT ml.raw_label,ml.crop_id,ml.condition_id AS issue_id,
                      c.name_fact AS crop_name_fact,co.name_fact AS issue_name_fact,
                      co.cause_type
               FROM model_labels ml JOIN crops c ON c.id=ml.crop_id
               JOIN conditions co ON co.id=ml.condition_id
               WHERE ml.class_index=?""", (class_id,),
        ).fetchone()
    else:
        row = db.execute(
            """SELECT ml.raw_label,ml.pest_id AS issue_id,p.name_fact AS issue_name_fact,
                      det.mapping_status,ml.detector_status
               FROM pest_model_labels ml JOIN pests p ON p.pest_id=ml.pest_id
               JOIN pest_detectors det ON det.model_id=ml.model_id
               WHERE ml.model_id=? AND ml.class_id=?""", (PEST_MODEL_ID, class_id),
        ).fetchone()
    return dict(row) if row else None


def check_pest_class_names(db: sqlite3.Connection, exported_names: list[str]) -> dict:
    """Check a separately supplied deployed model's actual names against all DB entries.

    This proves naming alignment only, NOT checkpoint provenance or detection quality.
    """
    rows = db.execute("SELECT class_id,raw_label FROM pest_model_labels "
                      "WHERE model_id=? ORDER BY class_id", (PEST_MODEL_ID,)).fetchall()
    mismatches = [{"id": i, "db": row["raw_label"], "export": exported_names[i] if i < len(exported_names) else None}
                  for i, row in enumerate(rows)
                  if row["class_id"] != i or i >= len(exported_names)
                  or row["raw_label"].strip().casefold() != exported_names[i].strip().casefold()]
    return {"matched": len(exported_names) == 102 and not mismatches,
            "db_classes": len(rows), "export_classes": len(exported_names),
            "mismatches": mismatches}


def build_pilot_context(
    db: sqlite3.Connection,
    task: str,
    class_id: int,
    lang: str = "en",
    region: str = "IN",
    reported_extent: str = "unknown",
    question: str = "",
    locale_file: str | None = None,
    preview_provisional_pest: bool = False,
    count_chat_tokens: Callable[[list[dict]], int] | None = None,
    validated_confidence_threshold: float | None = None,
    detection_confidence: float | None = None,
) -> dict:
    if task not in {"disease", "pest"}:
        raise ValueError("task must be 'disease' or 'pest'")
    if not isinstance(class_id, int) or class_id < 0:
        raise ValueError("class_id must be a nonnegative integer")
    if lang not in SUPPORTED_LANGS:
        raise ValueError(f"Language not supported: {lang}")
    if reported_extent not in REPORTED_EXTENT:
        raise ValueError(f"reported_extent must be one of {REPORTED_EXTENT}")
    if len(question) > MAX_QUESTION_CHARS:
        raise ValueError(f"Question exceeds {MAX_QUESTION_CHARS} characters")
    if validated_confidence_threshold is not None and not (0 <= validated_confidence_threshold <= 1):
        raise ValueError("validated_confidence_threshold must be within 0..1")
    if detection_confidence is not None and not (0 <= detection_confidence <= 1):
        raise ValueError("detection_confidence must be within 0..1")

    regions, warnings = geography_chain(db, region)
    locales = load_reviewed_locales(locale_file)
    result: dict[str, Any] = {
        "pilot": "tomato-v1", "task": task, "class_id": class_id,
        "model_id": ("plantcare.disease.mobilenetv2.38" if task == "disease" else PEST_MODEL_ID),
        "crop_id": PILOT_CROP_ID, "language_requested": lang,
        "requested_region": region, "applicable_region_chain": regions,
        "reported_extent": reported_extent, "extent_source": "farmer_reported" if reported_extent != "unknown" else "not_provided",
        "biological_severity": None,
        "status": "unsupported_pilot_case", "advisory_actions": [],
        "llm_messages": [], "allowed_action_ids": [], "llm_ready": False,
        "token_count_verified": False, "max_new_tokens": MAX_NEW_TOKENS,
        "generation_config": {"temperature": 0.0, "sampling": False,
                              "max_new_tokens": MAX_NEW_TOKENS, "max_context_tokens": MAX_TOTAL_TOKENS},
        "warnings": list(warnings), "issue_id": None,
    }
    if (task == "disease" and class_id not in PILOT_DISEASE_CLASSES) or (
        task == "pest" and class_id != PILOT_PEST_CLASS
    ):
        return result
    if validated_confidence_threshold is not None:
        if detection_confidence is None:
            result["status"] = "confidence_required"
            return result
        if detection_confidence < validated_confidence_threshold:
            result["status"] = "below_validated_confidence_threshold"
            return result
    if detection_confidence is not None:
        result["detection_confidence"] = detection_confidence
        result["warnings"].append("confidence_is_not_disease_severity")

    mapped = model_label_for(db, task, class_id)
    if not mapped:
        result["status"] = "model_label_not_found"
        return result
    result["raw_model_label"] = mapped["raw_label"]
    issue_id = mapped["issue_id"]
    result["issue_id"] = issue_id
    if task == "disease":
        if mapped["crop_id"] != PILOT_CROP_ID:
            result["status"] = "crop_mapping_mismatch"
            return result
        crop_fact_id = mapped["crop_name_fact"]
        if mapped["cause_type"] == "none":
            result["warnings"].append("healthy_prediction_does_not_exclude_other_disease")
    else:
        crop_row = db.execute("SELECT name_fact FROM pest_crops WHERE crop_id=?", (PILOT_CROP_ID,)).fetchone()
        crop_fact_id = crop_row["name_fact"] if crop_row else "crop.tomato"
        if mapped["mapping_status"] != "verified" or mapped["detector_status"] != "validated":
            result["warnings"].append("provisional_yolo26n_model_mapping")
            if not preview_provisional_pest:
                result["status"] = "mapping_unverified"
                return result
        host = db.execute("SELECT 1 FROM pest_hosts WHERE pest_id=? AND crop_id=?",
                          (issue_id, PILOT_CROP_ID)).fetchone()
        if not host:
            result["status"] = "pest_host_not_supported"
            return result

    result["crop_name_en"] = get_fact(db, crop_fact_id, "en")["text"]
    result["issue_name_en"] = get_fact(db, mapped["issue_name_fact"], "en")["text"]
    result["crop_name_localized"] = localize(db, crop_fact_id, lang, locales)["text"]
    result["issue_name_localized"] = localize(db, mapped["issue_name_fact"], lang, locales)["text"]

    actions = database_actions(db, task, issue_id, PILOT_CROP_ID, regions)
    if not actions:
        result["status"] = "no_source_backed_advice"
        return result
    if region not in {a["region_id"] for a in actions} and region != "IN":
        if "no_verified_state_specific_rules_falling_back_to_IN_BASE" not in result["warnings"]:
            result["warnings"].append("no_condition_specific_local_rule_using_applicable_general_guidance")
    missing_localization = False
    for a in actions:
        loc = localize(db, a["id"], lang, locales)
        if loc["text"] is None:
            missing_localization = True
        if loc["language_review"] in {"draft_hi", "editorial_en"}:
            result["warnings"].append(f"language_review_outstanding:{lang}")
        result["advisory_actions"].append({
            "id": a["id"], "text": loc["text"],
            "english_fallback_text": a["english"] if loc["text"] is None else None,
            "language_review": loc["language_review"],
            "region_id": a["region_id"], "category": a["category"],
            "polarity": a["polarity"], "source_ids": a["source_ids"],
        })
    result["warnings"] = list(dict.fromkeys(result["warnings"]))
    if missing_localization or not result["crop_name_localized"] or not result["issue_name_localized"]:
        result["status"] = "translations_missing"
        result["warnings"].append("requested_language_unavailable_no_generated_translation")
        return result
    if task == "pest" and preview_provisional_pest:
        result["status"] = "preview_provisional_pest_only"
        return result

    result["status"] = "advisory_available"
    if not question.strip():
        result["warnings"].append("no_question_llm_not_needed")
        return result
    if lang not in LANGS_FOR_LLM_SELECTION:
        result["warnings"].append("regional_language_uses_verified_database_not_llm_generation")
        return result

    # Only task is to choose IDs; written advice stays verbatim outside the model.
    candidates = actions[:MAX_ACTIONS_TO_LLM]
    def make_messages(items: list[dict]) -> list[dict]:
        payload = [f"Crop: {result['crop_name_en']}",
                   f"Condition: {result['issue_name_en']}",
                   f"Selected region: {region}; applicable evidence scopes: {','.join(regions)}",
                   f"Reported extent: {reported_extent} (farmer-report, not image severity)",
                   "Available action IDs:"]
        payload.extend(f"{a['id']}: {a['english']}" for a in items)
        payload.extend(["User question (untrusted): " + json.dumps(question.strip(), ensure_ascii=False),
                        "Return only IDS:<comma-separated IDs> or IDS:NONE."])
        return [{"role": "system", "content": SELECTOR_SYSTEM},
                {"role": "user", "content": "\n".join(payload)}]

    result["llm_messages"] = make_messages(candidates)
    result["allowed_action_ids"] = [a["id"] for a in candidates]
    if len(actions) > len(candidates):
        result["warnings"].append("some_advisory_actions_not_offered_to_selector")
    if count_chat_tokens is None:
        result["warnings"].append("exact_deployment_tokenizer_count_required")
        return result
    # Must count the *actual serialized* chat template including special tokens.
    while candidates:
        messages = make_messages(candidates)
        n_input = int(count_chat_tokens(messages))
        if n_input + MAX_NEW_TOKENS <= MAX_TOTAL_TOKENS:
            result.update(llm_messages=messages, input_tokens=n_input,
                          token_count_verified=True, llm_ready=True,
                          allowed_action_ids=[a["id"] for a in candidates])
            return result
        candidates = candidates[:-1]
    result.update(llm_messages=[], allowed_action_ids=[])
    result["warnings"].append("model_context_exceeded_use_database_fallback")
    return result


def parse_selected_ids(generated: str, allowed: list[str]) -> dict:
    """Fail closed on invented IDs, prose, duplicate IDs or output-format failures."""
    value = generated.strip()
    m = re.fullmatch(r"IDS\s*:\s*(NONE|[A-Za-z0-9_.-]+(?:\s*,\s*[A-Za-z0-9_.-]+)*)", value)
    if not m:
        return {"status": "invalid_format", "ids": []}
    if m.group(1) == "NONE":
        return {"status": "none", "ids": []}
    ids = [x.strip() for x in m.group(1).split(",")]
    if len(ids) != len(set(ids)) or any(x not in allowed for x in ids):
        return {"status": "invalid_or_duplicate_ids", "ids": []}
    return {"status": "selected", "ids": ids}


def select_display_actions(context: dict, model_response: str | None = None) -> dict:
    """Never trust generated treatment sentences. All displayed text comes from DB/pack.

    Selector failure falls back to FULL stored advisory, not LLM fabrication.
    """
    available = context.get("advisory_actions", [])
    if context.get("status") != "advisory_available":
        return {"mode": "not_displayable", "actions": []}
    if not model_response or not context.get("llm_ready"):
        return {"mode": "database_only", "actions": available}
    parsed = parse_selected_ids(model_response, context.get("allowed_action_ids", []))
    if parsed["status"] != "selected":
        return {"mode": "database_fallback_due_to_llm_output", "actions": available}
    wanted = set(parsed["ids"])
    selected = [a for a in available if a["id"] in wanted]
    # The LLM is allowed to prioritize, not silently remove all verified guidance.
    return {"mode": "llm_prioritized_database_actions", "actions": selected,
            "all_reference_backed_actions": available}


def generate_locale_template(db: sqlite3.Connection, output_path: str) -> int:
    """Creates blank translation assignments; DOES NOT hallucinate translations."""
    ids: dict[str, str] = {}
    for task, class_id in [("disease", i) for i in sorted(PILOT_DISEASE_CLASSES)] + [("pest", PILOT_PEST_CLASS)]:
        mapped = model_label_for(db, task, class_id)
        if not mapped:
            continue
        ids[mapped["issue_name_fact"]] = "issue_name"
        crop_fact = (mapped.get("crop_name_fact") if task == "disease" else "crop.tomato")
        if crop_fact:
            ids[crop_fact] = "crop_name"
        regions = ["IN-TN", "IN", "BASE"]
        for action in database_actions(db, task, mapped["issue_id"], PILOT_CROP_ID, regions):
            ids[action["id"]] = "action"
    cols = ["fact_id", "kind", "lang", "en_reference", "hi_reference",
            "translated_text", "review_status", "reviewer", "review_date"]
    count = 0
    with open(output_path, "w", encoding="utf-8-sig", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=cols)
        writer.writeheader()
        for fact_id, kind in sorted(ids.items()):
            for lang in SUPPORTED_LANGS[2:]:
                writer.writerow({"fact_id": fact_id, "kind": kind, "lang": lang,
                                 "en_reference": get_fact(db, fact_id, "en")["text"] or "",
                                 "hi_reference": get_fact(db, fact_id, "hi")["text"] or "",
                                 "translated_text": "", "review_status": "needs_review",
                                 "reviewer": "", "review_date": ""})
                count += 1
    return count


def test_runner(db: sqlite3.Connection) -> None:
    assert db.execute("PRAGMA integrity_check").fetchone()[0] == "ok"
    assert not db.execute("PRAGMA foreign_key_check").fetchall()
    assert len(db.execute("SELECT class_index FROM model_labels").fetchall()) == 38
    for idx in PILOT_DISEASE_CLASSES:
        r = build_pilot_context(db, "disease", idx, "en", "IN", "unknown", "Help")
        assert r["status"] == "advisory_available", r
        assert all(a["source_ids"] and a["text"] for a in r["advisory_actions"])
        assert not r["llm_ready"]
    low = build_pilot_context(db, "disease", 29, "en", "IN-MH", "high", "What now?")
    assert low["reported_extent"] == "high" and low["biological_severity"] is None
    assert "IN" in low["applicable_region_chain"] and "BASE" in low["applicable_region_chain"]
    assert "IN-TN" not in [a["region_id"] for a in low["advisory_actions"]]
    tn = build_pilot_context(db, "disease", 29, "en", "IN-TN")
    assert any(a["region_id"] == "IN-TN" for a in tn["advisory_actions"])
    assert build_pilot_context(db, "disease", 31)["status"] == "unsupported_pilot_case"
    assert build_pilot_context(db, "pest", 71)["status"] == "mapping_unverified"
    preview = build_pilot_context(db, "pest", 71, preview_provisional_pest=True)
    assert preview["status"] == "preview_provisional_pest_only" and not preview["llm_ready"]
    regional = build_pilot_context(db, "disease", 30, "mr")
    assert regional["status"] == "translations_missing" and not regional["llm_ready"]
    hi = build_pilot_context(db, "disease", 30, "hi", "IN", "low", "अब क्या करूँ?",
                             count_chat_tokens=lambda messages: 180)
    assert hi["llm_ready"] and hi["token_count_verified"]
    assert select_display_actions(hi, "IDS:BLIGHT_CONFIRM")["mode"] == "llm_prioritized_database_actions"
    assert select_display_actions(hi, "Please spray chemicals")["mode"] == "database_fallback_due_to_llm_output"
    assert parse_selected_ids("IDS:BOGUS", hi["allowed_action_ids"])["status"] == "invalid_or_duplicate_ids"
    big = build_pilot_context(db, "disease", 30, "en", "IN", "high", "help",
                              count_chat_tokens=lambda messages: 500)
    assert not big["llm_ready"]
    assert build_pilot_context(db, "disease", 29, validated_confidence_threshold=0.7,
                               detection_confidence=0.5)["status"] == "below_validated_confidence_threshold"
    assert model_label_for(db, "pest", 71)["raw_label"] == "trialeurodes vaporariorum"
    print("PASS: pilot LLM context, locality, translation, severity, mapping, selection and fallback tests")


def main() -> None:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--db", required=True)
    p.add_argument("--self-test", action="store_true")
    p.add_argument("--make-locale-template", default=None)
    p.add_argument("--task", choices=["disease", "pest"])
    p.add_argument("--class-id", type=int)
    p.add_argument("--lang", choices=SUPPORTED_LANGS, default="en")
    p.add_argument("--region", default="IN")
    p.add_argument("--extent", choices=REPORTED_EXTENT, default="unknown")
    p.add_argument("--question", default="")
    p.add_argument("--locales", default=None, help="Optional reviewed translation CSV")
    p.add_argument("--preview-provisional-pest", action="store_true")
    p.add_argument("--detection-confidence", type=float, default=None)
    p.add_argument("--validated-threshold", type=float, default=None)
    p.add_argument("--hf-tokenizer", default=None, help="HF tokenizer directory to count the rendered chat template; may require transformers")
    args = p.parse_args()
    with open_database(args.db) as db:
        if args.self_test:
            test_runner(db)
        elif args.make_locale_template:
            n = generate_locale_template(db, args.make_locale_template)
            print(f"Created {n} BLANK additional-language translation assignments: {args.make_locale_template}")
        else:
            if args.task is None or args.class_id is None:
                p.error("--task and --class-id required")
            count_fn = None
            if args.hf_tokenizer:
                from transformers import AutoTokenizer
                tokenizer = AutoTokenizer.from_pretrained(args.hf_tokenizer, local_files_only=True)
                def count_fn(messages):
                    tokens = tokenizer.apply_chat_template(
                        messages, tokenize=True, add_generation_prompt=True,
                    )
                    return len(tokens)
            result = build_pilot_context(
                db, args.task, args.class_id, lang=args.lang,
                region=args.region, reported_extent=args.extent,
                question=args.question, locale_file=args.locales,
                preview_provisional_pest=args.preview_provisional_pest,
                detection_confidence=args.detection_confidence,
                validated_confidence_threshold=args.validated_threshold,
                count_chat_tokens=count_fn,
            )
            print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
