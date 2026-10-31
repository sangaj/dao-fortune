"""Render docs/PRIVACY_POLICY.md into a standalone HTML page.

Play needs the privacy policy on a **public URL** that a reviewer can open.
The Markdown file is the source of truth; this script renders it so the hosted
page can never drift from the document in the repository.

Only the Markdown that the policy actually uses is supported: headings,
paragraphs, bullet lists, pipe tables, `**bold**`, `*italic*` and horizontal
rules.

Usage:
    python tools/docs/make_privacy_html.py
    python tools/docs/make_privacy_html.py --check     # fail if the HTML is stale
"""

from __future__ import annotations

import argparse
import html
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
SOURCE = ROOT / "docs" / "PRIVACY_POLICY.md"
TARGET = ROOT / "docs" / "privacy-policy.html"

PAGE = """<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Privacy Policy — Dao Fortune</title>
<meta name="description" content="How Dao Fortune handles data: nothing is collected, the reading is computed on your device.">
<style>
  :root {{
    color-scheme: light dark;
    --bg: #0b0e13;
    --card: #141922;
    --text: #e9e7dc;
    --muted: #9aa3af;
    --gold: #d9b36b;
    --line: #232a35;
  }}
  @media (prefers-color-scheme: light) {{
    :root {{
      --bg: #f7f5ef;
      --card: #ffffff;
      --text: #1a1d23;
      --muted: #5c6470;
      --gold: #8a6a24;
      --line: #e2ded3;
    }}
  }}
  * {{ box-sizing: border-box; }}
  body {{
    margin: 0;
    padding: 40px 20px 72px;
    background: var(--bg);
    color: var(--text);
    font: 16px/1.65 -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
  }}
  main {{ max-width: 760px; margin: 0 auto; }}
  h1 {{ font-size: 30px; line-height: 1.25; margin: 0 0 6px; letter-spacing: -0.01em; }}
  h2 {{ font-size: 19px; margin: 34px 0 10px; color: var(--gold); letter-spacing: 0.02em; }}
  h3 {{ font-size: 16px; margin: 22px 0 8px; color: var(--text); }}
  p {{ margin: 0 0 14px; }}
  ul {{ margin: 0 0 16px; padding-left: 22px; }}
  li {{ margin-bottom: 8px; }}
  hr {{ border: 0; border-top: 1px solid var(--line); margin: 26px 0; }}
  table {{ width: 100%; border-collapse: collapse; margin: 0 0 18px; font-size: 15px; }}
  th, td {{ text-align: left; vertical-align: top; padding: 9px 10px; border-bottom: 1px solid var(--line); }}
  th {{ color: var(--muted); font-weight: 600; font-size: 13px; letter-spacing: 0.04em; text-transform: uppercase; }}
  code {{ background: var(--card); border: 1px solid var(--line); border-radius: 5px; padding: 1px 5px; font-size: 14px; }}
  .meta {{ color: var(--muted); font-size: 14px; margin-bottom: 22px; }}
  footer {{ margin-top: 44px; padding-top: 18px; border-top: 1px solid var(--line); color: var(--muted); font-size: 14px; }}
</style>
</head>
<body>
<main>
{body}
<footer>
  Dao Fortune — Daily Luopan Wisdom · <code>com.laki.daofortune</code><br>
  This page is generated from <code>docs/PRIVACY_POLICY.md</code> in the project's repository.
</footer>
</main>
</body>
</html>
"""


def inline(text: str) -> str:
    """Bold, italic and code spans."""
    escaped = html.escape(text)
    escaped = re.sub(r"\*\*(.+?)\*\*", r"<strong>\1</strong>", escaped)
    escaped = re.sub(r"(?<![\*\w])\*([^*\n]+)\*(?!\w)", r"<em>\1</em>", escaped)
    escaped = re.sub(r"`([^`]+)`", r"<code>\1</code>", escaped)
    return escaped


def render(markdown: str) -> str:
    lines = markdown.splitlines()
    out: list[str] = []
    index = 0

    while index < len(lines):
        line = lines[index].rstrip()

        if not line.strip():
            index += 1
            continue

        if line.strip() == "---":
            out.append("<hr>")
            index += 1
            continue

        heading = re.match(r"^(#{1,4})\s+(.*)$", line)
        if heading:
            level = len(heading.group(1))
            out.append(f"<h{level}>{inline(heading.group(2))}</h{level}>")
            index += 1
            continue

        # a pipe table: a header row, a separator row, then body rows
        if line.startswith("|") and index + 1 < len(lines) and re.match(r"^\|[\s:\-|]+\|$", lines[index + 1].strip()):
            header_cells = [cell.strip() for cell in line.strip().strip("|").split("|")]
            index += 2
            body_rows: list[list[str]] = []
            while index < len(lines) and lines[index].strip().startswith("|"):
                body_rows.append([cell.strip() for cell in lines[index].strip().strip("|").split("|")])
                index += 1
            out.append("<table><thead><tr>")
            out.extend(f"<th>{inline(cell)}</th>" for cell in header_cells)
            out.append("</tr></thead><tbody>")
            for row in body_rows:
                out.append("<tr>")
                out.extend(f"<td>{inline(cell)}</td>" for cell in row)
                out.append("</tr>")
            out.append("</tbody></table>")
            continue

        if line.lstrip().startswith(("- ", "* ")):
            out.append("<ul>")
            while index < len(lines) and lines[index].lstrip().startswith(("- ", "* ")):
                item = re.sub(r"^\s*[-*]\s+", "", lines[index])
                out.append(f"<li>{inline(item)}</li>")
                index += 1
            out.append("</ul>")
            continue

        # a paragraph: everything until a blank line
        paragraph: list[str] = []
        while index < len(lines) and lines[index].strip() and not re.match(r"^(#{1,4}\s|\||-\s|\*\s|---$)", lines[index].strip()):
            # a line that starts with ** begins a new block (the header fields
            # such as "**Effective date:** …" are one field per line)
            if paragraph and lines[index].lstrip().startswith("**"):
                break
            paragraph.append(lines[index].strip())
            index += 1
        if paragraph:
            text = " ".join(paragraph)
            if text.startswith("**") and text.endswith(":**"):
                out.append(f'<p class="meta">{inline(text)}</p>')
            else:
                out.append(f"<p>{inline(text)}</p>")

    return "\n".join(out)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true", help="fail if the HTML is out of date")
    args = parser.parse_args()

    markdown = SOURCE.read_text(encoding="utf-8")
    html_text = PAGE.format(body=render(markdown))

    if args.check:
        if not TARGET.exists() or TARGET.read_text(encoding="utf-8") != html_text:
            print("privacy-policy.html is stale - run: python tools/docs/make_privacy_html.py")
            return 1
        print("privacy-policy.html is up to date")
        return 0

    TARGET.write_text(html_text, encoding="utf-8")
    print(f"wrote {TARGET}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
