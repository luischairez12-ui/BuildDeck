#!/usr/bin/env python3
"""Make a BuildDeck approval-first JSON patch from two project directories.

Example:
 python tools/create_patch_bundle.py --before /tmp/old --after /tmp/new \
   --project-name "BoreLab Field" --title "Repair calendar" --output calendar.patch.json

Each changed text file carries its previous SHA-256 or 'absent' precondition.
BuildDeck rejects stale edits and requires human approval before applying.
"""
import argparse
import hashlib
import json
import pathlib
import uuid

ALLOWED_EXTS = {'.kt', '.java', '.kts', '.xml', '.json', '.md', '.txt', '.properties', '.gradle', '.yaml', '.yml', '.sh', '.gitignore', '.toml'}
FORBIDDEN_PARTS = {'.git', '.gradle', '.idea', 'build', 'node_modules'}
FORBIDDEN_NAMES = {'local.properties', '.env', 'keystore.properties', 'secrets.properties', 'gradle-wrapper.jar'}
FORBIDDEN_SUFFIX = ('.jks', '.keystore', '.pem', '.key', '.p12', '.pfx')
MAX_FILE = 1_000_000


def is_safe(path: pathlib.Path):
    return (
        path.parts and not any(part in FORBIDDEN_PARTS or part in ('', '.', '..') for part in path.parts)
        and path.name.lower() not in FORBIDDEN_NAMES
        and not path.name.lower().endswith(FORBIDDEN_SUFFIX)
        and path.suffix.lower() in ALLOWED_EXTS
    )


def files(directory):
    directory = pathlib.Path(directory).resolve(strict=True)
    result = {}
    for path in directory.rglob('*'):
        if not path.is_file() or path.is_symlink():
            continue
        relative = path.relative_to(directory)
        if not is_safe(relative):
            continue
        if path.stat().st_size > MAX_FILE:
            continue
        data = path.read_bytes()
        try:
            content = data.decode('utf-8')
        except UnicodeDecodeError:
            continue
        result[relative.as_posix()] = (data, content)
    return result


def build_bundle(before_dir, after_dir, project_name=None, project_id=None, title='AI changes', summary='', request_build=False):
    if not project_id and not project_name:
        raise ValueError('project_name or project_id required')
    old, new = files(before_dir), files(after_dir)
    operations = []
    for path in sorted(old.keys() | new.keys()):
        older = old.get(path)
        newer = new.get(path)
        if older == newer:
            continue
        if older is None:
            operations.append({'op': 'write', 'path': path, 'expectedSha256': 'absent', 'content': newer[1]})
        elif newer is None:
            operations.append({'op': 'delete', 'path': path, 'expectedSha256': hashlib.sha256(older[0]).hexdigest()})
        else:
            operations.append({'op': 'write', 'path': path, 'expectedSha256': hashlib.sha256(older[0]).hexdigest(), 'content': newer[1]})
    if not 1 <= len(operations) <= 50:
        raise ValueError(f'Expected 1 to 50 changed text files, got {len(operations)}')
    return {'schema': 'builddeck.patch.v1', 'requestId': str(uuid.uuid4()),
            'title': title[:100], 'summary': summary[:500],
            **({'projectId': project_id} if project_id else {}),
            **({'projectName': project_name} if project_name else {}),
            'requestBuild': request_build, 'operations': operations}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--before', required=True)
    parser.add_argument('--after', required=True)
    parser.add_argument('--project-name')
    parser.add_argument('--project-id')
    parser.add_argument('--title', default='AI changes')
    parser.add_argument('--summary', default='')
    parser.add_argument('--request-build', action='store_true', help='Request a separate confirmed build after applying')
    parser.add_argument('--output', required=True)
    args = parser.parse_args()
    bundle = build_bundle(args.before, args.after, args.project_name, args.project_id, args.title, args.summary, args.request_build)
    pathlib.Path(args.output).write_text(json.dumps(bundle, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')
    print(f"Wrote {args.output} ({len(bundle['operations'])} changes)")


if __name__ == '__main__':
    main()
