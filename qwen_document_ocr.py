import argparse
import json
import os
import re
import tempfile
from pathlib import Path
from typing import Any, Optional

import torch
from transformers import AutoProcessor, BitsAndBytesConfig, Qwen2VLForConditionalGeneration

from qwen_vl_utils import process_vision_info


# This is deliberately a document extractor, not a medical extractor.  The
# output vocabulary is inferred from each document; only tables use a stable
# lossless grid representation so blank/duplicate columns cannot be shifted.
EXTRACTION_PROMPT = r"""
You are a visual document transcription engine. Reconstruct the COMPLETE visible
document as valid JSON. This is extraction, not summarization, correction,
classification, diagnosis, or interpretation.

Before writing the answer, silently perform these passes:
1. LAYOUT MAP: inspect the whole page and identify reading order, columns,
   regions, headings, paragraphs, lists, forms, tables, captions, footnotes,
   stamps, signatures, and cropped/unreadable regions from their X/Y positions.
2. EXACT TRANSCRIPTION: read every meaningful visible item in that order.
3. RELATIONSHIP CHECK: verify every label/value, heading/body, table cell/row,
   caption/table, marker/footnote, unit/value, and date/label association against
   its visual position.
4. FINAL AUDIT: remove anything not visibly supported and confirm the JSON is
   complete and syntactically valid.

NON-NEGOTIABLE RULES
- Extract ALL meaningful visible content. Never omit prose because a table is
  present. Preserve titles, headers, paragraphs, lists, form fields, tables,
  captions, footnotes, notes, warnings, annotations, readable stamp/signature
  text, identifiers, contact details, and page text.
- Infer an adaptive JSON hierarchy from this document. Do NOT force medical,
  invoice, research-paper, or any other pre-approved domain schema. Use neutral
  structural names when the document itself provides no semantic label.
- Preserve natural visual reading order and nesting. For multi-column content,
  follow the apparent reading flow rather than mixing lines across columns.
- Preserve visible wording and symbols exactly. Do not paraphrase, translate,
  fix grammar/spelling, normalize names, expand abbreviations, change '<' into
  '<=', or infer a cleaner value from domain knowledge.
- Treat every glyph as evidence. Preserve Unicode superscripts, subscripts,
  minus signs, primes, Greek letters, isotope numbers, degree signs, comparison
  operators, punctuation, and spacing when visible. For example, never change
  "¹²C¹⁸O" to "¹²C¹⁶O", and never change "cm⁻¹" to "cm^1" or "cm-1".
- A nearby number is not automatically a value for a label. Visual position,
  borders, alignment, whitespace, and explicit markers are the source of truth.
- If expected content is cropped, absent, obscured, or unreadable, use null only
  when the visible structure shows that a value/slot exists. Do not invent a
  value or create fields merely because they are common for that document type.
- Keep separately labelled facts separate (for example, "Dated" and "Print
  Date"). Preserve unlabeled visible identifiers without inventing their meaning.

TABLE PROTOCOL (CRITICAL)
- First reconstruct each table as a two-dimensional grid from X/Y geometry.
- Read one complete row horizontally from left to right, then the next row.
- Never extract columns independently and never shift values between rows.
- Represent a table losslessly with keys appropriate to what is visible, using:
  "caption" when present,
  "column_headers" as an ordered array (use null for a visibly blank header),
  "rows" as an array of equally sized cell arrays,
  "row_headers" only when row headers visibly exist,
  "footnotes" when present.
- Keep blank cells as null. Do not copy a value from above or below.
- Preserve multi-level headers as ordered header rows rather than flattening or
  guessing. Preserve merged-cell meaning explicitly only when visually clear.
- Every row must have the same number of cells as the resolved column grid. If
  alignment is genuinely ambiguous, preserve the uncertain cell as null instead
  of shifting later cells to fill the gap.
- Count columns from the visible vertical grid before reading values. A row such
  as "2115.632 | [blank] | 7 | 6" must be ["2115.632", null, "7", "6"], never
  ["2115.632", "7", "6"]. Empty cells are structural evidence.

OUTPUT CONTRACT
- Return exactly one complete JSON object or array and nothing else.
- No Markdown fences, commentary, confidence scores, diagnosis, or explanation.
- Choose keys and nesting from the document's own hierarchy and labels.
- Preserve repeated sections as ordered arrays rather than overwriting keys.
- Use JSON null, never strings such as "N/A", "unknown", or guessed placeholders,
  for a visibly present but unreadable/missing value.
- Ensure all strings are JSON escaped and all braces/brackets are closed.
""".strip()


TRANSCRIPTION_LEDGER_PROMPT = r"""
Create a lossless coverage ledger for this ONE visible document page. Return
valid JSON only, using this shape:
{"page_number": 1, "blocks": [{"order": 1, "kind": "heading|paragraph|list|table|caption|footnote|other", "text": "exact visible text", "lines": ["exact line 1"]}]}

This is a transcription inventory, not a summary or final document schema.
- Scan top-to-bottom and left-to-right. Record EVERY visible block separately.
- Never merge two paragraphs. If a section visibly has two paragraphs, create
  two paragraph blocks even if they discuss the same topic.
- Copy characters exactly. Never correct spelling or scientific notation.
  Preserve forms such as ¹²C¹⁸O, cm⁻¹, μm, α, β, ±, ≤, ≥, and subscripts.
- For tables, "text" must contain each row with a | separator for EVERY column.
  Preserve empty cells as consecutive separators, e.g. 2115.632||7|6.
- Include captions and footnotes as their own blocks. Do not infer hidden text.
- Use null only for a visibly present but unreadable item.
Return the JSON ledger and nothing else.
""".strip()


VERIFICATION_PROMPT = r"""
Audit the candidate JSON against every supplied document page and return the
FULL corrected JSON only.

Do not redesign it into a domain schema. Correct only image-grounded problems:
- delete invented or unsupported text/values/fields;
- restore omitted visible headings, prose, lists, fields, identifiers, captions,
  table cells, blank cells, notes, and footnotes;
- correct exact characters and symbols without normalization;
- correct reading order and label/value or heading/body relationships;
- rebuild misaligned tables as 2D grids with ordered column_headers and equally
  sized row arrays, using null for visibly blank/unreadable cells;
- keep separately labelled dates and facts separate;
- preserve adaptive structure and return complete parseable JSON.
- compare the candidate against the coverage ledger block by block; no visible
  ledger paragraph, heading, caption, table row, cell, note, or footnote may
  disappear from the corrected JSON;
- preserve exact scientific glyphs. In particular ¹²C¹⁸O is not ¹²C¹⁶O and
  cm⁻¹ is not cm^1 or cm-1;
- count the visible number of table columns before correcting any row. Preserve
  interior empty cells as null and never shift later values left.

Never fill missing content from knowledge or patterns. Output one JSON value and
nothing else.

CANDIDATE JSON:
""".strip()


VALIDATION_REPAIR_PROMPT = r"""
The candidate JSON failed one or more lossless-output validations listed below.
Inspect the supplied page image and coverage ledger, then return the FULL
corrected JSON only.

Restore every omitted visible block at its correct position without deleting or
rewriting existing supported content. For each affected table, determine the
fixed column count from visible X positions, borders, headers, and neighboring
rows. Preserve every empty interior cell as null. Never move a later value left
merely to fill an empty cell. Keep exact Unicode text unchanged.

VIOLATIONS:
""".strip()


def _strip_json_fence(text: str) -> str:
    text = text.strip()
    fenced = re.fullmatch(r"```(?:json)?\s*(.*?)\s*```", text, flags=re.DOTALL | re.IGNORECASE)
    return fenced.group(1).strip() if fenced else text


def parse_json_output(text: str) -> Any:
    """Parse exactly one JSON value while tolerating accidental surrounding text."""
    cleaned = _strip_json_fence(text)
    try:
        return json.loads(cleaned)
    except json.JSONDecodeError as original_error:
        decoder = json.JSONDecoder()
        starts = [i for i, char in enumerate(cleaned) if char in "[{"]
        for start in starts:
            try:
                value, end = decoder.raw_decode(cleaned[start:])
                if not cleaned[start + end :].strip():
                    return value
            except json.JSONDecodeError:
                continue
        raise ValueError(f"Model did not return one complete JSON value: {original_error}") from original_error


def table_width_issues(value: Any, path: str = "$") -> list[str]:
    """Find table grids that could lose alignment; never silently reshape them."""
    issues: list[str] = []
    if isinstance(value, dict):
        rows = value.get("rows")
        headers = value.get("column_headers")
        if isinstance(rows, list) and rows:
            header_width = len(headers) if isinstance(headers, list) else 0
            row_widths = [len(row) for row in rows if isinstance(row, list)]
            width = header_width or (max(row_widths) if row_widths else 0)
            if len(row_widths) != len(rows):
                issues.append(f"{path}.rows contains a non-array row")
            if width:
                for index, row in enumerate(rows):
                    if isinstance(row, list) and len(row) != width:
                        issues.append(
                            f"{path}.rows[{index}] has {len(row)} cells; visible grid requires {width}"
                        )
        for key, child in value.items():
            issues.extend(table_width_issues(child, f"{path}.{key}"))
    elif isinstance(value, list):
        for index, child in enumerate(value):
            issues.extend(table_width_issues(child, f"{path}[{index}]"))
    return issues


def _all_strings(value: Any) -> list[str]:
    if isinstance(value, str):
        return [value]
    if isinstance(value, dict):
        strings: list[str] = []
        for child in value.values():
            strings.extend(_all_strings(child))
        return strings
    if isinstance(value, list):
        strings = []
        for child in value:
            strings.extend(_all_strings(child))
        return strings
    return []


def _visible_text(value: Any) -> str:
    return re.sub(r"\s+", " ", str(value or "")).strip()


def coverage_issues(candidate: Any, ledgers: Optional[list[Any]]) -> list[str]:
    """Detect omitted non-table blocks without normalizing their characters."""
    if not ledgers:
        return []
    candidate_text = _visible_text(" ".join(_all_strings(candidate)))
    issues: list[str] = []
    for page in ledgers:
        if not isinstance(page, dict):
            continue
        page_number = page.get("page_number", "?")
        for block in page.get("blocks", []):
            if not isinstance(block, dict) or block.get("kind") == "table":
                continue
            text = _visible_text(block.get("text"))
            if text and text not in candidate_text:
                excerpt = text if len(text) <= 120 else text[:117] + "..."
                issues.append(
                    f"page {page_number} omitted or altered {block.get('kind', 'text')} block: {excerpt!r}"
                )
    return issues


class DocumentOCR:
    def __init__(
        self,
        model_name: str = "Qwen/Qwen2-VL-7B-Instruct",
        max_new_tokens: int = 8192,
        max_pixels: int = 3_211_264,
        verify_output: bool = True,
        use_coverage_ledger: bool = True,
    ) -> None:
        print(f"Loading {model_name} with 4-bit quantization...")
        quantization_config = BitsAndBytesConfig(
            load_in_4bit=True,
            bnb_4bit_quant_type="nf4",
            bnb_4bit_compute_dtype=torch.float16,
            bnb_4bit_use_double_quant=True,
        )
        self.model = Qwen2VLForConditionalGeneration.from_pretrained(
            model_name,
            torch_dtype=torch.float16,
            device_map="auto",
            quantization_config=quantization_config,
        )
        self.model.eval()
        self.processor = AutoProcessor.from_pretrained(model_name)
        self.max_new_tokens = max_new_tokens
        self.max_pixels = max_pixels
        self.verify_output = verify_output
        self.use_coverage_ledger = use_coverage_ledger
        print("Model loaded.")

    def _render_pdf(self, pdf_path: str, temp_files: list[str]) -> list[str]:
        try:
            from pdf2image import convert_from_path
        except ImportError as exc:
            raise RuntimeError("PDF input requires pdf2image (and Poppler).") from exc

        # PNG avoids the character artifacts introduced by the old JPEG path.
        # Render densely before the processor applies its pixel budget. This
        # materially improves small superscripts, footnote markers, and table
        # punctuation compared with the former 220-DPI source.
        pages = convert_from_path(pdf_path, dpi=300, fmt="png")
        if not pages:
            raise ValueError(f"No pages found in PDF: {pdf_path}")

        image_paths: list[str] = []
        for page in pages:
            handle = tempfile.NamedTemporaryFile(suffix=".png", delete=False)
            handle.close()
            page.save(handle.name, "PNG", optimize=False)
            image_paths.append(handle.name)
            temp_files.append(handle.name)
        return image_paths

    def _image_content(self, image_paths: list[str]) -> list[dict[str, Any]]:
        content: list[dict[str, Any]] = []
        page_count = len(image_paths)
        for index, image_path in enumerate(image_paths, start=1):
            if page_count > 1:
                content.append({"type": "text", "text": f"PAGE {index} OF {page_count}"})
            content.append(
                {
                    "type": "image",
                    "image": image_path,
                    "max_pixels": self.max_pixels,
                }
            )
        return content

    def _generation_budget(self, input_token_count: int) -> int:
        context_limit = int(getattr(self.model.config, "max_position_embeddings", 32768))
        available = context_limit - input_token_count - 64
        if available < 1024:
            raise RuntimeError(
                f"Only {available} output tokens remain after image/input tokens. "
                "Reduce PDF pages per request or max_pixels; refusing to produce predictably truncated JSON."
            )
        return min(self.max_new_tokens, available)

    def _generate(self, content: list[dict[str, Any]]) -> str:
        messages = [{"role": "user", "content": content}]
        chat_text = self.processor.apply_chat_template(
            messages,
            tokenize=False,
            add_generation_prompt=True,
        )
        image_inputs, video_inputs = process_vision_info(messages)
        inputs = self.processor(
            text=[chat_text],
            images=image_inputs,
            videos=video_inputs,
            padding=True,
            return_tensors="pt",
        )
        device = next(self.model.parameters()).device
        inputs = inputs.to(device)
        budget = self._generation_budget(inputs.input_ids.shape[1])

        with torch.inference_mode():
            generated_ids = self.model.generate(
                **inputs,
                max_new_tokens=budget,
                do_sample=False,
                use_cache=True,
            )

        completion_ids = generated_ids[:, inputs.input_ids.shape[1] :]
        return self.processor.batch_decode(
            completion_ids,
            skip_special_tokens=True,
            clean_up_tokenization_spaces=False,
        )[0].strip()

    def _build_coverage_ledger(self, image_paths: list[str]) -> list[Any]:
        ledgers: list[Any] = []
        for page_number, image_path in enumerate(image_paths, start=1):
            print(f"Building exact transcription ledger for page {page_number}...")
            prompt = TRANSCRIPTION_LEDGER_PROMPT.replace(
                '"page_number": 1', f'"page_number": {page_number}', 1
            )
            ledger_text = self._generate(
                self._image_content([image_path])
                + [{"type": "text", "text": prompt}]
            )
            ledger = parse_json_output(ledger_text)
            if isinstance(ledger, dict):
                ledger["page_number"] = page_number
            ledgers.append(ledger)
        return ledgers

    def _repair_validation_issues(
        self,
        page_content: list[dict[str, Any]],
        candidate: Any,
        issues: list[str],
        coverage_ledger: Optional[list[Any]],
    ) -> Any:
        candidate_text = json.dumps(candidate, ensure_ascii=False, separators=(",", ":"))
        ledger_text = json.dumps(coverage_ledger, ensure_ascii=False, separators=(",", ":"))
        repair_text = self._generate(
            page_content
            + [
                {
                    "type": "text",
                    "text": (
                        f"{VALIDATION_REPAIR_PROMPT}\n"
                        f"{json.dumps(issues, ensure_ascii=False)}\n\n"
                        f"CANDIDATE JSON:\n{candidate_text}\n\n"
                        f"COVERAGE LEDGER:\n{ledger_text}"
                    ),
                }
            ]
        )
        repaired = parse_json_output(repair_text)
        remaining = table_width_issues(repaired) + coverage_issues(repaired, coverage_ledger)
        if remaining:
            raise ValueError(
                "Refusing to return incomplete or structurally unsafe transcription: "
                + "; ".join(remaining)
            )
        return repaired

    def extract_data(self, document_path: str) -> Any:
        source = Path(document_path)
        if not source.is_file():
            raise FileNotFoundError(f"Document not found: {document_path}")

        temp_files: list[str] = []
        try:
            image_paths = (
                self._render_pdf(str(source), temp_files)
                if source.suffix.lower() == ".pdf"
                else [str(source)]
            )
            page_content = self._image_content(image_paths)

            coverage_ledger: Optional[list[Any]] = None
            if self.use_coverage_ledger:
                coverage_ledger = self._build_coverage_ledger(image_paths)
            ledger_instruction = ""
            if coverage_ledger is not None:
                ledger_instruction = (
                    "\n\nLOSSLESS COVERAGE LEDGER (a second image-grounded reading; "
                    "use it to prevent omissions, but correct it when the image disagrees):\n"
                    + json.dumps(coverage_ledger, ensure_ascii=False, separators=(",", ":"))
                )

            print(f"Extracting all visible content from {len(image_paths)} page(s)...")
            draft_text = self._generate(
                page_content
                + [{"type": "text", "text": EXTRACTION_PROMPT + ledger_instruction}]
            )

            draft: Optional[Any]
            try:
                draft = parse_json_output(draft_text)
            except ValueError:
                draft = None

            # The same vision model audits its own draft against the image. This
            # remains a single-model pipeline and is especially useful for shifted
            # table cells and plausible-but-invented values.
            if self.verify_output:
                candidate = draft if draft is not None else draft_text
                candidate_text = json.dumps(candidate, ensure_ascii=False, separators=(",", ":"))
                print("Verifying transcription against the document image...")
                verified_text = self._generate(
                    page_content
                    + [
                        {
                            "type": "text",
                            "text": (
                                f"{VERIFICATION_PROMPT}\n{candidate_text}"
                                f"{ledger_instruction}"
                            ),
                        }
                    ]
                )
                verified = parse_json_output(verified_text)
                issues = table_width_issues(verified) + coverage_issues(verified, coverage_ledger)
                return (
                    self._repair_validation_issues(
                        page_content, verified, issues, coverage_ledger
                    )
                    if issues
                    else verified
                )

            if draft is None:
                raise ValueError("Initial extraction was incomplete or invalid JSON.")
            issues = table_width_issues(draft) + coverage_issues(draft, coverage_ledger)
            if issues:
                raise ValueError(
                    "Lossless validation failed with verification disabled: "
                    + "; ".join(issues)
                )
            return draft
        finally:
            for temp_file in temp_files:
                try:
                    os.remove(temp_file)
                except FileNotFoundError:
                    pass


# Backward-compatible name for callers that imported the original class.
MedicalOCR = DocumentOCR


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Faithful, schema-adaptive document extraction with Qwen2-VL."
    )
    parser.add_argument("document_path", help="Path to an image or PDF")
    parser.add_argument(
        "--max-new-tokens",
        type=int,
        default=8192,
        help="Maximum output tokens (default: 8192)",
    )
    parser.add_argument(
        "--max-pixels",
        type=int,
        default=3_211_264,
        help="Per-page vision pixel budget (default: 3211264)",
    )
    parser.add_argument(
        "--no-verify",
        action="store_true",
        help="Disable the second image-grounded verification pass",
    )
    parser.add_argument(
        "--no-ledger",
        action="store_true",
        help="Disable the independent page-by-page completeness ledger",
    )
    args = parser.parse_args()

    engine = DocumentOCR(
        max_new_tokens=args.max_new_tokens,
        max_pixels=args.max_pixels,
        verify_output=not args.no_verify,
        use_coverage_ledger=not args.no_ledger,
    )
    result = engine.extract_data(args.document_path)
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
