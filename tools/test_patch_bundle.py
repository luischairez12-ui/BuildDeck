import hashlib
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from create_patch_bundle import build_bundle


class PatchBundleTests(unittest.TestCase):
    def test_write_add_delete_and_unchanged(self):
        with tempfile.TemporaryDirectory() as tmp:
            old = Path(tmp) / 'old'
            new = Path(tmp) / 'new'
            old.mkdir(); new.mkdir()
            (old / 'settings.gradle.kts').write_text('old settings', encoding='utf-8')
            (new / 'settings.gradle.kts').write_text('new settings', encoding='utf-8')
            (old / 'delete.kt').write_text('delete me', encoding='utf-8')
            (new / 'created.kt').write_text('hello', encoding='utf-8')
            (old / 'unchanged.md').write_text('same', encoding='utf-8')
            (new / 'unchanged.md').write_text('same', encoding='utf-8')
            p = build_bundle(old, new, 'BuildDeck', title='Test', request_build=True)
            self.assertEqual(p['schema'], 'builddeck.patch.v1')
            self.assertTrue(p['requestBuild'])
            self.assertEqual(len(p['operations']), 3)
            lookup = {v['path']: v for v in p['operations']}
            self.assertEqual(lookup['created.kt']['expectedSha256'], 'absent')
            self.assertEqual(lookup['delete.kt']['op'], 'delete')
            self.assertEqual(lookup['settings.gradle.kts']['expectedSha256'],
                             hashlib.sha256(b'old settings').hexdigest())

    def test_skips_secrets_and_unsafe_files(self):
        with tempfile.TemporaryDirectory() as tmp:
            old = Path(tmp) / 'old'; new = Path(tmp) / 'new'
            old.mkdir(); new.mkdir()
            for file in ['local.properties', 'release.jks', '.env', 'README.md']:
                (new / file).write_text('sensitive' if file != 'README.md' else 'safe')
            p = build_bundle(old, new, 'BuildDeck')
            self.assertEqual([x['path'] for x in p['operations']], ['README.md'])

    def test_rejects_missing_target(self):
        with tempfile.TemporaryDirectory() as tmp:
            p = Path(tmp)
            with self.assertRaises(ValueError):
                build_bundle(p, p)


if __name__ == '__main__':
    unittest.main()
