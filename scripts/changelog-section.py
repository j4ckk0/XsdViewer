#!/usr/bin/env python3
"""Prints the section of CHANGELOG.md for a version, as release notes ("## What's new" heading), or fails when there is none.

    scripts/changelog-section.py 3.4.0
"""
import re
import sys
from pathlib import Path

CHANGELOG = Path(__file__).resolve().parent.parent / 'CHANGELOG.md'


def section(version):
    text = CHANGELOG.read_text(encoding='utf-8')
    m = re.search(r'^## ' + re.escape(version) + r'\b[^\n]*\n(.*?)(?=^## |\Z)', text, re.M | re.S)
    return m.group(1).strip() if m else None


if __name__ == '__main__':
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    body = section(sys.argv[1])
    if not body:
        sys.exit('CHANGELOG.md has no section "## %s": write it first' % sys.argv[1])
    print("## What's new\n\n" + body)
