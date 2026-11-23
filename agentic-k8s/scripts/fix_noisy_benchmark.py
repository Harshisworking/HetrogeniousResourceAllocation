#!/usr/bin/env python3
"""Remove ONLY the known accidentally injected print that floods comparison runs.

Idempotent and conservative: never rewrite unrelated benchmark logic.
"""
from pathlib import Path
import ast
import sys

path=Path('scripts/compare_scalers.py')
if not path.is_file():
    sys.exit('ERROR: run this from the agentic-k8s repository root')
source=path.read_text(encoding='utf-8')
noise='''        print(
            f"[BENCHMARK] {time.strftime('%H:%M:%S')} "
            "Experiment running — collecting Kubernetes observations...",
            flush=True,
        )
'''
if noise not in source:
    print('No known noisy progress patch found; existing script unchanged.')
else:
    position=source.index(noise)+len(noise)
    # The original 5-second sleep may have been accidentally removed when the
    # progress-print patch was pasted. Restore pacing if needed; otherwise
    # remove only the noisy print and leave the existing sleep unchanged.
    following=source[position:].splitlines()
    already_sleeps=bool(following and following[0].strip()=='time.sleep(5)')
    replacement='' if already_sleeps else '        time.sleep(5)\n'
    revised=source.replace(noise,replacement,1)
    ast.parse(revised)
    backup=path.with_suffix('.py.before_batch3')
    if backup.exists():
        print('Existing backup preserved:',backup)
    else:
        backup.write_text(source,encoding='utf-8')
    path.write_text(revised,encoding='utf-8')
    print('Removed noisy progress print; preserved/restored 5-second polling. Backup:',backup)
