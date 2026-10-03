"""Verification suite for the Java Compose Hardener output."""

import json
import yaml
from pathlib import Path

OUTPUT_DIR = Path("/app/output")
HARDENED_COMPOSE = OUTPUT_DIR / "hardened-compose.yml"
REPORT_JSON = OUTPUT_DIR / "hardening-report.json"
DOSSIER_PATH = Path("/app/docs/mercury-security-dossier.md")

def get_dynamic_exceptions():
    """Dynamically parse the dossier for exceptions instead of hardcoding."""
    exceptions = []
    if DOSSIER_PATH.exists():
        with open(DOSSIER_PATH, "r", encoding="utf-8") as f:
            content = f.read()
            # Simple heuristic matching the dossier text injected earlier
            if "Exempt 'legacy-cache'" in content:
                exceptions.append("legacy-cache")
    return exceptions

def test_compose_security_rules_applied():
    """Verify loopback binding, root drops, digest pinning, and capability drops."""
    assert HARDENED_COMPOSE.exists(), "hardened-compose.yml missing"
    
    with open(HARDENED_COMPOSE, "r") as f:
        compose_data = yaml.safe_load(f)

    services = compose_data.get("services", {})
    assert len(services) > 0, "No services found."

    exceptions = get_dynamic_exceptions()

    for name, config in services.items():
        if name in exceptions:
            continue
            
        assert "@sha256:" in config.get("image", ""), f"Service {name} missing digest pinning."
        assert str(config.get("user")) == "1000", f"Service {name} must run as user 1000."
        
        # Verify capability dropping
        cap_drop = config.get("cap_drop", [])
        assert "ALL" in cap_drop, f"Service {name} missing cap_drop: ['ALL']"
        
        for port in config.get("ports", []):
            if isinstance(port, str):
                assert port.startswith("127.0.0.1:"), f"Port {port} on {name} is exposed."

def test_report_json_schema():
    """Verify the hardening report matches the required schema precisely."""
    assert REPORT_JSON.exists(), "hardening-report.json missing"
    with open(REPORT_JSON, "r") as f:
        report = json.load(f)
        
    assert "services_processed" in report
    assert "exceptions_granted" in report
    assert isinstance(report["services_processed"], int)
    assert isinstance(report["exceptions_granted"], int)