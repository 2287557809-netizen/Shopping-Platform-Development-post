"""Render a Markdown report in docs/ to a printable HTML sheet.

The PDF is produced from that HTML by the headless browser:

    python scripts/build-report.py
    msedge --headless=new --print-to-pdf=docs/ShopStream-Design-Report.pdf \
           file:///.../docs/DESIGN-REPORT.html

Optional arguments allow the Chinese edition to be built from the same layout:

    python scripts/build-report.py --source DESIGN-REPORT-zh.md \
        --target DESIGN-REPORT-zh.html --title "ShopStream 设计报告" --lang zh
"""

import argparse
from pathlib import Path

import markdown

ROOT = Path(__file__).resolve().parent.parent
DOCS = ROOT / "docs"

CSS = """
@page { size: A4; margin: 16mm 14mm; }
body { font-family: {FONT}; font-size: 10.5pt; line-height: 1.55;
       color: #1f2937; max-width: 100%; }
h1 { font-size: 19pt; border-bottom: 2px solid #2563eb; padding-bottom: 4px; }
h2 { font-size: 15pt; margin-top: 22px; border-bottom: 1px solid #e5e7eb; padding-bottom: 3px;
     page-break-after: avoid; }
h3 { font-size: 12.5pt; margin-top: 16px; color: #1e3a8a; page-break-after: avoid; }
p { margin: 6px 0; }
img { max-width: 100%; border: 1px solid #cbd5e1; border-radius: 4px; margin: 8px 0 4px;
      page-break-inside: avoid; }
em { color: #475569; }
code { font-family: Consolas, "Courier New", monospace; font-size: 9.5pt;
       background: #f3f4f6; padding: 1px 4px; border-radius: 3px; }
pre { background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 4px;
      padding: 8px 10px; font-size: 9pt; white-space: pre-wrap; word-break: break-word;
      page-break-inside: avoid; }
pre code { background: none; padding: 0; }
table { border-collapse: collapse; width: 100%; font-size: 9.5pt; margin: 8px 0; }
th, td { border: 1px solid #cbd5e1; padding: 4px 6px; text-align: left; vertical-align: top; }
th { background: #eff6ff; }
tr { page-break-inside: avoid; }
figure, .keep { page-break-inside: avoid; }
.page-break { page-break-after: always; height: 0; margin: 0; }
"""

FONTS = {
    "en": '"Segoe UI", Arial, sans-serif',
    "zh": '"Microsoft YaHei", "Segoe UI", "SimSun", sans-serif',
}


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", default="DESIGN-REPORT.md")
    parser.add_argument("--target", default="DESIGN-REPORT.html")
    parser.add_argument("--title", default="ShopStream — Design Report")
    parser.add_argument("--lang", default="en", choices=sorted(FONTS))
    args = parser.parse_args()

    source = DOCS / args.source
    target = DOCS / args.target

    html_body = markdown.markdown(
        source.read_text(encoding="utf-8"),
        extensions=["fenced_code", "tables", "sane_lists"],
    )

    css = CSS.replace("{FONT}", FONTS[args.lang])
    if args.lang == "zh":
        css = css.replace("line-height: 1.55", "line-height: 1.7")

    target.write_text(
        f"<!doctype html><html lang='{args.lang}'><head><meta charset='utf-8'>"
        f"<title>{args.title}</title><style>{css}</style></head>"
        f"<body>{html_body}</body></html>",
        encoding="utf-8",
    )
    print(f"wrote {target}")


if __name__ == "__main__":
    main()
