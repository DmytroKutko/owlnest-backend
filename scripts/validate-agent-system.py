#!/usr/bin/env python3
"""Validate OwlNest's project-local Codex system and objective source boundaries."""

from __future__ import annotations

import glob
import re
import subprocess
import sys
import tomllib
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]

EXPECTED_AGENTS = {
    "business_analyst",
    "requirements_acceptance_analyst",
    "project_rules_scout",
    "repository_domain_explorer",
    "spring_docs_researcher",
    "domain_architect",
    "application_architect",
    "api_contract_designer",
    "postgres_data_modeler",
    "persistence_jpa_reviewer",
    "redis_cache_architect",
    "transaction_consistency_reviewer",
    "security_authorization_reviewer",
    "integration_resilience_reviewer",
    "performance_concurrency_reviewer",
    "observability_production_reviewer",
    "backend_test_engineer",
    "spring_backend_developer",
    "architecture_conformance_reviewer",
    "spring_code_reviewer",
    "database_migration_reviewer",
    "api_integration_qa",
    "functional_regression_qa",
    "ci_deployment_engineer",
    "release_gatekeeper",
}

WORKSPACE_WRITE_AGENTS = {
    "backend_test_engineer",
    "spring_backend_developer",
    "api_integration_qa",
    "functional_regression_qa",
    "ci_deployment_engineer",
    "release_gatekeeper",
}

EXPECTED_SKILLS = {
    "spring-orchestrated-feature",
    "business-idea-to-feature-spec",
    "spring-architecture-conformance",
    "spring-api-contract",
    "postgres-jpa-migration-workflow",
    "redis-consistency-workflow",
    "spring-security-gate",
    "spring-testing-regression",
    "spring-ci-deployment",
    "spring-release-gate",
    "project-documentation-sync",
}

REQUIRED_DOCS = {
    "README.md",
    "project-profile.md",
    "business-analysis-guide.md",
    "domain-map.md",
    "architecture-invariants.md",
    "reference-features.md",
    "api-conventions.md",
    "postgres-conventions.md",
    "redis-conventions.md",
    "security-conventions.md",
    "testing-strategy.md",
    "observability-and-operations.md",
    "workflow-routing.md",
    "handoff-contracts.md",
    "validation-matrix.md",
    "maintenance-guide.md",
    "proposed-deterministic-checks.md",
}

SUPPORTED_AGENT_KEYS = {
    "name",
    "description",
    "developer_instructions",
    "sandbox_mode",
}


class Validation:
    def __init__(self) -> None:
        self.failures: list[str] = []
        self.passes: list[str] = []

    def require(self, condition: bool, message: str) -> None:
        if condition:
            self.passes.append(message)
        else:
            self.failures.append(message)


def parse_toml(path: Path, validation: Validation) -> dict[str, object]:
    try:
        with path.open("rb") as source:
            return tomllib.load(source)
    except (OSError, tomllib.TOMLDecodeError) as exception:
        validation.failures.append(f"valid TOML: {path.relative_to(ROOT)} ({exception})")
        return {}


def parse_frontmatter(path: Path, validation: Validation) -> dict[str, str]:
    text = path.read_text(encoding="utf-8")
    lines = text.splitlines()
    if not lines or lines[0] != "---":
        validation.failures.append(f"frontmatter starts with ---: {path.relative_to(ROOT)}")
        return {}

    try:
        closing = lines.index("---", 1)
    except ValueError:
        validation.failures.append(f"frontmatter closes with ---: {path.relative_to(ROOT)}")
        return {}

    values: dict[str, str] = {}
    for line in lines[1:closing]:
        if not line.strip():
            continue
        match = re.fullmatch(r"([A-Za-z0-9_-]+):\s*(.+)", line)
        if match is None:
            validation.failures.append(
                f"simple valid frontmatter entry in {path.relative_to(ROOT)}: {line!r}"
            )
            continue
        key, value = match.groups()
        values[key] = value.strip().strip('"').strip("'")
    return values


def validate_config_and_agents(validation: Validation) -> None:
    config_path = ROOT / ".codex/config.toml"
    validation.require(config_path.is_file(), ".codex/config.toml exists")
    config = parse_toml(config_path, validation) if config_path.is_file() else {}
    agents_config = config.get("agents", {})
    validation.require(isinstance(agents_config, dict), "config has [agents] table")
    if isinstance(agents_config, dict):
        validation.require(agents_config.get("max_threads") == 6, "agents.max_threads is 6")
        validation.require(agents_config.get("max_depth") == 1, "agents.max_depth is 1")

    agent_files = sorted((ROOT / ".codex/agents").glob("*.toml"))
    validation.require(len(agent_files) == 25, "exactly 25 custom-agent TOML files exist")

    names: list[str] = []
    for path in agent_files:
        data = parse_toml(path, validation)
        unknown = set(data) - SUPPORTED_AGENT_KEYS
        validation.require(not unknown, f"{path.name} uses only supported keys")

        for required_key in ("name", "description", "developer_instructions"):
            value = data.get(required_key)
            validation.require(
                isinstance(value, str) and bool(value.strip()),
                f"{path.name} has non-empty {required_key}",
            )

        name = data.get("name")
        if isinstance(name, str):
            names.append(name)
            validation.require(path.stem == name, f"{path.name} filename matches agent name")

        validation.require("model" not in data, f"{path.name} does not pin model")
        validation.require(
            "model_reasoning_effort" not in data,
            f"{path.name} does not pin model reasoning",
        )

        expected_sandbox = "workspace-write" if name in WORKSPACE_WRITE_AGENTS else "read-only"
        validation.require(
            data.get("sandbox_mode") == expected_sandbox,
            f"{path.name} has expected {expected_sandbox} sandbox",
        )
        instructions = data.get("developer_instructions", "")
        validation.require(
            isinstance(instructions, str) and "do not spawn subagents" in instructions.lower(),
            f"{path.name} forbids nested subagents",
        )

    validation.require(set(names) == EXPECTED_AGENTS, "agent names match the required catalog")
    validation.require(len(names) == len(set(names)), "agent names are unique")


def validate_skills_and_docs(validation: Validation) -> None:
    skills_root = ROOT / ".agents/skills"
    skill_dirs = {path.name for path in skills_root.iterdir() if path.is_dir()}
    validation.require(skill_dirs == EXPECTED_SKILLS, "project skill directories match catalog")

    for skill_name in sorted(EXPECTED_SKILLS):
        path = skills_root / skill_name / "SKILL.md"
        validation.require(path.is_file(), f"{skill_name}/SKILL.md exists")
        if not path.is_file():
            continue
        frontmatter = parse_frontmatter(path, validation)
        validation.require(frontmatter.get("name") == skill_name, f"{skill_name} frontmatter name matches")
        description = frontmatter.get("description", "")
        validation.require(len(description) >= 30, f"{skill_name} has routing-oriented description")

    docs_root = ROOT / "docs/agent-system"
    actual_docs = {path.name for path in docs_root.glob("*.md")}
    validation.require(REQUIRED_DOCS <= actual_docs, "all required agent-system documents exist")

    path_prefixes = ("src/", "docs/", ".codex/", ".agents/", "API/", "scripts/")
    for doc_path in sorted(docs_root.glob("*.md")):
        text = doc_path.read_text(encoding="utf-8")
        for reference in re.findall(r"`([^`\n]+)`", text):
            if not reference.startswith(path_prefixes):
                continue
            if any(marker in reference for marker in ("<", ">", "{", "}")):
                continue
            if "*" in reference:
                matches = glob.glob(str(ROOT / reference), recursive=True)
                validation.require(bool(matches), f"documented glob resolves: {reference}")
            elif not any(character.isspace() for character in reference):
                validation.require((ROOT / reference).exists(), f"documented path exists: {reference}")


def java_package(text: str) -> str | None:
    match = re.search(r"^package\s+([A-Za-z0-9_.]+);", text, re.MULTILINE)
    return match.group(1) if match else None


def java_imports(text: str) -> set[str]:
    return set(re.findall(r"^import\s+(?:static\s+)?([A-Za-z0-9_.*]+);", text, re.MULTILINE))


def feature_name(package_name: str | None) -> str | None:
    if package_name is None:
        return None
    parts = package_name.split(".")
    return parts[3] if len(parts) > 3 and parts[:3] == ["dev", "dkutko", "owlnest"] else None


def validate_java_boundaries(validation: Validation) -> None:
    main_files = sorted((ROOT / "src/main/java").rglob("*.java"))
    test_files = sorted((ROOT / "src/test/java").rglob("*.java"))

    entity_types: set[str] = set()
    for path in main_files:
        text = path.read_text(encoding="utf-8")
        package_name = java_package(text)
        public_type = re.search(r"^public\s+(?:final\s+)?(?:class|interface|record|enum)\s+(\w+)", text, re.MULTILINE)
        if "@Entity" in text and package_name and public_type:
            entity_types.add(f"{package_name}.{public_type.group(1)}")

    for path in main_files + test_files:
        text = path.read_text(encoding="utf-8")
        imports = java_imports(text)
        relative = path.relative_to(ROOT)
        validation.require(
            not any(import_name.endswith(".*") for import_name in imports),
            f"no wildcard imports: {relative}",
        )

        public_types = re.findall(
            r"^public\s+(?:final\s+)?(?:class|interface|record|enum)\s+(\w+)",
            text,
            re.MULTILINE,
        )
        validation.require(len(public_types) <= 1, f"at most one public top-level type: {relative}")
        if public_types:
            validation.require(public_types[0] == path.stem, f"public type matches filename: {relative}")

    for path in main_files:
        text = path.read_text(encoding="utf-8")
        imports = java_imports(text)
        package_name = java_package(text)
        current_feature = feature_name(package_name)
        relative = path.relative_to(ROOT)
        is_controller = "/controller/" in path.as_posix() or "@RestController" in text
        is_domain = "/domain/" in path.as_posix()

        if is_controller:
            validation.require(
                not any(
                    ".repository." in import_name and not import_name.endswith("Exception")
                    for import_name in imports
                ),
                f"controller does not import a repository API/adapter: {relative}",
            )
            validation.require(
                not (imports & entity_types),
                f"controller does not import a known JPA entity: {relative}",
            )

        if is_domain:
            forbidden_domain_import = any(
                import_name.startswith("org.springframework.")
                or re.search(r"dev\.dkutko\.owlnest\.[^.]+\.(controller|repository|security)\.", import_name)
                for import_name in imports
            )
            validation.require(not forbidden_domain_import, f"domain avoids infrastructure imports: {relative}")

        for import_name in imports:
            match = re.match(r"dev\.dkutko\.owlnest\.([^.]+)\.repository\.", import_name)
            if match and current_feature:
                validation.require(
                    match.group(1) == current_feature,
                    f"no cross-feature repository import: {relative} -> {import_name}",
                )

        if "@Transactional" in text:
            validation.require("/service/" in path.as_posix(), f"@Transactional is in service package: {relative}")


def validate_migrations_and_secrets(validation: Validation) -> None:
    migrations = sorted((ROOT / "src/main/resources/db/migration").glob("*.sql"))
    migration_pattern = re.compile(r"V[1-9][0-9]*__[a-z0-9]+(?:_[a-z0-9]+)*\.sql")
    for migration in migrations:
        validation.require(bool(migration_pattern.fullmatch(migration.name)), f"valid migration name: {migration.name}")

    build_text = (ROOT / "build.gradle.kts").read_text(encoding="utf-8").lower()
    validation.require("com.h2database" not in build_text, "H2 is not an application or test dependency")

    secret_patterns = [".env", "API/*.postman_environment.json", "*.key", "*.p12", "*.pfx", "*.jks"]
    command = ["git", "ls-files", "--", *secret_patterns]
    result = subprocess.run(command, cwd=ROOT, check=False, capture_output=True, text=True)
    validation.require(result.returncode == 0, "git can inspect tracked secret patterns")
    validation.require(not result.stdout.strip(), "no local secret/environment files are tracked")

    for ignored_path in (".env", "API/OwlNest.local.postman_environment.json"):
        result = subprocess.run(
            ["git", "check-ignore", "--no-index", "-q", ignored_path],
            cwd=ROOT,
            check=False,
        )
        validation.require(result.returncode == 0, f"{ignored_path} is ignored")


def validate_evidence_literals(validation: Validation) -> None:
    checks = {
        "build.gradle.kts": ['version "4.1.0"', "JavaLanguageVersion.of(21)", "spring-boot-starter-data-redis"],
        "src/main/resources/db/migration/V1__create_identity_and_profile_tables.sql": [
            "CREATE TABLE identity_account",
            "CREATE TABLE profile",
            "uq_profile_username_lower",
        ],
        "src/main/resources/db/migration/V2__add_profile_onboarding_fields.sql": [
            "onboarding_completed",
            "ck_profile_gender",
        ],
        "src/main/java/dev/dkutko/owlnest/presence/repository/RedisPresenceRepository.java": [
            '"presence:account:"',
            "lastActivityAt.toString()",
        ],
        "src/main/java/dev/dkutko/owlnest/presence/service/PresenceService.java": [
            "Duration.ofSeconds(90)",
            "PresenceStatus.UNKNOWN",
        ],
        "src/main/java/dev/dkutko/owlnest/identity/security/SecurityConfiguration.java": [
            '"/api/v1/**"',
            "SessionCreationPolicy.STATELESS",
        ],
        "src/main/java/dev/dkutko/owlnest/profile/controller/CurrentProfileController.java": [
            '"/api/v1/profile"',
            '"/me"',
        ],
        "src/main/java/dev/dkutko/owlnest/profile/controller/PublicProfileController.java": [
            '"/api/v1/profiles"',
            '"/{accountId}"',
        ],
        "src/main/java/dev/dkutko/owlnest/presence/controller/PresenceController.java": [
            '"/api/v1/presence"',
            '"/heartbeat"',
        ],
    }
    for relative_path, literals in checks.items():
        path = ROOT / relative_path
        validation.require(path.is_file(), f"evidence file exists: {relative_path}")
        if not path.is_file():
            continue
        text = path.read_text(encoding="utf-8")
        for literal in literals:
            validation.require(literal in text, f"evidence literal present: {relative_path} -> {literal}")


def main() -> int:
    validation = Validation()
    validate_config_and_agents(validation)
    validate_skills_and_docs(validation)
    validate_java_boundaries(validation)
    validate_migrations_and_secrets(validation)
    validate_evidence_literals(validation)

    if validation.failures:
        print("Agent-system validation failed:")
        for failure in validation.failures:
            print(f"  FAIL: {failure}")
        print(f"\n{len(validation.failures)} failure(s); {len(validation.passes)} checks passed.")
        return 1

    print(
        "Agent-system validation passed: "
        f"25 agents, {len(EXPECTED_SKILLS)} skills, {len(REQUIRED_DOCS)} documents, "
        f"and {len(validation.passes)} structural/evidence checks."
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
