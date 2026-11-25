"""Run from the PlantCareAI Android project root with py localize_scan_screens.py."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

# Key | English | Hindi | Punjabi | Marathi | Tamil
TRANSLATIONS = """history_title|Saved scans|सहेजे गए स्कैन|ਸੰਭਾਲੇ ਸਕੈਨ|जतन केलेले स्कॅन|சேமித்த ஸ்கேன்கள்
history_back|Back to list|सूची पर लौटें|ਸੂਚੀ ਵੱਲ ਮੁੜੋ|यादीकडे परत जा|பட்டியலுக்குத் திரும்பு
history_delete|Delete scan|स्कैन हटाएँ|ਸਕੈਨ ਮਿਟਾਓ|स्कॅन हटवा|ஸ்கேனை நீக்கு
history_empty|No saved scans yet. Use Save to history after a scan.|अभी कोई स्कैन सहेजा नहीं गया है। स्कैन के बाद उसे इतिहास में सहेजें।|ਅਜੇ ਕੋਈ ਸਕੈਨ ਸੰਭਾਲਿਆ ਨਹੀਂ ਗਿਆ। ਸਕੈਨ ਤੋਂ ਬਾਅਦ ਇਸ ਨੂੰ ਇਤਿਹਾਸ ਵਿੱਚ ਸੰਭਾਲੋ।|अजून कोणताही स्कॅन जतन केलेला नाही. स्कॅननंतर तो इतिहासात जतन करा.|இதுவரை ஸ்கேன் எதுவும் சேமிக்கப்படவில்லை. ஸ்கேன் செய்த பின் அதை வரலாற்றில் சேமிக்கவும்.
history_delete_title|Delete saved scan?|सहेजा गया स्कैन हटाएँ?|ਸੰਭਾਲਿਆ ਸਕੈਨ ਮਿਟਾਉਣਾ ਹੈ?|जतन केलेला स्कॅन हटवायचा?|சேமித்த ஸ்கேனை நீக்கவா?
history_delete_description|This removes the saved result and its photo copy.|इससे सहेजा गया परिणाम और उसकी फ़ोटो की प्रति हट जाएगी।|ਇਸ ਨਾਲ ਸੰਭਾਲਿਆ ਨਤੀਜਾ ਅਤੇ ਉਸ ਦੀ ਫ਼ੋਟੋ ਦੀ ਕਾਪੀ ਮਿਟ ਜਾਵੇਗੀ।|यामुळे जतन केलेला निकाल आणि त्याच्या फोटोची प्रत हटेल.|சேமித்த முடிவும் அதன் பட நகலும் நீக்கப்படும்.
history_cancel|Cancel|रद्द करें|ਰੱਦ ਕਰੋ|रद्द करा|ரத்து செய்
history_confirm_delete|Delete|हटाएँ|ਮਿਟਾਓ|हटवा|நீக்கு
history_delete_failed|Could not delete the scan.|स्कैन हटाया नहीं जा सका।|ਸਕੈਨ ਮਿਟਾਇਆ ਨਹੀਂ ਜਾ ਸਕਿਆ।|स्कॅन हटवता आला नाही.|ஸ்கேனை நீக்க முடியவில்லை.
history_save_failed|Could not save the scan.|स्कैन सहेजा नहीं जा सका।|ਸਕੈਨ ਸੰਭਾਲਿਆ ਨਹੀਂ ਜਾ ਸਕਿਆ।|स्कॅन जतन करता आला नाही.|ஸ்கேனை சேமிக்க முடியவில்லை.
history_photo|Saved scan photo|सहेजी गई स्कैन फ़ोटो|ਸੰਭਾਲੀ ਸਕੈਨ ਫ਼ੋਟੋ|जतन केलेला स्कॅन फोटो|சேமித்த ஸ்கேன் படம்
history_photo_missing|Saved photo unavailable.|सहेजी गई फ़ोटो उपलब्ध नहीं है।|ਸੰਭਾਲੀ ਫ਼ੋਟੋ ਉਪਲਬਧ ਨਹੀਂ ਹੈ।|जतन केलेला फोटो उपलब्ध नाही.|சேமித்த படம் கிடைக்கவில்லை.
history_guidance|Saved AI guidance|सहेजा गया एआई मार्गदर्शन|ਸੰਭਾਲੀ ਏਆਈ ਸਲਾਹ|जतन केलेले एआय मार्गदर्शन|சேமித்த AI வழிகாட்டுதல்
history_entry|Saved scan|सहेजा गया स्कैन|ਸੰਭਾਲਿਆ ਸਕੈਨ|जतन केलेला स्कॅन|சேமித்த ஸ்கேன்
history_pest_entry|Pest scan|कीट स्कैन|ਕੀੜਿਆਂ ਦਾ ਸਕੈਨ|कीड स्कॅन|பூச்சி ஸ்கேன்
history_save|Save to history|इतिहास में सहेजें|ਇਤਿਹਾਸ ਵਿੱਚ ਸੰਭਾਲੋ|इतिहासात जतन करा|வரலாற்றில் சேமி
history_saving|Saving scan…|स्कैन सहेजा जा रहा है…|ਸਕੈਨ ਸੰਭਾਲਿਆ ਜਾ ਰਿਹਾ ਹੈ…|स्कॅन जतन होत आहे…|ஸ்கேன் சேமிக்கப்படுகிறது…
history_saved|Saved to history|इतिहास में सहेजा गया|ਇਤਿਹਾਸ ਵਿੱਚ ਸੰਭਾਲਿਆ ਗਿਆ|इतिहासात जतन केले|வரலாற்றில் சேமிக்கப்பட்டது
pest_title|Pest detection|कीट पहचान|ਕੀੜਿਆਂ ਦੀ ਪਛਾਣ|किडीची ओळख|பூச்சி கண்டறிதல்
pest_import|Import the pest model in Settings to run this scan.|स्कैन के लिए सेटिंग्स में कीट पहचान मॉडल आयात करें।|ਸਕੈਨ ਲਈ ਸੈਟਿੰਗਾਂ ਵਿੱਚ ਕੀੜਿਆਂ ਦੀ ਪਛਾਣ ਵਾਲਾ ਮਾਡਲ ਇੰਪੋਰਟ ਕਰੋ।|स्कॅनसाठी सेटिंग्जमध्ये कीड ओळख मॉडेल आयात करा.|ஸ்கேன் செய்ய அமைப்புகளில் பூச்சி கண்டறிதல் மாதிரியை இறக்குமதி செய்யவும்.
scan_analyzing_photo|Analyzing photo…|फ़ोटो का विश्लेषण हो रहा है…|ਫ਼ੋਟੋ ਦੀ ਜਾਂਚ ਹੋ ਰਹੀ ਹੈ…|फोटोचे विश्लेषण होत आहे…|படம் பகுப்பாய்வு செய்யப்படுகிறது…
pest_none|No pest detections met the model score threshold.|किसी कीट की पहचान का स्कोर तय सीमा तक नहीं पहुँचा।|ਕਿਸੇ ਕੀੜੇ ਦੀ ਪਛਾਣ ਦਾ ਸਕੋਰ ਨਿਰਧਾਰਤ ਹੱਦ ਤੱਕ ਨਹੀਂ ਪਹੁੰਚਿਆ।|कोणत्याही किडीच्या ओळखीचा गुण ठरवलेल्या मर्यादेपर्यंत पोहोचला नाही.|எந்தப் பூச்சிக் கண்டறிதல் மதிப்பும் நிர்ணயிக்கப்பட்ட வரம்பை எட்டவில்லை.
scan_another_photo|Scan another photo|दूसरी फ़ोटो स्कैन करें|ਹੋਰ ਫ਼ੋਟੋ ਸਕੈਨ ਕਰੋ|दुसरा फोटो स्कॅन करा|மற்றொரு படத்தை ஸ்கேன் செய்
scan_model_score|Model score: %1$s|मॉडल स्कोर: %1$s|ਮਾਡਲ ਸਕੋਰ: %1$s|मॉडेलचा गुण: %1$s|மாதிரி மதிப்பெண்: %1$s
pest_dataset_crop|Dataset crop group: %1$s|डेटासेट में फ़सल समूह: %1$s|ਡਾਟਾਸੈੱਟ ਵਿੱਚ ਫ਼ਸਲ ਦਾ ਸਮੂਹ: %1$s|डेटासेटमधील पीक गट: %1$s|தரவுத்தொகுப்பின் பயிர் குழு: %1$s
pest_detection_count|Detections: %1$d|पहचान की संख्या: %1$d|ਪਛਾਣਾਂ ਦੀ ਗਿਣਤੀ: %1$d|ओळखींची संख्या: %1$d|கண்டறிதல்கள்: %1$d
scan_severity|Severity: %1$s|गंभीरता: %1$s|ਗੰਭੀਰਤਾ: %1$s|तीव्रता: %1$s|தீவிரம்: %1$s
scan_damage|Estimated damage: %1$s|अनुमानित नुकसान: %1$s|ਅਨੁਮਾਨਿਤ ਨੁਕਸਾਨ: %1$s|नुकसानीचा अंदाज: %1$s|மதிப்பிடப்பட்ட சேதம்: %1$s
scan_low|Low|कम|ਘੱਟ|कमी|குறைவு
scan_moderate|Moderate|मध्यम|ਦਰਮਿਆਨੀ|मध्यम|மிதம்
scan_severe|Severe|गंभीर|ਗੰਭੀਰ|गंभीर|கடுமை
scan_unknown_severity|Unable to estimate|अनुमान नहीं लगाया जा सका|ਅਨੁਮਾਨ ਨਹੀਂ ਲੱਗ ਸਕਿਆ|अंदाज लावता आला नाही|மதிப்பிட முடியவில்லை
scan_photo_error|Unable to load image.|फ़ोटो लोड नहीं हो सकी।|ਫ਼ੋਟੋ ਲੋਡ ਨਹੀਂ ਹੋ ਸਕੀ।|फोटो लोड करता आला नाही.|படத்தை ஏற்ற முடியவில்லை.
scan_estimating|Estimating leaf damage...|पत्ती के नुकसान का अनुमान लगाया जा रहा है…|ਪੱਤੇ ਦੇ ਨੁਕਸਾਨ ਦਾ ਅਨੁਮਾਨ ਲਾਇਆ ਜਾ ਰਿਹਾ ਹੈ…|पानाच्या नुकसानीचा अंदाज घेतला जात आहे…|இலைச் சேதம் மதிப்பிடப்படுகிறது…
scan_waiting|Waiting for disease diagnosis...|रोग निदान की प्रतीक्षा है…|ਰੋਗ ਦੀ ਪਛਾਣ ਦੀ ਉਡੀਕ ਹੈ…|रोगनिदानाची प्रतीक्षा आहे…|நோய் கண்டறிதலுக்காகக் காத்திருக்கிறது…
scan_loading_ai|Loading offline AI model...|ऑफ़लाइन एआई मॉडल लोड हो रहा है…|ਆਫ਼ਲਾਈਨ ਏਆਈ ਮਾਡਲ ਲੋਡ ਹੋ ਰਿਹਾ ਹੈ…|ऑफलाइन एआय मॉडेल लोड होत आहे…|ஆஃப்லைன் AI மாதிரி ஏற்றப்படுகிறது…
pest_detect_action|Detect Pest|कीट पहचानें|ਕੀੜੇ ਪਛਾਣੋ|कीड ओळखा|பூச்சியைக் கண்டறி
vision_pest_model|Pest model|कीट पहचान मॉडल|ਕੀੜਿਆਂ ਦੀ ਪਛਾਣ ਦਾ ਮਾਡਲ|कीड ओळख मॉडेल|பூச்சி கண்டறிதல் மாதிரி
vision_disease_model|Disease model|रोग पहचान मॉडल|ਰੋਗ ਦੀ ਪਛਾਣ ਦਾ ਮਾਡਲ|रोग ओळख मॉडेल|நோய் கண்டறிதல் மாதிரி
vision_import_model|Import model|मॉडल आयात करें|ਮਾਡਲ ਇੰਪੋਰਟ ਕਰੋ|मॉडेल आयात करा|மாதிரியை இறக்குமதி செய்
scan_analyze_error|Unable to analyze image.|फ़ोटो का विश्लेषण नहीं हो सका।|ਫ਼ੋਟੋ ਦੀ ਜਾਂਚ ਨਹੀਂ ਹੋ ਸਕੀ।|फोटोचे विश्लेषण करता आले नाही.|படத்தைப் பகுப்பாய்வு செய்ய முடியவில்லை."""

ROWS = [line.split("|") for line in TRANSLATIONS.splitlines()]
LANGUAGES = ("", "-hi", "-pa", "-mr", "-ta")
PREFIX = "plantcare_"
SOURCES = {
    "ScanHistoryScreen.kt": "history_ scan_ pest_",
    "PestResultContent.kt": "history_ scan_ pest_",
    "DiagnosisScreen.kt": "history_ scan_",
    "HomeScreen.kt": "pest_detect_action",
    "VisionModelSettings.kt": "vision_",
}

def string(key, argument=None, getter="stringResource"):
    suffix = "" if argument is None else ", " + argument
    return f"{getter}(R.string.{PREFIX}{key}{suffix})"

def update_source(path):
    source = path.read_text(encoding="utf-8-sig")
    updated = source
    allowed = SOURCES[path.name].split()
    for key, english, *_ in ROWS:
        if "%" in english or not any(key.startswith(prefix) for prefix in allowed):
            continue
        getter = "stringResource"
        if key.endswith("_failed"):
            getter = "context.getString"
        if key == "scan_analyze_error":
            continue  # The existing fallback is mixed with a runtime diagnostic.
        if key in ("scan_low", "scan_moderate", "scan_severe", "scan_unknown_severity"):
            continue  # Disease AI prompts use these same English terms.
        updated = updated.replace(f'"{english}"', string(key, getter=getter))

    if path.name in ("ScanHistoryScreen.kt", "PestResultContent.kt"):
        for literal, key, argument in (
                ('"Dataset crop group: $it"', "pest_dataset_crop", "it"),
                ('"Dataset crop group: $cropGroup"', "pest_dataset_crop", "cropGroup"),
                ('"Model score: $score"', "scan_model_score", "score"),
                ('"Model score: ${percent.format(score)}"', "scan_model_score", "percent.format(score)"),
                ('"Estimated damage: ${percent.format(damage / 100.0)}"', "scan_damage", "percent.format(damage / 100.0)"),
                ('"Severity: ${severity.displayLabel()}"', "scan_severity", "severityLabel(severity)"),
                ('if (results.size == 1) "1 detection" else "${results.size} detections"', "pest_detection_count", "results.size"),
        ):
            updated = updated.replace(literal, string(key, argument))

    if path.name == "ScanHistoryScreen.kt":
        updated = updated.replace("private fun ScanHistoryEntity.title()", "@Composable\nprivate fun ScanHistoryEntity.title()") if "@Composable\nprivate fun ScanHistoryEntity.title()" not in updated else updated
        if "severityLabel(severity)" in updated and "private fun severityLabel" not in updated:
            updated += """
@Composable
private fun severityLabel(value: String): String = when (value.uppercase()) {
    "LOW" -> stringResource(R.string.plantcare_scan_low)
    "MODERATE" -> stringResource(R.string.plantcare_scan_moderate)
    "SEVERE" -> stringResource(R.string.plantcare_scan_severe)
    "UNABLE_TO_ESTIMATE" -> stringResource(R.string.plantcare_scan_unknown_severity)
    else -> value.displayLabel()
}
"""
    if path.name == "DiagnosisScreen.kt":
        for prefix, key, value in (
                ("Model score", "scan_model_score", "item.confidence * 100"),
                ("Estimated damage", "scan_damage", "severity.damagePercentage"),
        ):
            pattern = r'"' + prefix + r': \$\{\s*String\.format\(\s*Locale\.US,\s*"%.2f",\s*' + re.escape(value) + r'\s*\)\s*\}%"'
            updated = re.sub(pattern, lambda _: string(key, f'String.format(Locale.getDefault(), "%.2f%%", {value})'), updated)
        updated = updated.replace('"Severity: $severityText"', string("scan_severity", "severityText"))
        start = updated.find("val severityText = when (severity.severityLevel)")
        if start >= 0:
            end = updated.index("val severityColor", start)
            block = updated[start:end]
            for key, english, *_ in ROWS:
                if key in ("scan_low", "scan_moderate", "scan_severe", "scan_unknown_severity"):
                    block = block.replace(f'"{english}"', string(key))
            updated = updated[:start] + block + updated[end:]

    if updated != source:
        package_end = updated.index("\n", updated.index("package "))
        for name, usage in (("androidx.compose.ui.res.stringResource", "stringResource("),
                            ("com.armbharat.plantcareai.R", "R.string.")):
            if usage in updated and f"import {name}\n" not in updated:
                updated = updated[:package_end] + f"\n\nimport {name}" + updated[package_end:]
        return updated
    return None

def main():
    root = Path("app/src/main")
    if not (root / "res").is_dir() or not (root / "java").is_dir():
        raise SystemExit("Run this script from the Android project root, next to gradlew.bat.")
    pending = {}
    for column, qualifier in enumerate(LANGUAGES, start=1):
        target = root / "res" / ("values" + qualifier) / "plantcare_scan_strings.xml"
        resources = ET.Element("resources")
        for row in ROWS:
            name = PREFIX + row[0]
            node = ET.SubElement(resources, "string", name=name)
            node.text = row[column]
        ET.indent(resources, space="    ")
        pending[target] = ET.tostring(resources, encoding="unicode") + "\n"

    for name in SOURCES:
        matches = list((root / "java").rglob(name))
        if not matches:
            print(f"Source not found: {name}")
        for path in matches:
            updated = update_source(path)
            if updated is not None:
                pending[path] = updated
            else:
                print(f"No matching English labels or already localized: {name}")

    for target, content in pending.items():
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(content, encoding="utf-8")
        print(f"Updated {target}")

if __name__ == "__main__":
    main()
