"""Read-only tomato and maize advisory for Stage 1B.

Place this beside plantcare_llm_two_crop.py. Explicit topics are the stable
interface for an app or an intent router. The CLI also accepts a limited set
of English and Hindi question patterns for local testing.
"""
from __future__ import annotations

import argparse
import json
import re
from contextlib import closing

from plantcare_llm_two_crop import (
    PILOT_DISEASE_CROPS,
    SUPPORTED_LANGS,
    database_actions,
    geography_chain,
    load_reviewed_locales,
    localize,
    model_label_for,
    open_database,
)

PEST_CROPS = {71: "tomato", 23: "maize"}
TOPICS = ("signs", "risk", "mitigation")
FACT_TOPICS = {
    "symptom": "signs",
    "differential": "signs",
    "sign": "signs",
    "life_stage": "signs",
    "risk": "risk",
}
CROP_MENTIONS = {
    "tomato": r"\btomato(?:es)?\b|टमाटर",
    "maize": r"\b(?:corn|maize|makka)\b|मक्का|मक्के",
}
ISSUE_MENTIONS = {
    "tomato_bacterial_spot": r"\bbacterial spot\b|बैक्टीरियल स्पॉट",
    "tomato_early_blight": r"\bearly blight\b|अगेती झुलसा",
    "tomato_late_blight": r"\blate blight\b|पछेती झुलसा",
    "maize_common_rust": r"\bcommon rust\b|कॉमन रस्ट",
    "maize_northern_blight": r"\bnorthern leaf blight\b",
    "greenhouse_whitefly": r"\b(?:greenhouse )?whitefl(?:y|ies)\b|सफेद मक्खी",
    "armyworm_group": r"\b(?:fall )?armyworms?\b|आर्मीवर्म",
}
TOPIC_PATTERNS = {
    "signs": r"\b(?:signs?|symptoms?|look for|spot|identify|what should i check|where should i (?:check|look))\b|लक्षण|पहचान|निशान|क्या देख|कहाँ देख",
    "risk": r"\b(?:risk|spread|spreading|early warnings?|conditions|vulnerab\w*)\b|फैल|जोखिम|खतरा",
    "mitigation": r"\b(?:manage\w*|mitigat\w*|control|treat\w*|prevent\w*|what (?:can|should|do) i do|what to do)\b|उपाय|इलाज|बचाव|नियंत्रण|क्या कर",
}
UNSUPPORTED_QUESTIONS = {
    "nutrition_unavailable": r"\b(?:fertili[sz]\w*|urea|npk|nutri\w*|soil tests?)\b|खाद|उर्वरक|यूरिया|पोषण",
    "dose_unavailable": r"\b(?:dose|dosage|application rate|how (?:much|many)\b.*\b(?:spray|apply|ml|grams?|kg))\b|मात्रा|खुराक|कितन[ाीे].*(?:छिड़क|दवा)",
    "forecast_unavailable": r"\b(?:forecast|outbreak prediction|when will it (?:spread|reach)|will it (?:spread|reach|happen) (?:tomorrow|next))\b|पूर्वानुमान|कब फैले",
}


def route_question(question: str, crop_id: str, issue_id: str) -> tuple[str, tuple[str, ...]]:
    """Return a topic list or a status that asks for a narrower request."""
    if not question.strip():
        return "choose_topics", ()
    for other_crop, pattern in CROP_MENTIONS.items():
        if other_crop != crop_id and re.search(pattern, question, re.IGNORECASE):
            return "crop_context_mismatch", ()
    for other_issue, pattern in ISSUE_MENTIONS.items():
        if other_issue != issue_id and re.search(pattern, question, re.IGNORECASE):
            return "issue_context_mismatch", ()
    if issue_id == "armyworm_group" and re.search(
        r"\bfall armyworms?\b|फॉल आर्मीवर्म|फॉल आर्मी वर्म", question, re.IGNORECASE
    ):
        return "species_identification_required", ()
    for status, pattern in UNSUPPORTED_QUESTIONS.items():
        if re.search(pattern, question, re.IGNORECASE):
            return status, ()
    topics = tuple(
        topic for topic, pattern in TOPIC_PATTERNS.items()
        if re.search(pattern, question, re.IGNORECASE)
    )
    return ("ready" if topics else "choose_topics"), topics


def issue_facts(db, task, issue_id, crop_id, regions):
    if task == "disease":
        rows = db.execute(
            """SELECT fact_id, role, 'BASE' AS region_id FROM condition_facts
               WHERE condition_id = ? ORDER BY fact_id""",
            (issue_id,),
        )
    else:
        rows = db.execute(
            """SELECT fact_id, role, region_id FROM pest_damage
               WHERE pest_id = ? AND crop_id = ? ORDER BY fact_id""",
            (issue_id, crop_id),
        )
    for row in rows:
        topic = FACT_TOPICS.get(row["role"])
        if topic is None or row["region_id"] not in regions:
            continue
        source_ids = [
            source["source_id"] for source in db.execute(
                "SELECT source_id FROM evidence_links WHERE fact_id = ? ORDER BY source_id",
                (row["fact_id"],),
            )
        ]
        if source_ids:
            yield topic, {
                "id": row["fact_id"],
                "role": row["role"],
                "region_id": row["region_id"],
                "source_ids": source_ids,
            }


def build_advisory(
    db, task, class_id, *, lang="en", region="IN", question="",
    topics=(), locale_file=None, preview_provisional_pest=False,
):
    if task not in {"disease", "pest"} or lang not in SUPPORTED_LANGS:
        raise ValueError("Unsupported task or language")
    if any(topic not in TOPICS for topic in topics):
        raise ValueError("Unsupported advisory topic")
    regions, warnings = geography_chain(db, region)
    mapping = PILOT_DISEASE_CROPS if task == "disease" else PEST_CROPS
    crop_id = mapping.get(class_id)
    out = {
        "status": "unsupported_pilot_case", "task": task, "class_id": class_id,
        "crop_id": crop_id, "language": lang, "region": region,
        "sections": {}, "sources": {}, "warnings": warnings,
    }
    if crop_id is None:
        return out
    mapped = model_label_for(db, task, class_id)
    if not mapped or (task == "disease" and mapped["crop_id"] != crop_id):
        out["status"] = "mapping_mismatch"
        return out
    issue_id = mapped["issue_id"]
    out["issue_id"] = issue_id
    provisional = task == "pest" and (
        mapped["mapping_status"] != "verified" or mapped["detector_status"] != "validated"
    )
    if provisional and not preview_provisional_pest:
        out["status"] = "mapping_unverified"
        return out
    if task == "pest" and not db.execute(
        "SELECT 1 FROM pest_hosts WHERE pest_id = ? AND crop_id = ?", (issue_id, crop_id)
    ).fetchone():
        out["status"] = "pest_host_not_supported"
        return out

    if question or not topics:
        out["status"], inferred = route_question(question, crop_id, issue_id)
        if out["status"] != "ready" and (out["status"] != "choose_topics" or not topics):
            return out
        topics = topics or inferred

    locales = load_reviewed_locales(locale_file)
    facts = {topic: [] for topic in dict.fromkeys(topics)}
    for topic, fact in issue_facts(db, task, issue_id, crop_id, regions):
        if topic in facts:
            facts[topic].append(fact)
    if "mitigation" in facts:
        facts["mitigation"] = [
            {
                "id": action["id"], "role": action["category"],
                "polarity": action["polarity"], "region_id": action["region_id"],
                "source_ids": action["source_ids"],
            }
            for action in database_actions(db, task, issue_id, crop_id, regions)
        ]

    for topic, records in facts.items():
        localized = []
        for record in records:
            value = localize(db, record["id"], lang, locales)
            if value["text"]:
                localized.append({**record, **value})
        section_status = "no_source_backed_record"
        if records:
            section_status = "available" if len(localized) == len(records) else "translations_missing"
        out["sections"][topic] = {"status": section_status, "records": localized}

    displayed = [
        record for section in out["sections"].values() for record in section["records"]
    ]
    for source_id in sorted({sid for record in displayed for sid in record["source_ids"]}):
        source = db.execute(
            "SELECT title, institution, url FROM sources WHERE id = ?", (source_id,)
        ).fetchone()
        if source:
            out["sources"][source_id] = dict(source)
    if any(r["language_review"] in {"editorial_en", "draft_hi"} for r in displayed):
        warnings.append("language_review_outstanding")
    if displayed and region != "IN" and not any(r["region_id"] == region for r in displayed):
        warnings.append("no_requested_region_specific_record")
    if provisional:
        warnings.append("preview_provisional_pest_only")
    statuses = {section["status"] for section in out["sections"].values()}
    if provisional:
        out["status"] = "preview_provisional_pest_only"
    elif statuses == {"available"}:
        out["status"] = "advisory_available"
    elif displayed:
        out["status"] = "partial_advisory"
    elif "translations_missing" in statuses:
        out["status"] = "translations_missing"
    else:
        out["status"] = "no_source_backed_record"
    return out


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--db", required=True)
    parser.add_argument("--task", choices=("disease", "pest"), default="disease")
    parser.add_argument("--class-id", type=int, required=True)
    parser.add_argument("--lang", choices=SUPPORTED_LANGS, default="en")
    parser.add_argument("--region", default="IN")
    parser.add_argument("--question", default="")
    parser.add_argument("--topic", choices=TOPICS, action="append", default=[])
    parser.add_argument("--locales")
    parser.add_argument("--preview-provisional-pest", action="store_true")
    args = parser.parse_args()
    with closing(open_database(args.db)) as db:
        result = build_advisory(
            db, args.task, args.class_id, lang=args.lang, region=args.region,
            question=args.question, topics=args.topic, locale_file=args.locales,
            preview_provisional_pest=args.preview_provisional_pest,
        )
    print(json.dumps(result, ensure_ascii=True, indent=2))


if __name__ == "__main__":
    main()
