#!/usr/bin/env python3
"""Build the Word review copy from doc/user-manual.md (Pandoc + python-docx)."""

import argparse
from pathlib import Path
import shutil
import subprocess
import tempfile

from docx import Document
from docx.enum.style import WD_STYLE_TYPE
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Inches, Pt, RGBColor


def reference_document(path):
    # Preserve Pandoc's required table, list and hyperlink styles.
    with path.open("wb") as destination:
        subprocess.run(["pandoc", "--print-default-data-file=reference.docx"], stdout=destination, check=True)
    document = Document(path)
    section = document.sections[0]
    section.page_width, section.page_height = Inches(8.5), Inches(11)
    section.top_margin = section.bottom_margin = Inches(0.65)
    section.left_margin = section.right_margin = Inches(0.7)
    section.header_distance = section.footer_distance = Inches(0.25)
    normal = document.styles["Normal"]
    normal.font.name, normal.font.size = "Arial", Pt(10.5)
    normal.paragraph_format.space_after = Pt(6)
    normal.paragraph_format.line_spacing = 1.08
    for name, size in [("Title", 26), ("Heading 1", 20), ("Heading 2", 14), ("Heading 3", 12)]:
        style = document.styles.get_by_id(name.replace(" ", ""), WD_STYLE_TYPE.PARAGRAPH)
        style.font.name, style.font.size = "Arial", Pt(size)
        style.font.color.rgb = RGBColor.from_string("20566A")
        style.paragraph_format.keep_with_next = True
    document.styles.get_by_id("Caption", WD_STYLE_TYPE.PARAGRAPH).font.size = Pt(9)
    document.save(path)


def format_review_copy(path):
    document = Document(path)
    section = document.sections[0]
    usable_width = section.page_width - section.left_margin - section.right_margin
    first_title = True
    for paragraph in document.paragraphs:
        if first_title and paragraph.style.style_id == "Heading1":
            paragraph.style = document.styles.get_by_id("Title", WD_STYLE_TYPE.PARAGRAPH)
            first_title = False
        elif paragraph.text == "Contents":
            paragraph.style = document.styles.get_by_id("Heading1", WD_STYLE_TYPE.PARAGRAPH)
            paragraph.paragraph_format.page_break_before = True
        elif paragraph.style.style_id == "Heading1":
            paragraph.paragraph_format.page_break_before = True
        if paragraph._p.xpath(".//w:drawing"):
            paragraph.alignment = WD_ALIGN_PARAGRAPH.CENTER
            paragraph.paragraph_format.keep_with_next = True
        if paragraph.text.startswith("Illustration:"):
            paragraph.style = document.styles.get_by_id("Caption", WD_STYLE_TYPE.PARAGRAPH)
    for image in document.inline_shapes:
        if image.width > usable_width:
            image.height = int(image.height * usable_width / image.width)
            image.width = usable_width
    for table in document.tables:
        table.style = document.styles.get_by_id("Table", WD_STYLE_TYPE.TABLE)
        table.autofit = False
        count = len(table.columns)
        proportions = {2: [0.29, 0.71], 3: [0.26, 0.48, 0.26]}.get(count, [1 / count] * count)
        for index, fraction in enumerate(proportions):
            table.columns[index].width = int(usable_width * fraction)
            for cell in table.columns[index].cells:
                cell.width = int(usable_width * fraction)
        for row_index, row in enumerate(table.rows):
            properties = row._tr.get_or_add_trPr()
            if not properties.findall(qn("w:cantSplit")):
                properties.append(OxmlElement("w:cantSplit"))
            if row_index == 0 and not properties.findall(qn("w:tblHeader")):
                properties.append(OxmlElement("w:tblHeader"))
            for cell in row.cells:
                if row_index == 0:
                    shade = OxmlElement("w:shd")
                    shade.set(qn("w:fill"), "EAF2F6")
                    cell._tc.get_or_add_tcPr().append(shade)
                for paragraph in cell.paragraphs:
                    paragraph.paragraph_format.space_after = Pt(4)
                    for run in paragraph.runs:
                        run.font.name, run.font.size = "Arial", Pt(9)
                        if row_index == 0:
                            run.bold = True
    section.different_first_page_header_footer = True
    header = section.header.paragraphs[0]
    header.text = "SCA Ledger User Manual  |  Working draft"
    header.runs[0].font.size = Pt(8)
    footer = section.footer.paragraphs[0]
    footer.alignment = WD_ALIGN_PARAGRAPH.RIGHT
    footer.add_run("Page ").font.size = Pt(8)
    page = OxmlElement("w:fldSimple")
    page.set(qn("w:instr"), "PAGE")
    footer._p.append(page)
    document.core_properties.title = "SCA Ledger User Manual"
    document.core_properties.subject = "First working draft for user review"
    document.core_properties.author = "SCA Ledger documentation"
    document.core_properties.last_modified_by = "SCA Ledger documentation"
    document.save(path)


def main():
    root = Path(__file__).resolve().parents[1]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=root / "doc/user-manual.docx")
    arguments = parser.parse_args()
    if shutil.which("pandoc") is None:
        parser.error("Pandoc is required; install it before generating the review copy.")
    output = arguments.output.resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="sca-user-manual-") as temporary:
        reference = Path(temporary) / "reference.docx"
        rendered = Path(temporary) / "manual.docx"
        reference_document(reference)
        subprocess.run([
            "pandoc", str(root / "doc/user-manual.md"), "--from=gfm", "--to=docx",
            "--resource-path=" + str(root / "doc"), "--reference-doc=" + str(reference),
            "--output=" + str(rendered),
        ], check=True)
        format_review_copy(rendered)
        shutil.copyfile(rendered, output)
    print(output)


if __name__ == "__main__":
    main()
