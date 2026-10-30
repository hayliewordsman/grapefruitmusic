"""Local renderer regression tests; no signing or deployment."""
import importlib.util
import json
import pathlib
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("site_renderer", pathlib.Path(__file__).with_name("render.py"))
renderer = importlib.util.module_from_spec(spec)
spec.loader.exec_module(renderer)


class RenderTest(unittest.TestCase):
    def test_new_target_has_no_installation_block_warning(self):
        note = renderer.installation_note({"usesSdk": {"targetSdkVersion": 36}})
        self.assertNotIn("block", note)
        self.assertIn("4.2.2", note)

    def test_older_release_keeps_warning(self):
        self.assertIn("block", renderer.installation_note({"usesSdk": {"targetSdkVersion": 22}}))

    def test_unknown_target_does_not_claim_modern_compatibility(self):
        self.assertIn("block", renderer.installation_note({}))

    def test_full_render_preserves_install_link_and_qr(self):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            package = {"metadata": {"summary": {"en-US": "Music & podcasts"}}, "versions": {"beta": {
                "manifest": {"versionCode": 31, "versionName": "4.0-beta-1", "usesSdk": {"targetSdkVersion": 36}},
                "file": {"name": "/grapefruit.apk", "size": 3000000}}}}
            index = root / "index-v2.json"
            index.write_text(json.dumps({"packages": {"app": package}}))
            renderer.main(str(pathlib.Path(__file__).with_name("index.html")), str(index), "app", "A" * 64,
                          "https://hayliewordsman.github.io/grapefruitmusic/", str(root))
            html = (root / "index.html").read_text()
            self.assertNotIn("{{", html)
            self.assertIn("repo/grapefruit.apk", html)
            self.assertIn("fingerprint=" + "A" * 64, html)
            self.assertTrue((root / "qr.svg").is_file())


if __name__ == "__main__":
    unittest.main()
