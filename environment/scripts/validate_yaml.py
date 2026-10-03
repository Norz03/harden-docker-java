"""Simple utility to validate YAML syntax before processing."""
import sys
import yaml

def validate(filepath):
    try:
        with open(filepath, 'r') as f:
            yaml.safe_load(f)
        print(f"✅ Valid YAML syntax: {filepath}")
        return 0
    except Exception as e:
        print(f"❌ Invalid YAML syntax in {filepath}: {e}")
        return 1

if __name__ == "__main__":
    target = sys.argv[1] if len(sys.argv) > 1 else "/app/fixtures/docker-compose.yml"
    sys.exit(validate(target))