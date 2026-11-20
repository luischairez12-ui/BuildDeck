#!/usr/bin/env python3
"""OPTIONAL LOCAL MCP PROTOTYPE — exports patches, never touches a phone directly.

Install the Python `mcp` SDK in a trusted Python virtual environment and start
this as a local stdio MCP server in a compatible desktop MCP client.

  export BUILDDECK_WORKSPACE=/path/to/exported/projects
  python bridge/local_mcp_bridge.py

Directory structure: BUILDDECK_WORKSPACE/BoreLab Field/{settings.gradle.kts,app/...}

No HTTP listener, GitHub token, Android permission or APK compiler is provided.
ChatGPT on a phone cannot automatically connect to this local stdio service.
"""
import hashlib
import json
import os
from pathlib import Path
import re
import uuid

try:
    from mcp.server.fastmcp import FastMCP
except ImportError as error:
    raise SystemExit('Install the optional MCP SDK first: python -m pip install mcp') from error

ROOT = Path(os.getenv('BUILDDECK_WORKSPACE', '')).expanduser().resolve()
if not os.environ.get('BUILDDECK_WORKSPACE') or not ROOT.is_dir():
    raise SystemExit('Set BUILDDECK_WORKSPACE to a folder containing exported project source folders')
OUT = ROOT / '_builddeck_outgoing'
OUT.mkdir(exist_ok=True)
mcp = FastMCP('BuildDeck Local Handoff')
STAGED = {}
ALLOWED_EXT = {'.kt', '.kts', '.java', '.xml', '.json', '.md', '.txt', '.properties', '.gradle', '.yaml', '.yml', '.sh', '.gitignore', '.toml'}
DISALLOWED = {'.git', '.gradle', '.idea', 'build', 'node_modules'}
BLOCK_NAMES = {'local.properties', '.env', 'keystore.properties', 'secrets.properties'}


def resolve_project(project: str) -> Path:
    if not project or project in ('.', '..') or '/' in project or '\\' in project:
        raise ValueError('Invalid project name')
    path = ROOT / project
    if not path.is_dir() or path.is_symlink() or path.resolve().parent != ROOT:
        raise ValueError('Project not found')
    return path


def resolve_source(project: str, relative: str) -> Path:
    base = resolve_project(project)
    parts = relative.split('/')
    if (not relative or relative.startswith('/') or '\\' in relative or ':' in relative or
        any(p in ('', '.', '..') or p in DISALLOWED for p in parts) or
        Path(relative).suffix.lower() not in ALLOWED_EXT or
        Path(relative).name.lower() in BLOCK_NAMES or
        Path(relative).name.lower().endswith(('.jks','.keystore','.pem','.key','.p12','.pfx'))):
        raise ValueError('Unsafe or unsupported source path')
    path = base.joinpath(*parts)
    if not path.resolve().is_relative_to(base.resolve()):
        raise ValueError('Path escapes source root')
    return path


@mcp.tool()
def list_projects() -> list[str]:
    """List local exported Android project folders in the configured workspace."""
    return sorted(p.name for p in ROOT.iterdir() if p.is_dir() and not p.is_symlink() and not p.name.startswith('_'))


@mcp.tool()
def list_source_files(project: str) -> list[str]:
    """List allowed source paths (ignores secrets and build artifacts)."""
    base = resolve_project(project)
    result = []
    for p in base.rglob('*'):
        if p.is_file() and not p.is_symlink():
            relative = p.relative_to(base).as_posix()
            try:
                resolve_source(project, relative)
                if p.stat().st_size <= 1_000_000:
                    result.append(relative)
            except ValueError:
                continue
    return sorted(result)[:500]


@mcp.tool()
def read_source(project: str, path: str) -> str:
    """Read one text source file from an explicitly exported project. No phone access."""
    file = resolve_source(project, path)
    if not file.is_file() or file.stat().st_size > 1_000_000:
        raise ValueError('Source file missing or too large')
    return file.read_text(encoding='utf-8')


@mcp.tool()
def stage_write(project: str, path: str, content: str) -> str:
    """Stage a source edit in memory; never modify source directly."""
    file = resolve_source(project, path)
    if len(content.encode('utf-8')) > 1_000_000:
        raise ValueError('Patch file over 1MB')
    original = file.read_bytes() if file.is_file() else None
    if original is not None and len(original) > 1_000_000:
        raise ValueError('Original source is too large')
    if len(STAGED.get(project, {})) >= 50 and path not in STAGED.get(project, {}):
        raise ValueError('Patch has more than 50 file operations')
    STAGED.setdefault(project, {})[path] = {'op': 'write', 'path': path,
        'expectedSha256': hashlib.sha256(original).hexdigest() if original is not None else 'absent',
        'content': content}
    return f'Staged {project}/{path}; nothing has been applied on the phone.'


@mcp.tool()
def stage_delete(project: str, path: str) -> str:
    """Stage deletion of one existing source file, subject to phone approval."""
    file = resolve_source(project, path)
    if not file.is_file() or file.stat().st_size > 1_000_000:
        raise ValueError('Existing small text file required')
    STAGED.setdefault(project, {})[path] = {'op': 'delete', 'path': path,
        'expectedSha256': hashlib.sha256(file.read_bytes()).hexdigest()}
    return f'Staged deletion {project}/{path}; source is unchanged.'


@mcp.tool()
def export_patch(project: str, title: str, summary: str = '', request_build: bool = False) -> str:
    """Write a reviewable patch JSON. User must import and approve it in BuildDeck."""
    resolve_project(project)
    operations = list(STAGED.get(project, {}).values())
    if not 1 <= len(operations) <= 50:
        raise ValueError('Stage 1 to 50 changes first')
    file = OUT / f'{uuid.uuid4()}.builddeck-patch.json'
    payload = {'schema': 'builddeck.patch.v1', 'requestId': str(uuid.uuid4()),
               'projectName': project, 'title': title[:100], 'summary': summary[:500], 'requestBuild': request_build,
               'operations': operations}
    file.write_text(json.dumps(payload, indent=2, ensure_ascii=False), encoding='utf-8')
    STAGED.pop(project, None)
    return f'Patch saved to {file}. Transfer it to the phone; BuildDeck requires human approval.'


if __name__ == '__main__':
    mcp.run(transport='stdio')
