import os
from pathlib import Path

import yaml


def load_config(path: Path) -> dict:
    try:
        config = yaml.safe_load(path.read_text(encoding="utf-8"))
    except yaml.YAMLError as error:
        raise ValueError(f"Configuration is not valid YAML: {error}") from error

    if not isinstance(config, dict):
        raise ValueError("Configuration must be a YAML mapping")
    return _expand_environment(config)


def _expand_environment(value):
    if isinstance(value, dict):
        return {key: _expand_environment(item) for key, item in value.items()}
    if isinstance(value, list):
        return [_expand_environment(item) for item in value]
    if isinstance(value, str):
        return os.path.expandvars(value)
    return value
