import ast
import sys
import unittest
from pathlib import Path
from fastapi import HTTPException


SERVICE_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(SERVICE_ROOT))
import internal_auth


class EkycSecurityContractTest(unittest.TestCase):
    def test_internal_credential_is_fail_closed(self):
        original = internal_auth.INTERNAL_TOKEN
        try:
            internal_auth.INTERNAL_TOKEN = "expected-internal-token"
            with self.assertRaises(HTTPException) as missing:
                internal_auth.require_internal_token(None)
            self.assertEqual(401, missing.exception.status_code)
            with self.assertRaises(HTTPException) as wrong:
                internal_auth.require_internal_token("wrong-token")
            self.assertEqual(401, wrong.exception.status_code)
            self.assertIsNone(internal_auth.require_internal_token("expected-internal-token"))
            internal_auth.INTERNAL_TOKEN = ""
            with self.assertRaises(HTTPException) as unconfigured:
                internal_auth.require_internal_token("anything")
            self.assertEqual(503, unconfigured.exception.status_code)
        finally:
            internal_auth.INTERNAL_TOKEN = original

    def test_protected_routes_require_internal_token(self):
        source = (SERVICE_ROOT / "app.py").read_text(encoding="utf-8")
        tree = ast.parse(source)
        protected = {"process_cccd_ocr", "match_live_face"}
        found = set()
        for node in ast.walk(tree):
            if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef)) and node.name in protected:
                decorator_text = " ".join(ast.unparse(item) for item in node.decorator_list)
                self.assertIn("Depends(require_internal_token)", decorator_text)
                found.add(node.name)
        self.assertEqual(protected, found)

    def test_api_models_do_not_expose_embeddings(self):
        source = (SERVICE_ROOT / "app.py").read_text(encoding="utf-8")
        self.assertNotIn("card_face_embedding", source)
        self.assertNotIn("live_embedding", source)

    def test_face_matcher_has_no_request_shared_embedding_state(self):
        source = (SERVICE_ROOT / "biometric_module" / "face_matcher.py").read_text(encoding="utf-8")
        tree = ast.parse(source)
        assigned_attributes = {
            node.attr
            for node in ast.walk(tree)
            if isinstance(node, ast.Attribute) and isinstance(node.ctx, ast.Store)
        }
        self.assertNotIn("id_embedding", assigned_attributes)
        self.assertIn("reference_embedding", source)


if __name__ == "__main__":
    unittest.main()
