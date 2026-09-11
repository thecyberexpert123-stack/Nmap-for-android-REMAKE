#!/usr/bin/env python3
"""Posts the tail of a log file as a GitHub check-run annotation.

The development sandbox has no egress to the Actions log-storage host, so
this relays step output through api.github.com (reachable from there).

Usage: post-annotation.py LOG_PATH TITLE [CONCLUSION=failure] [LEVEL=failure] [MAX_CHARS=12000]
"""
import json
import os
import sys
import urllib.request

log_path = sys.argv[1]
title = sys.argv[2] if len(sys.argv) > 2 else "Step details"
conclusion = sys.argv[3] if len(sys.argv) > 3 else "failure"
level = sys.argv[4] if len(sys.argv) > 4 else "failure"
max_chars = int(sys.argv[5]) if len(sys.argv) > 5 else 12000

try:
    with open(log_path, errors="replace") as handle:
        tail = handle.read()[-max_chars:]
except OSError as error:
    tail = f"(could not read {log_path}: {error})"
if not tail.strip():
    tail = "(no output captured)"

body = json.dumps({
    "name": "step-details",
    "head_sha": os.environ["GITHUB_SHA"],
    "status": "completed",
    "conclusion": conclusion,
    "output": {
        "title": title,
        "summary": "Captured step output.",
        "annotations": [
            {
                "path": log_path,
                "start_line": 1,
                "end_line": 1,
                "annotation_level": level,
                "message": tail,
            },
        ],
    },
}).encode()

request = urllib.request.Request(
    f"https://api.github.com/repos/{os.environ['GITHUB_REPOSITORY']}/check-runs",
    data=body,
    headers={
        "Authorization": f"Bearer {os.environ['GITHUB_TOKEN']}",
        "Accept": "application/vnd.github+json",
        "X-GitHub-Api-Version": "2022-11-28",
    },
)
with urllib.request.urlopen(request, timeout=30) as response:
    print("check-run posted:", response.status)
